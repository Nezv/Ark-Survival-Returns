# Taming Patch — Implementation Prompt

Implement a complete taming, torpor, and riding system for this dinosaur-survival mod.

**Inspect the repository before editing.** Identify the Minecraft version, mod loader, mappings, entity hierarchy, networking, animation library, existing taming/riding code, and every registered creature. Reuse working systems instead of introducing parallel implementations.

The requirements below define the intended behavior. Numerical values are **initial configurable balance defaults**, not claims about existing code.

## 1. Core gameplay requirements

### Torpor and unconsciousness

- Players and creatures have torpor: a measure of sedation.
- Sedative attacks, projectiles, or consumables increase torpor through one server-authoritative system.
- At maximum torpor, the entity becomes unconscious.
- Torpor gradually decreases after a recovery delay.
- An unconscious entity wakes when torpor falls below its wake threshold.
- Additional sedative maintains unconsciousness but **does not directly advance taming**.
- Apply the same knockout rules to players, but players can never be tamed.
- Unconscious entities cannot move voluntarily, attack, jump, interact, use items, or control mounts. Gravity, knockback, damage, and other external movement must still work.
- Unconsciousness does not grant invulnerability.
- Safely dismount an entity when it becomes unconscious. An airborne unconscious creature loses powered flight and falls according to the existing movement physics.
- Do not use blindness, a frozen animation, or movement-speed reduction as a substitute for actual server-side action restrictions.

Apply torpor to mod creatures, players, and ordinary living mobs through an appropriate shared integration. Explicitly document exclusions for nonliving entities or incompatible special entities.

### Taming

There are three taming methods:

| Method | Eligible creatures | Required behavior |
|---|---|---|
| Passive feeding | Ordinary tameable animals and small herbivores | Feed accepted food repeatedly while awake |
| Knockout feeding | Large herbivores and terrestrial carnivores | Render unconscious, provide accepted food, and keep unconscious until taming completes |
| Hunger-based aerial feeding | Flying creatures | Accept appropriate fish when hungry, even if capable of aggression |

**Flying creatures use the aerial rule rather than the terrestrial carnivore rule.** Do not classify aquatic creatures as aerial. Assign aquatic creatures explicitly during the roster audit.

Taming must require repeated feeding over time, not one interaction or a single stack consumed instantly.

## 2. Creature roster and classification

Find every registered mod creature; expect **40+**, but report the actual count. Do not invent species names or assume that a model file represents a registered creature.

Create a profile for **every registered creature**, including variants with distinct behavior or geometry. No creature may silently fall through to an unsuitable generic profile.

Use this classification as the initial policy:

| Creature group | Taming method | Accepted food category | Preferred food category | Initial target duration* |
|---|---|---|---|---:|
| Ordinary nonpredatory animals | Passive | Species-appropriate plant foods | Species-specific favorite | 60 seconds |
| Small herbivores | Passive | Berries, leaves, or other appropriate plants | Preferred plant food | 90 seconds |
| Medium herbivores | Explicit per-species decision | Appropriate plants | Preferred plant food | 150 seconds |
| Large herbivores | Knockout | Appropriate plants | High-quality plant food | 240 seconds |
| Small terrestrial carnivores | Knockout | Raw meat; fish if appropriate | Preferred meat | 120 seconds |
| Medium terrestrial carnivores | Knockout | Raw meat; fish if appropriate | High-quality meat | 210 seconds |
| Large terrestrial carnivores | Knockout | Raw meat | High-quality meat | 360 seconds |
| Flying creatures | Hunger-based aerial feeding | Fish | Preferred fish | 180 seconds |
| Aquatic creatures | Explicit per-species decision | Species-appropriate food | Preferred food | Set per species |

\*At normal rates, with accepted baseline food and maintained eligibility. Preferred food may shorten this.

Use existing item tags where available. If a proposed food does not exist, select a suitable existing item or tag and document the choice—do not add an unrelated food system.

**Villagers remain non-tameable. Players remain non-tameable.** Neither is part of the rideable creature roster.

Produce this completed roster as project documentation:

| Registry ID | Entity class | Group/size | Taming method | Accepted food tags | Preferred foods | Torpor maximum | Target duration | Ride profile | Animation controller |
|---|---|---|---|---|---|---:|---:|---|---|

## 3. Important variables

Centralize these values in configuration or creature profiles. Use the project's existing configuration approach.

### Torpor

Use normalized torpor units with size-dependent maxima. Sedatives specify potency in the same units.

| Variable | Initial default | Purpose |
|---|---:|---|
| `maxTorpor` — player | 100 | Player knockout threshold |
| `maxTorpor` — small creature | 60 | Small-creature threshold |
| `maxTorpor` — medium creature | 150 | Medium-creature threshold |
| `maxTorpor` — large creature | 350 | Large-creature threshold |
| `maxTorpor` — giant creature | 700 | Giant-creature threshold |
| `wakeThresholdRatio` | 0.20 | Wake below 20% of maximum |
| `torporRecoveryDelay` | 10 seconds | Delay after the latest sedative application |
| `torporRecoveryPerSecond` | 0.5% of maximum | Recovery after the delay |
| `sedativeResistance` | 1.0 | Incoming potency multiplier; lower means more resistant |
| `basicSedativePotency` | 25 | Initial baseline dose |
| `torporSyncInterval` | 5 ticks | Maximum normal interval between relevant client updates |

Clamp torpor to `[0, maxTorpor]`. Refresh the recovery delay on valid sedative application. Do not introduce overdose damage unless the project already specifies it.

### Hunger and taming

Use a creature-specific feeding hunger value: **0 means full; 100 means very hungry**. Do not reuse player hunger or silently replace an existing creature nutrition system.

| Variable | Initial default | Purpose |
|---|---:|---|
| `feedingHunger` | Persisted; initialize wild creatures in a configurable range | Prevent every spawn from behaving identically |
| `feedHungerThreshold` | 40 | Minimum hunger needed to eat |
| `hungerIncreasePerSecond` | 1 | Feeding appetite recovery |
| `hungerReductionPerMeal` | 20 | One meal's satiation |
| `minimumFeedInterval` | 20 seconds | Prevent interaction spam |
| `foodUnitsPerMeal` | 1 item | One successful feeding consumes one item |
| `preferredFoodMultiplier` | 1.5 | Taming progress multiplier |
| `tamingProgress` | 0–100% | Persisted progress |
| `damageProgressPenalty` | 10 percentage points | Penalty when a wild creature being tamed takes damage |
| `passiveProgressGracePeriod` | 120 seconds | Time before abandoned passive/aerial progress decays |
| `passiveProgressDecay` | 1 percentage point per 10 seconds | Decay after the grace period |
| `wakeBeforeCompletion` | Reset progress | Failed knockout attempt |

Derive per-meal progress from the creature's target duration and feeding interval rather than maintaining contradictory independent settings. Do not award progress merely because time passes.

Food must be valid, hunger sufficient, and the correct awake/unconscious condition satisfied **at the moment of consumption**.

### State transitions

Represent consciousness separately from taming status; a tamed creature can still be sedated.

| Current state | Event | Result |
|---|---|---|
| Conscious | Torpor reaches maximum | Unconscious |
| Unconscious | Torpor falls below wake threshold | Conscious |
| Wild, passive/aerial eligible | Successful feeding | Start or advance taming |
| Wild, knockout eligible and unconscious | Eats from taming inventory | Start or advance taming |
| Knockout taming in progress | Wakes early | Reset incomplete taming |
| Any incomplete taming | Takes damage | Apply progress penalty |
| Taming in progress | Progress reaches 100% | Tamed; persist owner |
| Tamed and unconscious | Taming completes | Remain asleep until ordinary recovery |

The player who starts the attempt becomes its claimant. Other players must not steal ownership with the final meal. Define claim expiry for abandoned attempts, and preserve team-sharing behavior if the project already supports it.

## 4. Feeding behavior

### Passive animals and small herbivores

- Feed by interacting with an awake creature while holding accepted food.
- Consume exactly one item only after server validation.
- Respect hunger, feeding interval, interaction distance, and taming eligibility.
- Failed feeding must not consume food.
- Provide feedback distinguishing wrong food, insufficient hunger, cooldown, and ownership restrictions.

### Knockout creatures

- Open the temporary creature inventory while unconscious.
- The claimant deposits food and manually administers sedative through the existing interaction/item system.
- The creature automatically consumes **one accepted food item per eligible feeding opportunity**.
- Sedatives in inventory must not be auto-consumed unless an existing supported mechanic explicitly provides that behavior.
- Incorrect food remains untouched.
- Completion must work even when the claimant is not beside the creature, provided the entity is loaded.

### Flying creatures

- Fish acceptance depends on hunger, not random chance.
- A hungry flying creature may accept fish despite being aggressive.
- Successful feeding clears its attack target against that feeder and grants a configurable **15-second feeding truce**.
- Attacking the creature immediately cancels that truce and applies the normal taming penalty.
- Truce protects only the feeder; do not globally disable the creature's defensive behavior.
- Do not teleport flying creatures to the player or allow feeding at arbitrary distance.
- Use existing landing/approach behavior where available; add only the minimal behavior needed to make normal-range feeding practical.

## 5. Riding for the entire creature roster

Every registered mod creature must have a working ride implementation, including small creatures. Treat this as an intentional gameplay requirement, not an invitation to omit inconvenient species.

Provide shared riding infrastructure with per-creature profiles for:

- Ground movement
- Flight
- Swimming
- Mixed movement where already supported

Only conscious, tamed creatures may be mounted. Respect ownership/team permissions.

Reuse existing saddles or riding equipment if present. Do not make riding impossible by requiring an item the project does not provide.

### Rider placement table

Seat positions must be **explicit per creature**, validated against its model—not one generic bounding-box offset.

Use the following anatomical targets:

| Body plan | Primary seat target | Facing | Placement constraints |
|---|---|---|---|
| Small quadruped | Center of upper back | Forward | Compact pose; do not position at the tail or inside the torso |
| Large quadrupedal herbivore | Upper back immediately behind shoulder girdle | Forward | Clear neck movement and front-leg animation |
| Sauropod | Stable shoulder/upper-back region at neck base | Forward | Do not attach the rider halfway up the moving neck |
| Bipedal carnivore | Back above the hips, slightly forward of pelvis | Forward | Clear tail root and moving thighs |
| Bipedal herbivore | Back above pelvis | Forward | Follow torso posture without sinking into the spine |
| Horned/frilled quadruped | Back behind shoulders and frill | Forward | No intersection with frill during turns |
| Armored or plated creature | Explicit clear saddle pocket on back | Forward | Do not place rider through plates or spikes |
| Flying creature | Dorsal torso near wing roots | Forward | Clear wing strokes; never attach to a wing bone |
| Long-bodied swimmer | Dorsal torso behind head/neck | Forward | Avoid tail-driven oscillation |
| Broad-bodied swimmer | Center of upper torso | Forward | Keep rider clear of fins and flippers |
| Spider-like body | Upper central abdomen/body | Forward | Avoid head and leg joints |

These are placement instructions, **not fabricated coordinates**. Inspect each model and fill in actual offsets.

Create the final seat manifest with:

| Registry ID | Model/bone reference | Local X | Local Y | Local Z | Yaw offset | Rider pose | Ground dismount offset | Alternate dismount offset | Validated |
|---|---|---:|---:|---:|---:|---|---|---|---|

State coordinate units, axes, and origin. Convert model units explicitly where necessary.

Keep gameplay passenger positioning server-authoritative. If animation bones exist only on the client, use them for visual alignment without making server mounting depend on client-only model code.

### Mounted animation requirements

- Idle, walk, run, swim, takeoff, flight, landing, and attack animations must work where the creature actually supports those actions.
- Select locomotion animations from actual movement and movement mode, not only AI navigation state.
- Mounted player control must not leave the creature stuck in its idle animation.
- Prevent movement AI from fighting rider input.
- Unconsciousness overrides locomotion and attack animation.
- Support multiplayer observers, not only the rider's client.
- Do not claim an animation exists without inspecting its assets. Report missing assets separately from broken animation wiring.

Validate collision-safe mounting and dismounting, including ceilings, walls, water, logout, death, and airborne knockout.

## 6. Inventory: reuse the horse interface

**Reuse the vanilla horse inventory screen/menu layout for now. Do not create a custom inventory UI.**

Inspect how the targeted Minecraft version implements horse inventory. Reuse its menu/screen contracts or build the smallest compatible adapter.

- Keep the existing creature inheritance hierarchy; do not force every dinosaur to extend the horse entity solely for its inventory screen.
- Use horse-style equipment slots only for equipment the creature supports.
- Provide a small food/storage area using the existing horse/chest layout.
- Allow the claimant to access an unconscious wild creature's taming inventory.
- Allow owner/team access after taming.
- Reject unauthorized access server-side.
- Revalidate access while the menu remains open; waking, distance changes, death, or removal must not leave stale access.
- Persist inventory and prevent duplication during shift-clicking, disconnects, death, and chunk unload.
- Clearly define what happens to deposited food after a failed taming attempt; it must not disappear silently.

## 7. Important classes and responsibilities

These names describe the intended separation of responsibilities. **Reuse equivalent existing classes and adapt names to repository conventions.**

| Class/component | Responsibility |
|---|---|
| `TorporComponent` | Torpor, recovery delay, unconscious state, serialization |
| `TorporService` | Server-side sedative application and knockout/wake transitions |
| `CreatureTamingProfile` | Food tags, method, hunger settings, duration, size-related balance |
| `CreatureProfileRegistry` | Complete registry-ID-to-profile mapping with validation |
| `TamingComponent` | Progress, claimant, feeding hunger, timestamps, persistent ownership integration |
| `TamingService` | Feeding validation, consumption, progress, failure, completion |
| `UnconsciousBehavior` | Suppress creature AI actions without breaking physics |
| `PlayerUnconsciousHandler` | Enforce unconscious player restrictions on the server |
| `CreatureRideProfile` | Movement capabilities, seat transforms, equipment requirements |
| `CreatureRideController` | Validated rider input and movement-mode handling |
| `CreatureInventory` | Persistent equipment and food/storage slots |
| `CreatureHorseMenu` | Horse-style inventory adapter and access checks |
| `CreatureAnimationBridge` | Map synchronized state and movement to existing animations |

Use the loader's existing component/capability/attachment system where appropriate. Do not maintain authoritative entity state in a global UUID map.

Client requests must express intent—feed, mount, interact—not supply trusted torpor, progress, ownership, position, or inventory changes.

Persist torpor, consciousness, taming progress, claimant, feeding hunger, ownership, and inventory. Define death/respawn behavior explicitly: player death clears sedation; logout/login must not.

Pause simulation while entities are unloaded unless the project already has a deliberate offline-progression system.

## 8. Debugging and code-level verification

Add a focused developer guide explaining how to debug these features with this repository's actual tools and commands.

### Trace these execution paths

Document concrete class names and methods after implementation:

1. Sedative hit → server validation → torpor update → knockout → synchronization.
2. Feeding interaction → food/hunger validation → item consumption → taming progress → ownership.
3. Inventory feeding tick → eligibility → consume one meal → progress.
4. Mount request → permission check → passenger attachment → rider input → movement.
5. Movement/state update → animation selection → rider positioning.
6. Save/load → component restoration → inventory and ownership restoration.

### Development diagnostics

Provide operator-only debug commands, using the project's existing command framework, to:

| Action | Required output/behavior |
|---|---|
| Inspect entity | Registry ID, profile, torpor, consciousness, hunger, method, progress, claimant, owner |
| Set torpor | Exercise knockout and wake transitions through the normal service |
| Set feeding hunger | Reproduce hungry/not-hungry conditions |
| Inspect mount | Controller, movement mode, rider UUID, seat transform |
| Validate roster | Report missing profiles, invalid foods, absent ride configuration |
| Toggle debug logs | Enable focused transition logs without per-tick spam |

Use target selection or entity IDs consistent with existing commands. Never expose mutation commands to ordinary players.

Log state transitions and rejected actions with useful reasons. Do not log every tick.

Explain breakpoints for:

- Food consumed without progress
- Torpor that does not decay
- Creatures acting while unconscious
- Lost ownership after reload
- Rider position clipping
- Mounted creatures playing idle animations
- Client/server disagreement
- Inventory duplication

### Required verification matrix

| Test | Expected result |
|---|---|
| Sedate a player and a creature | Both become genuinely unable to act |
| Apply further sedative | Sleep extends; taming does not increase |
| Let torpor recover | Wake at configured threshold |
| Feed wrong food/full creature | No consumption and no progress |
| Feed a passive small herbivore | Progress increases while awake |
| Feed an awake large herbivore/carnivore | Knockout taming is rejected |
| Feed an unconscious eligible creature | Consumes individual meals over time |
| Wake before taming completes | Incomplete knockout attempt resets |
| Feed a hungry aggressive flyer fish | Food accepted; feeder-specific truce applies |
| Complete taming | Correct persistent owner; no forced premature waking |
| Second player feeds final meal | Ownership is not stolen |
| Ride every registered creature | Controls, seat placement, and available animations work |
| Knock out rider or mount | Safe dismount and correct movement transition |
| Reload chunk/server | State and inventory restored without duplication |
| Reconnect an unconscious player | Sedation cannot be bypassed |
| Observe from a second client | Consciousness, movement, and animations agree |
| Launch a dedicated server | No client-only class loading errors |

Run available automated tests and builds. Use existing test infrastructure for state transitions, feeding timing, access controls, and serialization. Clearly separate automated passes from in-game checks that remain unverified.

## Deliverables

1. Implemented torpor, taming, riding, and horse-style inventory integration.
2. Completed creature profile and rider-seat tables covering the actual full roster.
3. Configurable balance defaults.
4. Tests and a repository-specific debugging guide.
5. A concise summary of changed files, validation results, missing assets, and remaining manual checks.

Do not report the patch as complete if creatures are missing profiles, riding is only implemented for a sample, or mounted animation and seat placement have not been checked. Preserve unrelated work and avoid unrelated feature changes.
