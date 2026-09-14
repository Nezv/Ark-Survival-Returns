# Land Ecosystem & Behavior Patch

Implemented 2026-09-14. The [approved proposal](land-ecosystem-proposal.md) describes the original nine-species baseline; the current runtime has nineteen species, seventeen of them land creatures.

## Families and water-associated homes

| Family | Fresh group | Preferred water distance | Excluded at | Ordinary roam | Return radius |
|---|---:|---:|---:|---:|---:|
| Big carnivore | 1 | 32 | 96 | 96 | 160 |
| Small carnivore | 4–6 | 24 | 80 | 80 | 128 |
| Titanosaur | 1 | 24 | 64 | 64 | 112 |
| Big herbivore | 2–4 | 16 | 48 | 48 | 96 |
| Small herbivore | 4–6 | 12 | 32 | 32 | 64 |

Distances are horizontal blocks. Full spawn acceptance applies inside the preferred water distance, then declines linearly to zero at the exclusion distance. This is an additional acceptance probability after the existing species/danger/biome selection, rather than a change to that selection's weights. Fresh groups register only after complete collision-safe placement and a reachable dry drinking approach. Existing population caps, mob-spawning rules, danger eligibility and Bronto/Rex encounter pairing still apply.

A valid water sample has exposed ordinary water, at least eight connected cells in a 4×4 window and a connected 2×2 patch. Waterlogged blocks, isolated one-block pools, covered water and missing chunks do not qualify. Sparse sampling means extremely narrow rivers may be missed; `minimumWaterCells` and family distances are configurable. Terrain must be loaded for all placement and navigation checks. No terrain generation or forced chunk load is requested.

A saved habitat keeps the group's ID, species, center, water coordinate, intended capacity, member UUID reservations, hunger, discoveries and replacement cooldown. Its center stays fixed during ordinary movement and pursuit. Invalid water pauses replacement and triggers bounded revalidation/relocation; occupied and vacant sites can recover. Habitat IDs and player discoveries survive relocation. A site without a replacement water source remains discoverable with a seeking-water status.

## Group behavior

Each active habitat advances one hunger clock at most once per second. A successful prey kill satisfies the group once; duplicate reports of the same victim do not multiply feeding. Herbivore grazing credit depends on the fraction of reserved members actually foraging on suitable ground. Offline time does not accumulate hunger updates.

The coordinator shares roam/search/forage/rest/feed intent, a destination and a direction. Stable per-member spacing offsets prevent every member aiming at one block. Shared forage decisions still require an animal to reach suitable feeding ground. Health, awareness, thirst and immediate combat/escape decisions remain individual, and urgent needs or danger can override the normal group routine. Existing nighttime decisions use a shared group phase when bound to a habitat.

Natural legacy groups adopt a usable loaded habitat gradually, preserving levels, HP and pack IDs. A legacy group that cannot find a suitable site keeps local behavior until adoption becomes possible. Existing oversized groups are retained rather than deleting animals to fit a new capacity. Manually spawned animals do not independently establish natural habitats.

Permanent member removal opens a reserved slot after 12,000 ticks by default (ten minutes at 20 TPS). Chunk unloading keeps the reservation, preventing replacements for animals that still exist in saved chunks. Vacant saved habitats still count toward the local group target, so leaving and returning cannot immediately reroll them. This is saved occupancy and replenishment; breeding, ages, nest eggs and terrain edits are outside the patch.

## Xaero habitats

The separate **Land habitats: on/off** control draws one stable home marker per discovered habitat on the fullscreen World Map. Herbivores use a [leaf asset](assets/land-herbivore-habitat.svg); carnivores use a [fang asset](assets/land-carnivore-habitat.svg). The runtime pixel patterns match these source SVGs. Hover shows species, coordinates and occupied/recovering/seeking-water state; overlapping markers cluster with a count.

Discoveries are per-player and require proximity to a loaded home (96 horizontal blocks, 64 vertical). Snapshots contain up to the nearest 128 discovered homes in the current dimension. Map entitlement, dimension checks and Xaero's exploration mask apply. Changes sync every 100 ticks for up to eight rotating players. Neither the map toggle nor the marker moves a creature; the symbol represents its saved home. This uses the optional World Map bridge, with no minimap integration or live creature tracking.

## Work bounds and configuration

`landHabitats` in the common config controls the feature, each family's ranges, water-cell threshold, recheck period and replacement cooldown. Disabling it retains saved records and returns land animals to their local behavior.

Water surveys use at most 256 reserved block/height probes per dimension tick by default. Background discovery takes at most half that budget, leaving room for final water validation. The queue holds at most 256 chunks, samples round-robin columns, and caches at most 2,048 chunk entries. Loaded samples expire; unknown terrain and exhausted budgets defer decisions. Requests are limited to eight per 100 ticks. Terrain placement/collision reads and vanilla pathfinding are separately bounded operations, not included in the water-probe counter.

Land navigation initiates at most eight paths per dimension tick, with at most two habitat-planning paths. Ordinary long movement uses short waypoints; the complete vanilla navigation region must already be loaded. Habitat decisions are cached and individual movement retries are staggered. These bounds control work; they are not measured FPS/MSPT guarantees. In busy worlds, delayed surveys and paths can reduce population or slow movement.

The dev defaults are 16 GB per client/server run, 24 render/view chunks and 12 simulation chunks. [Dependencies and reproducible settings](dev-dependencies.md) explain the verified Iris installation and unavailable River Redux build. Existing water is sufficient for this patch.

## Verification and remaining playtest

Data generation, Gradle build, unit tests, headless GameTests, client preparation, installer hashes and client-only dependency checks were run. The land checks cover water-distance rejection, complete fresh packs, shared feeding and grazing rates, codec/member persistence, unload versus permanent removal, replacement cooldowns, discovery isolation/removal, packet bounds, incremental survey work and actual shared-direction movement. Unit checks compare every source-symbol pixel with the runtime pattern. The combined suite also exercises flying, nighttime, expansion and debug-scope behavior.

Interactive testing is still needed for leaf/fang visibility at different zooms, wide-creature shoreline clearance, hills, long pursuits, natural population balance and separated multiplayer players at the requested render distance. No interactive client was launched. Frozen-water behavior and the six source-only cold creatures remain in the separate [snow proposal](snow-ecosystem-proposal.md).
