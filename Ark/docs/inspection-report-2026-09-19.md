# Ark Survival Returns — Code Inspection Report

Date: 2026-09-19
Scope: `Ark/src/main/java` (101 files, all features), datagen output under `src/generated/resources`, and vanilla/NeoForge 26.2.0.11 patched sources in `Ark/build/moddev/artifacts` used to verify API behavior.
Method: full read of every feature file, cross-checked against the generated JSON and the game log (`Ark/run/logs/latest.log`). Findings are only listed when verified in code; line numbers are current working-tree positions.

---

## Critical

### 1. Tamed creatures keep the wild AI and will attack their owner and their rider
- **Severity**: Critical
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/behavior/WildlifeGoal.java:52-55`, `:129-133`, `:194-204`, `:239-244` (same class of defect at `feature/aquatic/AquaticGoal.java:59`, `feature/creature/FlyingCreatureEntity.java:65-72`, `:90-101`)
- **Problem**: `canUse()` only checks `mob.isAlive() && mob.species().landHabitat()` and `tick()` runs `think()` unconditionally, so taming (which only sets `persistenceRequired`/`naturalWildlife=false` in `CreatureEntity.applyTameState()`) never removes the creature from the wild mind. `prey(other)` returns `true` for **any** player, and the attack path at `:194-204` calls `mob.doHurtTarget(world, sensed)` directly after `warning >= 60` (~3–5 s of proximity). The rider is inside the mount's melee box, so a ridden, tamed land predator reliably bites its own rider; dismounted, the owner standing within body distance is classified as `intruding` and is attacked too. `AquaticGoal` has the `isRidden` guard (`:64`) but no owner check, and `FlyingCreatureEntity.validThief()` (`:65-72`) excludes only a temporary feeding truce, so tamed aquatic and flying apex predators also engage their owner (territorial defense / egg defense).
- **Impact**: Riding or standing next to any tamed predator — the headline feature of the latest commit — gets the player attacked by their own mount. This makes taming actively harmful and can kill the tamer seconds after the tame completes.
- **Fix**: Treat tamed creatures as outside the wildlife system: add `&& !mob.isTamed()` (or `mob.isNaturalWildlife()`) to `WildlifeGoal.canUse()`/`AquaticGoal.canUse()`, mirror the aquatic `if (mob.isRidden()) { stop; return; }` guard at the top of `WildlifeGoal.tick()`, and exclude `TamingService.ownedBy(...)`/controlling passenger in `prey()`, `relevant()` and `FlyingCreatureEntity.validThief()`.

---

## High

### 2. Tranquilizer arrow recipe never loads (ranged taming route uncraftable)
- **Severity**: High
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/datagen/ArkData.java:193-199` → `Ark/src/generated/resources/data/arksurvivalreturns/recipe/tranquilizer_arrow.json:5-24`
- **Problem**: Datagen emits the pre-1.21.5 ingredient syntax `"ingredients":[{"item":"minecraft:arrow"},...]`. In 26.2 an ingredient is a holder-set (plain id string), so the file fails codec parsing. The game log confirms it: `run/logs/latest.log:47` — `Couldn't parse data file 'arksurvivalreturns:tranquilizer_arrow': DataResult.Error['List is too short: 0, expected range [1-9]']`.
- **Impact**: The tranquilizer arrow cannot be crafted in survival, removing the entire ranged sedative route; the `taming/sedative` tag entry for it is dead content.
- **Fix**: Emit plain ids (`"ingredients":["minecraft:arrow", ... ,"arksurvivalreturns:narcoberry","minecraft:bone"]`) in `ArkData.taming()` and re-run `./gradlew runData` (per `AGENTS.md`, do not hand-edit generated files).

### 3. Torpor suppression is not installed for ordinary mobs after reload or via the debug command
- **Severity**: High
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/taming/TorporService.java:94`, `:109-118`, `:120-127`, `:151-167`
- **Problem**: `installSuppression()` is only called from `sedate()`. A normal `Mob` sedated in play only has `UnconsciousBehavior` because it was added dynamically to its live `GoalSelector`; goal selectors are rebuilt on reload, so after a chunk unload/reload the attachment still says `TORPID` but the mob runs its full AI. `setTorpor()` (`:120-127`, used by `/arktaming torpor`) never installs the goal at all.
- **Impact**: A knocked-out vanilla mob (the documented cross-population contract of the class) resumes walking, targeting and attacking until recovery finishes; the debug knockout command is non-functional as a restraint.
- **Fix**: Re-install suppression whenever the entity is restricted, e.g. in `tickEntity()` call `if (state.restricted()) installSuppression(living);` (the presence check at `:114` makes it idempotent), or install it inside `setTorpor()` as well.

### 4. Creature mount screen stat labels render at double offset, off the GUI
- **Severity**: High
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/client/CreatureMountScreen.java:54-65`
- **Problem**: `extractLabels` is invoked inside the matrix already translated by `leftPos/topPos` (`AbstractContainerScreen.extractContents`, patched 26.2 source lines 97-103: `graphics.pose().translate(xo, yo); this.extractLabels(...)`), yet the override recomputes `xo/yo` from width/height and adds them again. The labels therefore draw at `(2*xo+8, 2*yo+6)` etc.
- **Impact**: The only readouts on the saddle screen (Taming %, Hunger, Torpor) are drawn near the window's bottom-right corner or outside the panel, so the values are unreadable at normal GUI scales.
- **Fix**: Use container-relative coordinates (`8, 6`, `90, 6`, `90, 58`) inside `extractLabels`, and move the first label so it does not overlap the title drawn by `super.extractLabels` at `(8, 6)`.

---

## Medium

### 5. Brazilian Portuguese strings are double-encoded mojibake
- **Severity**: Medium
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/datagen/ArkData.java:116`, `:122`, `:126`, `:130`, `:219-228` → `Ark/src/generated/resources/assets/arksurvivalreturns/lang/pt_br.json`
- **Problem**: Literals such as `"CaÃ§ando"`, `"RegiÃ£o"`, `"NÃ­veis selvagens"`, `"VocÃª nÃ£o consegue"` are UTF-8 bytes decoded twice; the generated locale carries the mangled text. Well-formed entries in the same file (`"Buscando \u00e1gua"`) show the intended encoding.
- **Impact**: pt-BR players see corrupted text for nearly all taming feedback, the biome announcement, map lock/legend labels and behavior states.
- **Fix**: Replace the corrupted literals with correct accented text or `\uXXXX` escapes and regenerate with `./gradlew runData`.

### 6. Unconscious players can swim freely because the anchor follows them in fluid
- **Severity**: Medium
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/taming/PlayerUnconsciousHandler.java:62-65`
- **Problem**: `if (player.isInWater() || player.isInLava()) { state.setAnchor(player.position()); return; }` re-anchors the clamp to the player's live position every tick, so the movement clamp at `:67` never runs while in any fluid. The Y coordinate is preserved by the teleport anyway, so the exemption is not needed to avoid drowning.
- **Impact**: A knocked-out player can swim/walk through a puddle, river or ocean at will, bypassing the core unconsciousness restriction (which still blocks attacks, item use and interaction, but not movement).
- **Fix**: Remove the fluid early-return and clamp as on land (`teleport(anchor.x, player.getY(), anchor.z, ...)` already keeps the vertical axis free).

### 7. Titanosaur can never satisfy `SEEK_WATER` (scan radius smaller than its own destination offset)
- **Severity**: Medium
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/behavior/WildlifeGoal.java:296` vs `:314`
- **Problem**: `nearbyWater()` scans at most 12 blocks (`Math.min(12, 2 + (int)(bbWidth/2))`), while `seekWater()` places the destination `1 + ceil(bbWidth/2)` blocks back from the water. Titanosaur (`Species.java:22` width 5.0 × multiplier 6 = 30, `Species.java:160,169`) gets `edge = 16 > 12`, so the search only ever finds water it cannot then detect.
- **Impact**: A thirsty Titanosaur walks to water, `o.water` stays false, the mind remains in `SEEK_WATER` indefinitely and never drinks (and never returns to normal routines).
- **Fix**: Raise the scan bound to cover the destination offset (e.g. `Math.min(16, 2 + (int)Math.ceil(bbWidth/2))`) or remember the chosen water block and test proximity to it instead of rescanning.

### 8. `WildlifeNoise.hear` runs raycasts and chunk sweeps before the distance rejection
- **Severity**: Medium
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/behavior/WildlifeNoise.java:39-46`
- **Problem**: `distance` is computed at `:39`, but the chunk-span `SpawnRules.loaded(...)` check (`:41-42`) and the `world.clip(...)` raycast (`:43-45`) execute before the cheap `distance < radius * radius` test at `:46`. Every land creature evaluates every one of up to 64 stored sounds per dimension, including sounds hundreds of blocks out of range.
- **Impact**: Mining/combat noise anywhere in the dimension causes per-creature chunk scans and raycasts at a 10-tick cadence; with packs and farms this produces server tick spikes exactly when many listeners exist.
- **Fix**: Compute `double limit = radius * radius;` and `if (distance >= limit) continue;` immediately after `:40`, before the loaded/raycast checks.

### 9. Land `think()` performs multiple large entity scans and terrain probes every 10 ticks per creature
- **Severity**: Medium
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/behavior/WildlifeGoal.java:96` (plus `:135`, `:154`, `:162`, `:186`, `:248`, `:293-302`)
- **Problem**: Each `think()` unconditionally runs a `getEntitiesOfClass(LivingEntity.class, ...inflate(max(48, sightRange)))` scan (up to ~62 blocks for solitary predators), up to 24 `WildlifeSenses.detect` raycasts, a `(2r+1)²` fluid/chunk probe (`nearbyWater`) and `forage` terrain reads — plus separate scans for guarded prey, herd defense, alarm propagation, pack leader search and regroup.
- **Impact**: Cost scales quadratically with local population; large herds/tamed clusters cause TPS drops and hitches, the opposite of what "bounded perception" promises.
- **Fix**: Slow the candidate scan (amortize across ticks/pack), gate `nearbyWater`/`forage` behind the thirst/forage state, and reuse a single scan for herd/alarm/regroup queries.

### 10. Xaero danger overlay rebuilds the whole cell grid on every pan/zoom frame
- **Severity**: Medium
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/client/XaeroDangerOverlay.java:42-48`, `Ark/src/main/java/dev/nez/arksurvivalreturns/client/XaeroExploration.java:15-22`
- **Problem**: The cache condition `!view.equals(cachedView) || nanoTime-refreshedAt > 250ms` is true every frame while the camera moves, so `DangerMapView.cells(...)` re-rasterizes ~10k cells (each calling `DangerBands.level` and `explored.contains`) and `XaeroExploration.current(...)` allocates a brand-new snapshot, discarding its 32,768-entry lookup cache.
- **Impact**: Dragging/zooming the Xaero map stutters badly, discarding the caching the class was designed around.
- **Fix**: Reuse one `XaeroExploration` per map screen/dimension and throttle recomputation independently of view equality (quantized camera key and/or a minimum refresh interval).

### 11. Development inspector (debug spyglass) ships enabled in production builds
- **Severity**: Medium
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/debug/DebugSpyglass.java:21-32` (registration and creative-tab entry), `feature/debug/DinoDebugSync.java:34`
- **Problem**: The item, its creative-tab entry and the server-side snapshot streamer are registered unconditionally with no config, permission or `FMLEnvironment.isProduction()` gate.
- **Impact**: Any player with creative/commands on a release server can obtain the tool and stream hidden creature state (taming progress, owner UUIDs, saved NBT) for entities within 96 blocks; it also exposes an internal dev workflow to players.
- **Fix**: Gate registration, the creative-tab entry and `DinoDebugSync.register/tick` behind a development flag or move `feature/debug` + `client/DinoDebugClient` to a dev-only source set.

---

## Low

### 12. Map legend names danger ranks 4 and 5 backwards
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/datagen/ArkData.java:115-116`
- **Problem**: The legend text is `"4 - Extreme", "5 - Severe"` (PT: `"4 - Extrema", "5 - Severa"`), but `BiomeTier` defines danger 4 as `SEVERE` and danger 5 as `EXTREME` (`BiomeTier.java:12-13,26`), and `BiomeAnnouncements` announces them that way.
- **Impact**: The Xaero legend contradicts the chat announcement for the same region.
- **Fix**: Swap the two labels and regenerate.

### 13. Config-permitted zero recovery rate causes permanent unconsciousness
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/Config.java:105-106` + `feature/taming/TorporState.java:226-232`, `:242-244`
- **Problem**: `torporRecoveryPerSecond` allows `0.0`, and the recovery code multiplies by it, so torpor never falls below the wake threshold; the `TORPID` phase then loops forever.
- **Impact**: With a legal config value, a sedated player or creature never wakes; only `/arktaming reset` or death recovers.
- **Fix**: Raise the config minimum above zero, or treat an expired recovery delay plus zero rate as a wake condition.

### 14. Sedative item is consumed even when the dose is rejected, and its item cost is tied to the meal size config
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/taming/SedativeItem.java:35-36`, `:41-43`, `:48`
- **Problem**: `use()` ignores the boolean return of `TorporService.sedate` and always calls `consume(...)`, contradicting the class contract "consumed only after the dose is accepted" (the dose returns false with taming disabled). Separately, `consume` uses `FOOD_UNITS_PER_MEAL` (documented as "items consumed by one successful meal", range 1–16) to size a sedative dose, so tuning the meal config makes one narcoberry use/hit devour up to 16 items.
- **Impact**: Taming-disabled servers silently eat the item for no effect; unrelated balance knobs are coupled.
- **Fix**: `if (TorporService.sedate(...)) consume(stack, player);` (mirroring the correct `hurtEnemy` pattern) and consume 1 item or add a dedicated config value.

### 15. Taming completion hearts never spawn
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/taming/TamingFeedback.java:92-96`, `:66-72`
- **Problem**: `completed()` calls `particles(...)`, which uses `Level.addParticle`. In 26.2 `Level.addParticle` is an empty method (patched `Level.java:473-474`); only `ClientLevel` implements it. `completed()` is invoked server-side from `TamingService.complete` (`TamingService.java:189`), so the hearts are a no-op.
- **Impact**: The tame completion has no visual burst, unlike knockout/wake which use `broadcastEntityEvent`.
- **Fix**: Call `hearts(entity)` (the existing `broadcastEntityEvent(entity, (byte) 7)` helper) from `completed()`.

### 16. `WildlifeGoal.save` persists the transient prey-herd anchor, not the permanent home
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/behavior/WildlifeGoal.java:47-51`, `:324-326` (same pattern at `feature/aquatic/AquaticGoal.java:238-240`)
- **Problem**: `save()` writes `home()`, which returns `transientHome` first; `transientHome` is set to a prey herd's position at `:82` and only cleared in `load()`. The comment at `:81` says the permanent habitat must never move.
- **Impact**: If `WildPreyHerd` exists (legacy save or any future caller of the public `followPreyHerd(UUID)`), the creature's saved territory permanently relocates to the prey herd on the next save. Currently latent — no code path calls `followPreyHerd` today.
- **Fix**: Persist the permanent field (`if (home == null) home = mob.blockPosition();` then write `home`), or clear `transientHome` when `preyHerd == null`.

### 17. Disabled-structure exploration maps still drop a blank map
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/theme/LootGuard.java:65-69`
- **Problem**: For an `exploration_map` function whose destination is a disabled structure tag, `sanitize` removes only the function object from the `functions` array; the enclosing `{"type":"minecraft:item","name":"minecraft:map", ...}` entry remains, so the item still drops without its destination function. The class contract says such maps are "replaced by nothing".
- **Impact**: Loot tables (third-party or future vanilla additions) can yield a useless blank map instead of nothing.
- **Fix**: When the dropped object is an entry containing a disabled `exploration_map` function, replace the parent entry with `EMPTY.deepCopy()` instead of only deleting the function.

### 18. Dead `FollowPackGoal`
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/creature/FollowPackGoal.java:8`
- **Problem**: The class is never registered: `CreatureEntity.registerGoals()` only adds `UnconsciousBehavior`, `FloatGoal` and the wildlife controller; a repo-wide search finds no `new FollowPackGoal(...)`.
- **Impact**: None at runtime today, but it is misleading dead code that suggests pack following exists when it does not.
- **Fix**: Register it for pack species or delete it.

### 19. Mount screen text is hard-coded English
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/client/CreatureMountScreen.java:59-64`
- **Problem**: "Taming", "Hunger", "Torpor" are built with `Component.literal`, while the mod ships complete `en_us`/`pt_br` translation key sets.
- **Impact**: Non-English clients see mixed-language UI; the strings cannot be translated by resource packs.
- **Fix**: Add keys (e.g. `screen.arksurvivalreturns.taming/hunger/torpor`) in `ArkData` and use `Component.translatable(...)`.

### 20. Torpor/taming attachments are created for every player every tick
- **Severity**: Low
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/taming/PlayerUnconsciousHandler.java:31` + `feature/taming/TorporService.java:151-167`
- **Problem**: `TorporService.of(player)` calls `getData(TORPOR)`, which creates and marks the serializable/synced attachment even for never-sedated players. `tickEntity` then sees `tracked() == true`, creates the `TAMING` attachment and runs the taming tick/sync for every player.
- **Impact**: Every player permanently carries never-used taming/torpor state in saves and receives attachment sync traffic; event handlers intended for sedated entities tick for everyone.
- **Fix**: Guard with `player.hasData(TamingAttachments.TORPOR)` before calling `of()` (the existing `TorporService.tracked(...)` helper).

---

## Verified clean

The following were read fully and cross-checked against generated resources/patched 26.2 sources with no additional findings:

- Taming core: `TamingService`, `TamingState`, `TamingEvents`, `TamingCommands`, `TamingTags`, `TamingMethod`, `CreatureTamingProfile`, `CreatureTorporClips`, `CreatureSize`, `CreatureSeats`, `CreatureRideProfile`, `CreatureRideController`, `CreatureMountMenu` (null-safe client path), `CreatureInventory`, `CreatureAnimationBridge`, `UnconsciousBehavior`, `SedativeArrowItem`, `SedativeArrow`, `CreatureProfileRegistry`.
- Behavior model: `WildlifeMind`, `WildlifeSenses`, `WildlifeController`, `NighttimeCycle`, `BehaviorState`, `NighttimeClientConfig`.
- Spawn/map/theme: `SpawnRules`, `ProgressionData`, `DangerBands`, `BiomeTier`, `BiomeAnnouncements`, `DangerMapView`, `DangerMapSync`, `DangerMapPayload`, `MapUnlockData`, `ThemePolicy`, `RecipeGuard`, `MechanicGuard`, `HostileGuard`, `DimensionGuard` (all removed ids/tags/features referenced by datagen exist in 26.2).
- Client: `ArkClient`, `TamingClient`, `NightEyesLayer`, `DinoDebugClient`, `DangerMapClient`, `CreatureRenderer`, `CreatureModel`, `SedativeArrowRenderer` (all referenced textures/models/lang keys exist, including GeckoLib path resolution).
- Registry/config/metadata: `ArkSurvivalReturns`, `Config` bounds, `ModContent` (no duplicate/missing registrations), `neoforge.mods.toml`, `build.gradle`/`gradle.properties`, `client-mods/` split.
- Creature assets: all 41 species have matching entity texture, GeckoLib geo/animation files, spawn eggs and nest assets; `NestBlock`/`Nests` and aquatic `Water` helpers are bounds-checked and chunk-load safe.

## Suggested fix order

1. Finding 1 (tamed AI) — highest player-visible damage; also blocks safe testing of everything else.
2. Findings 2–4 — recipe, torpor suppression, mount screen labels.
3. Findings 5–11 — locale, unconscious movement, water seeking, server/client performance, production debug tool.
4. Findings 12–20 — polish and latent correctness.
