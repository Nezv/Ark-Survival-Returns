# Taming patch proposal

Prepared 2026-09-13, America/Sao_Paulo. **Stage: implemented on 2026-09-16; see
[the taming roster](taming-roster.md) and [the debugging guide](taming-debugging.md) for what shipped.**
The dates, counts and species list below describe the proposal as it was written, when the roster was 19
species and no torpor assets were imported; treat this file as the design record rather than current
documentation.

[Standard.md](../../Standard.md) requires concept expansion, comparison with other projects and multiplayer estimates before implementation feedback. This report completes that stage. Proposed features are deliberately absent from the implemented-change lists in [CHANGELOG.md](../../CHANGELOG.md).

The recommended patch combines passive feeding for small herbivores, knockout taming for dangerous or large creatures, and familiarity earned through care after taming. Preserve the successful-tame hearts, build real inventories and usable saddle placeholders, and make every transition readable through animation and the new Debug Spyglass. Food offerings for carnivores are a promising optional follow-up, with a bounded encounter prototype proposed below.

## What exists today

Source inspection and a Graphify query across `CreatureEntity`, `WildlifeGoal`, `FlyingCreatureEntity` and `DinoDebugSnapshot` found:

- **19 registered species.** The much larger `Creatures/` asset library is not the playable roster. This proposal covers those 19 species.
- `CreatureEntity` extends `PathfinderMob`. It has levels, wildlife behavior, persistence and animation controllers, but no taming progress, owner, creature container, saddle slot, ride controls or tame-success heart event. The requested hearts will use the familiar Minecraft success presentation when taming is implemented.
- The four berries are currently plain items. Narcoberry has a sedative tag and name, but no consumable behavior or status effect. The broad berry tag includes narcoberry: it must not accidentally count as taming food.
- Land decisions run on staggered ten-tick intervals. Flyers use a separate flight state machine and override animation registration. Changes confined to the land goal or base animation controller would leave flyers broken.
- The new **Debug Spyglass** already inspects a server-selected visible creature within 96 blocks and sends one bounded page every ten ticks. Its overview has 12 rows, and raw runtime/saved data occupy later pages. No survival recipe currently exists for this tool.
- Wild groups share habitat needs. A tame must leave those systems: changing only its owner would still allow wild routines, habitat adoption or population logic to influence it.
- There are **89 torpor clips in the source assets**, none currently imported into the runtime animation files. Every torpor clip's referenced bone exists in its source geometry. This validates structure, not appearance.
- The renderer rotates imported model space by 180 degrees and additionally pitches flyers. Source coordinates cannot be copied directly into rider offsets. The current Titanosaur is 42 blocks high; a single generic riding height or ground dismount rule will be inadequate.

Relevant implementation locations are under `src/main/java/dev/nez/arksurvivalreturns/feature/{creature,behavior,debug,land,flying,map}`, `registry/ModContent.java`, `datagen/ArkData.java` and `tools/import_creatures.py`. The exact source clip names, durations, loop flags and SHA-256 hashes are recorded in [taming-animation-audit.json](taming-animation-audit.json).

## Lessons from other games and mods

| Reference | Observed mechanic | Design lesson for this patch |
| --- | --- | --- |
| [Minecraft: Horse](https://www.minecraft.net/en-us/article/mob-menagerie--horse) | Repeated mounting builds toward taming; feeding helps, and saddling enables the riding experience. | Separate gaining the animal from equipping it, with unmistakable completion feedback. |
| [ARK community wiki: Taming](https://ark.wiki.gg/wiki/Pets) | Knockout taming uses an unconscious creature's inventory and suitable food; damage during taming affects effectiveness. | Keep the readable preparation/knockout/care loop, but avoid long waits and permanent stat penalties for every imperfect attempt. |
| [ARK community wiki: Carcharodontosaurus](https://ark.wiki.gg/wiki/Carcharodontosaur) | Fresh kills can build trust before a further hunting stage. | The proposed prey-offering idea has a game precedent. Its value is the dangerous encounter around feeding; food need not grant immediate obedience. |
| [Fauna of the Stone Age: Taming, author's mod page](https://mods.vintagestory.at/fotsataming) | Describes feeding to tame and subsequently developing obedience for commands. | Separate taming progress from the relationship developed afterward. Avoid requiring a long breeding project for a first companion. |

These are inspirations, not claims about dinosaur biology or other games' internal implementation. All recommendations, numbers and cost estimates below are proposed for this mod. The ARK wiki is community documentation; the Minecraft article and mod page are first-party descriptions.

## Proposed species and food rules

Use an explicit per-species taming profile. Diet, taming method and rideability must be separate fields: both flyers currently have `predator=false` despite needing animal food. Do not infer a taming method from that flag, rendered height, or the existing land-family name.

The user specified passive herbivores through Parasaur and knockout creatures from Bronto size upward. **The intermediate herbivores are an open design choice.** Recommend knockout for Trike, Ankylo and Theri in the first patch because their existing defensive behavior makes approaching with food a separate encounter-design task.

| Species | Proposed method | Acceptable food; favorite in bold | Saddle/riding proposal |
| --- | --- | --- | --- |
| Lystrosaurus | Passive feeding | Three ordinary mod berries; **Amarberry** | Companion; no riding |
| Pegomastax | Passive feeding | Three ordinary mod berries; **Tintoberry** | Companion; no riding; shoulder carry deferred |
| Parasaur | Passive feeding | Ordinary mod berries, carrot; **Azulberry** | Rideable |
| Triceratops, Ankylosaurus | Knockout, pending feedback on middle group | Ordinary mod berries, carrot, wheat; **carrot** | Rideable |
| Therizinosaurus | Knockout, pending feedback on middle group | Ordinary mod berries, carrot, apple; **apple** | Rideable |
| Brontosaurus, Titanosaur | Knockout | Ordinary mod berries, carrot, wheat; **wheat** | Rideable, ordinary single seat; no platform building |
| Dilophosaur | Knockout | Raw beef, porkchop, chicken, rabbit, mutton; **raw rabbit** | Companion; no riding |
| Velociraptor, Allosaurus, Carnotaurus, Ceratosaurus, Tyrannosaurus, Giganotosaurus, Acrocanthosaurus | Knockout | Same raw land meats; **raw mutton** | Rideable |
| Spinosaurus | Knockout | Raw land meats, raw cod/salmon; **raw salmon** | Rideable |
| Pteranodon | Knockout | Raw cod/salmon; **raw cod** | Rideable flyer |
| Argentavis | Knockout | Raw land meats; **raw mutton** | Rideable flyer |

Food choices are accessible Minecraft balance choices, not assertions of ARK accuracy. Ordinary mod berries means Tintoberry, Amarberry and Azulberry. Narcoberry contributes **zero taming progress**. Rotten flesh, poisonous foods, cooked meats and unlisted items are excluded initially; explicit data tags allow later changes. Every species has a favorite available without adding a kibble/prime-meat asset pipeline.

Start favorite food at **1.5 times progress per meal**, with the same meal cooldown as ordinary food. Every consumed meal makes deterministic progress; avoid success rolls that consume a stack without visible advancement. Suggested level-one favorite-food targets: 30-60 seconds for small passive tames, 1-3 minutes for small/medium knockout tames, 3-6 minutes for large animals, and 6-8 minutes for Giga/Titano. These exclude capture time. Higher levels increase required meals with a capped curve; expose server multipliers and tune after playtesting. A favorite should improve preparation, not make other foods a trap.

## Torpor as a Minecraft status effect

Add a registered harmful **Torpor** effect, with an icon, duration and English/Portuguese descriptions. Players are susceptible. Maintain an authoritative current/max torpor meter separately from effect duration: a lingering dose builds torpor, while the meter decides whether a creature collapses. Effect expiration alone must not force an unconscious animal to stand up.

Recommend narcoberry consumption as the basic exposure and a craftable **tranquilizer arrow** as the survival delivery method for wild creatures. Reuse existing arrow/item art for the initial item; recipe and dose are tuning data. Admin `/effect` application should exercise the same torpor system. Broad brewing recipes and a new weapon family can follow later. Ordinary punches and all weapons should not silently become tranquilizers.

Proposed rules:

- Torpor is clamped between zero and a species/level maximum. Exposure adds a bounded dose over time; after exposure, it drains at a configured rate. Additional approved doses add within a cap instead of multiplying timers without limit. Keep damage and torpor separate so taking an animal down is not a race against its last hit point.
- Before collapse, increasing drowsiness slows movement and makes the effect legible. At maximum, creatures stop attacking/navigating and begin their collapse sequence. Ordinary nighttime sleep remains a separate state and never unlocks a wild inventory.
- An unconscious animal remains down until taming succeeds or torpor drains to zero. Narcoberries sustain torpor; food advances taming. Successful taming clears remaining sedation and queued doses so it can stand up immediately through its wake animation.
- For players, propose a 100-point meter, progressive movement slowdown, then a **four-second incapacitation** at maximum. Permit camera/chat/menu access while rejecting movement and combat on the server. Give **eight seconds of recovery immunity** afterward, so several attackers cannot renew the same stun forever. This is a proposed gameplay balance rule, separately configurable from creature knockout.
- Player incapacitation does not expose their inventory to others. PvP-disabled servers reject player-origin torpor against other players; self-consumption and environmental sources still work. Creative/spectator players are excluded. Death clears the player effect; milk clears exposure/current torpor and begins recovery. Saving/rejoining preserves remaining exposure/incapacitation and recovery rather than resetting them.
- Expose the player meter near their own HUD/effect information. No blackout or camera shake is needed to communicate the state.

## The complete interaction sequence

**Passive:** observe a calm animal -> approach with eligible food -> feed once -> eating animation -> wait for its appetite/cooldown -> repeat -> ownership and hearts. Sprinting into it, attacking it or an actual predator threat interrupts feeding and triggers its existing flight/defense. A missed approach pauses progress; a real attack removes a bounded portion, rather than every scare erasing the attempt. Ordinary hunger informs readiness, but a recently grazed animal must become feedable within a bounded taming cooldown. Holding food does not suppress danger perception.

**Knockout:** inspect -> deliver torpor -> collapse -> unconscious inventory becomes accessible -> deposit suitable food and narcoberries -> periodic eating and torpor maintenance -> successful wake-up -> hearts and tame controls.

Detailed contract:

1. At maximum torpor, stop target selection, attack controllers, routine movement, pack following and ordinary sleep transitions. Collapse must have priority over movement animations, including knockback movement. Physical damage, falling and fluids continue to apply.
2. Unlock a wild creature's inventory only when it has finished collapsing and is down. Approaching a conscious or naturally sleeping wild creature never opens it. While down, show health, torpor, taming progress, acceptable/favorite food and the current waiting reason.
3. The first eligible food deposit starts an attempt; the first completed feeding event earns progress. Keep a separate taming meal timer, seeded from hunger but capped, so shared herd satiety does not produce a long hidden wait. Consume one meal per due event. Opening/closing the screen changes no timer.
4. Recommend automatically consuming one deposited narcoberry when torpor falls below 50%, waiting for the existing dose before consuming another. Depositing berries explicitly opts into this upkeep; show the threshold and active dose. Never consume sedatives for a completed tame. A manual 'Use one' action can supplement this without requiring constant clicking.
5. Every food event plays the torpid-eating clip and returns to the torpid loop. The server chooses the event and consumes the item once; animation rendering never consumes items or advances gameplay. No food means paused progress while torpor keeps draining. Damage pauses feeding briefly and reduces progress by a bounded amount; it does not trigger ordinary sleep wake-up code.
6. Completion takes precedence over a torpor-zero tie in the same tick. Persist ownership, detach wild behavior, clear doses and play `Out-Tamed`. Emit a small, local heart burst once as the creature finishes getting up. Reopening a screen, rejoining or retriggering an animation must not emit success again. Death takes precedence over completion.
7. On torpor-zero failure, close wild inventory access immediately, play `Out-Wild`, then resume the appropriate flee/defense behavior. Give a clear waking warning before the zero threshold. Remaining items stay persisted in the now-locked container, recoverable on another knockout or death; do not delete or duplicate them. Attempt progress resets, and any reservation clears.

Flyers need a dedicated interruption path. A torpid bird must stop flapping and fall under gravity; it must not hover unconscious or be teleported safely to the ground. Play its grounded collapse/idle after landing; fall damage and water can kill it. Taming on a low perch is the readable safer approach. Both flyers must remain grounded through waking, and a saved unconscious flyer must not reload into the current default flying state.

## Animation audit and implementation contract

Source JSON contains these torpor sequences. Prefixes are followed by `-Torpid-In`, `-Torpid-Eat`, `-Torpid-Out-Tamed` and `-Torpid-Out-Wild` unless indicated. The exact loop name varies and must be explicitly mapped.

| Species | Prefix | Unconscious loop | Collapse / successful wake (seconds) |
| --- | --- | --- | --- |
| Pteranodon | Ptero | Ptero-Torpid-Idle | 1.667 / 3.000 |
| Velociraptor | Raptor | Raptor-Torpid | 2.000 / 3.333 |
| Argentavis | Argentavis | Argentavis-Torpid-Idle | 1.667 / 2.667 |
| Triceratops | Trike | Trike-Torpid | 2.667 / 3.333 |
| Therizinosaurus | Therizinosaurus | Therizinosaurus-Torpid-Idle | 2.000 / 3.667 |
| Brontosaurus | Sauropod | Sauropod-Torpid | 3.667 / 5.000 |
| Tyrannosaurus | Rex | Rex-Torpid | 2.000 / 3.333 |
| Giganotosaurus | Giganotosaurus | Giganotosaurus-Torpid-Idle | 2.000 / 3.667 |
| Titanosaur | Titanosaur | Titanosaur-Torpid-Idle | 3.333 / 3.667 |
| Spinosaurus | Spino | Spino-Torpid-Idle | 2.000 / 3.667 |
| Parasaur | Para | Para-Torpid | 2.000 / 3.000 |
| Dilophosaur | Dilo | Dilo-Torpid | 2.000 / 3.333 |
| Allosaurus | Allosaurus | Allosaurus-Torpid-Idle | 1.967 / 3.300 |
| Ankylosaurus | Ankylo | Ankylo-Torpid | 2.000 / 2.500 |
| Carnotaurus | Carno | Carno-Torpid-Idle | 2.000 / 3.667 |
| Pegomastax | Pegomastax | Pegomastax-Torpid-Idle | 2.000 / 3.667 |
| Lystrosaurus | Lystrosaurus | Lystrosaurus-Torpid-Idle | 2.000 / 2.000 |
| Acrocanthosaurus | Acro_Torp | Acro_Torp_Loop | 8.333 / 5.917; shared Acro_Torp_Out |
| Ceratosaurus | No source torpor sequence | Missing | Author collapse, loop, feeding and wake poses |

Acro has `Acro_Torp_In`, `Acro_Torp_Eat`, `Acro_Torp_Loop`, `Acro_Torp_Out`: use its shared exit for both outcomes, with behavior/hearts distinguishing success. Cerato's source has `Ceratosaurus_DrunkAdditive`, `Ceratosaurus_Cuddle` and `Ceratosaurus_Falling`, but none constitutes a complete grounded knockout sequence. Recommend authoring a modest sequence on its own rig through the asset tooling. Do not substitute the standing `Ark-Sleep` pose or borrow another skeleton's animation.

Import these clips through `tools/import_creatures.py` with the same position-track normalization as geometry. Preserve one-shot versus looping flags; map durations into server phase timers, including Acro's much longer collapse. Suppress standing eat, roar and attack controllers during torpor. Join-in-progress clients must start in the correct phase and elapsed time. A lost client animation callback must never leave an animal frozen or grant an extra meal.

The three passive species still need torpor reactions because the status effect can hit them. Their unconscious inventory does not enable knockout taming when their profile says passive: show that method restriction clearly, and resume passive attempts only when awake.

No new saddle model or texture is required. The animation audit checked clip metadata and source bone references only. Contact with terrain, final poses, blending, runtime normalization and rider alignment require rendered/in-game review during execution.

## Inventory, ownership and riding

Start with nine storage slots and one separate horse-style saddle equipment slot. Before taming, storage accepts eligible food and sedatives; afterward it can hold ordinary cargo. Nonrideable companions show an unavailable saddle slot with an explanation. Wrong-species saddles are rejected. Every rideable species receives a named, nonstackable saddle item with an inexpensive placeholder model referencing vanilla saddle art. Define recipes through the data provider; no saddle armor, platform structures or bespoke saddle assets in this patch.

Successful ownership allows the owner to open inventory while the creature is awake. Sneak-use opens inventory; ordinary use mounts an owned, awake, saddled creature. Provide follow/stay and passive/defend-owner commands so a tame has useful behavior immediately. Do not inherit its former wild hunt/home goals. No random command refusal or bucking is needed for an unfinished familiarity meter.

Store a per-species seat attachment (body bone reference or validated local offset plus orientation). Calibrate against the imported, scaled model and the renderer's 180-degree correction. Use server-authoritative passenger coordinates; decorative animation following on the client cannot decide collision, reach or player position. Verify idle/walk/run/turn and both flyers' pitch/landing. Define mount interaction near an accessible part of the body; giants cannot require the player to click a saddle 20 blocks overhead.

Provide usable land movement and explicit flyer takeoff/land/vertical controls, with riding overriding autonomous flight. Unoccupied tamed birds stay or follow under tame behavior. Normal dismount searches a bounded set of loaded, collision-free positions near the feet; giants get an explicit safe lowering rule to an eligible nearby surface. If no safe surface exists, refuse a voluntary dismount with a readable message. Emergency separation on death, player disconnect or forced removal must still resolve without a stuck passenger. Test leaves, slopes, water, ceilings and world borders. Never solve a dismount by loading distant chunks.

For multiplayer, reserve an active knockout encounter to the player who delivers the first effective torpor dose, and a passive encounter to its first successful feeder. Propose a renewable two-minute inactivity lease; first feeding converts this into the active tame claim. Prevent other players from taking the last bite for ownership or extracting supplies. Initially owner-only control is sufficient; an explicit helper allowlist can be added if cooperative taming is wanted. Server-side menu validation checks identity, range, ownership and state on every action. An expired uncompleted claim must not retain private items indefinitely; define a visible abandoned-attempt state before allowing another player to claim it.

Persist owner/claim, method, progress, food timer, torpor/doses, phase time, inventory, saddle and familiarity. Existing creatures load as wild with empty inventory and zero torpor. Save unloaded attempts and pause their timers; no offline catch-up or forced chunk loads. Mark completed tames persistent and detach land/flyer habitat membership without counting a tame as a wildlife death or duplicating a replacement spawn. Persistent tame populations are a separate server load consideration.

The map entitlement already anticipates taming a danger-5 creature. Recommend connecting completion to the existing entitlement using a saved wild-origin danger value, so moving a tame across a boundary does not manufacture eligibility. Do not change the currently disabled `mapRequiresUnlock` default without direction feedback; update the message that says taming is unavailable when implementation lands.

## Familiarity and a more dangerous feeding alternative

**Recommend familiarity after taming, on a 0-100 scale.** It represents repeated successful care by the owner, distinct from taming progress and sedation. Award it for a favorite meal when actually needed, a meaningful outing, and returning safely. Per-category cooldowns stop one stack of food or circling in place from filling it immediately. No decay while offline, no mandatory daily chore and no loss of ownership when it is low.

Proposed bonuses: at 25, up to 10% less tame food consumption; at 50, up to 15% shorter rest recovery where a recovery system exists; at 75, one longer follow-distance option; at 100, a cosmetic bonded label/hearts on an occasional owner greeting. Do not invent a hidden stamina subsystem solely to support a bonus; defer that tier where it has no applicable mechanic. Keep baseline commands dependable. Avoid health/damage/level multipliers until mounted combat has been balanced: they would compound existing wild level scaling and punish anyone who chose knockout taming.

**Optional prototype: food offerings for Velociraptor.** This is an alternative direction for feedback, not part of the recommended minimum patch and not a silent replacement of carnivore knockout taming.

1. Find a hungry animal and read its current alert/hunt state with the spyglass. A sated animal ignores an offering; it does not become hungry because the player activated taming.
2. Enter a short, visible offering range, place one suitable food offering, then retreat outside its immediate defensive reach. The animal investigates only if it has a reachable path and the player has not already triggered an attack. A distant dispenser or food thrown from behind a sealed wall earns no trust.
3. It approaches, eats and briefly tolerates this provider. Do not disable its ability to attack. Closing distance too soon, attacking it, or another actual threat can end the opportunity. Use existing warning/feeding cues before escalation rather than a hidden random death roll.
4. Repeat across several appetite windows, then perform a final close feed while it is watchful but calm. That last approach supplies the adrenaline; completion uses the same owner/hearts event as other methods. Cap the encounter duration so waiting does not dominate play.

First prototype should use one short-lived offering marker, an existing meat item and bounded local approach checks. It can establish whether spacing and timing are fun without implementing carcass dragging. Full live-prey delivery requires credit attribution, protection against livestock farming, prey luring/pathfinding and a rule preventing every ordinary hunt from earning trust. Bringing food should make a predator associate the player with a useful recurring event; one kill alone is insufficient. Do not simulate or spawn extra prey for each attempt.

| Direction | Assessment | Multiplayer cost |
| --- | --- | --- |
| Knockout + small passive tames + later familiarity | **Recommend now.** Fits the requested loop and source animation coverage. | Mostly low incremental bookkeeping; riding/persistent populations are the main risk. |
| Add the Raptor offering prototype | Worth trying after the core works, or select it explicitly for this patch. Gives a real choice and dangerous proximity. | Moderate local navigation/sensing cost during attempts. Reuse existing land path budgets and cap active markers. |
| Replace all carnivore knockout taming with prey delivery | Defer. It makes the entire patch depend on reliable prey attribution, pathing and species encounters. | Moderate-to-high worst-case pathfinding and extra entity load; substantial production scope. |
| Long-term breeding/domestication, automatic combat-stat bonuses, permanent neglect penalties | Skip this patch. They delay a useful first tame or turn care into upkeep pressure. | Breeding increases persistent entity counts; stat arithmetic itself is cheap. |

## Multiplayer budgets and verification plan

These are workload estimates, not measured TPS/FPS improvements or regressions.

| Mechanic | Proposed scheduling | Expected incremental impact |
| --- | --- | --- |
| Torpor on creatures/players | Constant-time arithmetic for affected entities; damage/threshold transitions immediately authoritative | Low CPU, proportional to affected entities. No global nearby-player searches. |
| Food and passive progress | Staggered ten-tick checks only for active attempts; at most nine supply slots checked per due evaluation | Low CPU. At 96 simultaneous attempts, at most 192 evaluations and 1,728 simple slot checks per second. |
| Familiarity | Event awards and persisted cooldowns for the owner | Very low CPU/memory; avoid a per-creature record for every player on the server. |
| Spyglass | Keep two bounded pages/second per active viewer; promote torpor/method/progress into the first overview and move less useful rows later | Small added formatting cost within the current <8 KiB/s per-viewer payload budget. The existing full debug snapshot still serializes fields/saved data; adding containers increases that work. Measure it. |
| Inventory UI | Send slot changes to actual viewers; send compact progress on change, at a bounded cadence | Low-to-moderate traffic while open. Never broadcast full inventories every tick. |
| Torpor animation/hearts | Replicate phase/start time and feeding events to tracking clients; one local success burst | Low transition traffic, client bone evaluation remains. 89 additional clips increase asset size/loading, not 89 simultaneously active controllers. |
| Riding/following | Seat transforms per occupied mount; bounded following paths | Low transform cost; moderate terrain-dependent movement/path cost. Large hitboxes and flyers need particular testing. |
| Persistent tames | Save containers/ownership and tick loaded companions normally | Potentially high aggregate cost if players collect large numbers. Wild population caps do not cap tames. Avoid force-loaded bases. |
| Optional food offering | Bounded local investigation and one active marker per candidate | Moderate during pursuit/approach; never allow scans of all dropped items or all prey each tick. |

At 20 TPS, 96 affected creatures plus 16 affected players would perform about 2,240 simple torpor updates/second if metering every tick. This is arithmetic work, not a latency estimate; vanilla entity ticks, AI and navigation are additional. Budget server state updates independently from that calculation rate.

Benchmark matched baseline/patch scenes at 24, 96 and 192 loaded creatures: passive attempts, many simultaneous knockouts, large tame pens, moving mounted players, both birds, and several open inventories/spyglass viewers. Record median/p95 server tick time, active path requests/failures, bytes per viewer, loaded chunks and client frame time. Target a modest incremental p95 cost for inactive/calm scenes; do not claim a percentage before measurement.

Implementation checks must cover:

- All 19 profiles and favorites; no narcoberry-as-food loophole; passive versus knockout inventory gates; favorites advance deterministically and cooldowns resist click spam.
- Torpor dosing, decay, zero/max boundaries, player susceptibility/recovery/PvP settings, milk, save/reload and disconnect. Damage during torpor must not invoke the nighttime wake path.
- One food consumed per event, sedation upkeep, missing-food pause, success/zero/death ordering, interrupted wake-ups and exactly one heart event. Concurrent menu actions must not duplicate items or steal ownership.
- Complete animation mappings, bone references and normalized position tracks; one-shot/loop flags; Acro shared exit; authored Cerato sequence; both flyers and late tracking clients.
- Persistence without stat rerolls, wildlife detachment/population accounting, owner commands, no forced chunk loads, item drops exactly once on death, and map entitlement without changing its default gate.
- Correct saddle validation, actual rider control, seat coordinates, safe dismount and mount death/removal on land and in flight. Interactive testing must include the current giant scale.

Run `gradlew.bat runData` after provider changes, then `gradlew.bat build` and `gradlew.bat runGameTestServer`, using the wrapper. Import assets through `tools/import_creatures.py`, validate output, and update Graphify after code changes. Per [AGENTS.md](../AGENTS.md), the user performs interactive client testing; do not launch `runClient`.

## Feedback needed before execution

The proposed default is: keep knockout taming for every carnivore and all herbivores above Parasaur; use automatic deposited-narcoberry upkeep; add modest familiarity through later care; author Cerato's missing sequence; keep prey offerings as the next experiment. Middle-sized herbivores and whether to include the Raptor offering prototype are the two most consequential direction choices. Player incapacitation and the proposed familiarity rewards can also be adjusted before implementation.

Completed for this proposal: repository and graph inspection, source/runtime animation metadata comparison for all 19 species, bone-reference validation for all 89 source torpor clips, and linked game/mod research. No gameplay build, GameTest, benchmark, rendered animation review or client playtest was performed, because this stage changes documentation only. The two proposal/audit files are the only intended backup paths; unrelated existing workspace changes remain outside this task.
