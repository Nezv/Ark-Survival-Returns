# Snow Biome Patch — proposal

2026-09-14 · America/Sao_Paulo · Proposal for feedback; no cold species have been registered or deployed by this document.

## Direction

Build a cold ecosystem around **food, shelter and safe travel**, with rivers as a strong landscape feature. Reuse the land habitat ID, group coordinator, shared satiation, saved occupancy, bounded navigation and Xaero discovery model. Give each cold species a distinct routine instead of replacing the behavior system.

An exposed-water requirement for every habitat would make frozen areas unreliable: the shoreline may be iced over, rivers may be absent, and the best shelter may be on a ridge. Cold habitats should accept snow-supported hydration as a species policy. Water remains preferable where accessible; ice is neither drinkable water nor a reason to send an animal beneath a frozen surface.

## Current assets and integration gap

The [creature catalog](../../Creatures.md) lists all six requested cold creatures as source-only. The working runtime now has 19 other registered species, including the recently added small land herbivores; the older land proposal's nine-species inventory is historical. Cold creatures still need entity registration, attributes and collision dimensions, importer entries, selected animation bindings, spawn profiles, loot, localization and gameplay checks.

The source animation JSON was inspected for this proposal:

| Creature | Available clips | Useful existing clips |
|---|---:|---|
| Direwolf | 34 | `Direwolf-Howl`, `Direwolf-Move-Fwd`, `Direwolf-Charge-Fwd`, `Direwolf-Attack-Bite` |
| Mammoth | 41 | `Mammoth-Graze`, `Mammoth-Attack-Tusk-Jab`, `Mammoth-Attack-Foot-Stomp` |
| Megalocerus | 36 | `Stag-Graze`, `Stag-Charge-Fwd`, `Stag-Attack-Gore`, jump-in/out clips |
| Megapithecus | 28 | `Gorilla-Move-Fwd`, `Gorilla-Attack-Pound`, `Gorilla-Attack-Smash-Lft`, throw-in/out clips |
| Sabertooth | 35 | `Saber-Eat`, `Saber-Attack-Claw`, `Saber-Attack-Leap-Far-In/Idle/Out` |
| Unicorn | 42 | `Equus-Idle2`, `Equus-Eat`, `Equus-Roar`, `Equus-Attack-Buck` |

These are animation assets, not implemented howl buffs, leaps, projectiles or terrain interactions. Preserve the source models; import selected clips through the existing tool. The [collection notes](../../Creatures/Collection/README.md) identify Megalocerus as the male Stag model and Megapithecus as Gorilla. Keep folder spellings stable.

## Recommended species behavior

All values are starting design values, not biological claims or measured balancing results. Distances are horizontal blocks from a saved habitat center.

| Creature | Group | Home and ordinary radius | Distinct behavior | Initial danger eligibility |
|---|---:|---|---|---:|
| Direwolf | 4–6 | Sheltered taiga edge or rocky ridge near prey corridors; 96 | A short howl starts a coordinated search; a few stable approach offsets spread the pack. Attack only sensed prey, share successful feeding, return after the existing chase timeout. | 2+ |
| Mammoth | 2–4 | Broad snowy valley or forest clearing, preferably near a river/lake; 48 | Alternate grazing with sheltered rests. Healthy adults turn toward a threat together; injured individuals retreat behind the group. Avoid crowded narrow banks. | 2+ |
| Megalocerus | 4–6 | Snowy forest edge and open grazing patches; 40 | Vigilant grazers: pause and look before fleeing as a group, then regroup at home. One alarm is enough; avoid repeating calls every decision tick. | 1+ |
| Sabertooth | 1–2 | Rock cover or wooded slope beside a prey route; 64 | Stalk, pause, then make a short committed attack. A leap is a later option only after capsule/body clearance and contact timing work; ordinary pursuit is sufficient initially. | 3+ |
| Megapithecus | 1 | Rare mountain basin or sheltered amphitheater; 48 | Recommended: a territorial guardian with a warning, visible boundaries and a short return leash. Escalate on close intrusion or attack; no endless pursuit or block destruction. | 5 |
| Unicorn | 1 | Rare quiet snowy clearing or sheltered grove; 40 | Watchful and elusive: retreat from threats and return when quiet. Keep it a discovery encounter, with a defensive buck if cornered. | 1+ |

Suggested outer return distances: Direwolf 144, Mammoth 96, Megalocerus 80, Sabertooth 112, Megapithecus 80, Unicorn 80. Immediate danger may require a temporary escape outside the normal range; no invisible wall or teleportation.

Do not force Sabertooth into a 4–6-member pack just because it is smaller than a Rex. Use an explicit cold profile with a 1–2 override. Likewise, the Unicorn's special rarity and guardian's territorial role should not inherit ordinary herd capacity rules accidentally.

Megapithecus is an explicit design choice. ARK introduced it as a boss in a snowy mountain arena, so ordinary random wildlife behavior would substantially change that identity. A rare avoidable guardian works with this project's open ecosystem; a summoned boss encounter is the alternative if its boss identity should dominate. No minions, throwing rocks or arena generation in the first implementation. [Studio Wildcard's introduction](https://survivetheark.com/index.php?%2Farticles.html%2Fmegapithecus-boss-arena-lystrosaurus-sabertooth-salmon-and-arthropluera-r170%2F=&comment=830&do=findComment)

## Habitats: rivers plus shelter

Use three reusable site profiles:

1. **Valley grove:** dry supported ground, space for a complete herd, nearby grazing and optional water. Mammoths and Megalocerus prefer this. Open water within 64–96 blocks increases placement weight, but its absence does not veto an otherwise usable cold site.
2. **Ridge shelter:** accessible dry ledge or forest edge, some cover and at least two navigable escape directions. Direwolves and Sabertooths prefer this. Place beside a travel corridor, not in a cave requiring distant pathfinding.
3. **Secluded clearing:** quiet open space with cover at the edges. Unicorns use small clearings; Megapithecus requires much larger body clearance and suitable high-danger terrain.

Cold biome membership is an explicit eligibility gate through configurable tags, with snowy taiga, snowy plains and suitable mountain biomes as initial candidates. A height threshold alone must not classify a warm mountain as cold. Sheltered terrain is scored using a few cached support/cover samples; a full wind simulation is unnecessary.

At creation, validate complete group placement, whole-body clearance, slope, world border and loaded terrain. Keep a single stable home with up to three cached local destinations: forage, shelter and an optional drinking approach. Save their role, not a rigid daily timetable. Reject sites with no usable feeding/hydration option. Accept snow over valid forage ground, and use snow-browsing as an animation/routine abstraction without destroying player terrain.

**Frozen-water policy:** identify a frozen river as useful geography only if a bounded check confirms water beneath a shallow ice layer. Never treat arbitrary packed-ice structures as a drink source. Prefer an existing opening with a safe shore. If unavailable, cold-adapted creatures can satisfy thirst slowly at a safe snow patch; they do not break ice, melt blocks or dive under it. Habitat validity survives ordinary freeze/thaw changes. This needs a separate hydration policy from the warm-land exposed-water gate.

**Travel policy:** avoid deep powder snow, steep drops and unsupported ice edges. Cold resistance and the ability to stand on powder snow are separate mechanics; immunity must not imply that a Mammoth can walk on any thin surface. Start with terrain avoidance and cold resistance for all six. Defer ice-breaking physics and species-specific powder-snow traversal until tested. Vanilla already distinguishes cold-adapted entities in its freezing rules; implementation should verify the current 26.2 tags and code. [Mojang's freezing changes](https://feedback.minecraft.net/hc/en-us/articles/360059253111-Minecraft-Java-Edition-Snapshot-21w13a)

## Routines and survival gameplay

Reuse roam, forage, rest, search, alert, threat, hunt, defend, flee, regroup and return. A habitat destination can express shelter without adding a separate full brain. Heavy snowfall biases normal destinations toward shelter and reduces long searches. Combat, damage and urgent needs still override rest.

Keep day/night changes gradual. Wolves can favor dusk/night; Mammoths and deer favor daytime feeding. Sabertooths use quieter approaches rather than constant sprinting. Weather should shift behavior, not switch every animal into an identical state at the same instant. Group hunger and direction stay shared; stagger individual movement and animations.

Add one short, cooldown-limited event per encounter: the wolf's howl, the Mammoth's warning posture, or a guardian's chest/ground display using appropriate available clips. Sounds must be authored or sourced separately; an animation name does not supply audio. Favor readable warnings over permanent stat multipliers or remote prey knowledge.

Keep the landscape useful to a player running a temperature mod: sheltered groves become places to plan a route, frozen crossings become choices, and an exposed chase has a natural risk. Avoid forcing unavoidable guardian fights on the only safe river crossing.

An optional later reward could use fur as an insulation ingredient through the chosen mod's API or data system. Defer a Unicorn warmth aura, automatic player warming near Mammoths and a second custom temperature meter. Those would change survival balance and can conflict with another mod's rules.

## Optional freezing-mod integration

No temperature mod is required for the first snow ecosystem. Use vanilla biome/weather context for habitat preferences, and retain the ability to add a small optional adapter once a mod is chosen. The external mod should own player temperature, insulation, damage and its UI; this mod contributes explicit creature resistance or supported loot recipes.

Cold Sweat is a relevant example because its author describes biome, weather, altitude, insulation and nearby-block temperature effects. Its currently published compatibility list does **not** include Minecraft 26.2; do not promise a working install or fetch a mismatched JAR. Check the exact loader/game version and required dependencies again when choosing the survival mod. Nothing has been installed for this proposal. [Cold Sweat project page](https://modrinth.com/mod/cold-sweat)

Integration checks should cover the chosen mod absent, present and disabled; avoid duplicate freezing damage. A cold-adapted creature should resist environmental cold without becoming immune to fire, drowning or unrelated damage. A weather mod's storm API may later improve shelter selection, but must not become mandatory for spawning.

## Population, map and performance

Reuse the 24-creature local cap, three-group target and bounded population pass. Cold species compete for those slots; do not add a second independent snow quota on top. Tune ordinary weights toward herbivores so wolves have readable hunting opportunities without filling every valley with predators.

Start with rare habitats spaced at least 512 blocks from the same special species' other saved habitat, using the saved spatial index. This is a rarity rule, not a global singleton or an instruction to load remote chunks. Keep vacancies and cooldowns persistent so repeatedly revisiting a site cannot immediately reroll Unicorns or guardians.

Use the existing leaf/fang habitat symbols for ordinary cold grazers and hunters, with species names in tooltips. Megapithecus warrants an explicit guardian label; a unique badge can follow after the encounter role is chosen. Discovery reveals a habitat, never a live animal tracker. Avoid revealing an undiscovered Unicorn merely because it exists in saved data.

| Mechanic | Work bound | Estimated incremental cost |
|---|---|---|
| Habitat and food/shelter sampling | Reuse land work queues and caches; no additional per-animal terrain scan | Low steady cost; bounded work while exploring |
| Group climate preference | One cached check per active group every 100–200 ticks | Low |
| Howl and warning events | Cooldown-limited, same-group recipients only | Low |
| Formation and escape paths | Existing global path budget; wider Mammoth clearance | Moderate uncertainty on slopes and crowded banks |
| Snow hydration | Arrival checks at a cached snow/shore point | Low; no terrain edits |
| Leaps/projectiles/ice physics | Deferred | Highest additional collision and testing cost |
| Model rendering | Only visible creatures, chosen runtime clip subset | Must be measured; source bone/cube counts do not establish FPS |

Retain the 16 GB / 24 render-chunk target with simulation distance separately configured, initially 12. More heap does not justify more path searches. Separated multiplayer players create independent populations; the shared per-dimension work budgets must remain global. No FPS/MSPT improvement has been measured for this proposal.

## Implementation after feedback

First import/register the six creatures with explicit cold spawn eligibility and appropriate dimensions. Then add the cold site/hydration policy and distinct group profiles, followed by warnings, shelter preference and marker labels. Implement a temperature adapter only after a compatible mod is chosen.

Acceptance checks: no warm-biome spawning; frozen-river and river-free habitat viability; no forced chunk loads; safe Mammoth slopes/banks; exact group sizes and cap sharing; no duplication after partial unload; shared feeding and bounded pursuits; guardian warning/leash; rare-site persistence; snow hydration without terrain edits; save migration; temperature-mod absence and damage interactions. Run data generation, build and headless GameTests. Interactive clearance, animation and winter-survival balance require user playtesting.

Recommended first scope: all six base creatures, three site profiles, cold adaptation, shelter-biased routines, shared group behavior and discoverable habitats. Keep advanced attacks, special warmth rewards, destructible ice and a full boss arena for later decisions.
