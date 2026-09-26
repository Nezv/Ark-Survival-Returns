# Flying Ecosystem — 2026-09-18

The original colony design was replaced by vanilla spawning plus one local nest per bird. The difficulty
map, danger gating and level scaling are unchanged. This document describes the current implementation,
not a claim of visual verification.

## Behavior

A natural flyer claims a single nest of its own near where it spawned; no colony record, member list or
map marker is saved. It flies smooth laps around its saved home (`FlightPath`): each lap picks a wide
wobbling loop, a figure eight that crosses over the nest, or, for soaring species, a thermal circle that
gains height (the SOAR phase). The bird follows a point that slides along the lap, only advancing while the
bird keeps up, so it banks and climbs gradually instead of snapping between waypoints. It perches at its
own nest, looks around now and then while perched, and takes off again on a staggered timer. In the air it
banks with its left and right flight clips in hard turns, flaps while climbing and glides while descending.
Flyers have no day schedule, hunger or thirst. Beyond the full-detail radius they skip territory scans and
check collisions less often (every 3 ticks in the ambient tier, every 10 in the dormant tier), and a
dormant bird stays perched. A safe perch is an existing owned nest with solid support and enough body clearance,
unoccupied by another bird. Landing approaches slowly and settles on the surface; removed or blocked
perches cause a return to flight.

Triggered landing, takeoff, pull-out, warning and attack clips are forced to play once, because several
imported source montages are flagged to loop and would otherwise override the perched or locomotion pose
forever. Unprovoked damage causes evasion without player retaliation.

Egg interactions run on the server. A successful collection changes the block's egg state once and gives
the species item, dropping it if the inventory cannot accept it. `NestBlock.disturb` alerts the nearby bird
whose `nestPosition()` is the disturbed nest; neither proximity nor another player's inventory grants
attack authority. There is one damage attempt per swoop, with melee range and line of sight required.
Defense expires after 600 ticks, after 60 ticks without visible contact, when the player becomes invalid,
or at the 64-block horizontal leash. The player must be within 64 vertical blocks of the nest. Transient
defense clears on reload. Creative/spectator players and Peaceful are excluded.

Hunger, thirst, reproduction, incubation and automatic egg production are still absent for flyers.

## Spawning and nesting

Flyers spawn through the vanilla spawner: each is added to its `spawns/<id>` biome tag by a generated
`neoforge:add_spawns` modifier, and the placement predicate applies the same danger gate every other
species uses. Since P00 every flyer tag points at `habitat/sky`, every temperate and snowy land biome; the
nest rules below decide where a bird can actually live.

| Setting | Default |
|---|---|
| Group size | Pteranodon/Argentavis 3–4, other flyers per species |
| Argentavis minimum nest floor | Y=96 |
| Pteranodon water distance | 12 horizontal / 4 vertical blocks |
| Nest search radius | Pteranodon 16 / Argentavis 24, others per profile |
| Roaming radius | Pteranodon 32 / Argentavis 48, others per profile |
| Perching | Enabled |

Pteranodon requires sand or red sand near at least a 2×2 patch of exposed water. Argentavis requires
supported natural high ground; Quetzal, Archaeopteryx and Dragon require their own minimum floor heights.
All require open sky, body clearance, loaded entity-ticking terrain and the world border.

Nest models are the same per-species bowls as before: sand for Pteranodon, twig and moss-colored fronds for
the others. A flyer that never finds a valid local site simply keeps flying with no nest.

## Animation and map assets

Flight, hover, swoop/contact, pull-out, landing, takeoff and perching select the species clips. One-shot
clips do not loop. Flight uses its own animation controller and renderer pitch; the vanilla ground look
controller is disabled for flyers so it cannot reset that pitch.

The habitat marker overlays (nests, land and aquatic homes) were removed with the habitat stores. The
difficulty map, biome entry messages and map entitlement remain.

## Work limits

Flight steering and physics tick normally; destination refreshes and visibility checks are staggered.
Collision lookahead uses overlapping body-sized segments along the route, bounded to 64 segments, after
checking that all involved chunks are already loaded. No 3D pathfinder, boid simulation or need searches
run for flyers. Collision recovery remains within the home's vertical extent. Nest placement is a bounded
local search that never requests a chunk and runs only while the bird is unclaimed.

## Validation

- `./gradlew runData`: passed; 41 spawn biome modifiers plus unchanged nest models, loot, translations and
  test instances generated from the provider.
- `./gradlew build`: passed; JUnit tests, no failures or skips.
- `./gradlew runGameTestServer`: passed; 32 required tests, including the flying ecology and both flyer
  movement suites. Flying checks cover shoreline/high-ground nest rules, local nest placement and egg
  collection, actual airborne motion, landing/perching, takeoff into egg defense, actual swoop contact
  damage, untouched bystander, leash termination, no damage through shelter, lost-contact expiry,
  creative/spectator exclusions, time expiry and recovery after entering water.

No interactive client was launched, following `Ark/AGENTS.md`. Remaining visual checks are nest/wing
clearance on irregular terrain, spawn density, animation timing and pitch, and performance at the chosen
render/simulation distances.
