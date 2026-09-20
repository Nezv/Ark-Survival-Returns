# Survival progression: research and proposed patch roadmap

Prepared 20 September 2026 for a two-player cooperative Ark Survival Returns campaign. **Proposal, not implemented gameplay.** Recommendations assume the current Minecraft 26.2 / NeoForge 26.2 project, the existing Overworld-only theme, and the current creature roster. All durations, quantities, capacities and balance targets below are design hypotheses to test with the two players, not research findings or measured project performance.

The central recommendation is to make progress change what you do: **hand gathering → creature-assisted production → organized expeditions → industrial preparation → mastery encounters.** Build an experience where a remembered hardship becomes a solved problem, and solving it makes a new adventure possible.

## 1. What your preferences suggest

You enjoyed starting with nothing, gathering, suffering losses, improving equipment and confronting a final challenge. You also sometimes increased ARK rates to get past repetitive work. My interpretation is that you enjoy **earning capability and taking consequential risks**, but the amount of repetition that feels worthwhile varies. Raising rates is useful design feedback; it does not mean gathering itself failed.

The target loop is:

1. Choose a concrete shared ambition: tame an Ankylosaurus, reach the snow, build a workshop, defeat a regional guardian.
2. Discover the missing capability: cargo, food, sedation, cold protection, ammunition or a route.
3. Prepare through activities with choices, including a satisfying amount of gathering.
4. Venture out, make decisions under pressure, and sometimes lose something meaningful.
5. Return with resources, knowledge or a story even when the main objective fails.
6. Invest the result in a permanent capability improvement.
7. See a new destination and stop comfortably at home, ready for the next session.

### Engagement and addiction are different design targets

The WHO definition of gaming disorder concerns impaired control, gaming taking priority over other activities, and continuation despite harmful consequences, with significant functional impairment. Enjoying a long campaign or grinding for an item alone does not establish that condition. Here, “addictive” is best translated into **compelling, satisfying play that you voluntarily return to**. [WHO, Gaming disorder](https://www.who.int/news-room/questions-and-answers/item/addictive-behaviours-gaming-disorder).

| Research lens | What the evidence supports | Design application for this mod |
|---|---|---|
| Autonomy, competence and relatedness | Four studies linked satisfaction of these needs with game enjoyment and motivation for future play. This is a useful framework, not proof of a particular recipe or mechanic. | Choose between exploration, husbandry and engineering projects; show improved skill and capability; make rescues and joint expeditions worthwhile. |
| Harmonious versus obsessive engagement | A study distinguished wanting to play from feeling compelled to play; obsessive engagement was associated with poorer enjoyment and more tension. Associations do not prove causation. | Avoid attendance rewards, offline starvation and mandatory real-time baby care. Finish sessions with achievements and an optional next ambition. |
| Extrinsic rewards | A meta-analysis found that some expected tangible rewards reduced intrinsic motivation, while positive feedback could improve it. These experiments are not a direct test of survival-game loot. | Make rewards useful tools and feedback. A better saddle should enable a route, rather than merely fill another reward checkbox. |
| Uncertain success and learning | A theoretical account explains enjoyment through learning to master uncertainty; it is not a universal difficulty formula. | Make a dangerous encounter increasingly understandable. Telegraph attacks, provide scouting information and let preparation improve the odds. |

Sources: [Ryan, Rigby & Przybylski, 2006](https://selfdeterminationtheory.org/SDT/documents/2006_RyanRigbyPrzybylski_MandE.pdf); [Przybylski et al., 2009](https://selfdeterminationtheory.org/SDT/documents/PrzybylskiWeinsteinRyan%26Rigby2009_CPB.pdf); [Deci, Koestner & Ryan, 1999](https://selfdeterminationtheory.org/wp-content/uploads/2014/04/1999_DeciKoestnerRyan_Meta.pdf); [Mastering uncertainty, 2022](https://pubmed.ncbi.nlm.nih.gov/35959012/).

The recommendations that follow are my synthesis of those ideas and your stated preferences. They are not clinical claims or scientifically validated balance settings. A “dopamine loop” alone is too vague to explain why your particular adventures worked.

### The mechanics worth preserving

- **Gathering:** preserve route planning, tool selection, dangerous loads and relaxing harvesting sessions. Reduce inventory cleanup and repeating the same trip after its challenge is solved.
- **Loss:** losing a haul or expedition animal can create a rescue, adaptation or revenge story. Losing every durable achievement usually destroys the next playable goal. Keep knowledge, established infrastructure and recovery options.
- **Rare finds:** use unusual traits, locations and cosmetic variants to surprise players. Guarantee essential progression materials through a visible route; random rewards can accelerate completion.
- **Anticipation:** reveal the next saddle, machine or biome early enough to create a plan. Show its recipe and missing prerequisites.
- **Ownership:** naming creatures, improving a home and keeping trophies make progress visible. Treat attachment as a reason to provide good companion control and recovery.
- **Cooperation:** two useful contributions are better than one person mining while the other waits. Roles should be swappable, and solo preparation should remain possible.

## 2. Current project: what exists and what is missing

This assessment uses the working checkout, which includes existing uncommitted work. It is not a claim that every documented system has been independently playtested in this research task.

| Area | Evidence in this checkout | Roadmap implication |
|---|---|---|
| Wildlife | 41 species, habitat systems, danger bands, saved creature levels and natural population management | Spend the next major development effort on reasons to interact with these creatures. |
| Taming and riding | Taming profiles, torpor, sedative arrows, saddle equipment, riding controls and persistent creature inventory | Extend the implementation; do not schedule taming from scratch. |
| Cargo | `CreatureInventory` currently provides nine storage slots | Species capacity, item mass and hauling specialization are new work. |
| Companions and combat | FOLLOW/STAY/WANDER, defense behavior and authored attack timing are present in code/changelog | Add cooperative permissions, useful jobs and expedition commands on this foundation. |
| Progression | `ProgressionData` persists danger layout; this is not a four-stage research tree | Introduce separate tribe progression and unlock definitions. |
| Theme | Nether/End, fantasy mobs and many associated items/progression routes are removed | Every proposed material must have an Overworld route. Audit recipes, trades and loot for bypasses. |
| Presentation | Map overlay, rendering extras, integrated ambience, footsteps and sound physics | Retain them; avoid duplicating their systems through extra mods. |
| Campaign | No complete four-stage crafting economy, industrial ladder or boss campaign was identified in the inspected registries and feature structure | Those are the central missing pieces, not merely extra recipes. |

Some older documentation still says taming is deferred, while current code and newer README text contain taming. Reconcile stale statements during the first patch. A registered Dragon or Megapithecus species is not evidence of a designed boss encounter.

Local references: [mod overview](../README.md), [content registry](../src/main/java/dev/nez/arksurvivalreturns/registry/ModContent.java), [creature inventory](../src/main/java/dev/nez/arksurvivalreturns/feature/taming/CreatureInventory.java), [danger persistence](../src/main/java/dev/nez/arksurvivalreturns/feature/spawn/ProgressionData.java), [theme](theme-alignment.md), [client pack](client-pack.md).

## 3. Campaign architecture and technology tree

Start with a **roughly 40–70 active-hour first-clear target** for two players, excluding optional construction and collection. This is a planning envelope to revise after playtesting, not a promise of entertainment duration. Start with Primitive 2–4, Early 8–12, Mid 15–25 and Late 15–25 hours; experienced players should be allowed to advance faster.

Use a shared tribe journal with four chapters and three intersecting branches:

```text
Camp: fire + shelter + basic tools + field journal
  ├─ Husbandry: first tame → harness/trough → harvest jobs → specialized saddles
  │                                      └→ breeding/nursery → expedition lineage
  ├─ Engineering: mortar → forge → workshop → crusher/press → generator/fabricator
  │                                                    └→ refrigeration/medical lab
  └─ Exploration: survey → field camp → first guardian → specialized biomes
                                                      └→ three regional challenges
                                                           └→ final expedition
```

Research should be earned by discovering and using things, not by repeatedly killing trivial animals for XP. Unlock most branches with observations and crafted prototypes. Reserve a few major milestones for encounters. Keep two attainable projects available in each stage.

**Stage gates:** enforce production through the correct station, ingredients and shared knowledge. Recipe-book visibility alone is not enforcement. Loot, trading, automation and transferred equipment must follow an explicit policy. A high-tier item found early may be salvageable, but should not bypass the entire manufacturing branch.

**Cooperative policy:** both players share tribe research and boss records. Personal journal entries record individual discoveries without requiring a second kill. A returning cousin gets the shared unlocks and a short “what changed” page. Owned tames remain individually identifiable, with explicit tribe permissions for riding, cargo, commands and breeding. Do not grant every action globally to every server player.

### Primitive: learn the land and establish a foothold

**Fantasy:** two vulnerable survivors turn a dangerous location into somewhere they can return to.

**Main activities:** forage, identify safe routes, hunt manageable prey, build shelter, make basic tools, tame a small companion and bring back the first useful cargo.

| Branch | Proposed content | Reason to exist |
|---|---|---|
| Materials | Plant fiber from common vegetation, flint, wood, stone, hide, bone and charcoal | Locally obtainable ingredients with distinct uses; no rare-biome starting bottleneck. |
| Tools | Stone hatchet/pick, flint knife, spear, basic bow | Knife processes carcasses; hatchet favors wood/hide; pick favors stone/flint. Avoid five interchangeable tool tiers. |
| Camp | Firepit, simple storage, bedroll, thatch/wood shelter, repair surface | Establish a repeatable return-and-prepare loop. Respawn fallback must survive a destroyed bedroll. |
| Supplies | Cooked meat, berry meal, fiber bandage, hide waterskin | Introduce preparation with forgiving thresholds and readable feedback. |
| Exploration | Field journal, landmarks, simple compass/route notes | Explain dangers without requiring debug UI or revealing every rare creature. |
| Creatures | Parasaur as early pack companion; Lystrosaurus as optional homestead companion | The first tame should solve a visible problem. These are proposed roles, not claims of existing abilities. |

**First-session sequence:** find food and shelter → craft tools → scout a manageable tame → one player distracts/guards while the other handles taming → return with a useful load → build a small permanent improvement. Allow either person to perform each action.

**Difficulty:** food scarcity and predator avoidance already provide tension. Introduce one additional survival meter at a time. Thirst can be omitted from the first playable slice and added after the core loop works. A camp near water should solve routine hydration; the interesting problem is carrying water into an expedition.

**Exit gate:** a functional camp, one useful tame and the first forge prototype. Offer alternate raw-material routes for unusually sparse starts. Do not require surviving an arbitrary number of nights.

**Complete when:** a fresh duo can discover the intended loop without commands, finish one gathering/taming outing, understand an avoidable loss and recover using their camp. Essential early resources must exist near multiple tested spawn seeds.

### Early: build a productive homestead

**Fantasy:** the settlement starts paying back the effort put into it.

**Main activities:** improve food supply, tame specialized workers, establish a metal route, move heavier loads and prepare for the first designed guardian.

| Branch | Proposed content | Dependency/payoff |
|---|---|---|
| Processing | Mortar, charcoal kiln or charcoal recipe, primitive forge, smithy | Converts familiar resources into medicine, fittings and durable tools. Reuse vanilla systems where they already serve the role. |
| Equipment | Iron pick/hatchet, crossbow, improved tranquilizers, hide armor, basic shield | Iron comes after a forge; stronger taming tools create new options. |
| Farming | Berry plots, compost bin, irrigation, trough, drying rack, cooking pot | Food grows from a chore into a planned supply chain. Crops support meals and taming feed. |
| Logistics | Pack harness, cargo sled/cart if technically feasible, sorting crates, local crafting from storage | Larger hauls and easier unloading. A creature cargo harness is the fallback if carts are too costly to implement. |
| Utility tames | Triceratops for berries/thatch; Ankylosaurus for mineral gathering; Parasaur for modest hauling | Tool and creature roles complement one another. Worker tames need different reachable capture preparations. |
| Fieldcraft | Spyglass, reusable route markers, field bedroll, stone perimeter, repair kit | Scouting and recovery improve before expeditions become longer. |

**Food branch:** raw ingredients → basic cooked ration → prepared meal with a clear activity benefit, such as longer stamina endurance or cold resistance. Avoid managing six independent nutrient bars. Preserve cheap basic food as viable.

**First guardian:** an authored territorial alpha encounter, using an existing suitable creature model. It teaches a readable charge, a flank opening and a retreat decision. The arena has enough space for two people and a small mount team. It is distinct from ordinary high-level wildlife and cannot be tamed to skip its encounter.

**Reward:** a guaranteed workshop schematic plus a trophy and useful salvage. Research can be understood as reconstructing equipment from a guarded expedition cache. A kill should not inexplicably create engineering knowledge by itself.

**Exit gate:** successfully make iron equipment, demonstrate a reliable food/material route and recover the workshop schematic. Keep husbandry and exploration side projects open while preparing.

**Complete when:** a harvesting tame reduces the elapsed cost of its intended material, both players can use shared infrastructure, and a failed guardian attempt costs supplies without forcing another full Primitive playthrough.

### Mid: expeditions, specialized biomes and industry

**Fantasy:** the pair operates a small expedition company, with a home workshop supporting dangerous field work.

**Main activities:** prospect, build outposts, discover regional resources, establish mechanical processing, improve creature lines and tackle multiple distinct challenges.

| Branch | Proposed content | Gameplay effect |
|---|---|---|
| Metallurgy | Steel from iron + carbon at a powered/advanced forge; crusher and press | Improved material yield, components and equipment. Balance batches around actual recipes. |
| Chemistry | Sulfur, nitrate, charcoal, resin, oil, natural rubber or another single polymer precursor | Deterministic Overworld sources for ammunition, sedation and industry. Do not depend on random wandering-trader visits. |
| Power | Water/wind mechanical source followed by fuel generation | Solve processing throughput; do not make a generator a universal instantaneous crafting block. |
| Gear | Long rifle, tranquilizer darts, steel tools, fur armor, insulated supplies, reinforced saddle | Introduce range, accuracy, specialized protection and higher-value preparation. |
| Travel | Pteranodon scouting saddle, Argentavis cargo rig, boat cargo, amphibious travel, basic diving apparatus | Different routes and capacities; flight should not erase hauling decisions or every terrestrial threat. |
| Agriculture | Better irrigation, feed recipes, seed propagation, controlled spoilage and bulk cooking | Support expedition length and specialist taming; stop adding manual chores to solved farms. |
| Husbandry | Nursery, readable inherited traits, feed trough controls, limited active breeding | Build a useful line without mandatory overnight attendance or hundreds of animals. |

**Three regional routes, selectable in any order once equipped:**

- **Highlands/cold:** exposed terrain, warmth preparation and limited shelter. Gather insulating materials and mineral samples. A Mammoth supports timber hauling; a ground escort remains useful where flying has poor landing choices.
- **Swamp:** sightlines, water crossings and ambushes. Gather resin and medicinal plants. Sarco or another amphibious tame improves the route; appropriate clothing and antidote address clearly communicated hazards.
- **Coast/deep water:** oxygen, navigation and underwater threat. Gather pearls, oil or salvage. Start with shallow options and progress to a diving mount; provide a route back before introducing deep objectives.

Each route delivers a guaranteed research component for the Late-game workshop plus an independent useful upgrade. Avoid gating a region's essential protective gear behind that region's hardest boss. The shallow/edge route supplies entry equipment; deeper travel upgrades it.

**Creature progression:** some species unlock through food quality, saddle manufacture or specialized capture preparation. Existing tames must survive migration; do not delete or silently disable them. Generic saddles can remain basic seats while improved rigs gate cargo efficiency, specialized jobs and combat protection.

**Breeding proposal:** inherit a small visible set of bounded traits—endurance, hauling and one species specialty. Separate cosmetics from power. Select a useful breeding pair in a handful of expeditions. Hatch/grow through active-world progress with paused care requirements when the tribe is offline. Strong wild tames must remain boss viable; perfect breeding is optional mastery.

**Exit gate:** complete the three regional challenges, manufacture advanced components and assemble an expedition-ready team. This should demonstrate breadth of preparation, not demand hundreds of duplicate boss kills.

**Complete when:** each biome changes the packing list and tactics, every resource has an accessible first acquisition route, industrial production saves manual effort, and the next expedition is viable after a wipe.

### Late-game: specialized mastery and a real ending

**Fantasy:** turn accumulated infrastructure, knowledge, equipment and animal partnerships into a difficult shared achievement.

**Main activities:** complete a major fabrication project, choose specialized loadouts, learn boss mechanics, recover from setbacks and finish a final expedition.

| Branch | Proposed content | Limits and purpose |
|---|---|---|
| Industry | Fabricator, precision bench, chemistry bench, refrigeration, medical station, automated local sorting | Shortens replenishment; fuel and ingredients still require a functioning economy. |
| Materials | Alloy plate, bearings, precision barrels, insulated cable, polymer composite, battery | A compact set of meaningful components, not dozens of nearly identical intermediates. |
| Equipment | Specialized rifle/shotgun, climbing line, advanced diving suit, expedition medicine, reinforced creature rigs | Sidegrades for specific encounters; finite and visible ammunition costs. |
| Logistics | Heavy caravan equipment and a Quetzal cargo role; fixed outpost supply infrastructure | Large projects become possible without giving personal inventories unlimited mass. |
| Mastery rewards | Distinct blueprints, saddle modules, trophies, cosmetic creature/builder rewards | Guaranteed core unlocks, optional rare variants and bounded quality improvements. |

**Theme choice:** use grounded advanced industry for the base campaign. A Tek-like science-fiction branch could be a later optional expansion, but jetpacks, teleportation and universal force fields would substantially change this project's current theme and transport balance.

**Encounter ladder:**

1. Early guardian: teaches the combat grammar and awards the workshop plan.
2. Three Mid regional guardians: each tests a different preparation problem and awards one guaranteed component.
3. Late final expedition: assemble a field installation, activate it at a dangerous site and survive a multi-phase apex encounter. The installation provides an explicit objective beyond reducing HP.

Use a grounded apex as the default finale. Dragon could be an optional alternate finale because the model already exists, but its presence needs a deliberate theme decision; the roadmap does not assume fantasy bosses are required.

**Final boss specification:** two-player scaling; visible windups; punishable recovery windows; distinct phases; limited adds; clear retreat rules; server-authoritative hit resolution; predictable arena bounds; meaningful participation for a support player; no requirement for an army of dozens of tames. Prototype with two players and two to four active combat mounts. Tune the exact cap based on arena/pathfinding tests.

Example roles: one player manages the boss's direction and safe positioning with a durable mount; the other protects/repairs the field installation and attacks exposed windows. Roles can swap. A solo mode substitutes longer windows or lower simultaneous objective pressure rather than merely halving boss HP.

**Victory:** permanent completion journal entry, a visible trophy structure, final equipment blueprint and a short account of the tribe's expedition. Then allow optional harder rematches, creature collection, building and alternative regional routes. Let the campaign be finished; continued play should have chosen goals.

**Complete when:** a duo can complete the whole campaign from a fresh world without operators, mandatory rare-drop luck or perfect breeding, and the final rewards make the achievement legible.

## 4. Weight, gathering and loss: detailed starting rules

### Weight should create logistics, not inventory punishment

Represent mass in adjustable gameplay units rather than pretending to simulate kilograms. Keep slots as organization capacity; mass controls mobility. Display both clearly.

Suggested initial capacities: player 100, early pack mount 400, specialist ground hauler 900. These are test values only. A working player's tools, armor and food should consume around one-quarter to one-third of capacity, leaving room for a useful haul.

| Load ratio | Initial behavior |
|---|---|
| Up to 75% | No movement penalty. |
| 75–100% | Gentle increase in sprint stamina cost; visible warning. |
| 100–125% | Gradual movement reduction; sprint unavailable. |
| Above 125% | Strong slowdown with walking and dropping cargo still possible. No permanent immobilization. |

Initial item examples: fiber 0.05, berries 0.1, logs 1, ore 2 units each. Rebalance after measuring whole recipes and trips. A selected mineral hauler might count designated ore at half mass, but its rider cannot recursively multiply reductions by nesting containers or remounting.

Implementation rules:

- Count equipped items, carried containers and mount cargo. Apply specialist reductions exactly once to eligible cargo; never recursively through other creatures or portable inventories.
- Prevent nested storage exploits through bounded content inspection, cached mass and explicit unsupported-container handling. Default unknown items conservatively; provide configurable tags/overrides.
- For mounts, account for rider cargo and equipment. Clearly define whether rider body mass is abstracted into capacity; use the same rule in the UI and server logic.
- Flight requires a safe takeoff load. Mid-flight overload or stamina exhaustion triggers warnings and controlled descent, not immediate freefall. Underwater overburdening must not silently create unavoidable drowning.
- Change mass when inventory changes, not by scanning every slot of every entity every tick. Sync threshold changes and relevant UI values.
- Give both players convenient unloading, bulk transfer and nearby crafting at home. Manual sorting is not the intended challenge.
- Include an off switch and a relaxed preset. Adjust material throughput with capacity: raising harvest yield without changing mass can make gathering feel worse.

### Gathering should evolve

Example progression for a forge project: manually carry ore for the first tools → tame a mineral worker → bring a pack animal and escort → place a field processing station → develop a supplied outpost. Later recipes require specialized ingredients and expedition decisions, rather than multiplying basic iron demand indefinitely.

Suggested session rhythm: 5–10 minutes planning/packing, 20–40 minutes on an outing, then 10–20 minutes processing/building. These are optional design rhythms, not compulsory timers. Gathering-heavy evenings and long boss nights should both work.

Rate configuration should separate harvest yield, taming duration, growth duration, crafting throughput, spoilage, recovery and combat difficulty. Provide **Relaxed Duo**, **Standard Duo** and **Harsh Expedition** presets with explicit descriptions. Do not increase every difficulty variable together.

### Recovery should be an adventure you can afford

- Keep research and base ownership through death. Drop carried expedition gear into a persistent recoverable container with coordinates available to the tribe.
- Proposed standard: no item decay timer on that container, but cap outstanding containers per player and consolidate older ones safely. A logging-out player should not lose cargo because a wall-clock timer expired.
- Introduce a downed/revive state with a short, configurable rescue window and escalating combat pressure. An outright lethal hazard can still be fatal; explain the distinction.
- Tames can be incapacitated during designed encounters; rescue/treatment costs medicine and recovery time. Permanent creature death can remain a harsher preset or occur after an explicitly failed rescue. Validate whether this preserves the stakes you enjoyed.
- Maintain a reachable fallback spawn and a basic recovery kit. Do not teleport a living stranded companion home for free.
- A typical failed attempt should consume supplies and perhaps a cargo run; aim to make a serious retry achievable in roughly 15–30 active minutes once infrastructure is established.
- Base attacks should be opt-in expeditions/events or clearly signaled threats. Do not add offline base destruction as a default source of retention pressure.

## 5. Mod integration decisions

Compatibility checked against publisher/project pages on 20 September 2026. Listings are not installation tests. Never infer compatibility just because a project separately lists “NeoForge” and a Minecraft version; select an exact artifact with both, resolve dependencies and test this pack. No new mods were installed for this proposal.

| Candidate | Decision | Integration work / present evidence |
|---|---|---|
| [JEI](https://modrinth.com/mod/jei) | First integration candidate | Project lists 26.2. Confirm an exact NeoForge artifact. Add custom processing, taming food and research descriptions; hide removed fantasy recipes and explain locked content. Its page says newer versions require server installation for recipe sync. |
| [Sophisticated Storage](https://modrinth.com/mod/sophisticated-storage/version/fRR7xgdK) | Strong optional base-storage candidate | Exact 26.2 NeoForge release `26.2-1.5.93.1969` is listed; requires Sophisticated Core. Pin a tested Core version. Restrict the initial integration to fixed storage and relevant upgrades; eliminate netherite/shulker progression conflicts. Test mass-bearing container handling. |
| [FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge) | Good conceptual fit; defer dependency | Team-based quests fit well. Checked listing shows 26.1.2 and other versions, but no verified 26.2 file. Build a modest native journal now; use a future adapter if a supported artifact is available. |
| [Farmer's Delight](https://modrinth.com/mod/farmers-delight/versions) | Good cooking reference; defer dependency | Checked page lists support through 1.21.1, not a confirmed 26.2 release. Implement only the small farming/cooking subset the campaign needs while staying on 26.2. |
| [Create](https://modrinth.com/mod/create) | Excellent mechanical progression reference; defer dependency | Checked project lists versions including 1.21.1, not a verified 26.2 artifact. Do not promise a drop-in install or casually undertake a full port. Prefer a few native machines for the first complete campaign. |
| [Immersive Engineering](https://modrinth.com/mod/immersiveengineering) | Alternative industrial direction, not a second simultaneous overhaul | Checked listing includes 1.21.1, no verified 26.2 release. Its visible power/processing infrastructure fits this proposal; use as a design reference. |
| Existing Xaero + Sodium + Iris pack | Retain the pinned setup | Already documented locally. Test journal/map interaction; provide useful basic navigation early and reserve detailed regional intelligence for discoveries. |

Do not add a giant collection of independent content mods before establishing the shared recipe and material economy. Avoid an additional creature pack now, unrestricted flight/teleport utilities, or effectively limitless personal storage; these can erase the roles the roadmap gives existing creatures.

Two implementation routes are possible: **keep 26.2 and build a compact native campaign** (recommended for this checkout), or deliberately migrate to a mature mod ecosystem version to use large automation/cooking mods. Migration would require a separate audit of APIs, GeckoLib, assets, world saves and the existing audio work. It is not a small prerequisite patch.

For any selected integration, maintain a manifest of exact Minecraft/loader versions, artifact IDs/hashes, dependencies, configuration, sides and licenses. Installing a mod is different from copying its source into Ark. Use documented extension points and review redistribution terms before bundling.

## 6. Next patches, in dependency order

These names describe proposed work packages, not releases or estimates of developer days. Complexity and server impact are qualitative estimates. Every patch includes UI, persistence, configuration, recipes, localization and acceptance checks appropriate to its mechanics; a registered item alone is not completion.

| Order / proposed patch | Concrete scope and dependencies | Definition of done | Complexity / multiplayer impact |
|---|---|---|---|
| **P01 — Tribe and Survival Journal** | Shared tribe IDs/permissions, four chapter definitions, discovery records, pinned objective, contextual recipe information; audit stale docs and vanilla progression bypasses. | Two players join, leave and reconnect without duplicate rewards or lost research; first objectives work on fresh saves; existing tames retain ownership. | Medium; low overhead using saved state and event-driven updates. |
| **P02 — Camp and Recovery** | Field bedroll, fallback spawn, recovery container, bandages/revive policy, first-hour resource routes and Primitive recipes. Depends on tribe identity. | Fresh duo establishes camp; forced death/relog yields exactly one recoverable inventory; no unrecoverable starting seed in the test set. | Medium; low normally, bounded recovery-container storage. |
| **P03 — Weight and Working Tames** | Configurable mass, visible thresholds, harnesses, species cargo, Trike/Anky harvest jobs, shared commands, fast unloading. Depends on P01–02. | Measured useful hauling advantage; no nested-storage mass bypass; harvest jobs respect protected/player-built blocks and chunk bounds; flight overload recovers safely. | High; medium risk from job pathfinding. Bound searches and recalculate mass on mutations. |
| **P04 — Homestead Economy** | Crops, compost, irrigation, troughs, preservation, cooking, sedative production, forge/smithy, optional storage integration. Depends on P03 balance. | Farm supports a two-person expedition; no offline care obligation; no mandatory ingredient relies on random trader arrival. | Medium–high; medium if many ticking farm blocks. Batch updates. |
| **P05 — First Guardian** | One complete authored encounter, arena/entry rules, telegraphs, retreat, guaranteed workshop schematic and trophy. Depends on P02–04. | Duo wins with intended early gear; a failed run can be retried; reload cannot duplicate rewards or strand the encounter. | High; medium–high in a bounded arena. Cap adds and combat tames. |
| **P06 — Regional Expeditions** | Cold, swamp and water routes, material distribution, field camps, protection gear, scouting flight and transport tradeoffs. Depends on P05. | All three routes accessible without circular recipes; old worlds have an explicit content access path; aerial shortcuts do not nullify every objective. | High; generation and exploration spikes. Never force chunk loads for spawns/jobs. |
| **P07 — Workshop and Power** | Steel, crusher/press, power, chemistry, local automation, grounded ammunition, optional JEI categories. Depends on P04/P06 resources. | Reproducible input/output accounting; no resource duplication; common replenishment becomes faster; every advanced recipe has a reachable source. | High; medium–high for networks. Cache topology and use bounded batch processing. |
| **P08 — Husbandry and Expedition Orders** | Nursery, bounded traits, limited breeding, readable lineage, regroup/retreat/passive commands, two-player tame permissions. Depends on tribe state and established food economy. | Offlining either player causes no care penalty; population remains bounded; good wild tames remain viable; ownership/reload tests pass. | High; potentially high entity/pathfinding cost. Enforce active breeding limits. |
| **P09 — Regional Guardians** | Three differentiated encounters and guaranteed advanced research components, reusing P05 encounter services. Depends on P06–08. | Different packing lists/tactics are rewarded; all three are clearable by a duo; essential components never depend on rare RNG. | High content scope; bounded encounter cost. Build and review one guardian at a time. |
| **P10 — Fabrication and Final Expedition** | Advanced stations, specialized equipment, final assembly chain, multi-phase finale, campaign ending and mastery rewards. Depends on P07/P09. | One fresh-world campaign reaches a legitimate finish; rewards persist; solo fallback and disconnect handling work. | Very high; highest combat integration risk. Prototype before final assets. |
| **P11 — Campaign Balance and Release** | Fresh-world and migrated-world runs, economy tuning, presets, recipe/trade audit, localization, journal completeness, reproducible pack/server setup. Applies throughout; release gate after P10. | Two players complete all stages without operator commands or hidden recipe knowledge, with documented performance and remaining limitations. | Medium–high; profiling should reduce rather than add runtime cost. |

**First useful milestone:** P01–P03 provides a camp, shared objective, recovery, meaningful cargo and a useful worker tame. **First complete campaign slice:** P01–P05 provides a beginning, a productive homestead, a boss and a visible payoff. Finish and play this before building the full industrial tier. Work on P08 can wait until the first slice proves that the core loop is enjoyable.

## 7. What “complete implementation” requires

### Data and systems

- Data-driven material tags, mass values, recipes, station tiers, unlock prerequisites, creature jobs, loot and encounter definitions.
- Server-authoritative tribe membership, crafting eligibility, ownership, combat, rewards and inventory changes. Clients render state and request actions.
- Separate persistent research state from `ProgressionData`'s geographic danger layout. Keep identifiers stable and schema versions explicit.
- Audit retained vanilla furnace/tool recipes, villagers, structure loot, fishing and any optional mod machines against the intended ladder. Preserve useful early scavenging through salvage rather than silently deleting every valuable find.
- Do not hand-edit generated resources. Extend data providers and regenerate them through the repository workflow.
- A migration policy for current tames, saddles, inventories, research and structures. New worldgen should use unexplored terrain or a deliberate, bounded placement mechanism; never blindly regenerate occupied bases.
- A stated unloaded/offline simulation policy. No new worker or crop feature may force chunks to load; no catch-up starvation or surprise production windfall after a long absence.

### Every item or machine needs an actual loop

For each feature, specify its first source, recipe, stage, station, cost, purpose, feedback, repair/replenishment, storage behavior, failure behavior and exit into another system. Provide English and Brazilian Portuguese text. Show locked prerequisites and failure explanations instead of silently refusing an action.

For each tame job, specify valid targets, search limits, protection rules, yield, tool/wear costs if any, stamina, cargo behavior, completion feedback, interruption and restart behavior. Do not let an automatic wood worker dismantle the players' house.

For each boss, specify encounter ownership, participants, scaling, trigger, arena, moves, damage timing, retreat, death/revive, tame rules, disconnection, server restart, rewards and repeatability. Reward issuance must be idempotent across reloads.

### Acceptance testing and performance

Follow the repository requirements during future gameplay implementation: use the Gradle wrapper; run `runData` for data changes; run `build` and `runGameTestServer` for gameplay changes. The user performs interactive client testing. This research-only task did not run gameplay tests or launch Minecraft.

Meaningful automated checks: crafting eligibility and source reachability, research persistence and tribe access, exactly-once recovery/rewards, container mass accounting, job target protections, creature ownership migration, encounter reload/disconnect handling and population/search bounds.

Manual duo scenarios: fresh spawn; separate exploration; cousin joining late; shared cargo/commands; death while mounted; overload during flight; failed boss retreat; disconnect during encounter; restart with active jobs; older save opened after the patch.

Profile a representative base with two players, several workers, farms, storage and processing—not only an empty world. Record hardware, view/simulation distance, entity counts, heap, server milliseconds per tick and encounter spikes. A proposed release target is sustained 20 TPS under the agreed workload, with p95 tick time below the 50 ms tick budget. This is an acceptance target, not a measured result. Test shader/render performance separately from server performance.

### How to tell whether the design works for you two

After each session, record: most memorable moment, least enjoyable repeated task, next desired project, whether either player felt stuck waiting, whether a rate change was wanted, and whether stopping at camp felt satisfying. Keep this as a small local playtest log; no telemetry service is needed.

Measure gather/travel/sort/wait time on representative outings. If sorting and idle waiting dominate, fix handling or throughput before multiplying rewards. If material gathering is enjoyable, leave room for it rather than automating everything by default. If a boss fails repeatedly because of unreadable attacks, fix readability before lowering resource costs.

The release is complete when **each stage introduces a different kind of problem, each major upgrade solves an earlier burden, losses produce recoverable stories, both players contribute, and the final expedition gives the campaign an earned ending.**
