# Theme alignment

The mod is a prehistoric survival experience. Minecraft's fantasy and alternate-dimension
content is out of scope: it competes for the same purpose as hunting, herding and reading
terrain. This patch removes those routes and keeps the mundane materials that surviving in
the Overworld still needs.

Everything below is enforced twice. Data changes stop new worlds and new loot from ever
containing the content; runtime guards cover worlds that already contain it, because a saved
level cannot be rewritten by a data pack.

## Step 1 — the Nether and the End

| Route | Result |
|---|---|
| Lighting a portal (`BlockEvent.PortalSpawnEvent`) | Portal is never built |
| Any dimension change to the Nether or the End (`EntityTravelToDimensionEvent`) | Cancelled |
| Nether portal travel, vanilla rule | `allow_entering_nether_using_portals=false` on every level |
| Ruined portals, strongholds, end cities, nether fortresses and bastions | Removed from world generation |
| Nether and End progression advancements | Replaced by an unobtainable placeholder |
| Nether and End trades (wart, glowstone is retained, ender pearls) | Removed from the trade tags |
| Netherite upgrades, nether stars, dragon eggs, dragon breath, echo shards, shulker shells | Removed items: no recipe, no loot, no trade |
| Players already inside a removed dimension | Returned to the Overworld on join and on respawn |

Dimension ids, saved data and registry entries are untouched, so an existing level keeps its
Nether and End files exactly as they are. `DimensionGuard` only refuses access.

**Where a player goes.** The recorded Overworld respawn point is reused when it is in the
Overworld, because that chunk is already loaded. A world whose respawn point was moved into
the Nether by a respawn anchor maps the Nether coordinates at the vanilla 8:1 ratio instead,
or falls back to the world border centre. The landing position must be dry ground with two
blocks of air, otherwise the next candidate is tried. A character saved in the End returns
through the same chain.

## Step 2 — fantasy hostile creatures

Removed creature families, including every variant present in this game version:

- **Zombies:** zombie, husk, drowned, zombie villager, zombie horse, camel husk, zombie
  nautilus, zombified piglin, giant
- **Skeletons:** skeleton, stray, bogged, parched, wither skeleton, skeleton horse
- **Creepers**; **Endermen** and **endermites**; **witches**
- **Illagers:** pillager, vindicator, evoker, illusioner, vex, ravager
- **Phantoms**; **cube monsters:** slime, magma cube, sulfur cube
- **Guardians** and **elder guardians**; **blazes**, **breezes** and **ghasts**, including the
  happy ghast
- **Piglins:** piglin, piglin brute, hoglin, zoglin
- **Shulkers**; **wardens**; the pale garden's **creaking**
- **Silverfish**, which exist only for infested blocks and monster rooms
- **Cave spiders** — ordinary spiders are kept: an oversized ground spider serves the survival
  experience, its venomous dungeon variant does not
- **Constructed servants:** iron, snow and copper golems
- **The Wither** and the **Ender Dragon**

Kept on purpose: ordinary spiders, every ordinary animal, villagers, wandering traders,
allays, nautilus, mannequin, and every mod creature — including the ones that can attack.

Every creation route is closed: natural spawning, structure spawning, block and structure
spawners, mob eggs, conversions, patrols, sieges, raids and phantom flybys. Spawn eggs of
removed creatures are hidden from the creative listings; an explicit `/summon` still works,
because administrator commands are not survival routes. Creatures already saved in a world
are removed as their chunks load.

### Materials that survive

| Material | Source kept |
|---|---|
| Bone | Animal carcasses: 1–2 bones at 75% from 23 vanilla animals, added as a loot modifier |
| String | Spiders, and the existing trader offers |
| Bone meal, wolf taming, bone blocks | Unchanged, fed by the new bone source |
| Gunpowder, slime balls | Wandering trader's uncommon offers, kept as the existing grounded source |
| Leather, feathers, wool, meat, ink | Animals and fishing, unchanged |
| Glowstone, quartz | Cleric and mason trades, kept for lighting and redstone |
| Copper bulbs | Grounded replacement recipes: the blaze rod in the centre becomes a torch, so all eight copper and redstone bulb variants stay craftable |

Removed-mob drops with no grounded role are removed instead of given replacement recipes:
rotten flesh, phantom membrane, shulker shells, blaze rods and powder, ghast tears, magma
cream, nether wart and dragon breath.

## Step 3 — content decisions

### Removed (rating 5)

- **Enchanting:** the table cannot be used or crafted, enchanted books, enchanted golden
  apples and golden apples are removed, and every loot table's enchantment function
  (`enchant_randomly`, `enchant_with_levels`, `set_enchantments`) is stripped. Anvils,
  grindstones, smithing tables and armor trims stay.
- **Brewing and supernatural consumables:** brewing stands cannot be used or crafted; potions,
  splash and lingering potions, tipped arrows, and the brewing ingredients are removed.
  Ordinary food, poison, hunger and disease are untouched.
- **Teleportation:** ender pearls, eyes of ender, ender chests, chorus fruit and popped chorus
  fruit are removed and cannot be used.
- **Resurrection and souls:** totems of undying are removed and cannot save their holder;
  soul torches, lanterns, campfires, sand and soil are removed with the Nether.
- **Magical infrastructure:** beacons, conduits, respawn anchors and lodestones cannot be
  crafted, placed or used.
- **Fantasy flight:** elytra are removed from loot and gliding is stopped server-side;
  firework rockets cannot be crafted.
- **Sculk and Warden:** sculk spread generation, veins, catalysts and infested ore are removed
  from every biome; catalysts, shriekers and sensors cannot be placed or used;
  `spawn_wardens=false`.
- **Constructed servants:** golem builds (pumpkin on iron, snow or copper) are refused.
- **Fantasy progression tiers:** netherite, nether stars, ancient debris, dragon eggs and
  breath, echo shards and shulker shells.
- **Trial chamber rewards:** mace, heavy core, breeze rod, wind charge, ominous bottle, trial
  keys and ominous trial keys.

### Disabled or replaced (rating 4)

- **Underground challenges:** monster rooms, infested ore and deep-dark sculk are removed from
  world generation; trial chambers, ancient cities, strongholds, woodland mansions, pillager
  outposts, witch huts and ocean monuments are removed from world generation. Villages,
  mineshafts, ocean ruins, shipwrecks, temples, igloos and trail ruins keep generating.
- **Infinite-resource exploits:** raids (`raids=false`), patrols (`spawn_patrols=false`),
  phantom flybys (`spawn_phantoms=false`) and village sieges are disabled, so raid rewards and
  bad-omen loops cannot start. Zombie villager curing is impossible because zombies are gone.

## Enforcement summary

| Layer | What it covers | Where |
|---|---|---|
| Biome modifiers | Spawn lists (`remove_spawns`) and features (`remove_features`) for every vanilla dimension | `src/generated/resources/data/arksurvivalreturns/neoforge/biome_modifier` |
| Structure sets | 11 sets emptied, so the set is dropped before placement is ever considered | `data/minecraft/worldgen/structure_set` |
| Advancements | One unobtainable placeholder per removed objective | `data/minecraft/advancement` |
| Trade tags | 23 profession/level tags re-listed without removed offers | `data/minecraft/tags/villager_trade` |
| Recipes | 53 recipes removed as raw JSON before deserialization, and the 8 copper bulb recipes re-authored | `RecipeGuard` |
| Loot | Enchantment functions and removed items stripped from the parsed table | `LootGuard` |
| Runtime | Spawn, conversion, join, interaction, placement, totem, glide, portal and travel guards | `feature/theme` |

The runtime guards can be switched off per group (`theme.dimensions`, `theme.monsters`,
`theme.mechanics`) for debugging. The data removals are always applied, because they are
generated resources rather than a runtime setting.

## Compatibility and known limitations

- **Saves are not rewritten.** Dimension ids, registries and saved data are untouched. Existing
  creatures are removed as their chunks load, and existing players inside the Nether or the End
  are returned to the Overworld.
- **Existing worlds keep what was already generated.** Sculk, monster rooms and disabled
  structures that already exist stay in place; their blocks cannot be placed again and their
  mobs cannot spawn. A sculk catalyst that still exists in an old world can keep spreading,
  because Minecraft offers no hook for sculk growth.
- **Loot coverage.** `LootTableLoadEvent` hands over a parsed table, so filtered tables are
  re-encoded and parsed back. Tables that hold references owned by a built-in registry rather
  than by the reload context cannot be re-encoded and are left untouched; those are block and
  removed-creature tables (for example `blocks/tall_grass`, `entities/zombie`). Chests,
  fishing, archaeology, village and structure loot are filtered. The server log reports the
  table ids left untouched.
- **Retained but unobtainable.** Tridents, prismarine and sponge (guardians and monuments),
  shulker boxes, nautilus shells, hearts of the sea, fire charges and the Mojang banner pattern
  have no source left. No replacement recipe was invented for them, except the copper bulb,
  which is an ordinary copper and redstone block and only lost its blaze rod to the Nether.
- **Mod creatures still drop nothing.** Their loot tables are intentionally empty, so bones
  come from vanilla animal carcasses only.
- Raids, patrols, phantom and warden game rules are re-applied on every server start, which
  overrides an administrator who changes them in the world.

## Verification

Refresh the data pack and check the mod headlessly:

```powershell
./gradlew runData            # writes the generated data pack
./gradlew build              # compile, JUnit and packaging
./gradlew runGameTestServer  # 20 headless game tests
```

The `theme_alignment` game test checks, against loaded registries: every removed id resolves
in this game version, a control animal still joins a level while ten removed families are
refused, no biome in either dimension keeps a removed spawner or feature, the eleven disabled
structure sets are empty while ordinary sets still place, removed recipes are absent and
unrelated recipes remain, four chest and fishing tables keep mundane loot with no enchantment
function or removed item, trade tags lose the removed offers and keep the surviving ones,
seven disabled advancements use the impossible trigger while an unrelated one does not, the
dimension rules are applied, portal travel is cancelled, the Overworld return position is dry
ground, and animal carcasses still drop bones.

`runClient` is not launched by the agent; interactive visuals and balance remain for the
player's own playtest.
