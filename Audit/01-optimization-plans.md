# Audit, phase 1: optimization plans

Written 2026-11-09 by Claude Fable 5 for the user, to be dispatched plan by plan to an agent (Opus).
Dashboard row: A01. Phase 2 (bugs and bad design) and phase 3 (recipes and features for a two-player
world) are separate documents; what this phase found for them is listed in section 8.

Nothing in the game was changed and nothing was run in the client for this document. It rests on three
things that already existed in the repository:

- the recorded benchmark runs of `Ark/tools/session_bench.py` (P12) and their Flight Recorder profiles,
  read again with `Ark/tools/SessionProfile.java` for the render thread and, for the first time, the
  server thread;
- the sources: Ark's own (459 Java files), the two forks built from source (`Ark/pack/src/toms-storage`,
  `Ark/pack/src/curios`), the configuration that ships and the configuration of the dev run;
- the shipped assets, counted by script (models, animations, textures, sounds).

Every claim below is marked by how it is known: **measured** (a number from a recorded run),
**read** (seen in source or in a log) or **estimate** (reasoned, to be confirmed by the plan's first step).

---

## 1. What the measurements say

Machine: the RTX 3050 6 GB laptop, 1920x1080 full screen, at its working temperature (card 87 C, 40 to 45 W),
render and simulation distance 12, shader pack Photon, Grassier Grass at 64 blades and 128 blocks, Distant
Horizons on, 49 to 51 Ark creatures drawn. Runs: `Ark/run/diagnostics/bench/20261007-120500` (four setups),
`20261007-104431/full` (profile), `20261008-125951` (out and back).

### 1.1 There are two regimes, and they need different work

| | Standing and turning | Flying over new land |
|---|---|---|
| Frames a second, full stack | 39.6 | 29.1 |
| 1 % low | 22.1 | 8.7 |
| Frames over 50 ms in the phase | 3 | 69 |
| What limits it | the graphics card (97 % load) | the render thread (card at 72 %) |
| Without Grassier Grass | 44.2 | 36.4 |
| Without Distant Horizons | 43.3 | 31.3 |
| Without the shader pack | 58.1 | 38.4 |

Standing, the card is the limit: work taken off the render thread does not raise the frame rate there, it only
frees a core. Flying, and in every stutter, the render thread is the limit. So the plans split in two kinds:
those that take load off the card (grass geometry, shader cost, shadows, texture sampling) and those that take
work off the render thread (grass building, map writing, creature posing and vertex writing, animation).
A plan that claims a gain has to say which regime it is for, and prove it there.

### 1.2 Render thread, by whose code is running (measured)

| Owner | Standing | Flying |
|---|---|---|
| Minecraft, Java and the driver | 28.1 % | 20.2 % |
| Ark (almost all of it creature drawing) | 17.5 % | 12.8 % |
| Iris | 16.2 % | 10.8 % |
| Distant Horizons | 9.7 % | 6.9 % |
| Sodium | 8.6 % | 6.5 % |
| Grassier Grass | 7.8 % | 24.5 % |
| GeckoLib | 7.1 % | 5.5 % |
| Xaero's World Map | 2.2 % | 10.8 % |
| Physics Mod | 1.2 % | 1.0 % |
| FancyMenu, Better Combat, FTB, Curios | 1.6 % | 1.0 % |

Details that decide the plans:

- **Creatures cost a quarter of the render thread standing.** `CreatureDraw$Task.render` is on the stack in
  20.7 % of the samples: writing vertices 14.0 % (`CubeWriter.write`, of which `IrisSink.cube` 10.2 %), and
  GeckoLib working out the bones inside it 5.7 % (`RenderPassInfo.compileBoneUpdates`,
  `AnimationProcessor.createBoneSnapshots`). Add GeckoLib's state extraction outside it and reading the keys
  (`PlainKeys.value` 2.8 %). This is after F27 already made it faster.
- **Grassier Grass builds its geometry on the render thread.** In flight it is a quarter of the thread and
  71 % of the time spent in slow frames (55 ms or longer): `GrassSectionCache.buildBudgeted` and
  `GrassSectionBuilder.build`, vertex by vertex through `BufferBuilder`, where Iris's per-vertex hook
  (`BufferBuilder.iris$beforeNext`) alone is 6.2 % of the thread.
- **Xaero writes its map on the render thread** (`MapWriter.onRender`, `MapProcessor.onRenderProcess`): 10.8 %
  in flight.
- **The shader pack's shadow pass** is 10.9 % of the thread standing (`IrisRenderingPipeline.renderShadows`),
  most of it walking and drawing terrain a second time.
- `glfwSwapBuffers` holds 10.6 % standing: that is the thread waiting for the card.

### 1.3 Graphics card (measured)

| Setup | Card load standing | Video memory |
|---|---|---|
| Full stack | 96.9 % | 1657 MB |
| Without Grassier Grass | 51.0 % | 1080 MB |
| Without the shader pack | 54.3 % | 956 MB |

Grassier Grass is about half of the card's work standing and 0.58 GB of video memory. Render scale (0.75,
0.5) changed nothing, so the card's cost is geometry and passes, not pixels.

### 1.4 Server thread (measured; never looked at before)

The integrated server's tick took 12 to 19 ms of its 50 ms. By what was on the stack, standing:

| What | Share of the server thread |
|---|---|
| Ticking entities, all | 71 % |
| Ark creatures (`CreatureEntity.tick`) | 25.2 % (29.9 % flying) |
| of which Ark's own behaviour code (`WildlifeGoal.tick`) | 1.5 to 2.1 % |
| Villagers (`Villager.tick`), mostly path finding | 22.7 % |
| Spiders (`Spider.tick`) | 14.7 % |
| Bats | 2.2 % |
| Movement and collision for every mob (`Entity.move`) | 24.5 % |
| Chunk ticking (`ServerChunkCache.tick`), with vanilla spawn attempts 6.0 % | 17.4 % |

Two facts stand out. Ark's behaviour model is cheap: the cost of an Ark creature on the server is vanilla's
body physics (movement, collision against blocks, the blocks it stands in, the block under it), which runs in
full every tick for every creature, asleep or 190 blocks away. And two vanilla mobs the theme kept, villagers
and spiders, cost more than all 49 dinosaurs together.

### 1.5 Memory and weight (measured and counted)

- Allocation: 287 to 316 MB a second over all threads; Distant Horizons is 49 to 55 % of it (its world
  generation thread). Garbage collection paused 360 ms in 40 s of flight, longest pause 153 ms. Without
  Distant Horizons the pauses were the same (372 ms), so its allocation is not what makes them.
- Heap: 6 to 8 GB in use under a 16 GB limit, on a machine with 23 GB.
- Animations: 67 MB of JSON, 684 clips, 99,373 channels, 1.55 million keyframes of three values each.
  GeckoLib keeps every value as objects. The Acrocanthosaurus alone is 6.4 MB and 146,000 keyframes.
- Models: 44, with 9,036 boxes. Over 380 boxes: Deinosuchus 416, the old Dragon 412, Ankylosaurus 406,
  Megalocerus 395, Sarco 382. The Acrocanthosaurus has 323 bones.
- Creature textures: 273 files, 243 MB once decoded if all are loaded. Titanosaur 2048x2084 in four colours
  (68 MB), the three wyverns 2048x2048 (50 MB). Six old dragon textures and the old dragon model still ship.
- The jar is 143 MB: 82 MB of ambient recordings, 67 MB of animation JSON before compression, 22 MB of
  creature recordings.
- **The block atlas has one mip level instead of four** (read, client log): Grassier Grass ships a 2x2 and an
  8x4 block texture, and the game drops the whole atlas to the level the smallest texture allows.

### 1.6 What the measurements do not cover

- The pack that ships by default is Bliss; every number above is Photon. Bliss draws entity shadows, Photon
  does not.
- No run has a base in it (storage terminals, fires, racks, tames), a fight, a chase, a second player or a
  dedicated server.
- The sound thread (the embedded Sound Physics ray tracing) was never profiled. It does not show on the
  render or server thread.
- World generation threads were not profiled; in flight the server tick rose to 19.5 ms.

Plan OPT-01 closes these gaps first, because every other plan is judged by it.

---

## 2. Ownership: what to keep, patch, fork or replace

"Can be built from source" is true for fewer mods than the pack lists, and the three closed jars are exactly
the ones that cost the most and that the pack may not be allowed to publish (Dashboard I13 already says so
for Physics Mod).

| Mod | Licence, source | Measured cost | Recommendation |
|---|---|---|---|
| Grassier Grass | closed, all rights reserved | 7.8 / 24.5 % of the render thread, half the card, 0.58 GB, atlas mips | Replace with Ark's own grass (OPT-13); until then OPT-02 and OPT-03 |
| Xaero's World Map and its bridge | closed / MIT | 2.2 / 10.8 % of the render thread | Cap now (OPT-03); replace when F17 builds the map item (OPT-14) |
| Physics Mod | closed, all rights reserved | about 1 %, plus a hitch at the first ragdoll | Keep or drop is the user's call (section 7); it does nothing for Ark's creatures |
| GeckoLib | MIT | 5.5 to 7.1 % of the render thread, and the heap of 1.55 million keyframes | Keep for loading models; take the animation away from it (OPT-08) |
| Iris | LGPL, forkable | 10.8 to 16.2 % | Keep the release. Fork only if OPT-11 reaches its third stage |
| Sodium | PolyForm Shield, source open | 6.5 to 8.6 %, healthy | Keep the release; Ark already uses its vertex API |
| Distant Horizons | LGPL, forkable | 6.9 to 9.7 % of the render thread, half of all allocation | Keep the release; settings and pre-built LODs (OPT-15). A fork is not justified by the numbers |
| Tom's Storage | MIT, built from source | not in any run; periodic scans read in source | Patch the fork (OPT-05) |
| Curios | LGPL, built from source | 0.1 to 0.3 % | Small patch (OPT-06) |
| FancyMenu, Better Combat, FTB, BetterF3 | mixed | under 1 % together in a world | Keep |
| Tectonic, Terralith, Lithostitched | MIT and Stardust | world generation, unmeasured | Pre-generate the play area (OPT-19) |
| Sound Physics, AmbientSounds, Presence Footsteps | embedded in Ark | unmeasured (sound thread) | Measure in OPT-01, then decide |

Ark's own blocks were checked and are not a cost: eight block entities with a ticker, two blocks with random
ticks, static loose rocks, block models of 58 elements at most. The block-level costs are the atlas (OPT-02),
the grass (OPT-13) and Tom's Storage scans (OPT-05).

---

## 3. The plans at a glance

Size: S is one agent session, M a few sessions, L a long effort with a spike first. Regime: C for the card,
R for the render thread, S for the server thread, M for memory and loading.

| ID | Plan | Regime | Size | Evidence | Needs a decision |
|---|---|---|---|---|---|
| OPT-01 | Performance gate: scenarios, server and sound thread, budgets | all | M | measured gaps | targets (7.1) |
| OPT-02 | Four mip levels back on the block atlas | C | S | read | no |
| OPT-03 | Caps on Grassier Grass building and Xaero map writing | R | S to M | measured | no |
| OPT-04 | Vanilla mob load: villagers, spiders, bats | S | S | measured | yes (7.3) |
| OPT-05 | Tom's Storage fork: no beacon scan, connector by events | S | M | read | no |
| OPT-06 | Curios fork: skip entities that wear nothing | S | S | read | no |
| OPT-07 | Population pass spread over ticks | S | M | measured (P21) | no |
| OPT-08 | Baked animation clips and Ark's own sampler | R, M | L | measured | no |
| OPT-09 | Creature detail levels, box and bone budgets | R, C | M | counted | no |
| OPT-10 | Creature texture budget | M, C | S to M | counted | no |
| OPT-11 | Retained creature meshes | R | L, spike first | measured, design is estimate | no |
| OPT-12 | Resting bodies: creature physics by state and distance | S | M | measured | no |
| OPT-13 | Ark Grass, replacing Grassier Grass | C, R | L, spike first | measured, design is estimate | yes (7.2) |
| OPT-14 | Ark map, replacing Xaero | R | L | measured | yes (7.4) |
| OPT-15 | Distant Horizons: pre-built LODs, shipped settings | R, M | S to M | measured | no |
| OPT-16 | Shader presets by measured cost | C | M | measured in part | yes (7.5) |
| OPT-17 | What ships: settings, heap, leftover jars, Physics Mod | all | S | read | yes (7.6) |
| OPT-18 | One authority for distance and budget | all | M | design | no |
| OPT-19 | Two players: server process, bounded pre-generated world | S, M | M | estimate | yes (7.7) |
| OPT-20 | Jar and memory weight | M | S | counted | no |

---

## 4. Rules for whoever executes a plan

These hold for every plan and are not repeated in each.

1. Read `AGENTS.md`, `CLAUDE.md` and the Dashboard rows the plan names before starting. Register the change in
   `Dashboard.csv` in the same commit (a new row, or the named row rewritten in place) and name the IDs in the
   final summary.
2. A gain is claimed only from `python Ark/tools/session_bench.py` with `--repeat 2 --interleave` against the
   unchanged build, at the laptop's hot steady state, with card watts and temperature compared too. A profile
   alone overstates a gain on this machine. State the regime the gain is for.
3. Client runs go through `tools/session_run.py` or `tools/session_bench.py`, never `runClient`. Ask the user
   before a run longer than a few minutes. While a batch runs, do not edit sources (each launch recompiles).
4. Every rendering change keeps the old path behind a client setting, as F27 did, and has a parity test that
   does not need the client (the model of `CubeWriterParityTest` and `PlainKeysTest`).
5. Ark's Java sources have CRLF line endings: no `sed -i`. Common and server code stays free of client
   imports. A mixin into another mod uses `require = 0` and logs once when its target is missing, so an
   update of that mod cannot stop the game.
6. If another session is editing `main`, work in a worktree under `.work/`. Plans that touch the same package
   run one after another: OPT-08, OPT-09 and OPT-11 all live in `client/draw`.
7. `./gradlew build` and the GameTests are run when the plan ships, not after every edit (C06).

---

## 5. The plans

### OPT-01 Performance gate: scenarios, server and sound thread, budgets

**Why.** Section 1.6: the numbers come from one biome, one pack that is not the shipped one, an empty world
and a single player, and the server and sound threads were never part of the result table. Plans OPT-04, 05,
12 and 19 cannot be judged with what exists.

**What changes.** `session_bench.py` and its summary gain what is missing; nothing in the game does.

**Steps.**
1. Put the server thread in `summary.json` per phase: its share by owner and its top entity types
   (`SessionProfile.java` already does the work when given `"Server thread"`; add the entity-type grouping by
   the `*.tick` frame nearest the top of `EntityTickList.forEach`). Add an entity census by type to
   `samples.csv` (the client knows the loaded entities; the integrated server knows all).
2. Add the sound thread to the profile report (the thread name is in the JFR file; find it with a listing of
   thread names by sample count).
3. New scenarios beside the plains flight: `base` (a prepared save with 2 storage terminals on 40 crates,
   4 lit fires, 2 drying racks, 12 tames with orders), `herd` (60 creatures within 64 blocks, a hunt started
   by the scenario), `forest` (a Terralith jungle, for leaves, shadows and the grass under canopy) and
   `bliss` (the shipped default pack and preset on the plains flight).
4. Name the caller of `GL11.glFinish` (3.1 % of the render thread in flight; `SessionProfile` shows only
   LWJGL above it, so print the full stack for that frame).
5. Write budgets into `Ark/design/existence/runs.json` or a new `Ark/design/performance/budget.json`: frame
   rate standing and flying, 1 % low, server tick, per-owner ceilings. `session_bench.py --gate` exits
   non-zero when a run is over budget by more than run-to-run noise (measure the noise first with four
   interleaved repeats).

**Done when.** One command produces a table for all scenarios with render, server and sound thread shares,
and fails on a regression.

**Risks.** The prepared `base` save has to be rebuilt when block ids change; build it by commands in the
scenario instead of shipping a save.

---

### OPT-02 Four mip levels back on the block atlas

**Why (read).** Client log of every run: `Texture grassiergrass:block/blade with size 2x2 limits mip level
from 2 to 1`, then `minecraft:textures/atlas/blocks.png: dropping miplevel from 4 to 1`. Every block in the
game is drawn with one mip level instead of four. That is shimmer on distant terrain, which the shader pack's
anti-aliasing then has to hide, and worse texture cache behaviour on the card.

**What changes.** Ark overrides the two textures with 16x16 versions (`blade` is 2x2, `grass_bands` 8x4).
Scaled up without smoothing, a sprite looks the same wherever it is sampled with nearest filtering.

**Steps.**
1. Add `assets/grassiergrass/textures/block/blade.png` and `grass_bands.png` at 16x16 to a resource source
   that is above the mod's own: the built-in Ark UI pack is forced on (`arkUiPack`), so it is the safe place.
   Generate them in `tools/` from the originals at build time rather than committing copies of a closed
   mod's art; if that needs the jar, read it from `client-mods` by its lock entry.
2. Confirm in the log that the atlas is created with level 4 (`Created: ...x4 minecraft:textures/atlas/blocks`).
3. Compare a screenshot of a grass plain at noon with and without; the blades must not change.

**Done when.** The log shows four levels; the grass looks the same; the bench on the plains shows no loss.
Expect a small gain on the card or none; the reason to do it is image quality and that it is free.

**Risks.** If Grassier Grass samples those sprites with linear filtering, the scaled blade edges soften;
the screenshot comparison catches it. OPT-13 removes the cause altogether.

---

### OPT-03 Caps on Grassier Grass building and Xaero map writing

**Why (measured).** In flight Grassier Grass is 24.5 % of the render thread and 71 % of slow-frame time, all
of it building sections on the render thread; Xaero is 10.8 %, writing map pixels on the render thread. Both
are closed, but neither is obfuscated: the profile names `GrassSectionCache.buildBudgeted`,
`GrassSectionBuilder.build`, `xaero.map.MapWriter.onRender` and `MapWriter.writeChunk`.

**What changes.** Two optional client mixins in Ark, each loaded only when its mod is present (the pattern of
`client/betterf3` and `client/title/fancymenu`).

**Steps.**
1. Decompile both jars locally for reading only (the CFR recipe is in the FancyMenu memory note). Find how
   `buildBudgeted` decides how much to build in a frame and how `MapWriter.onRender` decides how many chunks
   to write.
2. Grassier Grass: wrap `buildBudgeted` so the time it may spend in one frame is a client setting
   (`grassBuildMillis`, default 2 ms), and so sections nearest the camera are built first. If it already has a
   count budget, lower it while the camera moves faster than 8 blocks a second.
3. Xaero: wrap `MapWriter.onRender` with a time budget (`mapWriteMillis`, default 1 ms) and skip it entirely
   while the player flies or rides faster than a set speed, catching up when slow. The map is not on screen
   while flying.
4. Bench the flight, interleaved.

**Done when.** In the flight phase frames over 50 ms fall from 69 to under 20 and the 1 % low rises from 8.7
toward the no-grass value of 13.2, with the grass complete within two seconds of stopping. Standing must not
change.

**Risks.** A cap makes grass and map appear later after fast travel; the settings let the user choose.
An update of either mod can move the targets: `require = 0` and a logged warning.

---

### OPT-04 Vanilla mob load: villagers, spiders, bats

**Why (measured).** Villagers 22.7 %, spiders 14.7 % and bats 2.2 % of the server thread, against 25.2 % for
49 Ark creatures. The villager cost is path finding from their brain (`MoveToTargetSink.tryComputePath`,
13.6 %).

**Estimate to confirm first.** The theme removed every hostile mob except the spider
(`ThemePolicy.REMOVED_ENTITIES`, `remove_fantasy_spawns.json`), so the monster cap of 70 around a player can
only fill with spiders, in the caves under the player. Step 1 counts them.

**Steps.**
1. Count entities by type in the benchmark world (OPT-01 step 1, or once by command). If spiders are near 70,
   the estimate holds.
2. After the user's decision (7.3), one of:
   - spiders stay but as a rare cave animal: refuse their spawn placement above a count near the player
     (`MobSpawnEvent.SpawnPlacementCheck` in `HostileGuard`, counting spiders within 128 blocks, default 8);
   - spiders go: add `spider` to `ThemePolicy.REMOVED_ENTITIES` and rerun `runData`;
   - bats: same choice, they do nothing in this game.
3. Villagers, if villages stay: tick the brain of a villager with no player within 48 blocks every fourth
   tick (a mixin on `Villager.customServerAiStep`), and every tick again when one comes near. If villages go,
   add the village structure sets to `ThemePolicy.DISABLED_STRUCTURE_SETS` (new terrain only).
4. Bench standing on the plains start, which has a village in range.

**Done when.** The server tick on the plains start falls from 12 to 17 ms to under 10 ms with the same 49
creatures, and the entity census shows the intended counts.

**Risks.** Villager trades are part of the current economy (ThemePolicy trims them, it does not remove them);
slowing a distant villager's brain delays restocking slightly. Spiders are the only source of string and
spider eyes: phase 3 has to weigh that.

---

### OPT-05 Tom's Storage fork: no beacon scan, connector by events

**Why (read; never measured because no run has a terminal).** In `Ark/pack/src/toms-storage`:
- `StorageTerminalBlockEntity.updateServer` reads all 4,913 blocks of a 17-block cube around every terminal
  every 40 ticks, through a stream, looking for a beacon. Ark removed the beacon
  (`ThemePolicy.REMOVED_ITEMS`, `DISABLED_BLOCKS`); the scan can never find one.
- `InventoryConnectorBlockEntity.updateServer` rebuilds its whole network every 20 ticks: a flood fill over
  every touching inventory within `invConnectorScanRange`, new maps, sets and a stack each time, then
  `MultiInventoryAccess.build`, whether or not a block changed.
- The terminal regroups every stack of the network when the change counter moves.

**What changes.** A second Ark patch on the fork (`Ark/pack/patches/toms-storage/`, applied by
`build_pack.py` as the Curios patch is; the fork has none today).

**Steps.**
1. Remove the beacon scan; `beaconLevel` stays at -1 (no beacon), which is what it always was here.
   Check what reads it (the wireless terminal's range) and keep that path working at its base range.
2. Connector: keep the last topology and rebuild only when told to. Sources of change: a neighbour block
   update on any block of the last flood fill's edge (`neighborChanged` on the connector and on the trims and
   cable blocks it walked), a block capability invalidation (NeoForge's `BlockCapabilityCache` takes an
   invalidation listener), and chunk load or unload inside the scan range. Keep a slow safety rebuild
   (every 200 ticks) so a missed event heals.
3. Run the fork's GameTest (`integration_toms_storage`) with `-ParkPack`, and the `base` scenario of OPT-01.

**Done when.** With 2 terminals on 40 crates the storage code is under 0.5 % of the server thread, and
adding, breaking and moving a crate shows in the terminal within a second.

**Risks.** Missed invalidation leaves a stale network until the safety rebuild; ten seconds is the worst case.
Keep the patch small and separate so the fork can still follow upstream.

---

### OPT-06 Curios fork: skip entities that wear nothing

**Why (read).** `CuriosCommonEvents.tick` runs on every entity on both sides and asks each living one for a
Curios inventory, every tick. In Ark only players have slots. Measured share is small (0.1 to 0.3 %), so this
is tidiness, done when the fork is next touched.

**Steps.** In the existing Ark patch, return at once unless the entity is a player or its type is in the
slot data. Ark's own side: `Worn.state` calls `CuriosBridge.collect` every player tick; keep the collected
set and refresh it on `CurioChangeEvent`.

**Done when.** `integration_curios` passes and the accessory effects still switch on and off when an item is
put on or taken off.

---

### OPT-07 Population pass spread over ticks

**Why (measured, Dashboard P21).** The first budget pass over land nobody had been to took 0.6 s under the
bounded rules and 1.0 s under the first rules: one server tick that long, a visible freeze for both players
when either reaches new land. Later passes take 2 to 4 ms.

**What changes.** `NaturalPopulations.settle`, `SilentLife.pass` and `LandRegister` work from a queue with a
time budget instead of doing everything that became due at once.

**Steps.**
1. Record what the long pass spends its time on (the session recorder already times the pass; add the split:
   land survey of new chunks, region counts, group placement).
2. Queue newly loaded chunks; each tick take from the queue for at most 2 ms. The survey of a chunk
   (open ground, biome, fertility) reads only heightmaps and biomes, so compute it on a worker thread from
   a copy taken at chunk load and commit the result on the server thread.
3. Keep the rule that a chunk is settled once and only out of plain sight; a chunk that waits in the queue is
   simply settled a few ticks later. The GameTest entry point `enforce` drains the queue fully so tests stay
   deterministic.

**Done when.** On the out-and-back flight the longest server tick is under 50 ms and the register's counts
match a run of the old code on the same seed (the recorder's roll calls are the comparison).

**Risks.** Settling later changes which chunk is "in plain sight" at the moment of placing; the existing
`spawn_sight` GameTest guards it.

---

### OPT-08 Baked animation clips and Ark's own sampler

**Why (measured).** GeckoLib is 5.5 to 7.1 % of the render thread, about 2 % of it finding keyframes, and
Ark's reading of the keys for it (`PlainKeys.value`) another 2.0 to 2.8 %; it makes a snapshot object per
bone per creature per frame. It
holds 1.55 million keyframes as objects (several hundred MB of heap by estimate; step 1 measures it) parsed
from 67 MB of JSON at every resource load. The clips come from ARK at a fixed rate, so keyframes, easing
objects and searches buy nothing.

**What changes.** Ark reads its creatures' clips from a compact file of its own and poses the bones itself;
GeckoLib keeps loading the models, and stays the fallback.

**Design.**
- **File.** `tools/import_creatures.py` (or a new `tools/bake_animations.py` it calls) writes one binary per
  species beside the JSON: for each clip its length, rate (30 a second) and loop flag; for each bone and
  channel either one constant or a table of 16-bit values, three per sample. Channels that never move are
  one value. Estimate: 67 MB of JSON becomes about 10 MB before the jar's compression.
- **Sampler.** `client/draw/ArkClips`: the value of a channel at a time is two table reads and one
  interpolation; no search, no objects.
- **Mixer.** What `CreatureEntity.registerControllers` asks of GeckoLib is small and known: four controllers
  (movement, feeding as an additive layer, reaction and attack as one-shots), a blend of 5 or 12 ticks
  between clips, a playback rate per controller. `ArkMixer` reproduces exactly that per creature and writes
  the result straight into `CubeWriter.Posed` (translation, three rotations, scale, hidden flags), so
  `CubeWriter.pose` no longer reads `BoneSnapshot`s. The eyes (`CreatureEyes.apply`) and the two eye sets
  write into the same arrays.
- **Server.** Nothing changes: the server never evaluates animation, and hit frames come from
  `BehaviorClips` lengths. `triggerAnim` packets are replaced by Ark's own small payload or by the synced
  action Ark already has; decide in step 3.
- **Shared poses (second stage).** Beyond the animation distance a creature takes its pose from a small
  cache keyed by species, clip and one of eight phases of the loop, so a far herd costs one pose per phase,
  not one per animal.

**Steps.**
1. Measure the heap GeckoLib's keyframes hold (`jcmd <pid> GC.class_histogram`, filter `geckolib`) and the
   time of the animation part of a resource reload, as the baseline.
2. Baker and file format, with a round-trip test: every clip sampled at every key time equals the JSON's
   value within the 16-bit step.
3. Sampler and mixer behind a client setting `creatureOwnAnimation`; parity test against GeckoLib for every
   species, every clip, including a blend and the additive feeding layer, in the manner of `PlainKeysTest`.
4. Offline pose renders (`scripts/preview_dinosaurs.render`) of five species in three clips each, both ways.
5. Bench standing and the `herd` scenario. Then stop shipping the animation JSON for species that pass, and
   drop `PlainKeys` and its mixin.
6. Second stage: shared poses for the far tier.

**Done when.** Parity holds for all 44 models; GeckoLib plus Ark animation time on the render thread falls
from about 10 % to under 3 % standing; the heap and the jar shrink by the measured amounts.

**Risks.** A blend that differs from GeckoLib's by a frame is invisible but fails a bit-exact test: compare
with a tolerance and by eye. The title screen creature uses the same renderer and must be in the parity run.
The Guardian dragon and Giganotosaurus have their own entity classes: include them.

---

### OPT-09 Creature detail levels, box and bone budgets

**Why (counted and measured).** A creature averages 205 boxes, up to 24 vertices each, about 240,000 vertices
a frame for 49 creatures before the hidden-face cut. Today's only detail level is leaving out boxes under a
pixel, tested box by box every frame, and holding far animals' bones for two to four frames. Five models are
over 380 boxes and one has 323 bones.

**What changes.** The model builders write two simpler versions of every model, and the drawing picks one
by size on screen.

**Steps.**
1. Budgets, enforced by `tools/verify_assets.py`: the full model at most 300 boxes and 160 bones; a middle
   model at most 80 boxes; a far model at most 24. Report the models over budget (today: Deinosuchus,
   Ankylosaurus, Megalocerus, Sarco, the old Dragon, Titanosaur, Brontosaurus; Acrocanthosaurus for bones).
2. `scripts/handmade_models.py` (the P24 builder) and the fitter for the procedural species emit the middle
   and far models on the same rig: teeth, claws, osteoderms, eye sets and cut-out cards dropped or merged into
   their parent's hull, small bones folded into their parent. Same texture, so no new art.
3. `CreatureMesh` holds the three; `CreatureDraw` picks by projected height (it already has
   `pixelsPerRadian`), with a margin so a creature does not flip between levels. The far model's bones are a
   subset: `CubeWriter.pose` skips the folded ones.
4. Bring the over-budget full models under budget where it does not change the look (teeth rows as one
   cut-out card per row, as the sea species already do).
5. Offline render sheets of each level at its switching distance, for the user's eye; then the bench.

**Done when.** Vertices written per frame in the plains run fall by half or more (the draw report already
counts faces written), with no visible pop at the default distances in the user's judgement.

**Risks.** Detail levels are a look decision; send the sheets before making them the default.
Works with either drawing path, so it does not wait for OPT-11.

---

### OPT-10 Creature texture budget

**Why (counted).** 243 MB of creature textures if all are loaded, on a 6 GB card that already holds 1.6 GB.
The Titanosaur's four colourings are 68 MB, the three wyverns 50 MB. Entity textures have no mip levels and
are never released once loaded.

**Steps.**
1. Remove what is not used: `dragon.png`, `dragon_burgundy`, `dragon_darken`, `dragon_emerald`,
   `dragon_ivory`, `dragon_midnight` and `dragon.geo.json` (412 boxes) are not in `CreatureVariants` (red,
   white, black). Confirm nothing references them, then stop the importer from writing them.
2. Cap a creature texture at 1024 on its long side: re-pack the Titanosaur's and the wyverns' texture space
   in their builders (the black wyvern is hand-authored: ask before touching it, or leave it).
3. Release a creature texture that no drawn creature has used for five minutes
   (`TextureManager.release`), so 41 species in four colours never sit in memory together.
4. Report video memory in the bench before and after (`vram_mb` is already there).

**Done when.** The decoded size of all creature textures is under 100 MB and a session that has met every
species holds under 60 MB of them.

---

### OPT-11 Retained creature meshes

**Why (measured).** Writing creature vertices is 14.0 % of the render thread standing. Every frame, every
creature's every vertex is recomputed and copied, about half a gigabyte a second by estimate, although a pose only
changes at the animation's pace and a sleeping animal's not at all. Under a pack that draws entity shadows
(Bliss, Complementary) all of it is written a second time for the shadow map.

**The idea.** Keep each creature's posed vertices in a buffer on the card, in the creature's own space.
A frame then only sets a matrix and issues a draw. The buffer is rewritten when the pose changes: 20 times
a second near, 5 to 10 far, never while it sleeps. The shadow pass and the eyes layer draw the same buffer.

**This is a design, not yet a fact.** It depends on answers the spike has to give. The profile shows Iris
swapping in the pack's program inside `GlCommandEncoder.trySetup` for a draw through `RenderPass`, which is
why the idea is plausible under a shader pack.

**Stage 1, spike (S).** One species, behind a setting, answering:
1. Can a buffer kept by Ark be drawn through the vanilla entity pipeline with its own model matrix, and does
   Iris give it the pack's entity program, in the main pass and in the shadow pass? Compare the picture with
   the present path pixel for pixel in a fixed scene.
2. Normals and tangents: a pack transforms them by the matrix; does vanilla's own entity shader (no pack)
   expect them already turned? If so the no-pack path needs Ark's own small pipeline, or stays as it is.
3. Light and the hurt overlay are per vertex and the same for the whole creature: rewrite the buffer when
   they change by a step, or carry them another way.
4. What a rewrite costs (map, write, unmap for about 270 KB) against today's per-frame write.

**Stage 2, build (L)**, only if the spike holds: a pool of buffers by size, rewrite on pose change, draw
lists per render type, the eyes layer as a second index range, fall back to the present writer for anything
unusual (outlines, Physics Mod reading vertices, a GUI view). Parity by reading the buffer back in a test.

**Stage 3, optional and far (L):** true skinning on the card, where a frame uploads only bone matrices.
A shader pack's entity program knows nothing of bones, so this needs a patch in an Iris fork that adds the
skinning to the pack's vertex program as Iris already rewrites pack shaders. Only worth it if stage 2 leaves
creature drawing as the top cost.

**Done when (stage 2).** Creature vertex writing falls from 14 % of the render thread to under 5 % standing,
and under Bliss the shadow pass costs no second write.

**Risks.** High. The entity pipeline of 26.1 and Iris's handling of it may not allow a foreign buffer with
its own matrix; then the answer is no and OPT-08 and OPT-09 carry the creature work alone. Do OPT-08 first:
it moves posing to Ark's arrays, which stage 2 builds on.

---

### OPT-12 Resting bodies: creature physics by state and distance

**Why (measured).** An Ark creature costs the server its vanilla body, not its mind: movement and collision
(`Entity.move`, 24.5 % of the thread for all mobs), the blocks it stands in (8.8 %), the block under it
(6.2 %). `BehaviorTier` already stops the mind beyond 128 blocks; the body ticks in full regardless. Large
bodies pay most: a Titanosaur's box touches several hundred blocks per check.

**What changes.** `CreatureEntity` gets a resting state for its body.

**Design.**
- A body is at rest when it is on the ground, out of fluid, has no velocity, no path, no passenger, no
  target, was not hurt this tick, and is asleep, resting, idle or in the dormant tier.
- At rest, `aiStep`'s travel and the block checks run every tenth tick (dormant tier) or every fourth
  (asleep or resting near a player); timers, effects and the mind's own schedule run as now. Anything that
  ends rest (a hit, a push, a path, a change of tier, a rider) restores full ticking at once.
- The ground can be dug from under a resting animal: the slower full tick finds it within half a second.
- For bodies wider than two blocks, the check of blocks stood in looks at the bottom layer and the head
  only; nothing in the middle of a Brontosaurus matters to it.
- Position packets: a resting body has nothing to send; confirm the tracker already skips unchanged
  entities, and if not, skip it for resting ones.

**Steps.**
1. Profile `CreatureEntity.tick` by callee in the `herd` and plains scenarios to rank the three costs.
2. Implement behind `behavior.restingBodies` (server config, on by default once verified).
3. GameTests: a sleeping creature falls when its ground is removed; a pushed, hit, mounted or called
   resting creature moves on the same tick; water rising over a sleeper wakes it (the existing interrupt).
4. The 18 recorded behaviour trials (`session_run.py --scenario behavior`) must still give their verdicts.
5. Bench: server tick on the plains and in `herd`.

**Done when.** `CreatureEntity.tick` falls from 25 % of the server thread to under 12 % in the plains run
with the same creatures, and all trials pass.

**Risks.** Vanilla assumes a living entity moves every tick in many small places (fall distance, fluid
state, portal and pressure-plate contact). Keep the full tick as the default and enter rest only by the
strict test above. Tames with orders are included only when their order is Stay.

---

### OPT-13 Ark Grass, replacing Grassier Grass

**Why (measured).** Grassier Grass is the largest single cost in both regimes: half the card standing,
a quarter of the render thread and nearly all stutter in flight, 0.58 GB of video memory, and the cause of
OPT-02. It is closed, so none of that can be fixed in place, and its licence is a question for publishing.
The user has decided the look stays: 64 blades a block, out to 128 blocks.

**What changes.** Ark draws the grass itself, to the same look, and the jar leaves the pack.

**Design (estimate; the spike decides).**
- **Built off the render thread.** A section's blades are computed on worker threads from a copy of the
  chunk, as Sodium builds terrain, and handed over finished. The render thread only uploads.
- **Detail without rebuilding.** Each block's blades are ordered by a fixed random rank, and a section's
  buffer is sorted by rank. Drawing the first tenth of the buffer draws every block at a tenth of its
  density. Distance then only changes how much of the buffer is drawn, and blades widen as they thin so the
  ground stays covered. Density near the player stays 64.
- **Fewer vertices far away.** Beyond about 48 blocks a tuft is one textured card instead of segmented
  blades.
- **Under a shader pack** the geometry goes through the terrain cut-out pipeline with the grass block id, so
  the pack lights it and waves it with its own plant animation, the same as its short grass. Without a pack
  Ark's own vertex shader does the wind.
- **Trampling** (the trail field Grassier uploads every frame) becomes a small texture updated only where
  a creature or player moved.

**Stage 1, spike (M).** One biome, no detail levels: build off-thread, draw retained buffers through the
terrain pipeline under Photon and Bliss, measure card load and video memory against Grassier Grass at the
same density, and put screenshots side by side for the user. Questions it must answer: does the pack wave
and light geometry that is not part of a chunk mesh; what does 64 blades cost in vertices when a blade is
two triangles instead of segmented; does Distant Horizons' fade still meet the grass edge.

**Stage 2 (L).** Detail by rank, far cards, flowers and plants as Grassier draws them
(`grassPlantsAsBlades`, `denseFlowers`), snow, biome tint, the client settings, then removing the jar from
the lock file and `integrations.json`.

**Done when.** Side by side the user accepts the look; standing, card load with grass is under 75 % where
it was 97 %; in flight no slow frame is owned by grass; video memory for grass is under 300 MB.

**Risks.** High, and mostly about the look, which is the user's to judge. If the spike fails under shader
packs, the fallback is to keep Grassier Grass with OPT-02 and OPT-03 and accept the card cost. Do not start
stage 2 before the user has seen the spike's screenshots.

---

### OPT-14 Ark map, replacing Xaero

**Why (measured).** Xaero is 10.8 % of the render thread in flight and cannot be configured from files
(Dashboard P12). It is closed; Ark's danger overlay reaches it through a second mod and by reflection
(`XaeroExploration`). F17 already plans a map item and a radar, and F17 asks whether the map item replaces
the zone-3 unlock.

**What changes.** When F17 is built, the map is Ark's: explored land kept by the server per tribe, drawn by
Ark, the danger zones native, the radar on the same screen.

**Design.** The server records explored chunks per tribe (one bit a chunk, then a colour per column sampled
once on a worker thread from the heightmap and biome when the chunk is first seen by a member), saved with
the world and sent to members in small pieces. Both players see one shared map: that is something Xaero
cannot do and an ARK-like feature in its own right. The client draws tiles from that data; nothing is
computed on the render thread but the upload of a changed tile.

**Steps.** Decide with F17 (7.4). Until then OPT-03 caps the cost. When built: remove both jars and
`XaeroDangerOverlay`, `XaeroExploration`, `DangerMapClient.isMap`.

**Done when.** No map code runs on the render thread outside the map screen, and two players in one tribe
see each other's explored land.

**Risks.** A map people like using is real work (zoom, waypoints, cave layers). Scope it to what the two
players need; phase 3 lists it as a feature.

---

### OPT-15 Distant Horizons: pre-built LODs, shipped settings

**Why (measured).** Distant Horizons costs 8 to 9 % of the frame rate in both regimes. Its world generation
thread makes half of all allocation and its heat. The setting found to help (3 threads at half pace, +8 % in
flight) is in `Ark/run/config` only: the pack ships no Distant Horizons settings at all. A fork is not
justified: removing the mod entirely gains less than the grass plan.

**Steps.**
1. Ship `DistantHorizons.toml` in `Ark/config/client-defaults/config` with the measured thread settings and
   the quality the dev run uses (OPT-17 owns the file list).
2. For a bounded world (OPT-19), build the LODs once: generate the chunks with Chunky as
   `session_bench.py --prepare` does, let Distant Horizons build from the existing chunks, and keep its
   database with the world, so in play its generation thread has nothing to do. Measure allocation and the
   flight phase after.
3. Give the second player the same LOD database (copy the file) instead of having their client rebuild it.
4. Under a shader pack Distant Horizons draws a deferred pass (`renderDeferredLodsForShaders`, 4.1 %); note
   which packs need it when OPT-16 measures packs.

**Done when.** On a pre-built world the Distant Horizons generation thread allocates under 10 MB a second in
flight, and the pack installs with the tuned settings.

---

### OPT-16 Shader presets by measured cost

**Why (measured in part).** The pack costs 31 % of the frame rate standing (58.1 without, 39.6 with) and its
shadow pass is 10.9 % of the render thread. Which options cost what is not known; the shipped default
(Bliss) has never been benchmarked against the pack that was (Photon), and the choice between them is open
(Dashboard I06).

**Steps.**
1. Bench matrix on the plains and `forest` scenarios, interleaved, for Bliss, Photon and Complementary:
   stock, shadows off, shadow distance 6 / 8 / 12 chunks, shadow map 1024 / 2048, entity shadows off,
   volumetric fog samples halved, clouds off. One table: frames standing and flying, card load, video memory.
2. From the table write three presets per pack kept (`Performance`, `Balanced`, `Quality`) as the `.txt`
   option files the installer already seeds, with a line each on what was given up.
3. The user picks the default pack and preset with the table and screenshots (7.5).
4. Check each pack's terrain shadow pass against Sodium's settings: the walk of the terrain tree for the
   shadow frustum is 4.3 % of the render thread.

**Done when.** `client-defaults` holds measured presets, the default is chosen by the user from numbers, and
the Balanced preset holds the frame-rate target of 7.1 standing on the laptop.

**Risks.** Render scale was already shown not to help; do not spend runs on it.

---

### OPT-17 What ships: settings, heap, leftover jars, Physics Mod

**Why (read).** `Ark/config/client-defaults` holds `iris.properties`, the Bliss preset and the Physics Mod
preset, nothing else. Sodium, Distant Horizons, Grassier Grass, Xaero and the game's own options
(`options.txt`: render distance, mipmaps, particles) ship at stock values, so an installed pack does not run
the configuration that was tuned and measured. The dev run has a 16 GB heap on a 23 GB machine; the pack sets
none.

**Steps.**
1. Decide the file list for `client-defaults` and copy the tuned values from `Ark/run/config`:
   `sodium-options.json`, `DistantHorizons.toml`, `grassiergrass-client.toml` (until OPT-13),
   `options.txt` defaults (seeded only when absent, as the installer already does for Iris).
2. Heap: measure the smallest heap that holds the flight without longer pauses (try 6, 8 and 10 GB,
   interleaved; garbage collectors other than G1 were already tried and lost). Put the recommendation in the
   pack's description and the installer; a Modrinth pack cannot set it.
3. Extend `tools/verify_client_pack.py` to fail when a tuned file is missing from the built pack. It
   currently stops at the renamed Photon archive (Dashboard I14): fix that first.
4. Leftovers: `Ark/shared-mods` still holds the JEI jar and `Ark/client-mods` the AmbientSounds,
   CreativeCore and Sound Physics jars that Ark embeds or removed. Confirm the dev run and the pack load
   none of them, then delete them or move them out of the folders.
5. Physics Mod, after 7.6: keep with its preset, or remove the jar and its integration row.

**Done when.** A fresh install of the built pack starts with the same settings as the benchmarked dev run,
and the verifier proves it.

---

### OPT-18 One authority for distance and budget

**Why (design).** Distance decides detail in six places, each with its own numbers and its own way of
measuring: behaviour tiers (64, 128, 256 blocks, `BehaviorLod`), creature drawing (64 blocks,
`creatureAnimationDistance`), entity tracking (192), the grass (128), Distant Horizons, the sound engine.
None knows the frame is over budget. After OPT-09, 12 and 13 there will be more.

**What changes.** A small client service and a small server service that the others ask.

- Client `DetailGovernor`: the camera, the zoom, the frame time of the last second against the target.
  It hands out one scale (1.0 at target, lower when frames run long) that creature detail levels, the pose
  hold, the grass density beyond 32 blocks and the build caps of OPT-03 multiply their distances by.
  Slow to change, so nothing flickers.
- Server `ObserverField`: the nearest player to a position, computed once a tick per chunk rather than per
  creature (`BehaviorLod.nearestObserverSq` loops over players for every creature), shared by behaviour
  tiers, resting bodies and the population pass.

**Steps.** Introduce the two services with today's numbers and no change in behaviour (a refactor with the
GameTests as the guard), then let each plan that adds a distance take it from there.

**Done when.** No distance constant for detail lives outside the two services, and a forced 20 fps cap in a
test shows the scale falling and detail following.

**Risks.** A governor that reacts to its own effect oscillates: change the scale by at most a tenth a second
and never from one slow frame.

---

### OPT-19 Two players: server process, bounded pre-generated world

**Why (estimate; nothing was measured with two players).** Today the host's machine runs the client and the
integrated server in one process and one heap: the server thread is 25 to 44 % of a core, a garbage
collection stops both, and both players' land is generated and simulated there. Two players apart double the
loaded area, the creatures with a body and the chunk generation.

**Steps.**
1. Measure first: a two-client run (OPT-01) with the players 500 blocks apart, server tick and host frame
   rate against the single-player numbers.
2. Run the world as a dedicated server process beside the host's client (or on whichever machine has spare
   cores): own heap, own collector, and the client's frame no longer shares pauses with it. The pack already
   tests the server jars (`runGameTestServer -ParkPack`); write the start script and the server's mod list
   (Distant Horizons stays client-only).
3. Bound the world for the campaign (a world border, for example 4,000 blocks across) and pre-generate it
   once with Chunky, then build the Distant Horizons LODs from it (OPT-15). In play nothing is generated:
   that removes the largest allocation source and the server tick's rise in flight.
4. Server settings for two: simulation distance (12 today) against view distance; confirm
   `populationGlobalCap` and the per-player budget behave with two players apart and together.
5. Payloads that only exist in single player: the wildlife count of the debug screen (Dashboard I14).

**Done when.** With two players apart the server tick stays under 25 ms and the host's frame rate is within
5 % of playing alone.

**Risks.** A bounded world is a design decision (7.7); the danger zones repeat in a grid, so a border has
to leave whole zones inside it.

---

### OPT-20 Jar and memory weight

**Why (counted).** The jar is 143 MB. OPT-08 removes most of 67 MB of animation JSON. The 82 MB of ambient
recordings were taken over whole from AmbientSounds, while Ark's own catalog
(`arksurvivalreturns:audio/catalog.json`) decides which play.

**Steps.**
1. List the recordings no catalog entry references and stop shipping them (`tools/verify_assets.py` gains
   the check).
2. Check the three title-scene textures (2880x1200 twice, 1920x1080): 38 MB decoded. Confirm FancyMenu
   releases them when a world is loaded; if not, release them on world join.
3. Report jar size and the heap after a resource load in the bench summary, so growth is seen.

**Done when.** The jar is under 80 MB after OPT-08 and this plan, with every sound the catalog names present.

---

## 6. Order of work

Plans in the same wave can run at the same time in separate worktrees unless noted.

**Wave 0, first:** OPT-01. Everything after is judged by it.

**Wave 1, small and independent:** OPT-02, OPT-03, OPT-05, OPT-06, OPT-07, OPT-10 (step 1), OPT-20 (step 1),
and OPT-04 once 7.3 is answered. Expected together (estimate): most of the flight's stutter gone, the server
tick cut by about a third near a village, sharper distant terrain.

**Wave 2, the creature pipeline:** OPT-08, then OPT-09, then the OPT-11 spike, one after another in
`client/draw`; OPT-12 on the server at the same time; OPT-10 steps 2 to 4 with the asset builders.

**Wave 3, the world stack:** the OPT-13 spike and the OPT-16 matrix (both need the user's eye), OPT-15,
then OPT-18 once two or more plans want a distance.

**Wave 4, shipping and two players:** OPT-19, OPT-17 last (it collects every setting the others produced),
OPT-13 stage 2 and OPT-11 stage 2 if their spikes held, OPT-14 with F17.

What to expect, stated carefully: standing, only OPT-13 and OPT-16 can move the frame rate much, because
only they unload the card (removing the grass entirely gives 44 against 40; a cheaper shader preset is the
larger lever, 58 without any pack). In flight and for stutter, OPT-03 alone should recover most of the gap
to the no-grass run (36 against 29), and OPT-08, 09 and 11 free the render thread further. On the server,
OPT-04 and OPT-12 address about 60 % of the measured tick between them.

---

## 7. Decisions for the user

1. **Targets.** What frame rate should the laptop hold standing, flying and at the 1 % low, and what does
   the second player's machine have? OPT-01 turns the answer into budgets.
2. **Grass.** If the spike of OPT-13 matches the look in screenshots, may Ark Grass replace Grassier Grass?
3. **Villagers, spiders, bats.** Do villages belong in this world? Should spiders stay as a rare cave animal,
   or go? They cost 37 % of the server tick in the measured run.
4. **Map.** Replace Xaero with an Ark map shared by the tribe when F17 is built, or keep Xaero capped?
5. **Shader default.** Bliss or Photon, and is a measured Balanced preset acceptable as the default?
6. **Physics Mod.** Keep it for vanilla ragdolls and tumbling items, or drop a closed jar that does nothing
   for dinosaurs?
7. **World.** A bounded, pre-generated world for the two-player campaign, hosted as a separate server
   process: yes or no?

---

## 8. Seen in passing, for phases 2 and 3

- The spider is the only hostile mob left, so the monster cap can fill with spiders alone (to confirm, OPT-04).
- `TargetHealthBar` (server, a boss bar by ray cast every fourth tick per player) and the F35 nameplate
  (client) show the same thing twice.
- The shipped default pack is not the benchmarked one, and the installed pack runs stock settings (OPT-17).
- `TorporService` runs on every living entity's tick, vanilla mobs included.
- The old dragon model and six old dragon textures still ship (OPT-10).
- Spiders are the only source of string and spider eyes, villagers of several trades, and eggs have no
  source (Dashboard P22): removing mobs for speed has recipe consequences that phase 3 must weigh.
- A tribe-shared map (OPT-14) and a bounded world (OPT-19) are features as much as optimizations.

---

## 9. How to reproduce the numbers

```bash
# Render thread and server thread of a recorded profile, by owner and method
java Ark/tools/SessionProfile.java Ark/run/diagnostics/bench/20261007-104431/full "Render thread" 40
java Ark/tools/SessionProfile.java Ark/run/diagnostics/bench/20261007-104431/full "Server thread" 70
# One method: what it calls and who calls it
java Ark/tools/SessionProfile.java Ark/run/diagnostics/bench/20261007-104431/full "Render thread" 30 CreatureDraw
```

Frame rates, card load and video memory per setup: `summary.json` in `Ark/run/diagnostics/bench/20261007-120500`
and `20261008-125951`. The mip level warning: any `client.log` there, search `dropping miplevel`.
Model, animation and texture counts: scripts over `Ark/src/main/resources/assets/arksurvivalreturns`
(`geckolib/models`, `geckolib/animations`, `textures/entity`); OPT-09 and OPT-10 put them into
`tools/verify_assets.py` so they are kept current.
