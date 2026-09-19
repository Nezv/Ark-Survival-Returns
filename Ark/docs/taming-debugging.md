# Taming, torpor and riding: debugging guide

Everything below refers to code that exists in this repository. Class names, method names and command
syntax are exact; run `./gradlew build` and `./gradlew runGameTestServer` before using this guide, and
`./gradlew runData` after touching a data provider.

Run configuration note: `AGENTS.md` says the user performs interactive client testing, so this guide never
asks you to launch `runClient`. Everything except the rendered seat and animation review can be reproduced
headlessly with the game tests and the operator commands.

## 1. Where the state lives

| State | Owner | Persisted | Synced to clients |
|---|---|---|---|
| Torpor, recovery delay, sedation phase, sleeping-player anchor | `TorporState`, a NeoForge data attachment on any `LivingEntity` | yes, with the entity | yes, at `taming.torporSyncIntervalTicks` |
| Taming progress, claimant, owner, feeding appetite, meals, attempt clock | `TamingState`, a data attachment | yes, with the entity | progress, appetite, meals, owner and claimant; the attempt clock stays server-side |
| Feeding truce | `TamingState`, a data attachment | no: `TamingState.deserialize` resets `truceFeeder` and `truceUntil` on every load | no: not in the attachment stream codec |
| Nine food/storage slots | `CreatureInventory`, written to the entity's `TamingInventory` child tag | yes | via the open menu only |
| Saddle | the entity's real `EquipmentSlot.SADDLE` | vanilla | vanilla equipment sync |

There is intentionally no `UUID -> state` map anywhere: attachment lookups are `TorporService.of(entity)`
and `TamingService.of(entity)`.

## 2. Execution paths

### 2.1 Sedative hit, validation, torpor, knockout, synchronization

1. The dose arrives from one of three places, all on the server: `SedativeItem.use` (consumed),
   `SedativeItem.hurtEnemy` (melee) or `SedativeArrow.doPostHurtEffects` (projectile).
2. Every one of them calls `TorporService.sedate(target, potency, source, cause)`. This is the only method
   that writes torpor.
3. `sedate` refuses ineligible targets (`TorporService.eligible`: dead, `ArmorStand`, creative or
   spectator players) and non-server levels, then refreshes the ceiling from
   `TorporService.maximumFor` and applies `TorporState.sedate(potency, resistance)`, which clamps to
   `[0, maxTorpor]` and restarts the recovery delay.
4. `TorporService.tick` is subscribed to `EntityTickEvent.Pre`; it calls `TorporService.tickEntity`,
   which is also the deterministic entry point used by the game tests. `tickEntity` advances the phase
   machine in `TorporState.tick`.
5. `TorporState.tick` returns `COLLAPSE_STARTED`, `TORPID_ENTERED`, `WAKE_REQUESTED` or `WOKE`.
   `TorporService.collapse` handles `COLLAPSE_STARTED`: it stops the ride, dismounts riders, clears the
   target and navigation, tells the creature (`CreatureEntity.onUnconsciousnessChanged`) and starts the
   collapse phase using the authored clip length from `CreatureTorporClips`.
6. `TorporService.sync` calls `Entity.syncData` every `torporSyncIntervalTicks` while the state is dirty,
   so observers see the same phase the server enforces.

### 2.2 Feeding interaction, food and hunger validation, consumption, progress, ownership

1. `Player` right-clicks the creature → `Mob.interact` → `CreatureEntity.mobInteract`.
2. Awake wild creature → `TamingService.feedByHand(creature, player, hand)`. It validates, in order: the
   taming-enabled switch and the server side (`EXCLUDED`), `TorporService.eligible` (alive, not an
   `ArmorStand`; `EXCLUDED`), an already-tamed creature (`ALREADY_TAMED`), a `KNOCKOUT` creature that is
   not feedable right now (`WRONG_STATE`), any restricted phase (`WRONG_STATE`), the claim
   (`NOT_CLAIMANT` for a live claim held by another player; a missing or expired claim passes and is
   transferred to the feeder), the food tag (`WRONG_FOOD`), feeding appetite (`feedHungerThreshold`,
   `NOT_HUNGRY`), then the feeding interval via `TamingService.cooldownElapsed` (`COOLDOWN`).
3. Only after every check passes does it call `ItemStack.shrink(foodUnitsPerMeal)`.
4. `TamingService.advance` records the meal, adds `progressPerMeal * (preferred ? multiplier : 1)`, marks
   the feeding animation through `TorporState.markFeeding` and completes the tame at 100 %.
5. `TamingService.complete` writes the owner (the claimant, never the last feeder), clears the claim and
   calls `CreatureEntity.onTamed`, which sets persistence, detaches the creature from wildlife counting and
   broadcasts the vanilla heart event.

### 2.3 Inventory feeding tick, eligibility, one meal, progress

1. `TorporService.tickEntity` → `TamingService.tick` on every torpor-tracked entity every tick.
2. For a `KNOCKOUT` creature the tick calls `TamingService.autoConsume`, which requires
   `TorporService.feedable` (phase `TORPID`), appetite above the threshold and an elapsed interval.
3. It scans `CreatureInventory` for the first accepted stack, consumes exactly one item and calls the same
   `advance` used by hand feeding, so ownership and completion cannot diverge between the two routes.

### 2.4 Mount request, permission, passenger attachment, rider input, movement

1. `CreatureEntity.mobInteract` while tamed: sneak-use opens `CreatureMountMenu`, plain use asks
   `CreatureRideController.canMount` (tamed, owned, saddled, awake, empty, not already riding) and
   `hasRoomForRider`, then calls `player.startRiding(creature)`.
2. `CreatureEntity.getPassengerAttachmentPoint` owns the seat; `positionRider` also aligns the rider's
   body yaw for the side-mounted rigs. Both run on the server.
3. `CreatureEntity.getControllingPassenger` exposes the rider only when the creature is conscious, tamed
   and saddled.
4. `CreatureEntity.getRiddenInput` / `tickRidden` / `getRiddenSpeed` read rider intent through
   `CreatureRideController.input`, which uses `ServerPlayer.getLastClientInput` on the server and the
   movement fields on the client, because a remote player's `xxa`/`zza` are never filled server-side.
5. `FlyingCreatureEntity.travel` and `AquaticCreatureEntity.travel` branch to `travelFlying` with rider
   input while ridden, and to plain physics while unconscious.

### 2.5 Movement and state to animation, and rider positioning

1. `CreatureEntity.registerControllers`, `FlyingCreatureEntity.registerControllers` and
   `AquaticCreatureEntity.registerControllers` all ask `CreatureAnimationBridge.sedationClip` first, so a
   restricted phase always wins over locomotion and attack.
2. `CreatureAnimationBridge` maps `COLLAPSING` → collapse clip, `TORPID` → feeding clip while
   `TorporState.feedTicks > 0` else the unconscious loop, `WAKING_*` → the wake clip, and returns null when
   the caller should choose its ordinary clip.
3. Ridden creatures use real movement (`state.isMoving()`), not AI navigation state, so a mounted creature
   can never be stuck in its idle clip.
4. The client renderer only draws the model in the authored 180-degree corrected space; it never decides a
   rider position.

### 2.6 Save, load, restoration

1. `TorporState` and `TamingState` implement `ValueIOSerializable` and are written by
   `Entity.saveWithoutId` through the attachment system.
2. `CreatureEntity.addAdditionalSaveData` writes the nine inventory slots with `ContainerHelper` under the
   `TamingInventory` child.
3. `CreatureEntity.readAdditionalSaveData` restores them before the wildlife controller loads, and
   `TamingService.tick` re-applies persistence for a tame that came from disk
   (`CreatureEntity.applyTameState`).

## 3. Operator diagnostics

All commands require gamemaster permission; no ordinary player can reach them.

```
/arktaming inspect <entity>
/arktaming torpor <entity> <value>
/arktaming hunger <entity> <value>
/arktaming reset <entity>
/arktaming mount <entity>
/arktaming roster
/arktaming log <true|false>
```

| Command | What it proves |
|---|---|
| `inspect` | Registry id, size band, method, target duration, meals, derived progress per meal, resistance, accepted food, torpor/maximum/ratio/wake threshold, phase, recovery delay, taming summary, seat profile, rider list |
| `torpor` | Drives a real knockout or wake through `TorporService`, so the phase machine and the animation phase agree |
| `hunger` | Reproduces the hungry and not-hungry conditions without waiting for appetite to recover |
| `reset` | Clears sedation and abandons the attempt, for a stuck encounter |
| `mount` | Controller, movement mode, seat transform, dismount offsets and current riders |
| `roster` | Static audit plus resolved diet audit; fails loudly if a creature, a food tag or a seat is missing |
| `log` | Enables `TorporService.log` transition logging (state changes and rejected actions, never per tick) |

The debug spyglass (`docs/debug-spyglass.md`) is not a taming diagnostic: `DinoDebugSnapshot` never
calls `TamingService.summary`, so it has no readable torpor or taming row. The attachment state is not a
field of the creature either, so it can only appear indirectly and unformatted among the recursively
listed `saved.*` data, and only when the attachment exists; use `/arktaming inspect` for taming state.

## 4. Breakpoints for the reported symptoms

| Symptom | Set the breakpoint at | What to check |
|---|---|---|
| Food consumed without progress | `TamingService.advance` and `TamingService.complete` | If they never run, the failure was a rejected validation in `feedByHand`; the `Result` tells you which rule refused |
| Torpor that does not decay | `TorporState.tick` | Confirm `recoveryDelay` reaches zero and that `TORPOR_RECOVERY_PER_SECOND` is not zero in the save's config |
| Creatures acting while unconscious | `TorporState.restricted`, `UnconsciousBehavior.canUse`, `CreatureEntity.mobInteract` | A goal that keeps MOVE/LOOK/JUMP/TARGET without `UnconsciousBehavior` claiming the flags, or an entity whose attachment was never created |
| Lost ownership after reload | `TamingState.deserialize`, `CreatureEntity.applyTameState` | The owner UUID is written as a string; a malformed value is dropped silently by design |
| Rider position clipping | `CreatureEntity.getPassengerAttachmentPoint`, `docs/taming-seat-manifest.json` | Compare the local offset with the manifest row; the "Known model defects" section lists the species whose mesh is not normalised |
| Mounted creature playing idle animations | `CreatureAnimationBridge.sedationClip`, `FlyingCreatureEntity.registerControllers` | The ridden branch must be reached before the idle fallback; check `riddenTicks` and `isRidden` |
| Client and server disagree | `TorporService.sync`, `TamingService.sync`, `TamingAttachments` | The attachment sync handlers are the only channel; a phase that changes without `syncData` reaching the client means `dirty` was never set |
| Inventory duplication | `CreatureMountMenu.quickMoveStack`, `CreatureInventory.deserialize`, `CreatureEntity.dropCustomDeathLoot` | Shift-click paths mirror the vanilla horse menu; death drops the contents exactly once through `dropCustomDeathLoot` |

## 5. Verification matrix

Automated coverage is `./gradlew test` (35 JUnit tests) and `./gradlew runGameTestServer`
(32 required game tests, of which 12 are the taming suite registered from `TamingGameTests`). The 32 are
the 31 test instances generated into `src/generated/resources/data/arksurvivalreturns/test_instance/`
plus one shipped by the loader in the `minecraft:default` environment.

| Test | Status | Where |
|---|---|---|
| Sedate a player and a creature | automated; ordinary vanilla mobs are covered by `taming_ordinary_mob` | `taming_player_sedation`, `taming_torpor`, `taming_ordinary_mob` |
| Apply further sedative → sleep extends, taming does not increase | automated | `taming_torpor` |
| Let torpor recover → wake at the configured threshold | automated | `taming_torpor` (exact threshold check both sides) |
| Feed wrong food / full creature → no consumption, no progress | automated | `taming_passive_feeding` |
| Feed a passive small herbivore → progress while awake | automated | `taming_passive_feeding` |
| Feed an awake large herbivore → knockout taming rejected | automated | `taming_knockout_feeding` |
| Feed an unconscious eligible creature → individual meals over time | automated | `taming_knockout_feeding` |
| Wake before completion → attempt resets, deposit kept | automated | `taming_wake_before_completion` |
| Feed a hungry aggressive flyer fish → accepted, feeder truce applies | automated: hunger-gated acceptance, a feeder-only truce, the theft check it suppresses (against a control proving the feeder was otherwise a valid thief), attack cancellation, window expiry, and the fact that a reload drops it | `taming_aerial_feeding` |
| Complete taming → correct owner, no forced premature waking | automated: the finished tame stays asleep and feedable, and leaves torpor only through ordinary recovery; it is `taming_completion`, not the feeding suites, that asserts this | `taming_passive_feeding`, `taming_knockout_feeding`, `taming_completion` |
| Second player feeds the final meal → ownership not stolen | automated: a live claim is refused to everybody else, and an automatic knock-out tame keeps the depositing claimant even after the lease lapses | `taming_passive_feeding`, `taming_claim_expiry` |
| An abandoned claim expires → the player who resumes it takes the attempt over | automated | `taming_claim_expiry` |
| Ride every registered creature → controls, seat, animations | partly automated: `taming_riding` mounts, seats, sedates and dismounts all 41 species with a real passenger; rendered seat and clip review needs the client | `taming_riding` |
| Knock out rider or mount → safe dismount, movement transition | automated | `taming_riding` |
| Reload chunk/server → state and inventory restored | automated | `taming_persistence` |
| Reconnect an unconscious player → sedation cannot be bypassed | partly automated: the sleeping anchor is persisted for as long as the player stays unconscious and is cleared when the restriction lifts or on a reset; the reconnect itself needs a client | `taming_player_sedation`, `TorporState` |
| Observe from a second client | not verified: needs two real clients. The state is synchronized through attachment sync, and every decision is server-side | manual |
| Launch a dedicated server → no client-only loading | automated: `runGameTestServer` is a dedicated server, and the common code has no client imports | `runGameTestServer` |

### Known gaps and manual checks

- **Rendered seats and animations.** Seat transforms are measured from the real bones, and
  `tools/build_taming_manifests.py` checks each seat against the XZ bounds of its own model mesh
  (`seat_inside_mesh`; 0 of 41 outside). The `taming_roster` and `taming_riding` game tests assert only
  that the seat height is above the entity's feet and no higher than the registered height. Full hitbox
  containment is not asserted, so whether the rider visually sits *on* the back needs the client. The same
  applies to the collapse, torpor loop, feeding and wake clips.
- **Model normalisation.** 24 of 41 meshes are not normalised to their entity origin, so the mesh-based
  seat check is not equivalent to the entity hitbox and the rider is not reliably aligned with the visible
  back. See the end of `docs/taming-roster.md`.
- **Shift-click duplication.** `CreatureMountMenu.quickMoveStack` mirrors the vanilla horse menu, but the
  drag/swap paths are only exercised by hand in a client.
- **`verify_assets.py`** fails on a single pre-existing assertion:
  `assert not list(generated/data/arksurvivalreturns/neoforge/biome_modifier/*.json)` while `ArkData`
  itself generates `remove_fantasy_spawns` and `remove_fantasy_features`, both committed at HEAD. Every
  assertion before that line passes, including the per-creature animation clip comparison.

## 6. Generating the audits again

```bash
python tools/import_creatures.py            # copies runtime clips, including the torpor sequences
python tools/build_taming_manifests.py      # seat manifest, animation matrix, CreatureSeats.java, CreatureTorporClips.java
python tools/build_taming_roster.py         # docs/taming-roster.md
python tools/verify_assets.py               # packaged asset graph (see the known gap above)
./gradlew runData build runGameTestServer   # datagen, unit tests, headless game tests
```
