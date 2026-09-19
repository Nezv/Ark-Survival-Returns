# Nighttime patch proposal

Prepared September 9, 2026 (America/Sao_Paulo). Status: proposal for feedback, not implemented.

[Standard.md](../../Standard.md) requires concept expansion and multiplayer estimates before the user's implementation feedback. This document completes that first stage. Proposed mechanics belong here; the player-facing changelog should receive entries only during implementation.

The recommended experience is a quieter daytime wilderness with dangerous sleeping animals, followed by nights of visible predator activity and vulnerable, responsive herds. Night danger should come primarily from behavior. Red eyes, movement and warning cues should help players understand and avoid encounters.

## Existing foundation

Source inspection and a Graphify query confirmed the following:

- `WildlifeMind` already handles hunger, fatigue, resting, warnings, hunting, feeding, fleeing and bounded pursuit. Hunger rises by 1.0 per 24,000 simulated ticks at every time of day. Hunting normally requires hunger of at least 0.4; a wildlife kill resets it to 0.05.
- `WildlifeGoal` gives only Velociraptor a nocturnal preference. Rest is currently fatigue-dependent, so changing that preference alone would not create sustained daytime or nighttime sleep. Sensed creatures can also interrupt routines at a distance.
- Decisions run every ten ticks, staggered by entity ID. Each decision senses at most 24 candidates after collecting and sorting nearby entities. That cap limits detailed sensing, not the initial query or sorting cost in a crowded area.
- Current sight range falls to 70% in darkness. Bronto and Trike herds defend members and can deter predators. Pursuit expires after 300 ticks, with additional territory and path-failure limits.
- Only Rex and Trike have imported ordinary sleeping clips. Other species use an idle pose for rest. The five meat-eating species' runtime models have separate eye geometry; the asset builder separates eye and eyelid palette assignments. `CreatureRenderer` has no eye-glow layer yet.
- The existing `predator` flag is true only for Raptor, Rex and Giga. Argentavis and Pteranodon are false and share some non-predator food logic. Diet, temperament and hunting ability need separate profiles before applying the theme to all carnivores. Both birds still use ground navigation.

## Lessons from other projects

| Reference | Relevant design | Proposed adaptation |
| --- | --- | --- |
| [Studio Wildcard's Megalosaurus dossier](https://survivetheark.com/index.php?/forums/topic/13606-introducing-the-megalosaurus/) | Describes a nocturnal predator seeking a secluded daytime sleeping place and reacting when disturbed. This is a published design description, not verification of every current ARK mechanic. | Give players a learnable schedule and an opportunity to pass resting predators carefully. |
| [Nine Rocks Games: Way of the Hunter FAQ](https://ninerocksgames.com/posts/frequently-asked-questions) | Describes species-specific places and times for eating, drinking and sleeping. | Reuse local home areas for recognizable routines, with individual variation. |
| [Crytek: Hunt audio design](https://www.huntshowdown.com/news/hunt-audio-readability-realism-and-consistency) | Explains ambient, aware and combat voices and one-time escalation cues. | Pair waking and pursuit with restrained state cues so players can recognize danger in darkness. |

These are design inspirations. The recommendations and performance estimates below are our own inferences, not claims about those games' internal implementations or dinosaur biology.

## Recommended mechanics

All numbers are initial tuning proposals. Performance ratings describe incremental work at equal loaded population, not measured TPS or FPS.

| Mechanic | Proposed player experience | Multiplayer impact estimate |
| --- | --- | --- |
| **Red eyes at night — add** | Awake carnivores have red emissive eyes from dusk to dawn. Eyes dim during sleep and fade out by day. Use depth-tested eye-only geometry so the glow follows animation and is hidden by opaque cover. | Very low server cost for phase/state synchronization; low expected client cost for a small additional eye draw. Shader appearance needs visual testing. Avoid re-rendering an entire dinosaur for its eyes. |
| **Night hunger and hunting — add** | Start with **2× nighttime hunger growth**, retaining the 0.4 hunt threshold. Hungry animals search locally, then investigate, warn and pursue eligible detected prey. Feeding restores satiety and ends the hunt. | Hunger arithmetic is negligible. More simultaneous pursuits and fleeing prey create a **moderate, terrain-dependent** server risk through navigation and movement updates. |
| **Daytime carnivore sleep/roaming — add** | Unthreatened carnivores spend roughly **70% of their routine time sleeping and 30% slowly roaming** near home, with individual variation. A nearby detected player wakes them; continued intrusion escalates to warning and defense. A distant passerby should not keep them awake indefinitely. | Low scheduling cost. Fewer routine paths may reduce daytime work, but sleeping creatures still need bounded wake checks. |
| **Nighttime herbivore sleep/flee — add** | Safe herbivores settle to sleep. A credible sensed pursuit, dangerous approach, attack or local herd alarm wakes them and triggers escape. After losing danger, they regroup and eventually sleep again. | Low while resting; **moderate during herd flight** because several animals request escape paths together. Use the existing stagger and stagger routine replanning. |
| **Defensive herbivore exceptions — recommend** | Flight is the initial nighttime response to a credible predator threat. Healthy Trike/Bronto groups may defend after regrouping; Theri/Titano may defend if attacked or unable to escape. Injured or isolated animals favor continued flight. | Low decision overhead; group queries and combat add low-to-moderate work during encounters. This deliberately qualifies universal fleeing and needs direction feedback. |
| **Gradual dusk/dawn transitions — add** | Animals settle or wake across about **20–30 seconds** instead of switching together. Dawn never forces sleep during damage, pursuit or immediate danger. | Very low arithmetic cost; spreads routine path requests across ticks. |
| **Night vision for active hunters — recommend** | Hunters retain their daytime sight distance at night, replacing the current 30% darkness penalty for those profiles. Rain, crouching, facing and cover still matter. | Low expected extra sensing work with the same candidate limits; more detections can increase pursuit load. |
| **Wake cues and calm-down delay — add** | Show Sleeping, Alert and Hunting in the existing target display. Reuse appropriate existing reaction cues on escalation. Require about **10 seconds without a relevant threat** before settling again. Damage interrupts sleep immediately. | Very low timer/state cost. Local sound and state packets occur on transitions, with cooldowns. |

At 20 TPS, 2× hunger growth takes a predator from 0.05 satiety-state hunger back to the 0.4 hunting threshold in **3.5 minutes**, versus seven minutes at the current rate, assuming continuous simulation at that rate. This gives a concrete starting point for prey-survival tuning. Dusk should not instantly fill hunger, and sleep/time commands should not apply hours of catch-up hunger in one frame.

## Species scope

| Species | Proposed role |
| --- | --- |
| Velociraptor | Nocturnal pack hunter; day sleep/roaming; red eyes. Preserve local alarm membership and pursuit limits. |
| Tyrannosaurus, Giganotosaurus | Nocturnal large hunters; day sleep/roaming; red eyes. Healthy defended prey remain costly targets. |
| Argentavis | Include in carnivore glow, hunger and schedule. Recommend small-prey opportunism using existing ground navigation for this patch; actual aerial hunting remains separate. |
| Pteranodon | Include in carnivore glow, hunger and schedule while retaining its timid response to players. Any feeding-target expansion should be restricted to suitable small, reachable prey; aquatic and aerial hunting need a later movement system. |
| Triceratops, Brontosaurus | Night sleep, sensed-danger escape and regrouping, with the defensive exception above. |
| Therizinosaurus, Titanosaur | Night sleep and threat-based escape; may defend against direct attacks or when escape fails. Size alone must not classify them as carnivores. |

The recommended broad scope includes all five carnivores but preserves distinct temperaments. A narrower alternative is to ship hunting changes for the three existing land predators and give the birds only schedule/glow changes, explicitly recording their unfinished feeding behavior. That alternative is less work but should not be described as complete carnivore behavior.

## Behavior rules and implementation boundaries

- Add a clear distinction between scheduled sleep and brief fatigue rest. Fatigue reaching zero must not immediately end a scheduled sleep period. Sleep requires safe ground; floating, fire and immediate harm take priority.
- Use the authoritative world day cycle for the schedule, initially proposing night from ticks 13,000–23,000 of the 24,000-tick cycle. Local shade and rain should affect perception, not turn daytime into a nocturnal schedule. Define a neutral fallback for dimensions without an ordinary day cycle.
- Use existing sight, hearing, scent and local alarms for wake decisions. A predator merely selecting a hidden target must not give the herbivore supernatural knowledge of being hunted. Player approach distances should be configurable and measured from body bounds because creature sizes vary greatly.
- During daytime routines, suppress unsolicited prey pursuit and distant-stimulus interruptions. Direct attacks, nearby intrusion and urgent danger override that suppression. Retain warnings for an unprovoked approach and immediate defensive reactions to actual damage.
- Keep hunger, levels, health, home and pack identity on reload. Derive the schedule from the current phase; do not reset needs at dusk. Add defaults for any new saved timers and clear transient targets on reload.
- Preserve creative/spectator exclusions, Peaceful player-aggression rules, loaded-chunk checks, finite searches and finite pursuit. Keep spawn eligibility and population targets independent of day/night.
- Build eye assets through the existing import/tool pipeline. Reuse genuine sleep clips; author simple ordinary rest/sleep poses where clips are missing. Torpor animations should not silently stand in for natural sleep. Eye geometry and pose alignment require user visual review.
- Keep decisions authoritative on the server and rendering client-side. Synchronize discrete phase/state changes; calculate smooth visual fades locally. Expose server schedule/hunger/wake tuning and client glow strength.

## Mechanics to skip or defer

| Proposal | Recommendation and player reason | Multiplayer/performance implication |
| --- | --- | --- |
| Dynamic red lights illuminating terrain | Skip initially; emissive eyes provide the requested warning without adding another lighting dependency. Bloom can be assessed with the existing optional shader pack. | Additional client lighting and shader cost is avoided; emissive appearance alone does not guarantee a bloom halo. |
| Blanket night damage/speed boosts or instant sunset aggression | Skip initially. Existing predators already run faster than an ordinary player; behavior should be tuned before adding more combat power. | Stat arithmetic is cheap, but more frequent catches, kills and replenishment can amplify server activity. |
| Guaranteed stalking of the nearest player | Skip. Hungry predators should choose suitable detected wildlife or players and lose them through existing perception rules. | Avoids long-distance target searches and persistent pursuit churn. |
| Full pack flanking and chained panic across unrelated herds | Defer. First make a single hunt and local herd response reliable and readable. | Potentially high navigation and repeated-neighbor-query cost. |
| Persistent carcasses, migration and simulation in unloaded chunks | Defer to an ecosystem patch. Feeding after a successful kill already provides satiety. | Potentially high entity, persistence and simulation cost. No forced chunk loads. |
| Torch immunity zones, moon-phase raids and bespoke dinosaur audio | Defer until the core cycle is playtested. These add independent balance, sensing or asset work. | Light searches/raids can add moderate server work; custom sound assets mainly add production/download cost and local playback. |

## Performance validation after direction feedback

The current 24-creature local spawn cap is not a server-wide cap. Separated players can keep distinct populations active, and manual spawns can exceed natural limits. Costs should be compared by loaded creature count and active pursuits, not player count alone.

At the existing cadence, 24 loaded creatures run approximately **48 decisions/second**, with up to **1,152 candidate evaluations/second**; 96 creatures reach **192 decisions/second** and **4,608 evaluations/second**. These are arithmetic workload bounds for detailed sensing, not timings. Initial entity collection/sorting, herd queries, navigation, terrain checks and vanilla ticking add separate costs.

Benchmark the unchanged baseline and proposed patch with matched 24/96/192-creature scenes: calm day, calm night, several simultaneous hunts, dense woodland, grouped players and separated player regions. Record median/p95 server tick time, path requests/failures, active hunts and tracked state updates. Compare client frame time with glow off/on and shaders off/on. No credible percentage TPS/FPS claim is possible before those measurements.

Implementation validation should cover scheduled sleep at low fatigue, player/noise wake-up, attack interruption, hunger rates and post-kill satiety, realistic prey detection, fleeing/regrouping/defensive exceptions, boundary transitions, bed/time-command skips, persistence, bird diet profiles and unchanged loaded-chunk counts. Run `gradlew.bat build` and `gradlew.bat runGameTestServer`; run `gradlew.bat runData` for data-provider changes and commit its generated output. Validate imported assets and update Graphify after code changes. The user performs interactive client testing, per `Ark/AGENTS.md`.

Only source/asset inspection, graph queries and web research were performed for this proposal. No gameplay code, runtime assets or configuration was changed; no build, GameTest, performance benchmark or visual playtest was run. The changelog remains unchanged because these mechanics are proposals. After feedback and implementation, record actual results under Unreleased and finish with a dated Nighttime patch entry as required by Standard.md.
