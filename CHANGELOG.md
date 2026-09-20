# Changelog

Player-facing changes to Ark Survival Returns. Dates use America/Sao_Paulo. Patch names describe development milestones, not published releases. Maintenance rules are in [Standard.md](Standard.md).

## Unreleased

- **Camp gear and a survivor's start.** Plant fiber now drops from grass alongside berries. A fiber bandage heals health, a flint knife and a longer-reaching flint spear cover primitive weapons, and the placeable field bedroll sets the personal respawn point without risking it when the block is destroyed. Every player receives a one-time starter kit (bedroll, two bandages, eight fiber, flint knife) on their first join. New `[camp]` settings control the kit, the bedroll and bandage values.
- **Recovery caches.** Player death drops now become a placed Recovery Cache instead of loose items. The owner and their FTB Teams tribe see the coordinates, right-click collects the whole haul, and the items live in world data, so breaking or exploding the marker never destroys them. Up to three caches per player under `[recovery]`; the oldest folds into the newest instead of being lost. Void and lava deaths fall back to safe ground or the bedroll, and `/arkrecover list|claim|clear` inspects and recovers anything that could not be placed.

## Survival Journal and Tribes — 2026-09-20

- **Tribe permissions.** A tame stays owned by the survivor who tamed it, and members of that owner's FTB Teams party can be granted riding, cargo, order and (later) breeding permissions. The owner always keeps every permission. Party creation and invitations stay with `/ftbteams`; `/arktribe status`, `/arktribe perm` and `/arktribe reset` inspect and edit the flags. Four new server config defaults under `[tribe]` control what party members receive by default.
- **Field Journal.** Craft a Field Journal from a book and two leather, then press **J** or use it to open the tribe's survival journal. The journal is the FTB Quests book, so progress and rewards are shared per tribe and granted exactly once per survivor.
- **Primitive chapter.** Six starter objectives cover camp, stone tools, forage and bone, sedation with tranquilizer arrows, the first tame and the journal itself. Taming your first creature also awards a hidden personal discovery advancement that the journal reads.
- **Map entitlement from taming.** Each creature saves the danger band where it first appeared. Taming one from a difficulty-5 region unlocks the Xaero map for its owner and awards the hidden `journal/rank5_tame` discovery. `/arkmap` still grants or revokes access for operators, and the map stays open while `progression.mapRequiresUnlock=false`.
- **Validation.** `runData` generates the recipe, advancements and item assets; the build passes with 41 JUnit tests and all 40 headless GameTests, and FTB Quests loads the Primitive chapter without errors. No interactive client was launched: journal layout, the J keybind, party invitations and shader checks remain for playtesting. Known limitation: FTB Quests reads its pack from the instance `config/ftbquests/quests/`, so copies for other launchers must be placed there manually.

## Minecraft 26.1.2 Migration — 2026-09-20

- Moved the runtime to Minecraft 26.1.2, NeoForge 26.1.2.109 and GeckoLib 5.5.2 to use the FTB Quests
  26.1.2 line for the survival journal. Existing worlds were backed up; the downgrade is a fresh-world migration.
- Ported the 26.2 API deltas: `EntityType` statics, the `Criterion` package, `GameRenderer.getMainCamera` and
  `getGameRenderState`, `Minecraft.screen`, `Gui.getChat`, `WeatheringCopper.getFirst` and named `TextColor` values.
- Hardened `ThemePolicy` against defaulted registries: an absent entity id no longer resolves to the default
  entity, and the generated removed-creature tag lists only variants this version has (26.2's sulfur cube is excluded).
- Refreshed the pinned client pack to Sodium 0.9.2, Iris 1.11.4, Xaero's World Map 1.46.0 and bridge 0.1.2 for 26.1.2.
- Compatibility and known limitations: worlds saved on 26.2 cannot be opened by 26.1.2; existing saves were backed up
  and play continues from a fresh world. The FTB Quests stack and the survival journal arrive in the following P01 work.
- Validation: `runData` produced no changes except the removed-creature tag; the full `build` (39 JUnit tests) and all
  37 headless GameTests pass. No interactive client was launched; shader, audio and visual checks remain for the user.

## Unified Spatial Audio — 2026-09-19

- Merged Sound Physics Remastered's OpenAL EFX reverb, occlusion, absorption and reflected directionality
  into the Ark client runtime, ported directly to NeoForge 26.2 without a second mod container.
- Added material-aware positional footsteps backed by the Presence Footsteps recording library, with
  separate walking/running cadence and profiles for terrain, wood, stone, metal, glass, snow and mud.
- Added biome-, dimension-, weather-, time-, cave- and underwater-aware ambience backed by the full
  AmbientSounds catalog. A single resource-pack JSON controls all surface mappings and environment rules.
- Removed Fabric and CreativeCore runtime coupling, retained all upstream license texts and provenance,
  and changed the combined work's declared license to GPL-3.0-only. Validated all 48 configured events and
  the full Gradle build.

## Anatomy-Driven Creature Textures — 2026-09-19

- All 41 runtime creatures now ship with five procedurally painted variants: Ivory, Darken, Emerald,
  Midnight and Burgundy. Each creature keeps one stable variant from its UUID, so its appearance survives
  saves and is consistent for every client.
- The shared texture pipeline classifies every rig into an anatomical body plan, derives ventral/dorsal,
  head, jaw, limb, eye, tongue, claw, nail and horn regions from the fitted geometry, and paints seeded,
  bilateral 3×3-pixel cells. Variant atlases and their matching UV geometry are imported into the mod.
- Added `Creatures/Textures.py` as the complete-roster generator and `Creatures/textures.md` as its anatomy
  and density matrix. The former Giganotosaur-only painter no longer requires SciPy.
- Validated 205 runtime variant textures across 41 generated UV geometries, all four brush tests, and the
  full Gradle build.

## Apex Encounters and Night Eyes — 2026-09-19

- Regional large species (Tyrannosaurus, Giganotosaurus, Titanosaur, Spinosaurus, Acrocanthosaurus,
  Brontosaurus, Therizinosaurus) treat their biome tag as a weight instead of a hard requirement: triple
  odds in their habitat, base odds outside it, so a level-4/5 area can produce an apex even when no biome
  tag covers it. The population budget also biases its first placement each pass toward one regional large
  that is missing near the player — bounded, and never a forced spawn.
- Giant bodies no longer need a collision-free adult volume to spawn. Placement checks a feet slab plus
  solid-free headroom, ignores leaves, and keeps logs and terrain blocking, so an apex can stand under a
  canopy. `/arkwildlife [radius]` (gamemaster) reports the danger band, biome, local population and the
  exact placement failure tally per regional large species.
- Night eyes redraw with a slightly inflated eyeball and a translucent-emissive render type so the glow is
  not hidden inside the eyelid geometry, including under shaders. `nightEyeDebugLog` on the client prints
  the first draw per species. The glow remains wild-only.
- Validated with `runData`, `build` and all 37 headless GameTests, including the new `spawn_apex` suite.

## Hit-Frame Combat and Companion Orders — 2026-09-19

- Melee damage now lands on each attack clip's authored hit frame instead of the instant the AI decides to
  attack. Wind-up and recovery come from the imported clip length (new `[combat]` settings), a target that
  steps away makes the bite whiff, and attack speed now differs per species: the Velociraptor bites about
  every 13 ticks, the Tyrannosaurus about every 28, the Acrocanthosaurus about every 71. Large bodies kick
  dust and use heavier footstep sounds.
- Tamed creatures gain standing orders: FOLLOW, STAY and WANDER, cycled with the new Companion Whistle
  (sneak-use pets the creature with hearts). Orders are saved with the creature and moderated by new
  `[companion]` settings. There is deliberately no teleport: a companion left behind waits where it stands
  and resumes following when its owner is back in the same dimension.
- Tamed creatures now defend themselves and their owner against attackers, and never target the owner or
  another owned creature. Ground and amphibious companions path normally, flyers use their flight steering
  and swimmers stay inside their water column.
- Validated with `runData`, `build` and all 36 headless GameTests, including the new `combat_timing` and
  `companion` suites.

## Inspection Fixes — 2026-09-19

1. Tamed creatures no longer run wild routines and can never attack their owner or rider.
2. Tranquilizer arrow recipe repaired so it loads and can be crafted again.
3. Torpor restraint is re-applied after a sedated ordinary mob reloads, and command torpor restrains too.
4. Mount screen readouts now render inside the panel instead of at a doubled offset.
5. All Brazilian Portuguese text replaced with correctly encoded accents.
6. Unconscious players can no longer swim away from their anchor in water or lava.
7. Titanosaur water search fixed so it can detect the water it walks to and drink.
8. Wildlife hearing rejects out-of-range sounds before raycasts and chunk checks.
9. Land wildlife decisions reuse pack scans and only probe water/forage terrain when needed.
10. Xaero danger overlay reuses its exploration snapshot and throttles redraws while panning.
11. Map legend now names danger ranks 4 and 5 consistently with biome announcements.
12. Zero torpor recovery no longer leaves entities unconscious forever.
13. Sedatives are consumed only when accepted, and always one item.
14. Taming completion now shows the hearts burst.
15. Saved wildlife homes no longer follow the temporary prey-herd anchor.
16. Disabled-structure exploration maps no longer drop blank maps.
17. Removed the unused FollowPackGoal.
18. Mount screen Taming, Hunger and Torpor labels are localized in both shipped locales.
19. Players without taming state no longer carry unused torpor/taming attachments.
20. Added regression coverage for category mapping, world-generation spawning, natural persistence and the population budget.

### Natural spawning rework

- Chunk-generation spawn rules now accept the world-generation accessor, water-bound species use the
  water-creature category, ground placement tolerates ordinary uneven terrain, and the danger gate is
  served from a thread-safe layout snapshot so world generation never touches saved data.
- Natural wildlife persists like vanilla animals; an independent population budget (new `[spawning]`
  settings) tops up each player's surroundings without competing for the vanilla mob cap, and culls only
  over-budget or abandoned wilds. Tames, spawn eggs and commands are unaffected.
- Titanosaur and Giganotosaurus are budget-only: vanilla's world-generation spawner crashes when a body
  is wider than one chunk, so these two no longer join the generated biome spawn tables and rely on the
  population budget, eggs and commands instead.
- Validated with `runData`, `build` and all 34 headless GameTests, including the two new spawn suites.

## Vanilla Spawning — 2026-09-18

Creatures now spawn through Minecraft's own spawner instead of a saved-habitat population director. The
difficulty map, danger gating and per-species level scaling are unchanged; only the habitat system and the
creature's relation to it were removed. Pack identity, herd defense, alarms, nighttime routines and taming
are untouched.

### Changed

- **Spawning is vanilla-style.** Every species is added to its existing `spawns/<id>` biome tags through generated `neoforge:add_spawns` biome modifiers, carrying the previous weights and group sizes. The runtime placement predicate still enforces the danger/level gate, biome tag, surface support, body clearance and the water-column depth for water species.
- **The population director is gone.** `PopulationDirector`, the per-dimension `checkIntervalTicks` budget, the local cap and the direct placement code were removed; vanilla mob caps and despawn now own density.
- **Flyers keep nests and perching without colonies.** A natural flyer claims a single local nest of its own through the same site rules as before, lays an egg there, and defends it on theft. No colony record or map marker is saved, so discovery is by exploration.
- **Land and water wildlife keep their behavior without habitat records.** Each creature roams from its saved per-creature home, finds local water for thirst, forages with the same cold/warm rules and sleeps on the same clock. The shared group hunger clock was replaced by the existing per-creature needs clock.
- **Habitat map markers removed.** The nest, land and aquatic marker overlays and their payloads were deleted; the difficulty map, biome entry messages and map entitlement remain.

### Removed

- Saved habitat data and sync: `LandHabitatData`, `LandHabitatSync`, `LandHabitatPayload`, `LandWaterIndex`, `AquaticHabitats`, `HabitatData`, `HabitatSync`, `HabitatPayload`, `FlyerHabitats`, `GroupNeeds`.
- Client overlays `XaeroHabitatOverlay`, `XaeroLandHabitatOverlay`, `LandHabitatSymbols` and `HabitatMapClient`.
- Configuration: `[spawning]` now keeps only `enabled` and `minimumWaterDepth`; `[spawning.weights]`, the `[aquatic]` pool section and the `[landHabitats]` habitat keys were removed. The family roam/return/water distances moved to a new `[wildlife]` section.

### Compatibility and known limitations

- Both sides need the update: the habitat payloads and map marker overlays are gone.
- Existing worlds keep their creatures; stale `*_habitats.dat` files are simply ignored.
- The prior base required a valid water-adjacent habitat site before a land creature could spawn; that gate is what made the world feel sparse, and it is gone. Density now follows vanilla mob caps, so the per-species `enabled`/weight balance should be reviewed in play.
- No interactive client was launched; nest appearance, perching, spawn density and the removal of the map markers need playtesting.

## Taming, Torpor & Riding — 2026-09-16

All 41 registered creatures can now be tamed, saddled and ridden. Torpor, taming and riding share one
server-authoritative implementation, and the vanilla horse screen is reused for the creature inventory.

### Added

- **Torpor and unconsciousness** for mod creatures, players and ordinary living mobs, stored as per-entity data attachments. Sedative attacks, arrows and consumables all go through one service; entities are clamped to size-dependent ceilings of 60/150/350/700, recover 0.5% per second after a ten second delay, and wake below 20% of their maximum.
- **Narcoberry as a sedative**, eaten, swung or crafted into a **tranquilizer arrow** (four arrows, one narcoberry, one bone). The arrow reuses the vanilla arrow model and texture; no new art.
- **Three taming methods** with a complete profile for every creature: passive feeding, knock-out feeding from a creature inventory, and hunger-based aerial feeding for flying creatures. Aquatic species were assigned individually and never use the aerial rule.
- **Nine food and storage slots per creature** plus a real saddle slot, opened with the vanilla horse GUI layout, the vanilla saddle sprite and the vanilla equipment container contract. The claimant can reach a wild unconscious creature; the owner can reach a tame.
- **Riding for the entire roster**, including small creatures, with a measured seat per creature: the seat is derived from that creature's own back bone in its runtime model, not from one generic bounding box offset.
- **Torpor animation assets** imported from the source projects: 174 new runtime clips across the roster, wired so the collapse, unconscious loop, feeding and wake clips are selected from synchronized state.
- Operator commands under `/arktaming` for inspection, torpor, feeding hunger, mount inspection, roster validation and transition logging.

### Changed

- Berry behaviour: tintoberry, amarberry and azulberry are taming food, narcoberry is a sedative. All four were inert materials before.
- The map entitlement message no longer says taming is unavailable; the entitlement itself still follows `progression.mapRequiresUnlock` and is unchanged.
- New `[taming]` server configuration section with every balance value from the design: ceilings, recovery, sedative potency, feeding interval, appetite recovery and satiation, progress multiplier, damage penalty, decay grace period, claim expiry, feeding truce, rider speed, and the player movement tolerance and impulse window.
- A creature that finishes taming stops counting as wildlife for population and despawn accounting but keeps its needs, sleep and threat behaviour.

### Compatibility and known limitations

- **Seat and animation review requires a client.** Seat transforms are measured from the real bones and checked against the XZ bounds of each creature's own model mesh (0 of 41 outside); the roster and riding game tests keep the seat height above the entity's feet and within the registered height, but full hitbox containment is not asserted for the 24 meshes that are not normalised. The visual seat and the four torpor clip phases need in-client playtesting.
- **24 of 41 meshes are not normalised to their entity origin**, so the mesh-based seat check is not equivalent to the entity hitbox and those riders are not reliably aligned with the visible back (only the seat height is clamped). This is a pre-existing asset-pipeline limitation, listed per species at the end of `docs/taming-roster.md`.
- Six rigs have no complete source torpor sequence (Ceratosaurus, Cnidaria, Dragon, Megapithecus, Titanoboa, and Deinosuchus only partially) and fall back to their authored standing sleep pose for the unconscious loop. No clip was borrowed from another skeleton.
- A full torpor bar keeps a creature unconscious for about 160 seconds after the ten second recovery delay (about 170 seconds from the last dose), because waking happens below 20 % of the maximum, so a long knock-out tame needs the claimant to top the creature up. This is the intended maintenance loop and is documented in `docs/taming-roster.md`.
- Knock-out feeding consumes one item per eligible opportunity rather than the whole stack, and waking early keeps the deposited food in the now-locked inventory. The attempt completes even when the claimant is away: the player who deposited the food keeps ownership after the claim lease (two minutes by default) has lapsed, while an abandoned hand-fed attempt passes to the player who resumes it. Food left behind by an abandoned attempt is not eaten at all until a player claims the creature again, so it never disappears into a tame that has no owner to award.
- Owners are the only players who may access a tame. The project has no team or alliance system, so no team sharing is claimed.
- `tools/verify_assets.py` fails on one pre-existing assertion about `neoforge/biome_modifier`, which `ArkData` itself generates and which is already committed. Every assertion before it passes.

### Validation

- Gradle `runData` passed, writing the tranquilizer arrow item, its model, the five taming food tags plus the sedative and knock-out food tags, the crafting recipe, 29 taming messages in both locales and the twelve new test instances.
- Gradle `build` passed with 35 JUnit tests, no failures or skips.
- All 32 required headless GameTests passed, including twelve new taming tests: roster and diet audit with manifest cross-checks, torpor knockout and the exact wake threshold, passive feeding rules, knock-out inventory feeding with the claimant away, wake-before-completion, riding and dismounting every registered creature, save/load of torpor, taming, ownership and inventory, player sedation, hunger-gated aerial feeding with the feeder-only truce, completion without a forced wake, claim-lease expiry, and goal-level restraint of an ordinary vanilla mob.
- Creature import produced 41 creatures and 472 runtime clips, including the newly imported torpor sequences, with unchanged geometry and source hashes.
- Remaining work is visual: seat placement, the collapse and wake poses, and the rider's appearance on the side-mounted rigs.

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

### Fixed

- Flying colony markers carry the real species, so the map tooltip and rim color now identify Archaeopteryx, Quetzal and Dragon instead of labelling every non-Argentavis colony as a Pteranodon habitat.
- Triggered one-shot clips (landing, takeoff, swoop pull-out, warning, attack and torpor transitions) are forced to play once. Several imported montages are flagged to loop in the source asset, so a perched Archaeopteryx previously kept playing its looping landing animation instead of settling into the perched pose.

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

## Theme Alignment — 2026-09-16

The mod is a prehistoric survival experience, so Minecraft's fantasy and alternate-dimension
content no longer competes with hunting, herding and reading terrain. Detailed decisions,
retained material sources and limitations are in
[theme alignment](Ark/docs/theme-alignment.md).

### Removed

- Nether and End access: portals cannot be lit, every dimension change is cancelled,
  `allow_entering_nether_using_portals` is forced off, and ruined portals, strongholds, end
  cities, fortresses and bastions no longer generate. Players already inside either dimension
  are returned to safe Overworld ground on join and on respawn.
- Fantasy hostiles with every variant of this version: zombies, drowned, husks, zombie
  villagers and the zombie aquatic, camel and horse forms; skeletons, strays, bogged, parched
  and wither skeletons; creepers; endermen, endermites, shulkers and the Ender Dragon;
  witches and all illagers with vexes and ravagers; phantoms; slimes, magma cubes and sulfur
  cubes; guardians; blazes, breezes and ghasts; piglins, hoglins and zoglins; wardens and the
  creaking; silverfish; cave spiders; iron, snow and copper golems; and the Wither. Spawn
  eggs of removed creatures are hidden from the creative listings.
- Enchanting: the table cannot be used or crafted, enchanted books and golden apples are gone,
  and enchantment functions are stripped from generated loot and trades.
- Brewing and supernatural potions, tipped arrows and their ingredients; ender pearls, eyes of
  ender, ender chests and chorus fruit; totems of undying; soul items; beacons, conduits,
  respawn anchors and lodestones; elytra and firework rockets; sculk spreading, catalysts,
  shriekers and sensors; and the netherite, nether star, dragon, echo shard and shulker
  progression tiers, with the trial chamber rewards.
- Monster rooms, trial chambers, ancient cities, woodland mansions, pillager outposts, witch
  huts and ocean monuments from world generation, plus raids, patrols, sieges, phantom flybys
  and infested-block silverfish.

### Added

- Bones from animal carcasses: 23 vanilla animals drop 1–2 bones at 75%, so bone meal, wolf
  taming and bone blocks survive the removal of skeletons.
- A grounded copper bulb family: the Nether blaze rod in its centre becomes a torch, so every
  copper and redstone bulb variant stays craftable.

### Changed

- Ordinary spiders, every ordinary animal, villagers and wandering traders are kept, and the
  mod's own creatures are untouched. Gunpowder and slime balls keep the wandering trader as
  their grounded source; string, leather, feathers and wool never needed one.
- New `theme` server config section (`dimensions`, `monsters`, `mechanics`, all `true`) that
  switches the runtime guards off for debugging. The generated data removals always apply.
- Raids, patrols, phantom and warden game rules are re-applied on every server start.

### Compatibility and known limitations

- No save is rewritten: dimension ids, registries and saved data are untouched, existing
  creatures are removed as their chunks load, and characters inside a removed dimension are
  moved out. Both server and clients need the update.
- Content already generated in an existing world stays, so leftover sculk can keep spreading
  from catalysts, and disabled structures can still be looted. Block and removed-creature
  loot tables cannot be re-encoded and keep their original contents; chests, fishing,
  archaeology, village and structure loot are filtered, and the log lists what was skipped.
- Tridents, prismarine, sponge, shulker boxes, nautilus shells, hearts of the sea, fire
  charges and the Mojang banner pattern have no source left, and no replacement recipe was
  invented for them beyond the copper bulb.
- Interactive balance, the new bone supply and the return positions of player-owned worlds
  still need playtesting. No client was launched.

### Validation

- Data generation passed, writing 115 new resources: the removed-creature and biome tags, both
  biome modifiers, 11 emptied structure sets, 66 unreachable advancements, 23 filtered trade
  tags, 8 grounded copper bulb recipes, the animal bone loot modifier and the new test
  instance.
- Gradle build passed with 32 JUnit tests, no failures or skips.
- All 20 required headless GameTests passed, including the new `theme_alignment` test, which
  checks the removal lists against loaded registries, refuses ten removed creature families
  while a control animal joins, verifies biome spawn lists and features, the 11 disabled and
  4 surviving structure sets, removed and surviving recipes including the re-authored copper
  bulb, four sanitized loot tables,
  filtered and surviving trades, seven disabled advancements, the dimension rules, cancelled
  portal travel, the safe Overworld return position, and bones from animal carcasses.

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
