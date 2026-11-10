# Audit, phase 2: bugs and bad design

Written 2026-11-10 by Claude Fable 5 for the user, to be dispatched plan by plan to an agent (Opus).
Dashboard row: A02. Phase 1 is `Audit/01-optimization-plans.md`; phase 3 (recipes and features for a
two-player world) is a separate document, and what this phase found for it is in section 8.

Nothing in the game was changed and nothing was run in the client or on a server for this document. It was
done in two passes. The first read the systems a player touches most. The second sorted what the first found
into types of failure (section 3) and went through the code again type by type, with sweeps that can be run
again: `python Ark/tools/audit_static.py`.

Every finding is marked by how it is known:
- **read**: seen in Ark's source or in the Minecraft 26.1.2 source;
- **computed**: numbers worked out from what was read; the plan's first step confirms them in the game;
- **reasoned**: a consequence that nobody has seen in a client yet.

Read in full: taming, torpor, riding, companion orders, tribe, downed and revive, player levels, mass and
cargo, work orders, the tech tree service and its tree, both guardians, supply drops, workstation crafting,
the Ark inventory, camp, the creature entities (land, air, water), the wild routine's target rules, the seven
block entities, the theme guards. Section 9 lists what was left out.

---

## 1. What stands out

1. **Taming and riding, the centre of the game, have never been played end to end, and both break on the
   first try.** A knock-out tame cannot finish inside one sleep for 24 of 30 species with plain food. A tame
   keeps the pose it had when tamed. Ridden speed is wrong in all three realms. Getting off a large mount
   hurts or kills. A mount cannot attack. All of it passed the GameTests, because the tests set the state they
   should have reached (type T10).
2. **Progress depends on those paths.** The Prehistoric gate needs a tame that works (a Triceratops or an
   Ankylosaurus, both knock-out tames), and the Bronze gate a tamed Therizinosaurus.
3. **Two players are not yet a case the game handles.** Revive, shared tames and shared progress all hang on
   an FTB Teams party that nothing asks the players to make; tames attack a tribe mate who clips them; a
   dedicated server has never been started.
4. **The static picture is good.** No client code in common classes, every resource the code names exists,
   every Ark item has a source, every config value is read, block entities save and drop what they hold,
   commands and payloads are checked on the server (section 3.2).

---

## 2. The findings at a glance

Severity: A breaks a core loop, B is wrong or unfair in normal play, C is a rough edge or dead weight.
Size: S one agent session, M a few, L a long effort. Type: section 3.

| ID | Finding | Sev | Type | Known by | Size |
|---|---|---|---|---|---|
| BUG-01 | A knock-out tame cannot finish inside one sleep for most species | A | T1, T6 | read, computed | M |
| BUG-02 | A tamed creature keeps the look of the moment it was tamed | A | T2 | read | M |
| BUG-03 | Ridden speed: ground 2 to 3 times too fast, flight about 147 blocks a second, swimming 2 | A | T4, T3 | read, computed | M |
| BUG-04 | Dismounting drops the rider from the top of the hitbox (42 blocks off the Titanosaur) | A | T3 | read | S |
| BUG-05 | A ridden mount cannot attack and does not defend its rider | A | T6 | read | M |
| BUG-06 | No ally rules: a tame attacks a tribe mate who hits it; owners and mates can kill tames | A | T3 | read | M |
| BUG-07 | Revive needs an FTB Teams party that nothing prompts the players to make | A | T9 | read | S |
| BUG-08 | A downed player cannot be finished for a minute, cannot give up, and looks unharmed | B | T1, T11 | read | M |
| BUG-09 | Large tames cannot pass trees, ridden or following | B | T5 | read | M |
| BUG-10 | The WORK order stalls on any target that is not under the worker | B | T5 | read, computed | S |
| BUG-11 | A right-click with Narcotics in hand knocks the player out for about 170 seconds | B | T1 | read, computed | S |
| BUG-12 | A wild herd can follow the tamed animal that used to be in it | B | T2 | read | S |
| BUG-13 | Withdrawn: the Bedroll is a level 0 craft, so the first age has a respawn point | - | - | read | - |
| BUG-14 | Tribe-keyed progress does not follow the players into the party they form | B | T8 | read | S |
| BUG-15 | The Giganotosaurus Guardian can never be woken, and the Allosaur Heart still drops | C | T7, T12 | read | S |
| BUG-16 | No use, no effect: nest eggs, the zone-3 map unlock, the breeding permission | C | T6, T7 | read | S |
| BUG-17 | Sea and air taming are unlikely to complete in survival | B | T14 | reasoned | M |
| BUG-18 | A dedicated server has never been started | B | T14 | read | M |
| BUG-19 | Smaller defects and stale records | C | mixed | read | S |
| BUG-20 | The target health bar is titled with a raw translation key | B | T12 | read | S |
| BUG-21 | The level gates put most saddles out of reach | B | T1 | computed | S |
| BUG-22 | Follow distance and cargo radius are measured from the animal's centre | C | T5 | read, computed | S |
| BUG-23 | The trough empties in minutes and feeds nothing that matters | C | T1, T6 | computed | S |
| BUG-24 | Dried food made on different days does not stack | C | T12 | read | S |
| BUG-25 | Beating the first boss unlocks nothing | B | T6 | read | S |
| BUG-26 | Curios is used without a guard; two menus are left over | C | T15, T12 | read | S |

---

## 3. Types of failure

The first pass found bugs one by one. Sorted, they fall into fifteen types. Each type has a way to look for
more of the same, and the second pass did that for all of them. The table says what each sweep found; a type
is a better unit of work than a bug, because a fix that only closes the instance leaves the next one of its
kind to be written.

### 3.1 The types

| | Type | What it is | Instances | How to sweep | Result of the sweep |
|---|---|---|---|---|---|
| T1 | Numbers never run together | Each value is sensible; together they contradict | BUG-01, 08, 11, 21, 23 | Write the loop as arithmetic from the config defaults (`audit_static.py taming`, `levels`) | Three new: level gates, trough, Narcotics dose |
| T2 | State that outlives its owner | One system leaves state behind when another takes the creature over | BUG-02, 12 | List every writer of each synced field and every hand-over (tame, wake, mount, load) | Flight phase, nest and night flag added to BUG-02; a sea creature's gravity flag (BUG-18) |
| T3 | A vanilla default nobody chose | A method Ark did not override, whose default does not fit a 13-block animal | BUG-04, 06, 03 (swim) | Grep for the overrides a tameable mount needs | Leads, pushing, fall damage, logging out on a mount (BUG-19) |
| T4 | A formula outside its domain | A value tuned for one use fed to another | BUG-03 | Follow each tuned value to all its readers | None beyond BUG-03 |
| T5 | A point where a body is | Distance or reach measured from the centre of an animal up to 30 blocks wide | BUG-09, 10, 22 | Grep `distanceTo`, `distanceToSqr`, `distSqr` in tame code | Follow distance, cargo radius, group whistle range |
| T6 | A system without its last step | Everything is built except the verb or the consumer | BUG-05, 16, 23, 25; sedatives in BUG-01 | `audit_static.py items` (what goes into nothing), flags nothing reads | Boss reward, trough hunger |
| T7 | Unreachable by dependency or default | Needs a mod that is not in the pack, or a setting that is off | BUG-15, 16 | Grep `isLoaded`, config flags with false defaults, `future` tech nodes | 20 `future` tech nodes (known, F17); nothing new |
| T8 | Keys that change under the data | Progress keyed by an id that changes when the players team up | BUG-14 | List every saved store and its key | Three stores keyed by team id; the map unlock by player |
| T9 | A precondition the game never states | The rule exists, the player is not told | BUG-07 | Read each refusal message and ask where the player learned the rule | Level gates are shown on the bench; nothing new |
| T10 | Tests that set the state they should reach | A test pins torpor, calls the harvest directly, asserts a mount without moving it | BUG-01, 03, 10 | For each A finding, find the test that passed | Every A finding passed its tests (section 4) |
| T11 | A server rule without a client half | Enforced on the server, not predicted or shown on the client | BUG-08 | Grep `isClientSide` branches in items and interactions | Small prediction mismatches only (BUG-19) |
| T12 | Left behind by a rename or a replacement | The old consumer of something that moved | BUG-20, 24, 15, 26 | `audit_static.py lang`, `lone`, `ids` | Health bar key, day stamp, smithing menu |
| T13 | Reach that is too wide | A scan that takes more than it should | BUG-19 (cargo) | Grep `instanceof Container`, wide `getEntitiesOfClass` | Cargo only |
| T14 | Never run where it will run | No dedicated server, no second client, no real terrain for giants | BUG-17, 18 | The Dashboard's "unverified in client" rows | 48 rows say unverified; the ones that matter are named in BUG-18 |
| T15 | Optional that is not | The manifest says optional, the code calls it without a guard | BUG-26 | Grep imports of each optional mod against `isLoaded` guards | Curios in the inventory menu; Tom's Storage is guarded |

### 3.2 Swept and found clean

- **Side safety.** No class outside `client/` imports `net.minecraft.client`; the nine mixin configs keep client
  mixins under `"client"`; the two common mixins in the sound package touch no client class.
- **Resources.** Every `ArkSurvivalReturns.id("...")` that names a resource resolves in the built jar
  (`audit_static.py ids`: 0). 113 literal translation keys, 1 missing (BUG-20).
- **Config.** 224 server values, all read (`config`: 0).
- **Items.** 208 Ark items; none is unobtainable. 23 have no craft or loot table and are made by code or the
  world (bushes, nests, ores, sulphur, loose rocks, dried meat, the crate, the two boss items, the heart).
- **Data per species.** 41 entity loot tables, 41 spawn tags, the seven taming tags: all present and non-empty.
- **Block entities.** Stone Fire, Primitive Forge, Drying Rack, Trough, Cooking Pot, Crusher, Storage Crate:
  each saves what it loads and drops its contents when removed, by hand, by its other half or by an explosion.
- **Commands.** Nine roots, all behind the gamemaster level except `/arktribe status` and the owner's `perm`.
- **Payloads.** The eight gameplay handlers check on the server what the client sent (owner, range, open menu,
  rate). A downed or unconscious player can still craft and whistle: low, in BUG-19.
- **Static state.** Every static map in gameplay code is cleared on server stop or is keyed weakly by level.
- **Tech triggers.** All 37 live nodes use a trigger kind that some code emits.

### 3.3 Types not swept

Concurrency between world generation threads and saved data; behaviour of the client screens when a second
player changes what they show; long-run growth of saved data; anything about frame rate (phase 1).

---

## 4. Rules for whoever executes a plan

The rules of phase 1, section 4, hold. Three more, because these are bugs:

1. **The first step of every plan is a GameTest that fails today for the reason given.** It must reach the
   state the way a player would: no `setTorpor` before a meal, no direct call of the method under test, real
   doses, real ticks. If it cannot fail, the finding is wrong: say so and stop.
2. Build and the GameTests are run for each plan (they are bug fixes, C06), and the run is named in the
   Dashboard row. The Dashboard row to rewrite is given with each plan.
3. A finding marked computed or reasoned is confirmed in the game before code is written. Client checks go
   through `tools/session_run.py`; ask the user before a run longer than a few minutes.

---

## 5. The plans

### 5.1 Taming

#### BUG-01 A knock-out tame cannot finish inside one sleep

**Evidence (read).** `TorporState.tick` (taming/TorporState.java:219) recovers `maximum * torporRecoveryPerSecond / 20`
a tick once `torporRecoveryDelayTicks` has passed, and asks to wake below `wakeThresholdRatio`. With the defaults
of `Config.java` (delay 200 ticks, 0.005 a second, wake at 0.20) an animal put fully under sleeps 10 + 160 =
**170 seconds, whatever its size**. Feeding needs `ceil(targetSeconds * 20 / 400)` meals 20 seconds apart
(`CreatureTamingProfile.mealsPerTame`); a favourite food counts 1.5.

**Computed** (`audit_static.py taming`): with plain food 24 of the 30 knock-out species wake before their last
meal, every one of 210 seconds or more. With the favourite food six still do: Tyrannosaurus, Giganotosaurus,
Acrocanthosaurus, Spinosaurus, Mosasaurus, Tusoteuthis (last meal at 220 s).

Three more rules close the ways out:
- Waking resets the attempt to zero: `TamingService.onWakeWithoutCompletion` calls `resetAttempt`
  (TamingService.java:251).
- Any hit costs 10 points of progress (`TamingEvents.damaged`, `damageProgressPenalty`), and both ways of adding
  torpor are hits: `SedativeArrow.doPostHurtEffects` and `SedativeItem.hurtEnemy`. One more arrow on a
  Tyrannosaurus buys about 27 seconds of sleep and costs 24 to 36 seconds of feeding.
- Sedatives put in the taming inventory are accepted (`CreatureInventory.canPlaceItem`) and never used:
  `countSedatives` has no caller, and nothing in `TamingService.tick` or `TorporService` reads them.

**Why it matters beyond taming.** The tech node `scavenge` (a tame that works) is on the way to the Prehistoric
gate, and only the Triceratops and the Ankylosaurus work: both are 240 second knock-out tames whose favourite
is the carrot, found only in villages (F37). The Bronze gate needs a tamed Therizinosaurus (240 s, apple).

**Why the tests passed.** `TamingGameTests` (lines 341 to 346, 388, 604, 672) sets hunger to 100 and torpor to
the maximum before every meal.

**What changes.** Sedatives in the taming inventory keep the animal under; a sedative never costs progress; a
tame that wakes keeps part of its progress.

**Steps.**
1. GameTest `knockout_natural`: for one species of each duration (120, 210, 240, 360) knock it out with
   `TorporService.sedate` doses of an arrow's potency, put a stack of plain food and nothing else in the
   inventory, tick only. Assert it is tamed. Fails today for 210 and above.
2. In `TamingService.tick`, for an unconscious creature with a claimant: when torpor is within one dose of the
   wake threshold and the hold has a `taming/sedative` item, consume one and call `TorporService.sedate` with
   that item's potency (no damage event). Narcotics 100, narcoberry 25, an arrow its own potency.
3. In `TamingService.onDamaged`, skip the penalty when the source is a `SedativeArrow` or a hit with a
   `SedativeItem`; keep it for every other damage.
4. In `onWakeWithoutCompletion`, replace the reset by the user's choice (section 7.2): keep a share, or let it
   decay as the passive method does (`decay`).
5. Show the torpor bar and what the hold will do in `CreatureMountScreen` (the data slots are already synced).
6. Second GameTest: the same species with Narcotics in the hold completes; without them and with plain food the
   360 second species does not, and its progress follows the rule of step 4.

**Done when.** Both tests pass for all 30 species; one Tyrannosaurus is tamed in a real client.
**Dashboard.** P19, rewritten in place. **Risks.** Taming becomes easier than intended if sedatives are cheap:
5 narcoberries make 2 Narcotics (P23); the user tunes the dose.

#### BUG-02 A tamed creature keeps the look of the moment it was tamed

**Evidence (read).** The synced behaviour, action and night flag are written only by the wild routines
(`WildlifeGoal`, `AquaticGoal`), the flight phases, `WorkGoal` and `onUnconsciousnessChanged`. `CompanionGoal`
never calls `setBehavior`, `setAction` or `setNightActive`, and `CreatureEntity.onTamed`
(creature/CreatureEntity.java:155) resets none of them. `WildlifeGoal.canUse` is false once tamed, so the last
values stay. The animation controller and the sounds read them (`running`, `eating`, `idleClip`, `hunting`,
`ambientSoundRole`):
- knocked out while fleeing or chasing, the usual case: waking sets the behaviour to ROAM but the action stays
  BOLT or CHASE, so the tame always uses its run clip;
- fed by hand while grazing: it plays the eating clip, and the eating sound, whenever it stands;
- tamed while resting: it lies down every time it stops;
- a hunter knocked out at night keeps the night flag, so its eyes burn red by day (F23);
- a flyer keeps its flight phase: `FlyingCreatureEntity.tickFlight` returns for a tame before any phase logic
  (line 153). A bird fed on its perch follows its owner in the perch pose until it is ridden or hurt; after a
  ride it stays in TAKEOFF and only hovers. It also keeps its nest, so a tribe mate taking that nest's egg
  puts the tame into the defence phase.

None of these fields is saved, so loading the chunk again hides the bug: a tame looks right after a relog.

**What changes.** Taming hands the creature over cleanly, and the companion routine owns its presentation.

**Steps.**
1. GameTest: tame one land creature from each of FLEE, FORAGE, REST and a night HUNT, and one flyer from PERCH;
   assert behaviour, action, night flag and flight phase afterwards. Fails today.
2. `onTamed`: behaviour ROAM, action IDLE, night flag off; `FlyingCreatureEntity` overrides it to set the phase
   to ROAM and forget nest, thief and lap.
3. `CompanionGoal.tick` ends with a presentation step: action WALK below a pace threshold and RUN above it
   while travelling, IDLE when holding, REST after a minute on a STAY anchor, behaviour DEFEND while it fights.
4. Flyers: tame phases CRUISE (travelling), HOVER (holding in the air) and GROUNDED (holding within reach of
   the ground: gravity on, ground idle clip), chosen in the tamed branch of `tickFlight`.

**Done when.** The test passes; one land, one sea and one air tame are looked at in the client before and after
a relog. **Dashboard.** P19 and P20.

#### BUG-11 A right-click with Narcotics knocks the player out

**Evidence (read, computed).** `SedativeItem.use` (taming/SedativeItem.java:31) doses the user at once.
Narcotics are 100 and the player's ceiling is 100: one right-click on air is a collapse and about 170 seconds
unconscious. A narcoberry is 25, so four clicks do the same. It is the item a player holds while taming.
**Steps.** Self-use only while sneaking, with a use time like eating; a right-click on an unconscious creature
gives it the dose with no damage (this is also the hand route for BUG-01). GameTest for both.
**Dashboard.** P19.

#### BUG-12 A wild herd can follow a tamed former mate

**Evidence (read).** A tame keeps its `packId`. `WildlifeGoal.decide` builds `packCache` from every living
creature with that id and `leader()` takes the lowest entity id, tamed or not; `receiveAlarm` and
`receiveHerdThreat` are also sent to tamed mates.
**Steps.** A new `packId` in `onTamed`; `!c.isTamed()` in the pack scan. GameTest: a herd of three, tame the one
with the lowest id, walk it away, assert the others do not follow. **Dashboard.** P00.

#### BUG-17 Sea and air taming (reasoned)

All six sea species are knock-out tames. Arrows lose two fifths of their speed every tick in water, the body
sinks, and feeding takes 90 to 360 seconds under water with no breathing gear in the pack. The five flyers are
fed by hand (`TamingMethod.AERIAL`): they can be reached only on the nest for a few seconds at a time, nine
meals 20 seconds apart, and a knocked-out bird cannot be fed at all (`interactWhileUnconscious` refuses every
method but knock-out). Tamed flyers never land (BUG-02, step 4).
**Steps.** One recorded attempt at a Megalodon and at a Pteranodon with `session_run.py` before any redesign;
then the user decides (section 7.6). **Dashboard.** P19, P20.

### 5.2 Riding

#### BUG-03 Ridden speed is wrong in all three realms

**Evidence (read, computed).** Ark sets a creature's speed attribute to `sqrt(ratio * 5.612 / 43.17)`
(`MovementTuning.attribute`) because a mob's own forward input equals its speed, so its pace goes with the
square. A rider's input is 1, so `tickRidden` and `getRiddenSpeed` (CreatureEntity.java:218 to 229) give a pace
of about 44 times the attribute (`LivingEntity.getFrictionInfluencedSpeed`, sources line 2755):

| Species | Own top speed | Ridden |
|---|---|---|
| Default (ratio 0.85) | 4.8 blocks/s | 14.6 |
| Brontosaurus (1.05) | 5.9 | 16.2 |
| Tyrannosaurus (1.8) | 10.1 | 21.3 |
| Velociraptor (2.2) | 12.3 | 23.5 |

Flight: `FlyingCreatureEntity.travel` (line 389) calls `travelFlying(input, max(0.05, attribute * 2))`. Vanilla adds
that much speed every tick and keeps 0.91 of it (`LivingEntity.travelFlying`, line 2498), so a ridden flyer
settles near 0.66 / 0.09 = 7.4 blocks a tick, about **147 blocks a second**; vanilla's own ridden flyer adds
about 0.016 a tick. Swimming: `AquaticCreatureEntity.travel` (line 46) passes a speed that vanilla ignores in
water, where the push is a fixed 0.02: every ridden sea creature swims at **2 blocks a second**, and
`riddenSwimSpeedMultiplier` does nothing. No test rides anything; the tests assert who the controller is.

**Steps.**
1. Unit test of the kinematics (no client): a pure function `RideKinematics.settledSpeed(mode, attribute, config)`
   that reproduces the three vanilla updates; assert today's numbers, then the targets.
2. `CreatureRideController.target(creature)`: own top speed in blocks a second (from `MovementTuning`) times a
   ridden factor per mode from config (ground 1.15, flight 1.6, swim 1.2 as first values).
3. Ground: `getRiddenSpeed` returns `target / 44`. Flight and swim: stop using the two-argument `travelFlying`;
   call the four-argument form with acceleration `target / 20 * (1 - drag)` and the matching drag for air and
   water, so the settled speed is the target in both.
4. One scripted ride per realm in the client (`session_run.py`, a new `ride` scenario: mount, hold forward 10 s,
   record distance); the recorder already logs positions.

**Done when.** The unit test holds each realm within 10 % of its target and the user has ridden one of each.
**Dashboard.** P19. **Risks.** On a server, vanilla rejects a vehicle that moves more than 10 blocks in a tick
(`ServerGamePacketListenerImpl.handleMoveVehicle`); today's flight is under that by a quarter.

#### BUG-04 Dismounting drops the rider from the top of the hitbox

**Evidence (read).** `CreatureEntity` does not override `getDismountLocationForPassenger`; the vanilla default
(`Entity.java:3536`, called from `LivingEntity.dismountVehicle`) is the top centre of the bounding box, and an
entity does not hold a player up. The boxes come from `ModContent` (`sized(s.width, s.height)`): Titanosaur
30 x 42 blocks, Giganotosaurus 10.5 x 15, Acrocanthosaurus 14.4 tall, Tyrannosaurus and Spinosaurus 13.5,
Brontosaurus 11. The fall is that height: 8 to 12 damage from the large ones, death from the Titanosaur (above
the overkill line, so no downed state). The same fall happens when a mount dies under its rider. The measured
dismount offsets in `CreatureSeats` are used only when the mount is knocked out (`TorporService.dismountRiders`).

**Steps.** GameTest: mount and dismount every species on flat ground, assert the rider stands within 2 blocks
of the ground beside the animal. Override `getDismountLocationForPassenger`: try the profile's ground and side
offsets (`CreatureRideController.findSafePosition`, widened with vanilla's `DismountHelper`), fall back to the
feet position; in the air or in water keep the seat position and reset the rider's fall distance. Do the same
in `die`. **Dashboard.** P19.

#### BUG-05 A ridden mount cannot attack and does not defend its rider

**Evidence (read).** `strike` is called only by the AI goals (`WildlifeGoal`, `AquaticGoal`, `CompanionGoal`,
`GuardianCombatGoal`). `CompanionGoal.tick` (line 55) stands aside while ridden, and no payload carries a
rider's attack. A wild hunter targets the rider (`WildlifeGoal.prey`: a player is prey), who sits 11.6 blocks
up on a Tyrannosaurus and 33.6 on a Titanosaur (`CreatureSeats`), out of reach of anything on the ground.

**Steps.** A key (the attack key while mounted) sends a payload; the server checks controller, cooldown and
`canStrike`, takes the nearest valid target inside the mount's melee reach in the facing arc and calls
`strike`. A config flag sends melee damage aimed at the rider of a large mount to the mount instead. GameTest:
a mounted fake player triggers the handler, the target takes the mount's damage on the hit frame.
**Dashboard.** P19, I11.

#### BUG-09 Large tames cannot pass trees

**Evidence (read).** `setTrampling` (CreatureEntity.java:441) allows it only for a predator, and only
`WildlifeGoal` (line 610) turns it on, in a pursuit. A tame's wild routine is stopped, so a followed or ridden
Tyrannosaurus (8.4 blocks wide) stops at the first trunk, and tames never teleport. Herbivore giants never
trample. The Titanosaur is placed only on 30 x 30 nearly flat, treeless ground (`SpawnRules.groundFits`), which
was checked on the flat benchmark world only.

**Steps.** Trample for any body 3 blocks wide or more while ridden or while following, under the same config
flag and the mob griefing rule; `CreatureNavigation` already plans through trees when the flag is set. Count
giant placements on real terrain: `/arkwildlife` over a pre-generated Tectonic area, per species. Bring the
count to the user with the question of narrower collision boxes (section 7.4). **Dashboard.** B08, P21.

### 5.3 Tames in the world

#### BUG-06 No ally rules for tames

**Evidence (read).** `CompanionGoal.isFriend` (line 81) knows the owner and the owner's own tames. Nothing in
the mod overrides `isAlliedTo`, implements `OwnableEntity` or filters damage between allies. So a tribe mate
who clips a tame is attacked by it; a mate who hits the owner is attacked by every tame within 16 blocks; two
players' tames fight after one stray hit; and nothing stops an owner's sweep from killing a tame.

**Steps.**
1. GameTest in `TribeGameTests`: two players in a party, B hits A's tame; assert the tame does not target B.
2. `CreatureEntity implements OwnableEntity` (`getOwnerReference` from `TamingState.owner`); `isAlliedTo` true
   for owner, tribe members and their tames; `isFriend` by `TribeService.sameTribe`.
3. A `LivingIncomingDamageEvent` rule cancels damage from owner and tribe to a tame, and from a tame to them,
   unless `tribe.friendlyFire` is on (default off).
4. Check what Better Combat spares in a swing (its relation config) and set it for the pack.

**Dashboard.** P01, P19.

#### BUG-10 The WORK order stalls

**Evidence (read, computed).** `WorkGoal` (line 99) needs the worker's centre within 2.5 blocks of the target's
centre (`REACH_SQR`). The Ankylosaurus is 4.2 wide and the Triceratops 5.0, so a solid target at foot level or
above is at least 2.6 and 3.0 away: never reached. Only blocks under the body are. Targets are drawn at random
in a 25 x 5 x 25 volume (`findTarget`) with no check that a face is open, and a target is dropped only when it
stops being valid, so one wall block freezes the job. The tests call `harvest` directly.

**Steps.** GameTest that runs the goal itself beside a stone wall and asserts a harvest within 30 seconds.
Reach by distance from the bounding box plus 1.5; targets with at least one open face; give up a target after
10 seconds and exclude it for a minute. **Dashboard.** P03.

#### BUG-22 Follow distance and cargo radius are measured from the centre

**Evidence (read, computed).** `CompanionGoal.follow` (line 101) stops when the centres are 6 blocks apart
(`followStopDistance`). A Giganotosaurus is 5.25 blocks from centre to flank, so it stops half a block from its
owner; a Titanosaur (15) can never stop, it has to stand on the owner, and bodies push each other.
`CargoTransferService.transfer` (line 54) looks for storage within 8 blocks of the animal's centre block: for a
Titanosaur that is inside the body, where no chest can stand. `CompanionWhistle.whistleAll` measures its range
from the centre while `whistleOne` adds half the width.
**Steps.** One helper `bodyDistance(creature, point)` (the wild routine has it: `WildlifeSenses.bodyDistance`);
use it in all three. GameTests with the Titanosaur. **Dashboard.** F22, P03.

#### BUG-23 The trough

**Evidence (computed).** Every creature's hunger rises 1 a second (`TamingService.tick`, line 54). The trough
feeds a tame at 60 and takes 30 off (`TroughBlockEntity.feedNearby`, line 80), so each tame eats every 30
seconds: about 110 items an hour. A full trough (32) lasts one tame 17 minutes and four tames four. A feeding
heals 2 health; hunger does nothing else to a tame.
**Steps.** Decide what a tame's hunger is for (section 7.7). Without a use: feed only a hurt tame. With one:
slow the rise for tames by a config factor (first value 0.1). **Dashboard.** P04.

### 5.4 Two players

#### BUG-07 Revive needs a party nobody is told to make

**Evidence (read).** `FiberBandageItem.interactLivingEntity` (camp/FiberBandageItem.java:47) refuses the revive
unless `TribeService.sameTribe` (`downed.requireTribe`, default true). A tribe is an FTB Teams party; two fresh
players are in two solo teams. The only hint is in `/arktribe status`, while the downed message says "a tribe
member can revive you". The same party gates shared tames, the XP share and shared tech progress.

**Steps.** `requireTribe` defaults to false. On a player's first join while another player is online and
neither is in a party, both get a chat line with a click action that creates and joins the party
(`tribe.autoPrompt`, default on). A Camp quest completes when the player is in a party. GameTest: two fake
players without a party, one downed, the other revives. **Dashboard.** P01, P02.

#### BUG-08 The downed state

**Evidence (read).** `DownedPolicy.bleedOut` (recovery/DownedPolicy.java:12) takes one tick off a 1200 tick
window per point of damage, and all damage is cancelled meanwhile (`DownedHandler.incoming`), so a downed
player cannot be killed for a minute while hunters keep striking (`WildlifeSenses.validTarget` does not exclude
the downed; Dashboard B08 saw the strikes). There is no way to give up: alone, or with the partner away, every
death costs a minute. Other players get a chat line: no pose, no marker, though the state is already synced to
them. The player's own client still moves and is teleported back every tick (`PlayerUnconsciousHandler.holdDowned`).
A single hit of 30 or more kills outright (`overkillMultiplier` 1.5), which is what the largest hunters deal.

**Steps.** Bleed by a share of the window per hit (a tenth), so three or four bites finish it; a hunter drops
a downed player as a target after its strike; a hold-to-give-up key (payload, three seconds); a lying pose and
a marker above the head for trackers, read from the synced attachment; movement input blocked on the client
while the local player is downed; the overkill line raised or made to count only void and fall. GameTests for
bleed, give-up and target drop. **Dashboard.** P02.

#### BUG-14 Tribe-keyed progress

**Evidence (read).** `TechService.tribeOf` (tech/TechService.java:23), `TribeProgressData` and `GuardianEncounter`
key by the FTB team id. Forming a party gives a new id: Ark's marks and counters start again, and a workshop
schematic flag held by a solo id is not the party's, so a second boss kill pays the schematic again. FTB Quests
merges its own completions; Ark's stores do not. `MapUnlockData` is keyed by player, so a tribe's zone-3 tame
unlocks the map for the tamer only.
**Steps.** On the FTB Teams join and create events, merge the player's solo entries of the three stores into
the party's. Make the map unlock tribe-wide. GameTest: progress made solo is there after `party create`.
**Dashboard.** P01, B01.

#### BUG-18 A dedicated server has never been started

**Evidence (read).** The static picture is clean (section 3.2). Known risks to look at on the first run:
vanilla's vehicle checks with boxes 8 to 30 blocks wide (`handleMoveVehicle` snaps the vehicle back when its
box meets anything new); the speeds of BUG-03; a ridden sea creature whose `noGravity` was cleared by a
knock-out and never restored (`AquaticCreatureEntity.travel`), which vanilla may treat as flying; a mount that
leaves the world with a rider who logs out on it (`PlayerList.remove`, vanilla), so the tribe loses the animal
until that player returns.
**Steps.** Install a NeoForge server from `pack/out`, start it, join two clients, run this checklist: party
prompt, revive, shared tame ride, whistle, cargo, bench craft, supply drop, boss bar, relog on a mount.
Write the result into the Dashboard rows it touches. This is also OPT-19's first step. **Dashboard.** F10.

### 5.5 Progression

#### BUG-21 The level gates

**Evidence (computed, `audit_static.py levels`).** A level costs `ceil(100 * (level + 1) ^ 1.5)` XP
(`ArkLevels.xpForNextLevel`). A kill pays 5 XP per creature level, a tame 10, a first craft 10, a tech node 50,
a boss 500; the one-off sources add up to about 5,000 XP. The benches gate by level (`workstations.levelGates`):

| Craft | Level | XP | Kills of a level 30 animal |
|---|---|---|---|
| Tranquilizer arrow, Velociraptor saddle | 8 | 8,408 | 57 |
| Bronze tools, Parasaur saddle | 12 | 22,077 | 148 |
| Triceratops saddle | 22 | 96,031 | 641 |
| Tyrannosaurus saddle | 33 | 259,795 | 1,732 |
| Giganotosaurus saddle | 43 | 499,185 | 3,328 |
| Titanosaur and Dragon saddles | 48 | 655,236 | 4,369 |

In the first zone animals are level 1 to 12, so a kill pays about 30 XP: the first tranquilizer arrow is some
230 kills away. Saddle levels are a quarter of the animal's health (F28), on a curve that was written for a cap
of 50 and never set against the XP in the world.
**Steps.** This is the user's call (section 7.5). The cheap fix is the exponent (1.5 to 1.0 makes level 33 cost
56,100 XP) or the saddle rule (level by danger zone instead of health). Then rerun the sweep and put the table
on the showcase. **Dashboard.** F19, F28.

#### BUG-25 Beating the first boss unlocks nothing

**Evidence (read).** `GuardianDragonEntity.reward` (line 202) drops a trophy and the Workshop Schematic and sets
the tribe flag `workshop_schematic`. The flag is read only to decide whether to drop the schematic again; no
tech node, bench or recipe asks for it or for the advancement `journal/first_guardian`. The quest that follows
pays two golden apples, an item the theme removed. The dragon also pays nothing if it dies with no credited
player. **Steps.** The user names what the schematic opens (section 7.3); wire it as a tech node trigger and
change the quest reward. **Dashboard.** P05.

#### BUG-15 The Giganotosaurus Guardian

**Evidence (read).** `GuardianService.activate` needs the mod `ancient_remnants` (`GuardianStructure.available`),
which is in no lock file. About a thousand lines (service, entity, combat goal, bar, half the config section)
cannot run, while `GuardianEvents.drops` (line 34) gives an Allosaur Heart for every wild Allosaurus killed by a
player or a tame, and offering it answers "requires mod". **Steps.** Remove the subsystem and the heart, or
give the heart a use at the sky beacon (section 7.3); either way one boss path. **Dashboard.** P05, I12.

#### BUG-16 No use, no effect

- Nest eggs: taking one sets the bird on the player for 30 seconds (`NestBlock.takeEgg`); the item is in no
  recipe, hatches nothing and is not food.
- `progression.mapRequiresUnlock` is false by default, so the zone-3 tame unlocks nothing.
- `TribePermission.BREEDING` and `TribeService.canBreed` have no caller; there is no breeding (P08).
**Steps.** Each needs its consumer or its removal; all three belong to phase 3. **Dashboard.** P20, I01, P08.

### 5.6 Small and local

#### BUG-20 The target health bar is titled with a raw key

**Evidence (read).** `TargetHealthBar` (creature/TargetHealthBar.java:33) names its boss bar with
`hud.arksurvivalreturns.creature`, which `en_us` no longer has: the nameplate (F35) took the key's children
(`.creature.level`, `.tame`, `.unconscious`, ArkData.java:147) and the parent went. The bar is on by default
(`display.targetHealthBar`), so looking at any creature within 32 blocks shows the key as text, above a
nameplate that says the same thing properly.
**Steps.** Remove `TargetHealthBar` (the nameplate replaces it; phase 1 asked the same for the server's sake),
or restore the key. Add `audit_static.py lang` to `tools/verify_assets.py` so a missing key fails the check.
**Dashboard.** F35.

#### BUG-24 Dried food made on different days does not stack

**Evidence (read).** `DryingRackBlockEntity.stamp` (line 113) writes the day into the stack's custom data. The
tech tree could ask for aged food (`TechTrigger`, `days`), but no node does (the tree's `dried` node asks for
the tier). Stacks with different stamps do not merge, in the inventory or in a crate.
**Steps.** Remove the stamp and the unused `days` branch, or stamp only when a node asks for days. A data fixer
is not needed: strip the tag when a stack is picked up. **Dashboard.** F04.

#### BUG-26 Curios without a guard; two menus left over

**Evidence (read).** `ArkInventoryMenu` (inventory/ArkInventoryMenu.java:53) calls `CurioSlots.add` every time;
`CurioSlots` imports Curios classes, one of them internal (`common.inventory.CurioSlot`). The manifest lists
Curios as optional, so without it the inventory key crashes. The pack always ships Curios, so this is a wrong
manifest more than a live bug. `StationSmithingMenu` has no user and `StationCraftingMenu` is named only in an
`instanceof`: both predate the graph benches.
**Steps.** `if (Worn.CURIOS)` around the call, or make Curios required; delete the two menus. **Dashboard.** I07, F14.

#### BUG-19 Smaller defects and stale records

- Fast Load and Unload take from and put into every `Container` in range: cooking pots, crushers, loot crates
  (`CargoTransferService.nearbyStorage`). Limit to storage blocks (a block tag).
- Vanilla defaults nobody chose: a lead works on any wild animal (`canBeLeashed`); giants are pushed about by
  anything that walks into them; a 42-block animal takes fall damage from 4 blocks (no `SAFE_FALL_DISTANCE`).
- A tame's death tells nobody; a tame left behind cannot be found; a tame remembers an attacker for 5 seconds
  and has no stance. (Phase 3.)
- Every wild creature syncs its taming attachment about once a second while its hunger rises.
- The forge pays vanilla experience orbs, and the anvil spends vanilla levels, behind an XP bar that Ark
  replaced (to check in the client).
- A coal block in an empty Stone Fire gives 6,000 of its 16,000 ticks (`addFuel` clamps after accepting).
- A village crafting table still opens the 3 x 3 grid for the recipes that wait for phase B.
- A downed or unconscious player can craft from the inventory graph and whistle.
- Quests: three checkmark tasks pay items for a click (a free Stone Fire); rewards hand out cooked beef and
  golden apples, both outside the theme.
- Records: Dashboard P19 says a meal is a full stack (the default is one item); the `camp.starterKit` comment
  lists a kit that is now a flint knife; `BedrollBlock` and F16 call the Bedroll a Bronze Age item though it is
  a level 0 craft; `Ark/config/arksurvivalreturns-server.toml` is an old copy that nothing ships.

---

## 6. Order of work

Plans in a wave can run at the same time in separate worktrees unless noted.

**Wave 0, an afternoon:** BUG-20, BUG-12, BUG-24, BUG-26. Four small fixes in four files, and the lang sweep
becomes part of `verify_assets.py`.
**Wave 1, taming is playable:** BUG-01, then BUG-11 (same files); BUG-02 beside them (it and BUG-12 both touch
`onTamed`, so after wave 0).
**Wave 2, riding:** BUG-04 first (small, stops the deaths), BUG-03, then BUG-05 and BUG-09: all in the ride
path, one after another.
**Wave 3, two players:** BUG-07, BUG-06, BUG-14 at once; BUG-08; then BUG-18 as the check of the whole wave.
**Wave 4, after the user's decisions:** BUG-21, BUG-25, BUG-15, BUG-16, BUG-23, BUG-17, BUG-10, BUG-22, BUG-19.

What to expect: after waves 1 and 2 a player can tame, ride and dismount every land species and the tests
would catch a regression. Wave 3 makes the second player a case the game handles. Nothing here needs a design
from scratch except BUG-05 (mounted attack) and BUG-08 (the downed pose).

---

## 7. Decisions for the user

1. **Sleep length.** The same 170 seconds for all, with sedatives from the inventory doing the rest, or longer
   for larger animals?
2. **Progress on waking.** Lost, kept in part, or decaying?
3. **The first boss.** What does the Workshop Schematic open? And the Giganotosaurus Guardian: remove it, or
   keep the heart and move the ritual to the sky beacon?
4. **Collision boxes of the giants.** As wide as the models (30 blocks for the Titanosaur) or narrower, so they
   can walk, be followed and be ridden through a forest?
5. **Levels.** Is a Tyrannosaurus saddle meant to cost 1,700 kills? If not: a flatter curve, lower saddle
   levels, or more XP per kill and tame?
6. **Sea and air taming.** Keep the methods and add gear (a diving mask, a perch lure), or change the methods?
7. **A tame's hunger.** Should it matter (a starving tame weakens), or should the trough only heal?
8. **Friendly fire.** May owners and tribe mates hurt tames (a server setting, default off)?

---

## 8. Seen in passing, for phase 3

- A tame only answers blows, for 5 seconds; there is no attack order and no stance (P08).
- A tame left behind cannot be found, its death is not announced, and it never catches up.
- The mount is a watchtower: from 11 to 33 blocks up the rider reaches nothing.
- Nest eggs, the boss reward and the map unlock are rewards without a use.
- The trough and the tame's hunger are a system without a purpose.
- Narcotics have no force-feed; the taming screen does not say what the animal will eat or how long it sleeps.
- Slime balls have no source (leads are fiber now, sticky pistons are not craftable); eggs have none (P22).
- Quest rewards are vanilla food from removed animals.
- Death returns to world spawn until the first Bedroll (a Mattress, 4 hide, 8 fiber); the items lie five
  minutes where the player died.
- One supply drop per two days for the whole server, near one of the two players at random (F32).

---

## 9. What was not audited

- The client screens (inventory, benches, command wheel, tech tree, nameplate) as code paths: read only where a
  server rule depended on them.
- The 36 accessories' effects, the kitchen recipes, sulphur and tin world generation.
- `WildlifeRegister`, `NaturalPopulations` and `BoundedLife` beyond their player and dimension handling: they
  are the best measured part of the mod (C05, F34).
- The two source forks (Tom's Storage, Curios) and the session recorder.
- Anything that needs a running client: key conflicts, HUD overlap, what a second player sees.

---

## 10. How to reproduce

```bash
# Every sweep of section 3 (reads sources and build/resources/main; ./gradlew processResources refreshes it)
python Ark/tools/audit_static.py
# One or more by name: lang config lone ids items levels taming
python Ark/tools/audit_static.py taming levels
```

Vanilla behaviour quoted here was read in `Ark/build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar`:
`LivingEntity.travelFlying`, `travelRidden`, `getFrictionInfluencedSpeed`, `dismountVehicle`;
`Entity.getDismountLocationForPassenger`; `ServerGamePacketListenerImpl.handleMoveVehicle`; `PlayerList.remove`;
`Mob.createEquipmentSlotContainer`; `OwnableEntity`; `Leashable.canBeLeashed`.
