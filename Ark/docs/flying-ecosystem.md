# Flying Ecosystem — 2026-09-11

Implemented from the [approved proposal](flying-ecosystem-proposal.md), including perching. Pteranodon and Argentavis now have a dedicated aerial routine; land AI is bypassed for these species. This document describes the implementation, not a claim of visual verification.

## Behavior

A colony is a saved UUID, species, center, nest positions and per-player discoveries. Each bird saves its colony and preferred nest. It retains its existing level and health. Ordinary flight varies orbit direction, phase, height and radius per bird; no pack-leader follow is used. Legacy hunger/thirst values remain serialized but do not grow or influence flyers.

Flight phases are roam, defense circle, swoop, return home, landing, perching and takeoff. A safe perch is an existing nest with solid support and enough body clearance, unoccupied by another bird. Landing approaches slowly and settles on the surface; takeoff and resting duration are staggered. Removed or blocked perches cause a return to flight. Triggered landing, takeoff, pull-out, warning and attack clips are forced to play once, because several imported source montages are flagged to loop and would otherwise override the perched or locomotion pose forever. Unprovoked damage causes evasion without player retaliation.

Egg interactions run on the server. A successful collection changes the block's egg state once and gives the species item, dropping it if the inventory cannot accept it. Breaking an egg-bearing nest also alerts its own colony. Defense stores the responsible player's UUID; neither proximity nor another player's inventory grants attack authority. There is one damage attempt per swoop, with melee range and line of sight required. Defense expires after 600 ticks, after 60 ticks without visible contact, when the player becomes invalid, or at the 64-block horizontal leash. The player must be within 64 vertical blocks of the nest. Transient defense clears on reload. Creative/spectator players and Peaceful are excluded.

Hunger/thirst, reproduction, incubation, automatic egg production and aerial hunting are absent. Collectible eggs are inert items, distinct from creative spawn eggs. Minecraft cues remain temporary bird audio.

## Placement and persistence

| Setting | Default |
|---|---|
| Group size, both species | 3–4 |
| Argentavis minimum nest floor | Y=96 |
| Pteranodon water distance | 12 horizontal / 4 vertical blocks |
| Nest distribution radius | Pteranodon 16 / Argentavis 24 |
| Roaming radius | Pteranodon 32 / Argentavis 48 |
| Typical altitude above nest | Pteranodon 6–18 / Argentavis 12–24, plus a small orbit variation |
| Collision recovery ceiling | 56 blocks above home; externally displaced birds can return down |
| Defense leash / time | 64 blocks / 30 seconds |
| Perching | Enabled |

Pteranodon requires sand or red sand near at least a 2×2 patch of exposed water. The bounded shoreline sampler may miss a narrow valid site and retry later. Argentavis requires supported natural high ground. Both require open sky, body clearance, loaded entity-ticking terrain, player separation, the world border and existing regional danger rules. Biome tags remain preferences as in the population director.

Nest models are shallow bowls on the existing ground, without excavation: sand for Pteranodon, twig and moss-colored fronds for Argentavis. Natural placement succeeds as a complete colony or rolls back. Replenishment reuses existing nest records and preserves empty bowls, checks surviving members, and reserves space under the local 24-creature cap. Repairing a partial colony respects disabled species weights and the per-pass budget. A habitat whose nests are deliberately removed is not reconstructed piece by piece; destroying its last nest removes the record and marker.

Legacy natural birds may adopt an empty nearby colony or create a suitable local one through a bounded search led by one member of their old pack. Without a valid site, they retain a temporary flight home. Named/manual creatures do not generate natural nests. Natural birds retain normal despawn eligibility, while nest state/discoveries persist.

## Animation and map assets

Thirteen existing extracted flight clips were added through `tools/import_creatures.py`; the mod now packages 72 clips including the earlier land poses. Flight, hover, swoop/contact, pull-out, landing and takeoff select the corresponding species clips. One-shot clips do not loop. Flight uses its own animation controller and renderer pitch; the vanilla ground look controller is disabled for flyers so it cannot reset that pitch. Original files under `Creatures` are unchanged.

The optional Xaero fullscreen bridge draws a single pixel nest glyph at the habitat center. A source preview is [habitat-symbol.svg](assets/habitat-symbol.svg). Every marker carries the colony's own species, so the tooltip and rim color are per species: sand for Pteranodon, green for Argentavis, moss for Archaeopteryx, oak for Quetzal and blackstone for Dragon. Hovering shows species and X/Y/Z. The nests toggle is independent of the difficulty filter. The implementation uses the installed bridge's rectangle drawing and world-to-screen conversion; it does not require an additional mod or a minimap API.

Server snapshots contain at most 128 nearest discovered colonies for the player's current dimension, deduplicated by identity. Discoveries are per-player saved state. New discoveries require being within 96 horizontal and 64 vertical blocks of a loaded habitat. The overlay also requires map access and explored terrain, and hides a snapshot while another dimension is viewed. Client state clears at logout. Nearby marker removal is refreshed during discovery checks; block removal updates habitat persistence immediately.

## Work limits

The existing population pass remains every 100 ticks with its shared creation budget. There are at most 24 placement attempts per planned nest, and legacy adoption has eight candidate origins per attempt with a delay of at least 200 ticks per pack leader. Flight steering and physics tick normally; destination refreshes and visibility checks are staggered. Collision lookahead uses overlapping body-sized segments along the route, bounded to 64 segments, after checking that all involved chunks are already loaded. No 3D pathfinder, boid simulation or need searches run for flyers. Collision recovery remains within the colony's counted vertical extent.

Habitats use saved records and spatial indices, with no ticking nest block entity. Map updates check up to eight players every five seconds in rotation and only transmit changed snapshots; login/dimension changes refresh immediately. Many separated players can still have separate local populations. No FPS/MSPT or 16 GB heap performance guarantee is made from the headless tests; visual/render-distance performance needs an interactive comparison on the target machine.

## Validation

- `./gradlew.bat runData`: passed; models, loot, item definitions, translations and test instances generated from the provider.
- `./gradlew.bat build`: passed; 26 JUnit tests, no failures or skips.
- `./gradlew.bat runGameTestServer`: passed; all 12 required tests, including three new flying tests.
- Flying ecology checks: shoreline/high-ground restrictions, 3–4 colonies, capacity, duplicate prevention, single egg collection, habitat/discovery persistence and player isolation, entity reload without rerolling HP/level or retaining aggression, packet bounds/round-trip, last-nest marker removal and no forced chunk loads.
- Both flyer movement tests: actual airborne motion, unchanged inactive needs, rejected external targeting/direct-damage retaliation, landing/perching, takeoff into egg defense, actual swoop contact damage, untouched bystander, leash termination, no damage through shelter, lost-contact expiry, creative/spectator exclusions, time expiry and recovery after entering water.
- `verifyClientPack`: passed; seven presentation mods remain confined to the client. Resource checks passed for the 72 clip bindings/loop flags, nest model geometry/texture references and documentation links. The code graph was updated; its parser reports an existing Gradle syntax limitation.
- The existing nighttime test exposed loss of regroup intent during an investigation transition. That small adapter fix retains the intent until the animal returns; the existing regression test now passes.

No interactive client was launched, following `Ark/AGENTS.md`. Remaining visual checks are nest/wing clearance on irregular terrain, animation timing and pitch, Xaero pan/zoom/labels, sound balance, and performance with the user's chosen render/simulation distances. The tests do not substitute for those observations.
