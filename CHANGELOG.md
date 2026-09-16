# Changelog

Player-facing changes to Ark Survival Returns. Dates use America/Sao_Paulo. Patch names describe development milestones, not published releases. Maintenance rules are in [Standard.md](Standard.md).

## Unreleased

All 41 creature projects are now runtime species: the ice, flying, aquatic and swamp collection has been
registered with realms, group settings, saved homes, day routines and night routines.

### Added

- Land wildlife day/night routines: scheduled sleep, daytime carnivore roaming, nighttime prey searching, sensed-danger escape and defensive herd responses.
- Red emissive eyes for awake nighttime Raptor, Rex and Giga, with client brightness control and smooth fading. Five authored standing sleep poses supplement Rex/Trike sleeping clips.
- Twenty-two collection species with their own models, spawn eggs, biome preferences and selected runtime clips: Cnidaria, Plesiosaur, Megalodon, Liopleurodon, Mosasaurus, Tusoteuthis, Kaprosuchus, Sarco, Deinosuchus, Titanoboa, Megalocerus, Unicorn, Mammoth, Direwolf, Sabertooth, Megapithecus, Paraceratherium, Terrorbird, Ravager, Archaeopteryx, Quetzal and Dragon.
- Four realms that decide where a creature lives and how it moves: water-bound pools, semi-aquatic shorelines, ground habitats and nest colonies. Every species reports status, group settings, a saved home, day routines and night routines through the existing decision model.
- Saved home pools for water species. A pool needs connected deep water, is re-checked periodically, relocates when it is filled in, and rejects puddles and shallow water.
- Cold-adapted hydration: snow cover, powder snow or ice over water satisfies thirst, and snow over browse ground is used for grazing. Ice is never broken and no terrain is edited.
- Nest blocks and collectible eggs for Archaeopteryx, Quetzal and Dragon, sharing the existing colony, perching and egg-defense behavior.
- Apex flyers guard the airspace around their own roost; Peaceful, creative and spectator players are excluded and the chase ends at the habitat leash.

### Changed

- Land carnivore nighttime hunger grows at 2× and sight reaches 1.3× daytime range before rain/crouching modifiers. Successful wildlife kills still satisfy hunger.
- Added configurable night hours, individual transition delays, daytime sleep share, player wake distance and calm-down time. Flying creatures are excluded from the patch.
- Water predators hunt at night and patrol by day through the same clock, so the night patch now covers all six water species and every semi-aquatic one.
- Semi-aquatic species switch to their own swim clip set in water while keeping ground navigation, herd limits and the authored sleep pose.
- Cold species use dedicated family profiles: Direwolf 4–6 with the widest cold range, Mammoth 2–4, Megalocerus 4–6, Sabertooth an explicit 1–2, and Megapithecus/Unicorn stay solitary. Water and swamp species are mostly solitary; Sarco is the only water-side species with a 2–3 bask group.
- Terrorbird is a pack carnivore, matching its ARK behavior, rather than the herbivore label the catalog previously carried.
- Sprint defaults now follow each species profile, so the configured default and the entity speed agree for every new species.
- `Species` gained realms, water/browse clip sets and per-species flight policies; the flying controller, nest site policy and habitat records are now profile-driven instead of hard-coded per species.
- New `[aquatic]` configuration section: pool depth, connected columns, search radius, per-tick probe budget, water recheck and replacement cooldown. Nine `[landHabitats]` family sections were added, and `[spawning.weights]` and `[movement.*]` gained one entry per new species.

### Compatibility and known limitations

- Implementation in progress. Existing levels, health, homes, packs and hunger are retained. New sleep poses and eye rendering require client playtesting; they are not visually verified.
- Both server and clients need the update: 22 entities, 3 nests, 3 egg items and new payload fields are not optional.
- An existing server config keeps its stored sprint values, so a species added earlier can retain the previous default until its section is removed or edited.
- Deinosuchus, Dragon and Mosasaurus own eye bones without usable cube geometry and therefore do not receive the emissive eye layer.
- Titanoboa has no separate swim animation in the ARK library and reuses its ground clips in water.
- ARK abilities remain out of scope: Cnidaria shock, venom, throws, fire breath and platform saddles are not implemented. Water species never dive under ice and no creature breaks blocks.
- Water, swamp, snow and nest visuals, marker readability and cold-biome balance need interactive playtesting. No client was launched.

### Validation

- Data generation passed, writing 111 new resources: 22 spawn tags, 3 nest blocks with models, block states and loot tables, both translations and the item models.
- Gradle build passed with 32 JUnit tests, no failures or skips.
- All 19 required headless GameTests passed, including three new collection tests: water pool validation, occupancy, containment and save/load; realm, group, clip and registry invariants for all 41 species; and the cold hydration, snow-browsing and site policy checks.
- Creature import produced 41 creatures and 298 unique runtime clips; 41 spawn egg sprites were rebuilt.
- Asset validation passed for 41 creatures, 298 clips, 5 nests and 51 item definitions with unchanged source hashes; the expansion validator checked 411 sampled poses against the source rigs.
- Remaining work is visual: movement and containment in real terrain, nest and egg appearance, cold-biome balance and marker readability.

## Land Ecosystem & Behavior — 2026-09-14

Persistent water-associated homes, coordinated herds and discoverable land habitats.

### Added

- Saved land habitats with exposed water, reachable dry drinking approaches and complete group placement.
- Shared group satiation, feeding, roaming direction and destinations, with individual spacing and danger responses.
- Leaf and fang symbols on Xaero's fullscreen World Map, per-player discovery, coordinate/status tooltips and clustered markers.
- A dev dependency list that verifies installed Iris/Sodium pins and records River Redux as unavailable for Minecraft 26.2 / NeoForge.

### Changed

- Family group sizes use solo large predators/Titanosaur, 4–6 small predators/herbivores and 2–4 large herbivores. Herbivores use smaller home ranges and stricter water distances.
- Confirmed permanent losses replenish after a configurable cooldown; unloaded members retain their slots. Occupied and vacant habitats can recover after water changes.
- Terrain surveys and navigation use shared dimension budgets. Legacy natural groups adopt suitable loaded homes without changing levels or HP.
- Reproducible dev profiles use 16 GB maximum heap per run, 24 render/view chunks and 12 simulation chunks, configurable separately.

### Compatibility and known limitations

- Both server and clients need the updated mod. A separate versioned land save preserves pack identity and player discoveries; disabling land habitats retains those records.
- Narrow water, wide-creature slopes/banks, natural population balance and marker appearance still need interactive playtesting. No client was launched or multiplayer performance measured.
- River Redux has no compatible published artifact for this runtime and was not installed. Ordinary exposed water supports habitats. The six cold creatures remain source-only in the separate snow proposal.
- See [behavior and work bounds](Ark/docs/land-ecosystem.md) and [dev dependencies](Ark/docs/dev-dependencies.md). The separate Unreleased nighttime work remains unfinished.

### Validation

- Gradle data generation and build passed, including 32 unit tests and all 16 headless GameTests.
- Land checks cover natural river placement, dry rejection, group feeding, persistent identity/occupancy, unload/death distinction, replacement cooldown, vacant-site recovery, incremental water discovery and actual coordinated movement without forced chunk loads.
- Source leaf/fang pixels match runtime symbols. Installer hashes, client-only dependency scope and client preparation passed. The code graph was refreshed; its parser could not index Gradle's build script, which Gradle itself validated.

## Debug Spyglass — 2026-09-13

A separate scope for inspecting dinosaur state through a green terminal overlay.

### Added

- Debug Spyglass in the Ark creative tab and via `/give @s arksurvivalreturns:debug_spyglass`. Hold use to zoom; scroll to browse live stats, mod AI/flight variables, species configuration and saved entity data.
- Read-only server snapshots, independent targets/pages per viewer, a 96-block range, solid-wall checks and bounded updates twice per second while inspecting.
- English and Brazilian Portuguese item names and overlay instructions.

### Compatibility and known limitations

- Restart clients and server to load the item. No crafting recipe or dinosaur save migration. Vanilla Spyglass behavior is unchanged.
- Mod runtime fields and saved data are exposed; private Minecraft/GeckoLib engine fields and animation-cache internals are outside the inspector. Oversized/deep saved data has visible limits. See [controls, bounds and visual checklist](Ark/docs/debug-spyglass.md).
- Overlay appearance and live two-player interaction require in-client playtesting; automated checks did not launch the client.

### Validation

- Gradle data generation and build passed, with 26 unit tests and all 14 headless GameTests passing.
- New coverage checks all 19 species' snapshots and unchanged saved state, scope activation in both hands, vanilla item isolation, packet round trips/bounds, nearest/invisible targets, walls, direction changes, range and unloaded-chunk protection.
- Verified the built JAR includes the debug item model, classes and generated test definition; refreshed the code graph.

## Source Model Collection — 2026-09-12

Detailed ice, flying, aquatic and swamp source art, with no new gameplay integration.

### Added

- Twenty-one editable creature projects: Direwolf, Megalocerus, Megapithecus, Mammoth, Unicorn, Sabertooth, Quetzal, Archaeopteryx, Paraceratherium, Terrorbird, Ravager, Tusoteuthis, Cnidaria, Mosasaurus, Megalodon, Plesiosaur, Liopleurodon, Kaprosuchus, Deinosuchus, Sarco and Titanoboa.
- Original named skeletons, 696 converted animation clips, 1,583 bones, detailed cuboid geometry, palette textures, previews, source exports and repeatable rebuild scripts. The complete source portfolio now contains forty model projects.
- Finer triangle-based surface fitting for tusks, antlers, wings, fins, tentacles and fur. Unicorn retains its original horn attachment; Megapithecus combines its base/fur components on the original animation skeleton.

### Compatibility and known limitations

- These twenty-one projects are source assets only; this collection adds no runtime entities, behavior or spawn rules. The playable roster remains nineteen.
- Cuboids approximate the original skins with rigid bone ownership. Palette textures replace the original materials; shader transparency and Unreal animation events are not converted. No interactive game or Blockbench test was run.

### Validation

- All 33,365 source animation frames passed hierarchy, animation binding, reduced-key and full affine transform checks. Geometry and Blockbench cube contents agree; triangle-cell merging preserves occupied cell volume.
- Original source hashes are retained. Earlier Rex and Ceratosaurus conversion checks and the nineteen-creature runtime asset checks still pass. See the [collection and measured results](Creatures/Collection/README.md).

## Creature Expansion — 2026-09-12

Ten additional creatures reuse the established wildlife behavior families.

### Added

- Spinosaurus, Parasaur, Ceratosaurus, Dilophosaur, Acrocanthosaurus, Allosaurus, Ankylosaurus, Carnotaurus, Pegomastax and Lystrosaurus, each with its own model, animations and spawn egg. Nineteen creatures are now available.
- Allosaurus and Dilophosaur use predator packs; Ankylosaurus uses defensive herds; Parasaur, Pegomastax and Lystrosaurus use timid foraging herds. Spinosaurus, Ceratosaurus, Acrocanthosaurus and Carnotaurus use solitary predators.
- Regional danger eligibility, biome preferences and the existing configurable movement, habitat and nighttime routines for the new species. Population caps remain unchanged.
- Editable projects and rebuild scripts for all ten additions, preserving original skeletons and 383 source animation clips. The runtime selects 70 clips for the additions, including sleep poses.

### Compatibility and known limitations

- Existing entity and family IDs are preserved. Both server and clients need the updated mod for the new entity registrations.
- Models are fitted cuboid approximations with generated palettes. ARK-specific spit, venom, stealing, stance changes, buffs and resource harvesting are not implemented. See [creature mappings and workflow](Ark/docs/creature-expansion.md).
- No interactive client was launched. Visual quality, combat balance, natural habitat selection and multiplayer performance still require playtesting.

### Validation

- Data generation and build passed, including all 26 JUnit tests and all 13 required headless GameTests.
- All 18,453 source frames for the additions passed conversion checks. All nineteen runtime models and 142 selected clips passed asset validation; 120 sampled expansion poses matched the converted source cubes after runtime scaling.
- The dry, synchronous population test explicitly uses legacy replenishment mode. It does not validate the water planner across biomes.
- The separate Unreleased nighttime work remains in progress.

## Flying Ecosystem — 2026-09-11

Nest-centered Pteranodon and Argentavis colonies with independent flight and egg defense.

### Added

- Colonies of 3–4 flyers with persistent nest bowls: Pteranodon on shoreline sand and Argentavis on high ground at Y≥96 by default.
- Independent circling, safe landing/perching/takeoff, and staggered swoops against the player who takes an egg or breaks an egg-bearing nest.
- Separate collectible species eggs and one discovered habitat symbol on Xaero's fullscreen map, with its own toggle and coordinate label.
- Configurable altitude, shoreline distance, roaming/defense radii, defense duration and perching. Thirteen extracted aerial animation clips join the runtime assets.

### Changed

- Flyers bypass land hunger/thirst, broad sensing and pack-leader following. Existing wild birds can adopt suitable loaded habitats; replenishment reuses nests and counts surviving colony members.
- Defense requires real contact/visibility, leaves bystanders alone, and ends at its habitat leash, timeout or lost contact. Direct damage alone prompts evasion.

### Fixed

- Preserved flight pitch instead of allowing vanilla ground look control to reset it.
- Corrected an existing nighttime regrouping regression found by the combined test suite: brief investigation no longer loses the return intent.

### Compatibility and known limitations

- Existing levels/HP persist; legacy flyer needs become inactive. Named/manual birds do not create nests in player builds. Empty nests/discoveries persist; eggs do not hatch or refill automatically.
- Uses the installed optional Xaero World Map bridge; no minimap integration. Nest/flight visuals, audio balance and target-machine performance still need interactive playtesting.

### Validation

- Data generation and build passed; all 26 JUnit tests and all 12 required headless GameTests passed. Both flyers are tested for actual flight, perching, swoop damage, bystander safety, cover and defense expiry. See [details](Ark/docs/flying-ecosystem.md).
- No interactive client was launched. The existing Unreleased land-nighttime entry remains separate.

## Beta baseline — 2026-09-09

Consolidated record of the implemented mod as of this date. This is a retrospective baseline, not a claim that all features were implemented or released on September 9. Individual implementation dates are incomplete: [behavior research](Ark/docs/behavior-research.md) records September 6, while [verification](Ark/docs/verification.md) and [client-pack installation](Ark/docs/client-pack.md) record September 7. Those milestones do not establish separate historical patch releases.

### Added

- A standalone Ark Survival Returns mod for Minecraft 26.2, NeoForge 26.2.0.11-beta, Java 25 and GeckoLib 5.5.3, replacing the copied All-Under-Heaven mod content and configuration.
- Nine creatures: Pteranodon, Velociraptor, Argentavis, Triceratops, Therizinosaurus, Brontosaurus, Tyrannosaurus, Giganotosaurus and Titanosaur. Imported models, palette textures, 54 animation clips and nine spawn eggs are included.
- Natural wildlife across Overworld biomes, with preferred habitats, complete spawn groups, terrain/collision checks, local population limits and bounded searches that do not force chunk loads.
- Five recurring difficulty ranks with approximately equal area, gradual neighboring-rank transitions and an easy initial world-spawn region. Chat announces the biome, difficulty and wild-level range on entry.
- Persistent random creature levels and independent HP/damage scaling. A targeted HP bar displays creature name, level, health and behavior.
- Ground wildlife behavior: roaming, foraging, water seeking/drinking, resting, alertness, investigation, warnings, hunting, defense, fleeing, returning home and feeding. Perception includes sight, sound and scent; needs and home positions persist.
- Four berry items with complete assets and English/Portuguese names: Tintoberry, Amarberry, Azulberry and Narcoberry. Short/tall grass can drop berries alongside vanilla loot; Narcoberry is designated for future sedation.
- A Windows launch script that builds and starts the development client without VS Code, plus a preparation-only check mode.
- A reproducible optional client pack: Xaero World Map and its bridge, Sodium, Iris, Complementary Reimagined, AmbientSounds, CreativeCore and Sound Physics Remastered. Downloads are pinned and checksum-verified.
- A toggleable Xaero difficulty filter over explored terrain, with a legend and cursor readout. Saved per-player map entitlements and operator commands support a future unlock from taming a creature originating in a difficulty-5 region.

### Changed

- Population replenishment targets three natural groups within 96 blocks, with a local cap of 24 creatures. High-danger areas seek a varied large-creature encounter when absent instead of filling a roster of every large species. No day/night spawn filter applies.
- Bronto and Trike herds contain 2–4 members. Brontos spawn in ranks 4–5 with a linked nearby Rex; healthy herds defend attacked members and deter predators, while injured predators retreat.
- Argentavis groups use wider spacing, strongly reduced lowland spawn weight and a preference for medium elevations. Birds have a flying-creature classification in preparation for future flight behavior.
- Final size multipliers relative to the original baseline: Titanosaur 6×, Rex/Giga 3×, and all other species 2×, including Therizinosaurus. Meshes, animation translations and collision dimensions scale together.
- Movement targets use a configurable normal-player sprint benchmark. Rex targets 1.8×, Giga 2× and Raptor 2.2× that speed on ordinary ground and in open water. Large herbivores have separate targets; Titanosaur can exceed normal player sprint speed on land.
- Animation cadence follows actual horizontal travel, body size and clip duration. Per-species sprint ratios, swimming retention and visual stride controls are exposed in server configuration. See [movement tuning](Ark/docs/movement-tuning.md).
- The development client uses a 12 GB maximum heap. The launcher registers Windows high-performance GPU preferences for its selected Java executables to favor the NVIDIA adapter.

### Fixed

- Corrected imported model facing to address backward-looking walking.
- Replaced the permanently increasing difficulty layout with recurring ranks; adjacent and diagonal locations cannot jump directly from rank 1 to rank 5.
- Removed the hidden easy-biome-tag veto that blocked apex spawns in terrain displaying high difficulty. New natural Rex spawns require rank 4+, Giga/Titanosaur rank 5, and Therizinosaurus rank 3+.
- Relaxed overly strict flat-footprint placement for large creatures while retaining bounded terrain support, collision, border and loaded-chunk checks.
- Prevented common groups from permanently suppressing the first eligible high-danger encounter, and prevented subsequent replenishment from adding every large species nearby.
- Added explored-terrain masking and render ordering for the difficulty filter so unknown territory remains uncolored.
- Applied a development-client workaround for the reported Iris GL-validation crash via `neoforge.disableGlValidation=true`. This is a launch setting, not a patch to Iris itself.

### Compatibility and known limitations

- Map access is temporarily open through `progression.mapRequiresUnlock=false`. Re-enabling the gate restores saved player entitlements; taming is not implemented.
- Berries have no usage effects yet. Actual flight, riding, breeding, harvesting, custom dinosaur audio and expanded ecosystem simulation remain future work.
- Difficulty is an overlay on existing terrain, not biome regeneration. Existing creature levels and injuries remain; loaded creatures adopt movement tuning without healing or rerolling levels.
- Existing populations are not culled. Lower density takes effect through replenishment and normal despawning. Existing creatures can wander across difficulty boundaries.
- Restart the client to load code, assets and launch settings. Natural encounter frequency, animation/foot placement, map visuals, shader performance and actual GPU selection still require client playtesting.
- Optional presentation mods are separate from the Ark JAR and are loaded only by the development client configuration. See [client-pack instructions](Ark/docs/client-pack.md).

### Validation

- Recorded September 7 checks passed: Gradle build, 18 unit tests, all eight headless GameTests, data generation, creature/item asset checks, client-pack checksums and launcher preparation.
- Coverage includes spawn groups/caps, high-danger encounter replenishment, herd defense, low-health retreat, save persistence, difficulty transitions, unexplored-map masking and measured land/water movement.
- No interactive client was launched for those checks. This September 9 documentation update does not represent a new gameplay test run. See [verification details](Ark/docs/verification.md).
