# Progression bypass audit

P01 deliverable: list the vanilla routes that can skip the Primitive objectives, state which theme
guards already close, and record what is deferred to P02 (camp, tools and recipes) and P04
(mid-game industry). This is a decision record, not a promise of economy changes.

## Method

A bypass is a route that reaches a Primitive outcome without the intended loop. Only routes that
survive the existing Overworld-only theme are listed; removed content (Nether, End, fantasy mobs,
enchanting, brewing, teleportation, monster rooms, trial chambers, mansions, outposts, raids) needs
no further action because the theme already blocks it. `ThemeGameTests.theme_alignment` verifies
those removals, including village trade and loot rewrites.

## Routes

| Objective | Route that bypasses it | Status | Decision |
|---|---|---|---|
| Campfire and bedroll | Village houses, shipwrecks, mineshafts, woodland-mansion chests (structure removed) | Accepted | Loot requires travel into danger; no change in P01 |
| Stone tools | Village toolsmith trades, shipwreck and mineshaft loot, desert-temple chests | Accepted | Travel-gated; revisit with tool tiers in P02 |
| Berries | None: berries come only from grass loot and drops | Closed | — |
| Bones | Animal carcasses (intended), villager trades for bone, fishing junk | Accepted | Fishing junk is rare; P02 may trim it |
| Narcoberry | Grass loot only | Closed | — |
| Tranquilizer arrows | Player crafting only | Closed | — |
| First tame | Taming food from farms and animals (intended), villager food trades | Accepted | Taming is the intended sink; no change |
| Saddle | Vanilla loot and leatherworker trades can supply one before the first tame | Accepted | The quest reward guarantees one saddle for every player; P02 may add a craft |
| Field Journal | Book from sugar cane and leather (intended); the enchanting-table route is removed | Closed | — |
| Map entitlement | Operator `/arkmap unlock`; a rank-5-origin tame is the only survival route | Closed | Provenance is saved at first spawn |
| Primitive chapter completion | FTB team progress is per tribe, so one member's find counts for both | Intended | This is the shared-research design, not a bypass |
| Death item loss | Recovery Cache returns the whole haul at the death spot (P02) | Intended | The cache is a place to walk back to, not a free item return; it deliberately softens loss into a rescue trip |
| Respawn positioning | Field bedroll sets a personal respawn point that survives the block (P02) | Intended | Reachable fallback is a P02 acceptance rule; it grants no material advantage |
| Lethal damage | Downed state with a rescue window; void/lava/overkill stay fatal (P02) | Intended | The distinction is documented and configurable; a rescue costs a fiber bandage and a trip |
| Cargo capacity and harnesses | A saddle or a loose item never raises capacity; both harnesses are crafted from leather and fiber (P03) | Closed | Capacity is a movement budget, not a resource shortcut |
| Harvester tames | Work orders require the WORK flag, a nearby authorized survivor and only loaded chunks (P03) | Closed | Jobs pause without supervision and stop at the cargo ceiling; no unattended automation |
| Overloading | Slots are never blocked, so manually exceeding capacity is intended (P03) | Intended | Capacity only shapes the movement penalty and the automated fill ceiling |
| Prepared meals | Only the cooking pot makes them; ingredients come from farms, animals and the drying rack (P04) | Closed | No trader or loot table supplies a meal |
| Dried rations | Only the drying rack converts raw food (P04) | Closed | Raw ingredients still come from hunting, fishing and farms |
| Charcoal and iron | The kiln and forge save fuel but the vanilla furnace still reaches the same results (P04) | Intended | Efficiency station, not a gate; structure loot and trades are unchanged |
| Storage crate | Fixed 27 slots, no upgrades, no automation, mass-aware when carried (P04) | Intended | It replaces chest sprawl, not storage progression |

## Deferred work

- **P04 (homestead):** decide whether stone and metal tool tiers need a stricter source than
  structure loot. Any change must keep the two-player campaign free of forced grinding.
- **P04 (industry and economy):** audit automated production against the remaining gather loops,
  and revisit fishing junk, villager restocking and loot-table densities with measured play data.
- A saddle craft remains optional: the quest reward still guarantees one, and P03 offers harnesses
  rather than a second saddle source.
- Sophisticated Storage stays an optional, deferred integration: the fixed P04 crate covers the
  homestead need, and the mod will only be considered once a verified 26.1.2 artifact exists and its
  upgrades are audited against the mass and cargo rules.
- Any future theme additions should extend `ThemeGameTests` with the same pattern used here: one
  assertion per removed route, so a game update cannot silently reopen it.
