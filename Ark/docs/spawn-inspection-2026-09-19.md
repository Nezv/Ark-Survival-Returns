# Ark Survival Returns — Spawn & Biome Rules Inspection

Date: 2026-09-19
Scope: Natural creature spawning only (`SpawnRules`, `ModContent` entity category, biome modifiers/tags, `Species` danger, `ProgressionData` / `DangerBands`, aquatic placement, leftover config vs deleted `PopulationDirector`). Cross-checked against generated `neoforge/biome_modifier/spawn_*.json` and biome tags. Game tests were read to see why they still pass.

Symptom: walking the Overworld finds no Ark creatures.

---

## Critical

### 1. Chunk-generation spawns are rejected because the world is not a `ServerLevel`

- **Severity**: Critical
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/feature/spawn/SpawnRules.java:57-60`, `:83-86`
- **Problem**: Both `canSpawn` and `canSpawnWater` start with `if (!(world instanceof ServerLevel level)) return false`. Vanilla animal placement during **chunk generation** calls this predicate with a `WorldGenRegion` (`ServerLevelAccessor`, not `ServerLevel`). Every Ark species is therefore forbidden at the only phase where `MobCategory.CREATURE` actually fills the world.
- **Impact**: New terrain is generated empty of dinosaurs. Game tests never see this: they always pass a real `ServerLevel` into `SpawnRules.canSpawn`.
- **Fix**: Resolve the server world via `world.getLevel()` (or accept `ServerLevelAccessor` for block/fluid reads). Keep Overworld / game-rule / config checks on that server world. Do not require `instanceof ServerLevel` on the accessor the spawner passes in.

### 2. Custom population director was removed; vanilla `CREATURE` cannot replace it

- **Severity**: Critical
- **Location**: `Ark/src/main/java/dev/nez/arksurvivalreturns/registry/ModContent.java:50-56`; deleted `feature/spawn/PopulationDirector.java`; leftover knobs in `Ark/config/arksurvivalreturns-server.toml` (`localCap`, `minimumGroups`, `checkIntervalTicks`, `groupsPerPass`) that `Config.java` no longer defines
- **Problem**: All 41 species register as `MobCategory.CREATURE` (vanilla cap **10**, shared with cows, sheep, pigs, chickens, horses). After chunk gen fails (finding 1), the only remaining path is the periodic creature spawner. That spawner almost never adds Ark mobs because vanilla animals from chunk gen already occupy the cap. The previous director spawned Ark wildlife on its own budget, independent of that cap; it is gone, and the live config still documents the old independent cap.
- **Impact**: Even on a long walk through already-generated chunks, the periodic spawner has no room (and little reason) to create dinosaurs. The world stays empty of the roster the biome modifiers list.
- **Fix**: Restore a dedicated spawn budget (custom `MobCategory`, or a bounded director that does not force-load chunks), **or** register land predators as `MONSTER` / water species as `WATER_CREATURE` with caps that are not already full of livestock. Do not rely on vanilla `CREATURE` alone.

### 3. Chunk-gen successes would despawn at the generation frontier

- **Severity**: Critical (latent until finding 1 is fixed; then it still empties the world)
- **Location**: `CreatureEntity.java:312-314`, `:354`
- **Problem**: `finalizeSpawn` sets persistence only for spawn eggs and commands. `removeWhenFarAway` always returns `true`. Chunk generation places mobs at simulation-distance (~128–192 blocks). Vanilla `Mob.checkDespawn` then discards anything that far away unless persistence is set. Vanilla animals survive this because they do not despawn; Ark creatures would vanish within seconds of appearing.
- **Impact**: Fixing only the `ServerLevel` check still yields an empty map while exploring.
- **Fix**: For `EntitySpawnReason.CHUNK_GENERATION` (and likely `NATURAL` while wild), either `setPersistenceRequired()` or return `false` from `removeWhenFarAway` unless the creature is truly transient. Keep despawn for overcrowding only if a director/cap handles it.

---

## High

### 4. Water-bound species never enter the water-creature spawn path

- **Severity**: High
- **Location**: `ModContent.java:56` (`MobCategory.CREATURE`); `SpawnRules.java:32-34` (`IN_WATER`); `Species.aquatic()` / `canSpawnWater`
- **Problem**: Cnidaria, Plesiosaur, Megalodon, Liopleurodon, Mosasaurus and Tusoteuthis are `CREATURE` with `IN_WATER` placement. Vanilla `CREATURE` chunk gen and the periodic creature pass sample **ground / world-surface** columns. The `WATER_CREATURE` / `UNDERGROUND_WATER_CREATURE` passes, which look for water, never see these types. `canSpawnWater` then fails on dry feet positions (`!level.getFluidState(pos).is(WATER)`).
- **Impact**: Oceans and rivers have biome modifiers and tags, but no sharks or plesiosaurs appear.
- **Fix**: Register realm `WATER` as `MobCategory.WATER_CREATURE` (and keep `IN_WATER`). Do not put them on the livestock category.

### 5. Ground predicate requires the spawn Y to equal the *highest* block of a multi-column footprint

- **Severity**: High
- **Location**: `SpawnRules.java:69`, `:99-114`
- **Problem**: `placementSurface` walks every column under `bounds(species, pos)` and returns `new BlockPos(x, high, z)`. `canSpawn` then requires `pos.equals(...)`. Vanilla `ON_GROUND` supplies the heightmap Y of **that** column (`center`), not `high`. Any neighbor one block taller makes `center.y != high` and rejects the spawn. Large species (Triceratops width 5, sauropods much more) also fail `high - low > 1` (or `> 3` when `width >= 5`) on ordinary rolling terrain. Game tests only use a flat 16×16 grass pad, so they stay green.
- **Impact**: After findings 1–3 are fixed, spawns still fail on real Overworld ground except superflat / perfectly level pads. Plains walks stay empty.
- **Fix**: Accept the vanilla feet position if enough of the footprint is a listed surface within the slope limit. Do not require `pos.y == max(neighbor heights)`.

---

## Medium

### 6. Hitbox vs `noCollision` rejects almost all large species even on legal grass

- **Severity**: Medium
- **Location**: `SpawnRules.java:51-54`, `:80`; `Species.java:159-169` (Titanosaur ×6, Rex/Giga/Spino/Acro ×3, default ×2)
- **Problem**: The collision box is the full scaled body standing on the spawn block. Trees, leaves the heightmap ignored, grass variants with collision, and slight grade put solid cubes inside a 8–30 block-wide AABB. `level.noCollision(null, box, true)` then fails. Titanosaur / Giga / Bronto are effectively unspawnable without a dedicated flattening step.
- **Impact**: The apex roster in the biome tags is ornamental; only small bodies (Lystrosaurus, Dilophosaur, Pegomastax) would clear this, and they are still gated by 1–5.
- **Fix**: Collision-check a feet slab (or a shrunk box), not the full adult volume, at placement time.

### 7. Headless tests never run the vanilla spawner, so an empty world cannot fail CI

- **Severity**: Medium
- **Location**: `ArkGameTests.spawnRules` / `population` (`Ark/src/main/java/dev/nez/arksurvivalreturns/gametest/ArkGameTests.java:111-228`); `ThemeGameTests.java:106-116` only asserts **removed** vanilla spawners are gone, not that Ark spawners are present
- **Problem**: Tests call `SpawnRules.canSpawn` on a `ServerLevel` fixture or `create()` + `addFreshEntity`. Nothing invokes `NaturalSpawner.spawnMobsForChunkGeneration` with a `WorldGenRegion`, nothing asserts `biome.getMobSettings().getMobs(CREATURE)` contains `arksurvivalreturns:triceratops`, and nothing waits for a natural spawn.
- **Impact**: `./gradlew runGameTestServer` can pass while the player sees zero wildlife.
- **Fix**: Assert biome lists after modifiers apply; add one test that uses a non-`ServerLevel` accessor (or documents `getLevel()`); optionally tick spawn in a loaded chunk and expect at least one Ark entity when danger and biome match.

---

## Low

### 8. Stale server.toml still describes the deleted director

- **Severity**: Low
- **Location**: `Ark/config/arksurvivalreturns-server.toml` `[spawning]` (`localCap`, `solitarySpacing`, `minimumGroups`, …, per-species `[spawning.weights]`) vs `Config.java:57-59` (only `enabled` and `minimumWaterDepth`)
- **Problem**: Extra Forge keys are ignored. Operators think raising `localCap` / `minimumGroups` / weights will populate a world. Those values are dead.
- **Impact**: Confusing empty-world “fixes” that do nothing.
- **Fix**: Drop the orphan keys on next config version, or wire them back if a director returns.

### 9. `speciesAllowed` ignores the biome holder (not a cause of a totally empty map)

- **Severity**: Low
- **Location**: `SpawnRules.java:41-44`
- **Problem**: The method takes `Holder<Biome> biome` and never reads it; eligibility is only `danger >= species.minimumDanger()`. Biome filtering is solely the generated `neoforge:add_spawns` + `tags/worldgen/biome/spawns/<id>.json`. That split is valid **if** modifiers load. Difficulty biome tags (`difficulty/easy` …) are unused by `BiomeTier.at` (spatial bands via `ProgressionData`), which is also valid by design.
- **Impact**: None by itself. If a modifier failed to parse, this predicate would still allow a species in the wrong biome whenever something else spawned it. Not why walking finds nothing today.
- **Fix**: Optional: `biome.is(species.biomes)` as defense in depth; keep spatial danger as the gate.

---

## Not bugs (checked, not reported as defects)

- Generated `spawn_<species>.json` files exist for all 41 species with `weight > 0`, pointing at `#arksurvivalreturns:spawns/<id>`. Tags are populated (e.g. plains: Triceratops, Parasaur, Lystrosaurus, Pteranodon).
- `Config.NATURAL_SPAWNS` defaults to `true`; the run config `spawning.enabled = true`.
- `HostileGuard` only fails spawn for `ThemePolicy.removed` types (`minecraft:ravager`, not `arksurvivalreturns:ravager`).
- `GameRules.SPAWN_MOBS` is not turned off by `DimensionGuard`.
- Spatial danger 1 around world spawn is intentional (`DangerBands` / `ProgressionData`). It would still allow danger-1 plains species (Triceratops, Parasaur, Pteranodon, Lystrosaurus) if findings 1–5 were fixed. It does **not** explain a completely empty map by itself.

---

## Why the player sees nothing

In order, in a normal Overworld session:

1. Chunk gen asks Ark `canSpawn` with `WorldGenRegion` → **false** (finding 1).
2. Vanilla livestock still spawn at chunk gen and fill `MobCategory.CREATURE` (finding 2).
3. Periodic creature ticks have no cap left and almost never pick an Ark type (finding 2).
4. If a dinosaur did appear at the gen edge, `removeWhenFarAway` would delete it (finding 3).
5. Water species are on the wrong category (finding 4); uneven ground fails the Y/`high` check (finding 5).

Game tests never execute that sequence, which is why CI stayed green.

## Suggested fix order

1. Finding 1 (`getLevel()` / stop requiring `instanceof ServerLevel` on the accessor).
2. Finding 3 (persist chunk-gen / natural wild spawns, or stop despawning them at 128 blocks).
3. Finding 2 (own spawn cap / category; do not share livestock's 10).
4. Finding 4 (water category) and finding 5 (footprint Y).
5. Finding 6 (placement collision box) and finding 7 (a test that would have caught this).

---

## Resolution (current build)

- Findings 1-4: `canSpawn` resolves the server world through `world.getLevel()`, natural wildlife no longer
  despawns (`removeWhenFarAway` returns false for it), water realms register as `WATER_CREATURE`, and
  `NaturalPopulations` owns an independent budget that does not compete for the vanilla animal cap.
- Findings 5-6: placement no longer requires the highest column Y; `groundFits` accepts bounded slopes,
  and `clearForBody` checks a feet slab plus solid-free headroom, ignoring leaves while logs and terrain
  still block, so apex bodies can spawn under canopies.
- Finding 9: `SpawnRules.placement` exposes explicit reasons (`CONFIG`, `LOADED`, `BOUNDS`, `FLUID`,
  `DANGER`, `FOOTPRINT`, `CLEARANCE`) shared by the spawner and `/arkwildlife [radius]`.
- Apex reachability: regional large species use the biome tag as a x3/x1 weight instead of a hard filter,
  and the budget biases its first placement each pass toward one missing regional large. The `spawn_apex`
  game test covers danger-1 rejection, danger-5 placement and foliage/solid clearance.
