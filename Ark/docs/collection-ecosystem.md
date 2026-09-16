# Collection ecosystem — ice, flying, aquatic and swamp

The 22 source-only projects from [`Creatures/Collection`](../../Creatures/Collection/README.md) are
registered runtime species. Every one of them reports status, group settings, a saved home,
day routines and night routines through the same decision model the original nineteen use.

Implementation: pending client playtesting per `Ark/AGENTS.md`; the checks listed under
[Validation](#validation) are automated only.

## Realms

A species belongs to one realm, and the realm decides where it lives, how it moves and which
habitat store keeps its home.

| Realm | Species | Habitat | Movement |
|---|---|---:|---|
| `WATER` | Cnidaria, Plesiosaur, Megalodon, Liopleurodon, Mosasaurus, Tusoteuthis | Saved home pool | Gravity-free in-pool steering |
| `AMPHIBIOUS` | Kaprosuchus, Sarco, Deinosuchus, Titanoboa | Saved shoreline habitat with exposed water | Ground navigation, swim clip set in water |
| `LAND` | Megalocerus, Unicorn, Mammoth, Direwolf, Sabertooth, Megapithecus, Paraceratherium, Terrorbird, Ravager | Saved land habitat | Ground navigation |
| `AIR` | Archaeopteryx, Quetzal, Dragon | Saved nest colony | Habitat-anchored flight |

`Species.realm()` is derived from the profile, so a species cannot silently lose its realm:
`LandFamily.AQUATIC` means water, a swim clip set means amphibious, a flight policy means air.

## Group settings

| Species | Group | Family / policy | Danger | Notes |
|---|---:|---|---:|---|
| Cnidaria | 1 | AQUATIC | 1 | Drifts alone; defensive shock, no pursuit |
| Plesiosaur | 1 | AQUATIC | 2 | Hunts at night, flees as a timid swimmer by day |
| Megalodon | 1 | AQUATIC | 3 | Nocturnal hunter of players, wildlife and fish |
| Liopleurodon | 1 | AQUATIC | 3 | Fast charge, spin display as its warning |
| Mosasaurus | 1 | AQUATIC | 4 | Deep-water apex |
| Tusoteuthis | 1 | AQUATIC | 4 | Deep-water apex |
| Kaprosuchus | 1 | AMPHIBIOUS | 2 | Solitary bank ambusher |
| Sarco | 2–3 | SWAMP_PACK | 2 | The one water-side species with a real bask group |
| Deinosuchus | 1 | AMPHIBIOUS | 3 | Solitary; larger body and longer reach |
| Titanoboa | 1 | AMPHIBIOUS | 3 | Swims with its ground clips; no separate water set |
| Megalocerus | 4–6 | COLD_GRAZER | 1 | Vigilant herd, one alarm is enough |
| Unicorn | 1 | RARE_GRAZER | 1 | Rare discovery encounter; bucks when cornered |
| Mammoth | 2–4 | COLD_BROWSER | 2 | Wide clearance, needs a broad valley |
| Direwolf | 4–6 | COLD_PREDATOR | 2 | Widest cold range so a pack can follow prey |
| Sabertooth | 1–2 | COLD_STALKER | 3 | Explicit 1–2 override, not an ordinary pack |
| Megapithecus | 1 | GUARDIAN | 5 | Rare territorial guardian, short leash |
| Paraceratherium | 2–4 | BIG_HERBIVORE | 3 | Large browser |
| Terrorbird | 4–6 | SMALL_CARNIVORE | 2 | Pack hunter; the catalog now lists it as a carnivore |
| Ravager | 4–6 | SMALL_CARNIVORE | 3 | Cave-wolf pack profile |
| Archaeopteryx | 3–4 | GLIDER | 1 | Small ground-nesting glider, timid |
| Quetzal | 1 | GIANT_FLYER | 4 | Solo high-altitude roost at Y≥110 |
| Dragon | 1 | APEX_FLYER | 5 | Rare apex; guards the airspace around its roost |

Water, swamp and cold groups are intentionally small: a pool or a shoreline home is a scarce
resource, so most of these species are solitary and only Sarco, the cold grazers and the warm
pack hunters share a home.

## Geolocation

- **Pools.** A water home is a bounded sample of connected deep water: at least
  `aquatic.minimumDepth` (6) and `aquatic.minimumColumns` (12) deep columns inside
  `aquatic.searchRadius` (32). A one-block puddle, a shallow river and a rejected sample are
  never a home. The body is re-checked on `aquatic.waterRecheckTicks` and relocated to a nearby
  pool if it is filled in.
- **Shorelines.** Semi-aquatic species reuse the land habitat system, which already requires
  exposed water and a reachable dry approach. They bask, roam and sleep on the bank and swim the
  water between them.
- **Snow.** Cold species accept snow cover, powder snow, or ice that a bounded check confirms
  sits over water as their hydration point (`LandHabitats.coldHydration`). Bare packed ice is
  never a drink source, ice is never broken, and snow cover over valid browse ground is used for
  foraging as an abstraction rather than terrain damage.
- **Rarity.** Rare species keep the ordinary cap and group target; they are not given a second
  quota. Megapithecus and Unicorn stay solitary so a valley cannot fill with guardians.
- **Map.** Water and shoreline homes reuse the saved land marker payload, so discovered homes
  appear on Xaero's fullscreen map with the existing leaf/fang glyphs and tooltips. Nest colonies
  keep the separate nest toggle.

## Day and night

- Every non-flying species keeps the shared `WildlifeMind`: roaming, foraging, drinking, resting,
  alertness, investigation, warnings, hunting, defense, fleeing, returning home and feeding.
- Night is the same clock the original nineteen use. Aquatic predators hunt at night with the
  configured `nighttime.carnivoreHungerMultiplier`; by day they patrol, investigate and rest.
- Nocturnal species never enter `SLEEP`. Water species have no authored sleep pose, and the
  amphibious ones share the authored standing pose (`Ark-Sleep`) on the bank.
- Cold routines stay gradual: Direwolf and Sabertooth favour dusk and night, Mammoth and
  Megalocerus favour daytime feeding, and every individual is staggered by its own transition
  delay.
- Flyers keep their flight phases (roam, defense circle, swoop, return home, landing, perching,
  takeoff). Dragon additionally guards the airspace around its own roost; Peaceful, creative and
  spectator players are excluded and the engagement ends at the habitat leash.

## Behavior and status

| Surface | Detail |
|---|---|
| Status | `BehaviorState` on every creature; the HP bar and debug spyglass show it |
| Saves | Home, needs, level, pack identity, occupancy and nest state |
| Alarms | Shared with same-pack members; a hidden target cannot be hit through cover |
| Feeding | One hunger clock per group; a kill satisfies the whole pool or herd |
| Succession | Confirmed permanent removals free a slot after the realm's cooldown |
| Work bounds | Per-dimension probes, bounded samples, `getChunkNow` only, no chunk loads |

## Configuration

- `[aquatic]`: `enabled`, `waterProbesPerTick`, `minimumDepth`, `minimumColumns`, `searchRadius`,
  `waterRecheckTicks`, `replacementCooldownTicks`.
- `[landHabitats]`: nine new family sections (`aquatic`, `amphibious`, `swamp_pack`,
  `cold_predator`, `cold_grazer`, `cold_browser`, `cold_stalker`, `guardian`, `rare_grazer`)
  with the usual `roamRadius`, `returnRadius`, `preferredWaterDistance` and
  `maximumWaterDistance`.
- `[spawning.weights]` and `[movement.*]` gained one section per new species.
- `[nighttime]` and `[flying]` are unchanged; the flying keys still drive Pteranodon and
  Argentavis, and the other flyers carry their own profile values.

## Compatibility and known limitations

- **Both sides need the update**: new entities, items, blocks and payloads are not optional.
- Sprint defaults now follow each species profile. An existing config keeps its stored values, so
  a saved file can still hold the previous default for a new species; deleting the stale section
  restores the intended speed.
- `verify_assets.py` gained the collection's eye-bone table. Deinosuchus, Dragon and Mosasaurus
  carry eye bones without usable cube geometry, so they do not receive the emissive eye layer;
  adding eye geometry to those rigs would enable it.
- Titanoboa has no separate swim clip set in the ARK library and reuses its ground clips in water.
- ARK-specific abilities are still out of scope: Cnidaria shock, Kaprosuchus/Titanoboa venom,
  Megapithecus throws, Dragon fire breath and Quetzal platform saddles are not implemented.
- No interactive client was launched for this work; movement, containment, nest appearance and
  marker readability need playtesting.

## Validation

- `./gradlew.bat runData`: passed; 111 new resources including 22 spawn tags, 3 nest blocks with
  models, block states and loot tables, translations and item models.
- `./gradlew.bat build`: passed; 32 JUnit tests, no failures or skips.
- `./gradlew.bat runGameTestServer`: passed; all 19 required tests, including the three new
  collection tests (`aquatic_ecology`, `collection_registration`, `collection_cold`).
- `python tools/import_creatures.py`: 41 creatures, 298 unique runtime clips.
- `python tools/build_item_assets.py`: 41 spawn egg sprites and four berry sprites.
- `python tools/verify_assets.py`: passed; 41 creatures, 298 clips, 5 nests, 51 item definitions,
  unchanged source hashes.
- `python tools/verify_expansion.py`: passed; 32 new runtime models, 411 sampled poses against the
  source rigs, exact clip contracts and preserved hierarchies.
