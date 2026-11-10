# Audit, phase 3: recipes and gameplay for two players

Written 2026-11-10 by Claude Fable 5 for the user, to be dispatched plan by plan to an agent (Opus).
Dashboard row: A03. Phase 1 is `Audit/01-optimization-plans.md`, phase 2 `Audit/02-bugs-and-design.md`.

The question of this phase: does the recipe and gameplay logic suit a survival world of two players who used to
play ARK, and what would hold them longer. The answer is two lists: **A**, what to add (section 3), and **B**,
what is broken, unfinished or disappointing (section 4), each entry with a plan (sections 6 and 7).

Nothing in the game was changed and nothing was run in the client or on a server. Read for it: the species
roster, levels and scaling, taming, torpor, companion orders, work, cargo, mass, player levels, the tech tree
and its service, bench crafting, the seven shipped bench designs and the six unshipped ones, the recipe plan
(`tools/workstation_plan.py`), every Ark loot table, supply drops, the beacon and its dragon, accessories, the
kitchen, the journal's 22 quests, the theme policy and the danger zones. Five sweeps were added to
`python Ark/tools/audit_static.py` (`combat`, `sedation`, `yield`, `roles`, `grid`); every number below marked
computed comes from them and can be run again (section 13).

Every finding is marked as in phase 2: **read** (seen in source or data), **computed** (worked out from what was
read), **reasoned** (a consequence nobody has seen in a client; the plan's first step confirms it).

---

## 1. What stands out

1. **The loop ARK players come for has its first and last step missing.** Find a good wild animal, bring it
   down, raise it, put it to work. Here a wild animal is one number, rolled evenly, and the cost of taming is
   blind to it; and a tame is finished the moment it is tamed: no experience, no points, no name, no young
   (read). Two of 41 species have a job and none has an ability of its own (computed, `roles`).
2. **All the cost is in the wrong place.** A level 80 Tyrannosaurus costs 15 narcoberries and six minutes
   (computed, `sedation`); the saddle to ride it is level 33: 259,795 XP, about 1,700 kills (phase 2, BUG-21).
   The hunt is cheap and the grind is long, where ARK is the other way round.
3. **The player does not grow.** A level is a sound and a recipe gate: no points to spend (read). Health stays
   20 and carry stays 100. Armour does not change the number of bites: against most zone 2 predators hide,
   keratin and bronze all leave a player downed in two (computed, `combat`). The player's best blade takes 95
   blows to kill that Tyrannosaurus. All power is in the tame, and the verbs to use it (an attack order, a
   mounted attack) are phase 2 bugs.
4. **The frame is decoration.** The tech tree unlocks nothing (read): every gate is a level. The first boss
   opens nothing (BUG-25). The journal pays fiber and beef. A supply drop lands 32 to 96 blocks from the door.
5. **The first hours are thin and the order is upside down.** Zone 1 has one land predator, at 3 damage; one
   step over the border everything downs a player in two bites (computed). The first animal a player meets and
   tames by hand, the Parasaur, needs a level 12 saddle with bronze, while a Pteranodon saddle is level 6 and
   three hide (read).
6. **What is there is worth building on**, and most plans below reuse it: the wildlife that lives and dies on
   a register, the danger zones, accessories sewn from the parts of a hunt, the downed state, mass and cargo,
   benches as graphs, and animation clips nobody uses yet (roars, howls, calls, tail sweeps, stomps, a shock,
   fire, dung).

---

## 2. What holds two former ARK players

Each entry of the lists names the drives it feeds. They are the six things ARK did to its players, and a
pack for two should do all of them:

| | Drive | In ARK | Here today |
|---|---|---|---|
| **Hunt** | The search for a rare good thing | A level 150, a colour, a blueprint in a drop | Levels rolled evenly, nothing to compare |
| **Growth** | Numbers that go up because I chose them | Stat points, engrams, a tame's levels | A level opens recipes; a tame never changes |
| **Bond** | An animal that is mine, named, raised, missed | Names, imprint, the loss of a tame | No name, no history, a death tells nobody |
| **Purpose** | Always knowing what I want next and why | "I need an Anky for metal" | Two jobs; the tree is a checklist |
| **Tension** | Danger out there, relief at home | The tame under guard, the night, the run for a corpse | Flat zone 1, a cliff after; items gone in 5 min |
| **Duo** | Things that need or reward the second player | One rides and one shoots, the tribe log | Revive; XP shared at a loss (FIX-02) |

---

## 3. List A: what to add

Size: S one agent session, M a few, L a long effort. "Needs" names what must be done first.

| ID | Addition | Feeds | Size | Needs |
|---|---|---|---|---|
| ADD-01 | Tames that grow: experience, levels and points spent by the owner | Growth, Bond | M | BUG-02, BUG-05 |
| ADD-02 | Wild animals worth searching for: stat spread, rarer high levels, taming effectiveness, a way to inspect | Hunt | M | FIX-04 |
| ADD-03 | A role for every species: traits, four jobs, rider abilities on clips that already ship | Purpose, Duo | L (three parts) | BUG-05, BUG-10 |
| ADD-04 | Orders and a roster: attack target, stances, names, where my tames are, a death notice | Bond, Tension | M | BUG-06 |
| ADD-05 | Player attribute points, one a level | Growth, Duo | M | FIX-02 |
| ADD-06 | Taming kit: the bola and a taming HUD | Hunt, Tension | M | BUG-01 |
| ADD-07 | Dossiers and survivor's notes: a collection that pays experience | Purpose, Hunt | M | - |
| ADD-08 | Quality tiers and blueprints for saddles, weapons and armour; saddles armour the mount | Hunt, Growth | L | FIX-10 |
| ADD-09 | Dens, artifacts and tribute; three dragons as three keys | Purpose, Tension, Duo | L (two parts) | BUG-25, BUG-15 |
| ADD-10 | Alpha predators and the Hunter's Moon | Tension, Hunt | M | ADD-08 for the drop |
| ADD-11 | Survivor's cache: what a death and a dead tame leave behind | Tension | S | - |
| ADD-12 | Duo kit: a passenger seat, the tribe log, a partner marker | Duo | M | BUG-07 |
| ADD-13 | Stable pieces: gateway, hitching post, dung and fertilizer | Bond, Purpose | M | BUG-02 |
| ADD-14 | Breeding and raising young | Bond, Hunt, Growth | L | ADD-02, FIX-15 |

## 4. List B: broken, unfinished or disappointing

Severity as in phase 2: A breaks a core loop, B is wrong or unfair in normal play, C is a rough edge.

| ID | Finding | Sev | Known by | Feeds when fixed | Size |
|---|---|---|---|---|---|
| FIX-01 | The tech tree unlocks nothing; every gate is a player level | A | read | Purpose | M |
| FIX-02 | Experience: one real source, a wall, a crafting exploit, no reward, and a duo earns less than two loners | A | read, computed | Growth, Duo | M |
| FIX-03 | Armour does not change a fight with an animal | A | computed | Growth, Tension | S |
| FIX-04 | Taming cost is blind to level and follows height: a Megapithecus sleeps on three arrows | A | read, computed | Hunt, Tension | M |
| FIX-05 | A carcass is a vanilla drop: 0 to 3 hide from a Tyrannosaurus, no trophy from a tame's kill | B | read, computed | Purpose | S |
| FIX-06 | The saddle ladder is upside down: flight at level 6, a Parasaur at 12 with bronze | A | read, computed | Purpose | S |
| FIX-07 | 130 items have no bench: bread, paper, glass panes, metal blocks; a village crafting table is the only way | B | computed | Purpose | S |
| FIX-08 | Recipe defects one by one (16) | B | read | Purpose | S |
| FIX-09 | The danger curve is a cliff: nothing in zone 1, two bites in zone 2 | B | computed | Tension | S |
| FIX-10 | Supply drops are deliveries, not expeditions | B | read | Hunt, Tension, Duo | M |
| FIX-11 | The boss has no before and no after; three dragon colours are one fight | B | read | Purpose | S (the short version of ADD-09) |
| FIX-12 | Accessories cost nothing: +40 % carry for six twigs and a hide at level 0 | C | computed | Growth | S |
| FIX-13 | The journal pays in fiber and beef, and five quests complete on a click | C | read | Purpose, Duo | S |
| FIX-14 | Carry weight never grows and a block weighs 1 of 100 | B | read, computed | Growth | S |
| FIX-15 | The homestead has no pull: trough, two meals, crops, eggs | C | read, computed | Bond, Purpose | M |

Phase 2 findings this phase leans on and does not repeat: BUG-01 (the sleep is too short), BUG-05 (a mount cannot
attack), BUG-07 (the party nobody is told to make), BUG-16 (nest eggs, map unlock), BUG-17 (sea and air taming),
BUG-21 (the level gates), BUG-23 (the trough), BUG-25 and BUG-15 (the boss rewards).

---

## 5. Rules for whoever executes a plan

The rules of phase 1, section 4, and phase 2, section 4, hold. Five more, because these are design changes:

1. **Every number is a first value and lives in the server config** (`Config.java`), named in the plan. The
   user tunes without a build. A default that changes today's behaviour is said so in the Dashboard row.
2. **A plan with a sweep states its target in the sweep.** Run the named `audit_static.py` sweep before and
   after; the two outputs go in the commit message. If the sweep cannot show the change, extend the sweep.
3. **A decision marked "user" in section 9 is answered before the plan starts.** Do not choose for them; if the
   answer is missing, do the part that does not depend on it and stop.
4. Data goes through the providers and `./gradlew runData`; recipes on a bench go through the design files in
   `Ark/design/workstations` and `python tools/build_workstation_data.py`, never by hand in generated files.
   Player-facing text is English only.
5. A plan that needs a phase 2 bug fixed says so in "Needs". Do not start it on top of the bug: the tests
   would set the state they should reach (type T10).

Paths below are relative to `Ark/src/main/java/dev/nez/arksurvivalreturns/` unless they start with `Ark/`.
New Dashboard rows take the next free ID of their type at the time (today F41, P26, C08, X09, B11).

---

## 6. The plans of list A

### ADD-01 Tames that grow

**Today (read).** `CreatureEntity.initializeLevel` (feature/creature/CreatureEntity.java:630) sets health and
damage once, at spawn. `onTamed` changes no attribute and nothing gives a tame experience; its kill pays its
owner (`LevelEvents.credit`). A tame at day 100 is the animal of the minute it was tamed.

**Change.** A tame earns experience and gains up to 25 levels over the level it was tamed at; each gained
level is a point its owner spends.

| | First value | Config key (`tames.`) |
|---|---|---|
| Experience | its own kill 5 x victim level; a fifth of what its owner earns within 32 blocks; 1 per work action | `xpKill`, `xpOwnerShare`, `xpWork` |
| Next level | `60 * (gained + 1) ^ 1.2` | `xpBase`, `xpExponent` |
| Cap | 25 gained levels | `maxGainedLevels` |
| Health point | +5 % of the health it was tamed with | `healthPerPoint` |
| Damage point | +4 % | `damagePerPoint` |
| Carry point | +6 % cargo capacity | `carryPerPoint` |
| Speed point | +1.5 %, ten points at most | `speedPerPoint`, `speedMaxPoints` |

**Steps.**
1. Unit test of the curve and of the four formulas (no game).
2. `TamingState` gains `xp`, `gained`, `spent[4]` (saved and synced to trackers with the rest). `applyTameState`
   puts four attribute modifiers with fixed ids on the creature from `spent`; cargo capacity reads the carry
   points in `MassService.creatureCapacity`.
3. `LevelEvents.killed`: when the killer is a tame, pay the tame too. `ArkLevels.addXp`: after the owner's
   award, pay the share to the owner's tames in range. `WorkGoal` pays on a harvest.
4. `CreatureMountScreen`: a second tab, Stats: level and gained levels, an experience bar, four rows with the
   value and a `+` button. `TamePointPayload(entity, stat)`: the server checks owner or tribe (COMMANDS
   permission), range and a free point.
5. Nameplate (F35): `Lv 32 +5`. A gained level plays a note over the tame and tells the owner in the action bar.
6. GameTest: tame at level 10, credit three levels, spend two on health, save and reload: max health matches;
   a player outside the tribe is refused.

**Done when.** The test passes and one tame has been levelled in a client. **Dashboard.** P19 rewritten, a new
Feature row. **Needs.** BUG-02 (both touch `onTamed`), BUG-05 or ADD-04 (or a tame never kills).
**Risks.** With ADD-02 a tame has two sources of points: keep one `spent` array and one place that applies it.

### ADD-02 Wild animals worth searching for

**Today (read).** `rollLevel` (CreatureEntity.java:638) draws evenly between the zone's ends. `LevelScaling`
makes health and damage from that one number, so two level 40 Tyrannosaurs are the same animal.
`TamingService.complete` (line 177) gives the same tame whether it was fed its favourite food untouched or
plain food while being hit. The nameplate cannot show a wild animal's taming progress (F35, open).

**Change.** Four things, each small:
- **Rarer high levels.** Level = `min + floor((max - min + 1) * r^2)`: the top tenth of a zone's range is one
  animal in twenty instead of one in ten (`levels.rollExponent`, 2.0; 1.0 is today).
- **A spread.** The `level - 1` points are dealt to health, damage, carry and speed by a draw seeded from the
  animal's UUID (weights 35, 30, 15, 20). Each stat is `base * (1 + k * points ^ 0.85)` with `k` set so that
  the average deal gives today's health and damage within 5 % (unit test). Nothing new is saved for a wild
  animal: the deal is recomputed from the UUID.
- **Taming effectiveness.** Starts at 100. Loses half a point for each percent of its health taken by
  anything that is not a sedative, and 2 for each meal that is not the favourite food. On completion the tame
  gains `floor(level * 0.4 * effectiveness / 100)` bonus levels, dealt like the rest and saved
  (`taming.effectivenessBonus`, 0.4).
- **A way to look.** The nameplate shows the level, as today. Holding a Spyglass, or wearing the Argentavis
  Totem ("keen eye"), adds four small bars for the deal. A tame always shows them in its Stats tab (ADD-01).

**Steps.** Unit tests for the roll, the deal and the scale; `CreatureEntity`: deal on spawn and on load,
attributes from the deal; `TamingState`: effectiveness and the bonus deal; `CreatureInfoHud`: the bars and the
effectiveness of an attempt in progress; GameTest: two animals of one level differ, a clean tame of a level 40
gains 16 levels and a tame hit twice gains fewer.
**Done when.** Tests pass; `/arkwildlife` lists levels of 200 spawned animals and the top tenth is near 5 %.
**Dashboard.** P17, P19, a new Feature row. **Needs.** FIX-04 (cost must rise with level, or the best animal
is still free). **Risks.** `SilentLife` sums strength from level (feature/spawn/SilentLife.java:273): leave it
on the level. `tools/existence_model.js` reads level only: unchanged.

### ADD-03 A role for every species

**Today (computed, `roles`).** 41 species; two have a job (Triceratops forages, Ankylosaurus mines:
feature/work/WorkProfiles.java); every other tame can follow, stay, wander, carry and be ridden. No species
has an ability: `strike` is one melee hit for all (CreatureEntity.java:856). The clips for more are already in
`assets/arksurvivalreturns/geckolib/animations`: `Rex-Roar`, `Giganotosaurus-Roar`, `Acro_Attack_Roar`,
`Allosaurus-Roar_Anim`, `Ceratosaurus_Roar1`, `Spino-Roar`, `Direwolf-Howl`, `CaveWolf-Howl`, `Raptor-Call`,
`Para-Roar-Alert`, `Mammoth_new_Call`, `Ankylo-Attack-Tail-Sweep`, `Sauropod-Attack-FootStomp`,
`Titanosaur-Attack-Footstomp`, `Paraceratherium-Attack-Footstomp`, `Cnidaria-Shock`, `Dragon-Fly-Attack-Fire`,
`Liopleurodon-Spin`.

**Change.** One table, `feature/role/SpeciesRoles.java`, written like `CargoProfiles`, with a config switch per
kind. Three parts, each a plan of its own:

**Part A, traits (S to M).** No key, no screen:

| Species | Trait |
|---|---|
| Every predator | Its kills give half as much meat and hide again |
| Sabertooth, Direwolf, Ravager | Twice the hide from their kills |
| Argentavis | Ores and ingots in its hold weigh half |
| Mammoth, Paraceratherium | Logs and planks weigh a quarter |
| Brontosaurus, Titanosaur | Stone and cobblestone weigh a quarter |
| Titanoboa, Cnidaria | Their bite adds torpor (25 and 15): a living tranquilizer |
| Lystrosaurus | Tribe members within 16 blocks earn 15 % more experience |
| Parasaur, on STAY | Calls when a wild predator comes within 40 blocks and outlines it for the tribe for 10 s |
| Unicorn | Rider and tribe within 8 blocks lose poison; tames there mend slowly |
| Pegomastax, on WANDER | Puts a berry in its hold every two minutes near grass |

**Part B, jobs (M).** `WorkProfiles.Job` gains WOOD (Mammoth, Therizinosaurus: fells natural trees with the
tree test of `TreeTrample`, logs to the hold, the sapling stays), FISH (Sarco, Kaprosuchus, Spinosaurus beside
water: a raw fish per interval, the food every flyer is tamed with), and FORAGE for Brontosaurus and
Paraceratherium at twice the radius. **Needs** BUG-10 (the WORK order stalls).

**Part C, rider abilities (M).** A key, Mount Ability (default R), on the payload path BUG-05 adds for the
mounted attack; the server checks controller and cooldown, the HUD shows the cooldown as a ring.

| Ability | Species | Effect (first values) |
|---|---|---|
| Roar | Tyrannosaurus, Giganotosaurus, Acrocanthosaurus, Allosaurus, Ceratosaurus, Spinosaurus | Wild animals of lower base health within 16 blocks flee for 8 s (the flight of B10); 45 s |
| Rally | Direwolf, Ravager, Velociraptor, Mammoth | Tribe tames within 16 blocks: +25 % damage and speed for 15 s; 60 s |
| Sweep, stomp | Ankylosaurus; Brontosaurus, Titanosaur, Paraceratherium | Hits everything in the front arc within 5 or 8 blocks, with knockback; 10 s |
| Fire | Dragon | A cone of 12 blocks, burning; 20 s |

**Steps for each part.** A GameTest per trait, job or ability; the showcase creature card gains the role line
(`Ark/design/showcase/species.json`, `tools/build_showcase.py`); `audit_static.py roles` reads the new table
and must end with "41 species; N have a job; M have an ability".
**Dashboard.** P03 (jobs), P19, a new Feature row for abilities. **Risks.** The Roar must not move a guardian
or an animal in torpor.

### ADD-04 Orders and a roster

**Today (read).** `CompanionGoal.threat` (feature/companion/CompanionGoal.java) knows two targets: whatever hit
the tame, and whatever hit its owner within 16 blocks. There is no order to attack and no stance. A tame has
no name of its own: a name tag needs paper, which no bench makes (FIX-07). A tame left behind cannot be found
and its death tells nobody (phase 2, section 8).

**Change.**
- The command wheel (F22) grows from six to eight: **Attack** (the creature under the crosshair within 48
  blocks becomes the target of the tames in whistle range) and **Stance**, which cycles Passive (never
  fights), Neutral (today) and Aggressive (also attacks any wild predator within 12 blocks).
- A name field at the top of the mount screen sets the custom name; no item needed.
- A **Tames** tab in the Tribe Chronicle: every tame of the tribe with species, name, level, order, health and
  its last known place (block position and the direction from the viewer). `TribeTameData` (saved) is written
  when a tame is tamed, every 5 seconds while loaded, when its chunk unloads and when it dies.
- A tame's death is told to the tribe in chat with its name, its killer and the place.

**Steps.** GameTests: an Attack order on a wild animal 30 blocks off makes the tame close and strike; a
Passive tame hit by a wild animal does not answer; an Aggressive one attacks a wild predator that has not hit
anyone; the roster keeps a tame whose chunk is unloaded; the death line reaches a party member.
**Dashboard.** F22, P01, P19. **Needs.** BUG-06 (ally rules, or an Aggressive tame attacks the partner's).
**Risks.** Aggressive tames near a base empty the land: they attack only what is within 12 blocks and return to
their anchor.

### ADD-05 Player attribute points

**Today (read).** `ArkLevels.award` (feature/levels/ArkLevels.java) raises the level, shows a line and plays a
sound. A level is used only by `ArkLevels.requires` at a bench.

**Change.** One point a level, fifty in all, spent in a small panel of the Ark inventory (F38):

| Attribute | A point gives | Most |
|---|---|---|
| Health | +2 health | 20 points |
| Carry | +10 capacity (of 100) | 20 |
| Melee | +4 % damage dealt in melee | 15 |
| Fortitude | 3 % less torpor and poison received, +2 s of downed window | 10 |
| Lungs | +15 % air, +3 % swim speed | 10 |

Fifty points cannot fill 75: the two players end up different (one carries and takes blows, one hunts).
A Clear Mind Tonic on the Medicine Bench gives the points back.

**Steps.** `ArkProgress` gains `spent[5]` (saved, synced to its owner); modifiers applied on login, respawn and
spend; `MassRules.playerCapacity` and `DownedPolicy` read their attribute; `AttributePointPayload`; the level-up
line becomes a toast that names what the level opened and that a point is free; GameTest for spend, cap, save
and the tonic. Add a `points` column to `audit_static.py combat` (bites to down with ten points of health).
**Dashboard.** F19, F38. **Needs.** FIX-02, or points arrive at the pace of today's wall.

### ADD-06 Taming kit: the bola and a taming HUD

**Today (read).** The mount screen's data slots carry progress, hunger, torpor and its ceiling
(feature/taming/CreatureMountMenu.java:40); they are seen only with the screen open, on an animal already
down. A player watching a knocked-out animal sees none of it. Nothing holds a small fast animal still, and a
flyer is fed on its nest a few seconds at a time (BUG-17).

**Change.**
- **Bola** (Armoury, level 2: 3 rocks, 10 fiber, 1 hide; stacks to 8). Thrown. A small or medium creature
  (`CreatureSize`) it hits cannot move for 20 seconds and can still bite what stands in reach; a flyer it hits
  comes to the ground for as long. Larger animals shrug it off.
- **Taming HUD.** Under the nameplate of an animal the viewer or the viewer's tribe has claimed, or that lies
  unconscious: torpor with the seconds left before it wakes, the seconds to the next meal, progress,
  effectiveness (ADD-02), what it eats and its favourite as item icons.

**Steps.** First find out what of `TamingState` and `TorporState` a tracking client already receives (phase 2
notes that every wild creature syncs its taming attachment about once a second) and send the rest only to the
claimant's tribe. `BolaItem`, `BolaEntity`, a root that is an attribute modifier plus a refused `travel` for
flyers; GameTests: a Velociraptor is held, a Triceratops is not, a Pteranodon lands.
**Done when.** A second player standing guard can read, without opening anything, when the animal wakes.
**Dashboard.** P19, P20, F35. **Needs.** BUG-01. **Decision 9.6** decides whether flyers become knock-out
tames once the bola exists (ARK's route: bola, then arrows).

### ADD-07 Dossiers and survivor's notes

**Today (read).** The only collection is the tech tree. C04 removed the vanilla advancements and Ark has no
tab of its own. The showcase already holds a portrait and the facts of every species
(`Ark/design/showcase/species.json`).

**Change.**
- **Dossiers**, a tab of the Tribe Chronicle. Each of the 41 species has three stages: seen within 16 blocks
  (a silhouette and its name), killed (what it drops and what its parts make), tamed (how it is tamed, its
  favourite food, its saddle level, its role). Each stage pays experience once per tribe: 25, 50 and 100, so
  7,175 XP for all 41, earned by going out, not by repeating.
- **Survivor's notes.** A consumable page in the chests of the vanilla structures that are left (shipwrecks,
  ruins, pyramids, jungle temples, igloos, mineshafts, buried treasure, villages), by a loot modifier. Reading
  one pays 300 to 1,000 XP, counts toward "notes found N of 30" and names the direction of the nearest beacon
  or den (ADD-09).

**Steps.** `DossierData` per tribe (keyed as BUG-14 decides); triggers from the nameplate's target, from
`LivingDeathEvent` and from `applyTameEffects`; the tab in `client/tech`; portraits shipped as 128 px textures
by `tools/build_showcase.py`; a GameTest for each stage and for the party share.
**Dashboard.** F01, a new Feature row. **Risks.** Portrait textures add to the jar: 41 x 128 px is about 1 MB.

### ADD-08 Quality tiers and blueprints

**Today (read).** F20 is under discussion. Every crafted item is the same item. A saddle is a key: it lets a
player ride and does nothing else. Bench crafts are instant and have no outcome but success
(feature/station/WorkstationCrafting.java).

**Change.** Six tiers (Primitive, Ramshackle, Apprentice, Journeyman, Mastercraft, Ascendant) as a data
component on weapons, armour and saddles: a multiplier on damage or armour (1.0 to 1.6) and on durability
(1.0 to 2.5), a coloured name and a tooltip line. **A saddle armours its mount**: hide 10 %, bronze 20 %, iron
30 % less damage taken, times the tier. A **blueprint** is an item that names a craft and a tier; a bench that
has that craft makes it from the blueprint at 1.5 times the cost for each tier above Primitive, without the
level gate. Blueprints come from supply drops by colour (FIX-10), the beacon crate, alphas (ADD-10) and dens
(ADD-09); nothing crafts one.

**Steps.** The component and its codec; attribute modifiers written at craft time; the mount's damage rule in
`CreatureEntity.hurtServer`; a blueprint slot or a "from blueprint" row in `WorkstationView`; loot functions
`set_quality` and `blueprint`; the four supply tables and `chests/sky_beacon` rewritten; GameTests.
**Dashboard.** F20 (decided and rewritten), F28, F32. **Needs.** FIX-10 for the drops to be worth the walk.
**Risks.** The bench payload names a craft by key: a blueprint craft needs the blueprint's slot checked on the
server, or a client crafts any tier.

### ADD-09 Dens, artifacts and tribute: three dragons as three keys

**Today (read).** A beacon stands every 512 blocks or so in three colours that are "visual variants, not
difficulty tiers" (`Ark/docs/first-guardian.md`). Its dragon comes when a player climbs in; it has 400 health
whoever comes (`guardian.healthPerExtraPlayer` is not used by it). Its reward flag is read by nothing (BUG-25)
and the Allosaur Heart has no use (BUG-15). Caves hold spiders and nothing else (`ThemePolicy.REMOVED_ENTITIES`).
P09, more bosses, waits for a mod that has no 26.1 build (I12).

**Change, part 1 (M): the beacons become the three bosses.**
- The colour follows the zone the beacon stands in: red in zone 1, white in zone 2, black in zone 3.
- Health and damage by colour and by the players in the fight: red 400 and 0.6 of the dragon's damage, white
  900 and 1.0, black 1,800 and 1.4; +50 % health for the second player (`guardian.<colour>.*`).
- The dragon comes for a **tribute** laid in the bowl beside the nest, not for the climb alone:
  red: 2 sickle claws, 1 giant antler, 1 Allosaur Heart; white: 4 croc scutes, 4 thick pelts, 1 megalodon
  tooth, the Artifact of the Hunter; black: 3 tyrant teeth, 1 unicorn horn, 4 quetzal feathers and all three
  artifacts. Trophies get a second purpose and the heart its first.
- The reward is a key: each colour drops its Schematic once per tribe, and a tech gate asks for it (FIX-01).
  Which gate is the user's (9.3); proposed: red for the second half of the Bronze Age, white for the Iron Age,
  black for the last node and the Dragon saddle.

**Change, part 2 (L): dens.** Small structures written by a tool like the beacons
(`tools/build_sky_beacons.py` is the model): a raptor hollow (zones 2 and 3), a wreck on the sea bed held by
Megalodons, an eyrie on a peak held by Argentavis. Each has a Loot Crate with an artifact (Hunter, Deep, Sky)
and a blueprint, and its guards come with the chunk at the top level of the zone, as the beacon brings its
dragon. One den per 384 blocks or so.

**Steps, part 1.** `SkyBeaconStructure`: colour from `ProgressionData.dangerAt`; `GuardianDragonEntity`: stats
by colour and participants, reward by colour; an offering block entity in the template
(`tools/build_sky_beacons.py`); the Guardian chapter rewritten (FIX-13); GameTests for a refused and an
accepted tribute and for the three rewards. **Dashboard.** P05, P09, I12. **Needs.** The answers to 9.3.
**Risks.** Beacons generated before the change keep their colour: decide by position at wake time, not at
generation.

### ADD-10 Alpha predators and the Hunter's Moon

**Today (read).** Every wild animal of a species and level is the same. Nights differ from days by who is
awake (`nighttime.*`); no night differs from another.

**Change.**
- **Alpha.** One wild predator in sixty in zones 2 and 3 is an alpha (`wildlife.alphaChance`): four times the
  health, half as much damage again, immune to sedatives, the hunting red eyes by day, "Alpha" on its
  nameplate. It drops two of its trophy, six prime meat, a blueprint (ADD-08) and ten times the experience.
  The flag is saved on its register record so it survives unloading.
- **Hunter's Moon.** One night in eight (`nighttime.huntersMoonEvery`), told at dusk: the hunters' night
  hunger and sight multipliers rise by half and they do not sleep until dawn. A night to stay in, or to hunt.

**Steps.** `WildlifeRegister.Life` gains the flag; `CreatureEntity` applies it after the level; loot through a
`LivingDropsEvent` like `TrophyDrops`; the moon as saved data and a dusk message; GameTests.
**Dashboard.** P17, P00, a new Feature row. **Risks.** `BoundedLife` hunts by strength: an alpha counts as its
species there.

### ADD-11 Survivor's cache

**Today (read).** A real death drops the items as vanilla does: they lie five minutes. A dead tame drops its
saddle and its hold the same way.

**Change.** A death leaves a Survivor's Cache at the spot (the Loot Crate's block in its own paint) holding
everything, for three game days (`camp.deathCacheDays`), with the supply drop's beam and a chat line to the
tribe with the place. Only the owner and the tribe open it. A tame's death leaves one too.
**Steps.** `LivingDropsEvent` for players and tames collects the drops into the block entity (a `LootCrate`
with an owner and a tribe); placement finds the nearest free block above solid ground or on water; GameTest.
**Dashboard.** P02, F32. **Risks.** Death in lava or the void: place the cache at the last safe position.

### ADD-12 Duo kit

**Today (read).** A mount carries one player. The tribe has no log. A partner is found by the map only.

**Change.**
- **Passenger seat** on every mount of size large or giant: a second player sits behind the rider and can use
  a bow and items. `CreatureSeats` gains a second measured seat per large species
  (`tools/build_taming_manifests.py` measures the first).
- **Tribe log**, a tab of the Chronicle: tamed, died, downed, revived, a boss, a gate, each with who, where and
  when (the last 200 lines, saved per tribe).
- **Partner marker.** An arrow at the edge of the screen with the distance when the partner is more than 32
  blocks away; red and pulsing while they are downed.

**Steps.** `canAddPassenger` and `positionRider` for seat two; the passenger takes no control; GameTest with
two mock players. `TribeLogData`; the HUD marker reads a position payload sent every second to party members
in the same dimension. **Dashboard.** P01, P19. **Needs.** BUG-07.
**Risks.** Better Combat on a passenger: check the swing is allowed or blocked on purpose.

### ADD-13 Stable pieces

**Today (read).** A vanilla gate is one block wide and a Triceratops five. Nothing ties a tame to a place but
the STAY order. A base with tames looks like a parking lot: BUG-02 leaves them in the pose of their taming.
Seven species ship a dung clip (`Direwolf-Poop`, `Mammoth-Poop`, `Stag-Poop`, `Paraceratherium-Poop`,
`CaveWolf-Poop`, `Saber-Poop`, `Equus-Poop`) that nothing plays.

**Change.**
- **Gateway**, a block of logs, planks and fiber that fills an opening 3 x 4 or 5 x 6 and opens for the tribe.
- **Hitching post.** Up to four tames bound to it keep within 8 blocks, lie down at night and when idle
  (X06 clips), and are fed by a trough in range first.
- **Dung.** A tame on STAY or at a post drops Dung now and then (every 10 minutes for a large animal). Dung
  and fiber make Fertilizer on the Working Station: bone meal that works on Ark berry bushes three stages at
  once.

**Steps.** Two block entities and their models (`tools/build_camp_assets.py`); the post's rule in
`CompanionGoal`; dung in `CompanionGoal`'s idle step with the clip where it exists; GameTests.
**Dashboard.** P04, P19, a new Feature row. **Needs.** BUG-02.

### ADD-14 Breeding and raising young

**Today (read).** P08 is under discussion. `TribePermission.BREEDING` exists and nothing calls it. A nest egg
hatches nothing (BUG-16). The register lets wild animals have young, full-grown (C05).

**Change (the short form of P08).** Sex from the UUID, shown on the nameplate. Two tames of one species and
opposite sex on WANDER within 8 blocks of each other and of a filled trough mate after ten minutes; the female
lays an egg (every species, for simplicity) once a day at most. An egg on a nest block within 4 blocks of a
lit Stone Fire hatches in a day. The young is the adult model at a third of its size, grows in three days,
eats from the trough and dies of hunger without it. Each stat takes the better parent's points with odds of
70 %; one young in twenty is a mutation (two more points in one stat and the next colour variant). Three
requests while it grows (a pet, a berry, a walk) add 5 % to every stat each.

**Steps.** Split in three: mating and the egg; hatching and growth (scale on the GeckoLib model and on the
hitbox); inheritance and imprint. **Dashboard.** P08 rewritten. **Needs.** ADD-02 (there must be stats to
inherit), FIX-15 (the trough and hunger must mean something), decision 9.8. **Risks.** A hitbox that grows
inside a building; a population of tames nobody caps (`tames.maxPerTribe`).

---

## 7. The plans of list B

### FIX-01 The tech tree unlocks nothing

**Evidence (read).** `TechService.evaluate` (feature/tech/TechService.java) marks a node complete, mirrors it
to FTB Quests, pays 50 XP and writes a chat line. No class outside `feature/tech` and the tests reads
`TechProgressData`. `WorkstationCrafting.craft` asks one thing, `ArkLevels.requires(player, level)`. So the
Primitive Forge is a level 0 craft whatever the tree says, bronze opens at level 12 whether or not the Hell
Forge gate was passed, and the 20 Iron nodes guard nothing.

**Change.** The ages gate, the levels pace. A bench category may name a node; the craft is refused until the
tribe has it.

**Steps.**
1. GameTest: a level 50 player in a fresh tribe is refused a Bronze Pickaxe. Fails today.
2. `WorkstationDefinition`: a category and an item accept `"node": "<id>"`. `WorkstationCrafting.craft` checks
   `TechService.completed(tribe, node)`; the screen dims the category with "Needs: <node title>".
3. Design files: the Primitive Forge asks for the four lane ends of its age (`lasting`, `scavenge`,
   `narcotics`, `armoured`); `armoury/bronze`, the Smithing Table, the Medicine Bench's recipes and the bronze
   saddles ask for `shiny` (bronze smelted); the steel categories and the three machines ask for `steel`.
4. `tree.json`: each node gains a `reward` (experience by kind: side 50, main 100, gate 500; a gate may also
   give an item or a blueprint). The service pays it instead of the flat 50.
5. `python tools/build_workstation_data.py`, `./gradlew runData`.

**Done when.** The test passes and the Chronicle shows what a gate opens. **Dashboard.** F01, B01, P14. **Needs.**
BUG-01 and BUG-10: `scavenge` asks for a tame that works, and until both are fixed a real gate would close the
Bronze Age. Decision 9.5. **Risks.** A world in progress loses access to crafts its tribe has not gated: grant
every node whose trigger is already satisfied on first load (the periodic `scan` does this for collect and craft
triggers).

### FIX-02 Experience: one source, a wall, an exploit, no reward

**Evidence (read, computed).**
- Sources (`Config.java`, playerLevels): a kill 5 x level, a tame 10 x level, a first craft 10, a tech node 50,
  a boss 500. Nothing for gathering, building, exploring or crafting a second time.
- The wall (BUG-21): level 8 is 8,408 XP, level 33 is 259,795.
- The exploit: a first craft pays per recipe id, and every wood and colour variant is its own id
  (`WorkstationCrafting.craft`). The benches hold 743 crafts, 426 of them at level 0: one oak button, one birch
  button and so on pay 4,260 XP, level 5 and most of 6, for a few logs. A zone 1 kill pays about 32.
- No reward: `ArkLevels.award` shows a line and plays a sound.
- The duo: a kill pays the killer in full and a party member within 64 blocks a quarter
  (`playerLevels.tribeShare` 0.25). Two players hunting together earn 62 % each of what one earns alone.

**Change.**

| | Today | First value |
|---|---|---|
| `tribeShare` | 0.25 | 1.0: hunting together is never slower than apart |
| `exponent` | 1.5 | 1.15: level 8 is 4,623 XP, level 15 16,851, level 33 88,392, level 48 195,850 |
| Kill | 5 x level | 5 x level, twice for a predator |
| Tame | 10 x level | 30 x level, times the effectiveness (ADD-02) |
| First craft | 10 per recipe id | 10 per family (one for all planks), and 1 to 4 for every later craft by its level |
| Gathering | none | 1 per ore block mined, 1 per 8 logs or stone |
| Tech node | 50 | by kind (FIX-01) |
| Boss | 500 | 5,000 |
| Dossiers, notes | none | ADD-07 |

A level-up shows what the level opened and gives the attribute point of ADD-05.

**Steps.** `audit_static.py levels` already prints the wall: add the sources as a small budget ("XP in a first
evening: 40 kills, 3 tames, 60 crafts, 12 nodes") and keep both outputs. Config defaults; the craft family key
from `WorkstationDefinition.Entry.family()`; `BlockEvent.BreakEvent` for gathering with a per-chunk cap
against placed blocks (`WorkProtection` knows placed blocks); GameTests for the share and the family rule.
**Done when.** The sweep's budget reaches level 8 in a first evening and the user has agreed the pace (9.1).
**Dashboard.** F19. **Risks.** An existing world's players jump levels when the exponent drops: say so in the
row, it is wanted.

### FIX-03 Armour does not change a fight with an animal

**Evidence (computed, `combat`).** Vanilla armour wears off against a hard hit
(`CombatRules.getDamageAfterAbsorb`): 13 points of bronze stop 12 % of a 20 damage bite. Bites to down a
20 health player, lowest to highest level of the zone:

| Zone 2 | Bite | None | Hide (7) | Keratin (8) | Bronze (13) | Diamond (20) |
|---|---|---|---|---|---|---|
| Velociraptor | 6.9 to 9.9 | 3-3 | 4-3 | 4-3 | 5-3 | 11-7 |
| Direwolf | 11.0 to 15.8 | 2-2 | 2-2 | 3-2 | 3-2 | 6-4 |
| Carnotaurus | 13.7 to 19.8 | 2-2 | 2-2 | 2-2 | 2-2 | 5-3 |
| **Zone 3** | | | | | | |
| Tyrannosaurus | 24.9 to 31.4 | 1-dead | 1-1 | 1-1 | 1-1 | 2-2 |
| Giganotosaurus | 30.3 to 38.2 | dead | 1-dead | 1-dead | 1-dead | 2-1 |

"Dead" is a hit of 30 or more, which skips the downed state (`downed.overkillMultiplier`). Hide, keratin and
bronze are one tier in practice, and 34 kills of hide (FIX-05) buy nothing. Gold (11 points, level 18 to 20)
comes after bronze (13, level 13 to 15) and is worse. There is no iron armour.

**Change.** Damage dealt by a `CreatureEntity` to a player ignores vanilla's rule and is cut by
`min(0.8, armour * 0.035)`: hide 24 %, keratin 28 % (raise it to 10 points: 35 %), bronze 45 %, steel (15 to
17) 55 %, diamond 70 % (`combat.armourPerPoint`, `combat.armourCap`). Overkill counts only hits of twice the
player's maximum health.

**Steps.** A damage type tag `arksurvivalreturns:creature_strike` that bypasses armour, set in
`CreatureEntity.doHurtTarget`; the cut applied in a `LivingIncomingDamageEvent` handler that reads the armour
attribute; the armour still loses durability (`hurtArmor`); `audit_static.py combat` gains the new rule beside
the old. Target, bare to diamond: a zone 2 Direwolf goes from 2-2, 2-2, 3-2, 3-2, 6-4 bites to 2-2, 3-2, 3-2,
4-3, 7-5, and in bronze with ten points of health (ADD-05) to 6-4; a zone 3 Tyrannosaurus in bronze from 1-1
to 2-2, and to 3-2 with those points. Armour and health together make the difference; neither does alone.
**Dashboard.** P13, P17. **Risks.** The Amber Amulet and the downed handler run on the mitigated damage:
keep their order (`DownedHandler.mitigated` is LOWEST).

### FIX-04 Taming cost is blind to level and follows height

**Evidence (read, computed, `sedation`).** `TorporService.maximumFor` (feature/taming/TorporService.java:64)
takes the ceiling from the size band and the band from the hitbox height (`CreatureSize.of`): 60, 150, 350 or
700, at level 1 as at level 80. Feeding time is the profile's seconds, also blind to level.

| | Ceiling | Arrows | Narcoberries | Feeding | Health |
|---|---|---|---|---|---|
| Sabertooth | 60 | 1 | 2 | 120 s | 91 to 306 |
| Megapithecus | 150 | 3 | 4 | 210 s | 513 to 918 |
| Tusoteuthis | 150 | 3 | 4 | 360 s | 428 to 765 |
| Tyrannosaurus | 700 | 12 | 15 | 360 s | 371 to 663 |
| Titanosaur | 700 | 19 | 24 | 240 s | 542 to 969 |

Low long animals fall in a small band: an ape of 900 health sleeps on three arrows. A wild Dragon is tamed by
hand with six salmon (`TamingMethod.AERIAL`, 180 s). The best animal of the game costs what the worst of its
species does, so there is one right choice, the highest level in sight, and no price for it.

**Change.** Ceiling = `4 * base health * (1 + 0.03 * (level - 1))`; the profile's resistance stays. Feeding
time = profile seconds `* (1 + 0.01 * (level - 1))`. First values in `taming.torporPerHealth`,
`taming.torporPerLevel`, `taming.secondsPerLevel`.

| Arrows at level | 1 | 8 | 32 | 80 |
|---|---|---|---|---|
| Velociraptor | 2 | 3 | 4 | 6 |
| Sabertooth | 4 | 4 | 7 | 11 |
| Triceratops | 5 | 6 | 10 | 17 |
| Tyrannosaurus | 9 | 11 | 17 | 30 |
| Megapithecus | 12 | 14 | 22 | 39 |
| Titanosaur | 21 | 25 | 40 | 69 |

A level 80 Tyrannosaurus then asks 30 arrows (38 berries), 11 minutes of feeding and Narcotics in the hold to
last them (BUG-01): an evening's project for two, one feeding and one on guard.

**Steps.** `audit_static.py sedation` gains the new columns; `maximumFor` and `CreatureTamingProfile.mealsPerTame`
take the level; the wild Dragon is left out of taming until 9.6 is answered; first step in the game: knock a
wild animal out beside a wild hunter and record whether the hunter attacks it (`WildlifeSenses.validTarget`
excludes neither a sleeping nor a tamed animal: reasoned). GameTest per size.
**Dashboard.** P19. **Needs.** BUG-01. **Risks.** Arrow damage: 30 arrows are 180 to 300 damage against 663
health; the sweep prints both so no species dies of its own taming.

### FIX-05 A carcass is a vanilla drop

**Evidence (read, computed, `yield`).** `PrimitiveData` (datagen/PrimitiveData.java:248) gives hide as
`uniform(0, clamp(round(health / 40), 1, 6))`: a mean of 0.5 for a Parasaur, 1.5 for a Tyrannosaurus, 2.5 for a
Titanosaur, whatever the level. A Tyrannosaurus gives 3 to 7 meat. Birds and sea animals give no hide, so 11
species cannot pay for their own saddle. Zone 1 averages 0.7 hide a kill: hide armour (24) is 34 kills, the
Tyrannosaurus saddle (16) is 23. `TrophyDrops.creatureDrops` (feature/accessory/TrophyDrops.java:71) asks for a
player as the killer, so a tame's kill gives no trophy, and the flint knife's keratin bonus is lost too.

**Change.** Meat and hide scale with the body and the level: count `* size factor * (1 + level / 25)`, size
factor 1, 2, 4, 8 by `CreatureSize`. A trophy drops when the killer is a player or a tame of a player. A kill
by a predator tame gives half as much again (ADD-03, part A). Then cost saddles and armour against it: a
saddle is 3 to 5 carcasses of its own kind (P23 left saddles at their old costs).

**Steps.** A loot function `arksurvivalreturns:scale_by_creature` in the carcass pools (`PrimitiveData`),
`./gradlew runData`; `TrophyDrops` accepts a tame's owner and passes the owner as the attacking player;
`tools/build_saddle_items.py` recosts hide from the new yield; `audit_static.py yield` reads the function and
must show 3 to 5 in "own kills" for every species with hide.
**Dashboard.** F05, F28, P23, F13. **Risks.** More meat than a duo eats: the drying rack, the trough (FIX-15)
and feed are the sinks, check them in the same pass.

### FIX-06 The saddle ladder is upside down

**Evidence (read, computed).** `tools/build_saddle_items.py` sets a saddle's level to a quarter of the
animal's health. So: Archaeopteryx and Cnidaria 3, Pegomastax and Lystrosaurus 5, **Pteranodon 6 with three
hide**, Velociraptor 8, **Parasaur 12 with bronze**, Megalocerus 15, Triceratops 22, Ankylosaurus 24. The first
animal a new player can tame by hand and ride is locked behind the Bronze Age; flight is not. The steel tier
costs iron because steel is not registered.

**Change.** Levels by the animal's place in the game, not its health (levels on the curve of FIX-02):

| Tier | Material | Species and level |
|---|---|---|
| First mounts | hide, fiber | Lystrosaurus 2, Pegomastax 2, Parasaur 3, Dilophosaur 4, Megalocerus 5, Unicorn 8 |
| Runners and workers | hide, keratin | Velociraptor 7, Terrorbird 8, Titanoboa 9, Direwolf 9, Sabertooth 10, Ravager 10, Kaprosuchus 10, Triceratops 10, Ankylosaurus 12 |
| Bronze | hide, bronze | Carnotaurus 14, Sarco 14, Allosaurus 15, Ceratosaurus 15, Therizinosaurus 16, **Pteranodon 16**, Plesiosaur 18, Argentavis 20, Megalodon 20, Liopleurodon 20, Mammoth 20, Paraceratherium 22, Deinosuchus 22 |
| Iron | hide, iron | Brontosaurus 26, Spinosaurus 28, Tyrannosaurus 30, Quetzal 32, Acrocanthosaurus 34, Mosasaurus 34, Tusoteuthis 34, Giganotosaurus 38, Megapithecus 40, Titanosaur 45, Dragon 48 |

Archaeopteryx and Cnidaria lose their saddles (F28 notes the concept gives them no seat).
**Steps.** A level table in `tools/build_saddle_items.py` in place of `level_of`; costs from FIX-05; the
bronze saddles carry `"node": "shiny"` (FIX-01); rebuild the bench data; the GameTest that checks "levels that
rise with health" is rewritten to check the table. **Dashboard.** F28, F29.
**Needs.** Decision 9.2 (is flight a Bronze Age reward).

### FIX-07 130 items have no bench

**Evidence (computed, `grid`).** No Ark bench has a grid. `tools/workstation_plan.py waiting()` (line 893) moves
to the inventory's Bench work only the recipes that fit 2 x 2 (`fits2`). The rest wait for benches that are
not built, with their grid recipes still loaded: 6 for the Campfire (bread, cake, cookie, beetroot soup,
rabbit stew, golden carrot), 52 for the Primitive Forge (glass panes, stained glass, terracotta, the flower
pot, the decorated pot), 23 for the Mechanical Press (paper, the map, iron, gold, coal, copper, diamond and
hay blocks, the bone block), and 49 undecided on the Control page. Consequences: no paper, so no book and no
name tag; no iron block, so the anvil on the Smithing Table (3 iron blocks) cannot be made; wheat makes no
bread. A village's crafting table still opens a 3 x 3 grid (BUG-19) and is the one place they can be made.

**Change.** A bench craft is a cost, not a shape, so the filter is not needed.
**Steps.**
1. `waiting()`: drop the `fits2` test. The 81 items appear in the inventory's Bench work at their bench's
   level. `python tools/build_workstation_data.py`; `audit_static.py grid` must print only the undecided.
2. Then the loophole can close: add the 81 grid recipes to the grid removals and make the vanilla crafting
   table open the Working Station's graph.
3. The 49 undecided: proposed fates in one pass for the user (9.9): Nether and End families (crimson,
   blackstone, quartz, purpur, end stone, nether brick, prismarine) go; comparator, observer, daylight
   detector and redstone lamp follow P15; granite, diorite, the book and the filled map stay.

**Dashboard.** P14, F12, F39. **Risks.** The inventory's graph grows by 81 nodes: they sit in groups that fold.

### FIX-08 Recipe defects one by one

All read in the design files and the built bench data. Each is one edit and one line in a GameTest that walks
the chain.

| | Defect | Fix |
|---|---|---|
| 1 | Healing Mixture (Mortar, level 6) needs a glass bottle, made on the Medicine Bench (level 13, bronze) from glass, which needs the forge | A Clay Vial on the Mortar at level 4 (clay, fired on the Stone Fire) takes the bottle's place |
| 2 | The quest Sedation and Capture asks for a tranquilizer arrow (level 8) in the first chapter | Ask for Narcotics |
| 3 | Flint and Steel is at level 3 and costs an iron ingot (the forge is the age's finale) | Level 10 |
| 4 | Gold tools and armour (level 18 to 20) are worse than bronze (13 to 15) | Remove them from the Armoury, or keep gold as ornament at its level (9.9) |
| 5 | Between bronze (15) and diamond (35) no gear improves, because iron tools are gone and steel is planned | Register the steel ingot and the steel set (F17's Steel Kit lane) before any other Iron item |
| 6 | A torch needs coal or charcoal; charcoal is smelted in the forge only | Charcoal from a log on the Stone Fire's spit |
| 7 | The trough needs a resin clump, scraped from spruce only | Any log |
| 8 | The bucket is a level 12 iron craft: water cannot be carried in the first age | A Clay Pot or a Waterskin (hide) that holds water |
| 9 | Carrots grow only in villages; they are the favourite of the Triceratops and the Ankylosaurus (the two workers) and an input of the Hearty Stew | Wild carrots and potatoes as a rare grass drop (`gameplay/grass_berries`) |
| 10 | Apples: the Therizinosaurus (a Bronze gate) prefers them; oak forest is replaced by birch (`ThemePolicy.BIOME_REPLACEMENTS`) | Reasoned: count oak in a generated area first; if it is scarce, make its favourite a berry |
| 11 | Eggs and slime balls have no source (P22) | Cnidaria drops slime (it drops nothing today); eggs come from nests (ADD-14, BUG-16) |
| 12 | Tin ore and sulphur generate by `#minecraft:is_overworld`, which misses five Terralith biomes (F02, open) | The tag F02 widened for rocks |
| 13 | The Improved Tranquilizer Arrow (level 12) gives 100 torpor for 3 Narcotics and a bone; four plain arrows give 300 for 2 Narcotics | Potency 200 (`improvedTranquilizerArrowPotency`) and 2 Narcotics: fewer wounds for the same sleep |
| 14 | A vanilla bed asks 12 string, from spiders only | Leave; the Bedroll is the bed. Remove beds from the Working Station if unwanted (9.9) |
| 15 | The crossbow (level 12) needs string and a tripwire hook | Fiber cord in place of string, as the bow got in P23 |
| 16 | The purple drop holds a ready Tyrannosaurus saddle | A blueprint for it (ADD-08) |

**Dashboard.** F12, F16, P23, X05.

### FIX-09 The danger curve is a cliff

**Evidence (computed, `combat`).** Zone 1, a fifth of the land, has one land predator, the Dilophosaur: 3.0 to
4.4 damage, seven bites to down a bare player. Zone 2 starts at level 8 with animals that down in two or
three bites in any armour, and its levels run 8 to 50 on the same ground: a level 50 Allosaurus (291 health)
stands beside a level 8. Nothing on the ground says how far in a player is. `DangerBands.level`
(feature/spawn/DangerBands.java) already computes a continuous position inside the tile and throws it away.

**Change.** A level follows depth. `DangerBands.depth` returns 0 at a zone's inner border and 1 at its outer
one; a wild level is drawn around `min + (max - min) * depth` within 6 levels either way. Zone 1 gets a second
predator at night at its outer half: Velociraptor packs of two, at the zone's levels
(`Species.minimumDanger` becomes a night and depth rule for it alone). The zone entry message
(`BiomeAnnouncements`) and the F3 module show the levels of the spot, not of the zone.

**Steps.** Unit test of `depth` against `level`; `CreatureEntity.rollLevel`; the register's arrivals use the
same draw; `tools/preview_danger_map.py` shades by depth; the `combat` sweep prints zone 2 by thirds.
**Dashboard.** P17. **Risks.** With ADD-02's skew: apply the skew inside the 12-level window, not the zone.

### FIX-10 Supply drops are deliveries

**Evidence (read).** `SupplyDrops.landing` (feature/drop/SupplyDrops.java) picks a spot 32 to 96 blocks from a
random player, in loaded chunks, any tier anywhere, and tells the coordinates in chat. One drop every two
days for the server. Any player opens any tier. Nothing guards it. A purple crate with a Tyrannosaurus saddle
can land beside a zone 1 hut on day two.

**Change.**
- A drop is a saved record first (`SupplyDropData`): a column 250 to 600 blocks from a player, in a zone that
  fits its colour (white anywhere, green zone 1 or more, blue zone 2 or more, purple zone 3;
  `ProgressionData.dangerAt` needs no chunk). The crate takes its place when a player loads the chunk; no
  chunk is forced.
- The beam is drawn by the client from the record's position at any distance, and the HUD shows its colour
  and bearing while it lasts (two game days).
- One a game day, plus one for the second player online (`supplyDrops.perPlayer`).
- Loot: blueprints by colour (ADD-08) in place of finished gear.

**Steps.** `SupplyDropData` holds pending drops; a payload syncs them; the beam renderer takes a position
instead of an entity; the crate is placed from `ChunkEvent.Load` on the first solid block under open sky;
GameTests for the zone rule and the late placement.
**Dashboard.** F32. **Risks.** A record over an ocean: the crate floats, as today.

### FIX-11 The boss has no before and no after

The short version of ADD-09, part 1, for a user who does not want dens and tribute: the dragon's health
follows the players in the fight (`guardian.healthPerExtraPlayer` is defined and unused by it), the three
colours get the three stat lines, and the Schematic is asked for by a tech gate (FIX-01). The Guardian quest's
golden apples (a removed item) become a blueprint or experience. **Dashboard.** P05. **Needs.** 9.3.

### FIX-12 Accessories cost nothing

**Evidence (computed from the built bench data).** The 24 primitive accessories are level 0 crafts; the 12
relics are level 10 with Red Ochre (a clay ball, a rock and red dye). Pack Frame, +40 % carry: 6 twigs, 1
hide, 1 fiber. Tally Bone, +1 Looting: a bone and a rock. Diver Stones: 3 rocks, 2 fiber. P23 left
accessories at their old costs. What is good: most ask for a trophy (three fangs, two sickle claws, wing
membranes), so each is the prize of a hunt.

**Change.** Keep the trophy at the centre and put a ladder around it: gadgets at levels 2 to 12 by what they
give, relics at 14 to 30, costs at the scale of P23 (a Pack Frame: 12 twigs, 4 hide, 10 fiber, level 4). The
dossier of a species (ADD-07) shows what its parts make.
**Steps.** `datagen/AccessoryData.java` costs and the level of each entry in the Working Station design;
rebuild. **Dashboard.** F13, P23.

### FIX-13 The journal pays in fiber and beef

**Evidence (read, `Ark/config/ftbquests/quests/chapters`).** 22 quests in five chapters. Rewards: 8 fiber,
cooked beef (three times; cows are removed), 4 flint, 8 torches, 2 golden apples (removed). Five quests are
checkmarks that pay for a click, one of them a Stone Fire. One quest is about the partner (Lift Your Partner
Up).

**Change.** The journal becomes the duo's guide. Each quest teaches one verb and pays what the next step
needs: A Trusted Companion pays a choice of first saddle; the taming quests pay Narcotics and bolas; the
checkmarks become observed tasks (the advancement triggers exist: `journal/first_tame`, `first_revive`,
`first_downed`); a chapter for two: form the tribe (BUG-07), revive, ride together (ADD-12), tame while the
other guards, bring down a beacon. Every reward item must exist in the theme.
**Steps.** The quest files are generated: follow `Ark/docs/journal-tribe.md`, Authoring the quest pack; a
GameTest that every reward item is obtainable and no task is a checkmark. **Dashboard.** P01, P02.

### FIX-14 Carry weight never grows

**Evidence (read, computed).** A block item weighs 1 (`MassCalculator.baseMass`), an ingot or an ore 2, a tool 2,
an armour piece 3. A player carries 100 before slowing and 125 at the floor of 35 % speed (`mass.playerCapacity`,
`heavyRatio`). Nothing raises it but the Pack Frame. So: a stack of cobblestone is 64 of 100; the Primitive
Forge's 40 cobblestone and 16 clay are a trip of their own; a mining run ends at about 35 ores beside a working
kit. This makes pack animals matter, which is the intent (`Ark/docs/weight-and-work.md`), and makes building a
house a chore of trips.

**Change.** Keep ores, ingots and gear as they are, so the Ankylosaurus keeps its job. A tag `mass/building`
at 0.25 for stone, wood, earth and glass blocks. Capacity grows with the Carry attribute (ADD-05).
**Steps.** The tag in `datagen/ArkData.java` (line 95) from `#minecraft:logs`, `#minecraft:planks`,
`#c:stones`, `#c:cobblestones`, `#minecraft:dirt`, sand and glass; a line in `MassCalculator`; a `carry` table
in `audit_static.py` (what a bare player and a Parasaur carry of each). **Dashboard.** P03. **Needs.** 9.4.

### FIX-15 The homestead has no pull

**Evidence (read, computed).** The trough feeds a hunger that does nothing (BUG-23). The cooking pot makes two
meals (`CookingRecipes`), one of which needs a village carrot. Wheat, carrots and apples are the favourite of
ten species, and bread cannot be made (FIX-07). A third of all grass gives a berry
(`gameplay/grass_berries`, 0.35), so a bush is a convenience. Eggs do nothing.

**Change.**
- **Feed.** Two pot recipes, Herbivore Feed (wheat, two berries, fiber) and Carnivore Feed (dried meat, raw
  meat, bone): the favourite food of every species of that diet, and they do not cost effectiveness (ADD-02).
  A farm and a drying rack become the way to tame well.
- **Hunger that matters a little** (BUG-23's decision): a tame's hunger rises a tenth as fast; a hungry tame
  does not mend and earns no experience (ADD-01); a fed one mends slowly. The trough keeps a stable fed.
- **Six more meals** with effects this game has uses for: against torpor (the Titanoboa, Narcotics), against
  cold water, more carry for ten minutes, quiet steps, a longer breath, a slower hunger.
- **Fertilizer** (ADD-13) for the bushes.

**Steps.** `CookingRecipes` becomes data (a recipe type, as its own comment plans) so meals are added without
code; taming tags for the two feeds; the rise factor in `TamingService.tick`; GameTests.
**Dashboard.** P04, F04. **Needs.** BUG-23's decision (phase 2, 7.7).

---

## 8. Order of work

Phase 2, waves 1 to 3 (taming, riding, two players) come first: most plans here stand on them. Plans in a
wave can run at the same time in separate worktrees unless they name the same files.

**Wave A, the numbers fit (small, mostly data).** FIX-02 (config first: `tribeShare` and `exponent` are one
commit), FIX-07, FIX-08, FIX-06, FIX-05, FIX-03, FIX-12, FIX-14. After it: a duo levels at a human pace, every
kept item can be made, armour and hide mean something.
**Wave B, the animal matters.** FIX-04, then ADD-02; ADD-01 beside them (all three touch `TamingState`: one
after another); ADD-06; ADD-04. After it: the hunt has a price and a prize, and a tame is somebody.
**Wave C, the frame.** FIX-01, FIX-13, ADD-07, FIX-09, FIX-10, ADD-11. After it: the players always know what
is next and the world sends them out.
**Wave D, the long game.** ADD-05, ADD-03 (A, then B, then C), ADD-12, FIX-15, ADD-13, FIX-11 or ADD-09 part 1,
ADD-10, ADD-08.
**Wave E, when the rest is played.** ADD-09 part 2 (dens), ADD-14 (breeding).

What to expect: after A and B the pack plays like the first twenty hours of ARK for two. C and D give it the
next sixty. E is the hobby that keeps a world alive after its bosses.

---

## 9. Decisions for the user

1. **Pace.** With exponent 1.15, level 8 is 4,623 XP and level 33 is 88,392. How many evenings should the
   first tranquilizer arrow and the Tyrannosaurus saddle be away for two players? (FIX-02)
2. **Flight.** Is the first flyer a Bronze Age reward (Pteranodon saddle at 16), or early as today (6)? (FIX-06)
3. **The bosses.** Three dragons by zone with tribute (ADD-09) or the short version (FIX-11)? What does each
   Schematic open? Proposed: red the second half of the Bronze Age, white the Iron Age, black the finale and
   the Dragon saddle.
4. **Weight.** Building blocks at a quarter of a unit, or the load as it is? (FIX-14)
5. **Gates.** Ages gate and levels pace (FIX-01), or levels alone with the tree as a record?
6. **Flyers and the wild Dragon.** Knock-out tames once the bola exists, as in ARK? Is the Dragon tameable at
   all, or a boss only? (ADD-06, FIX-04, and phase 2's 7.6)
7. **Home under threat.** May an apex break wooden walls during a Hunter's Moon (default: no)? (ADD-10)
8. **Breeding.** The short form of ADD-14, the full P08, or none?
9. **Leftovers.** The 49 undecided recipes, gold gear, vanilla beds: the proposed fates of FIX-07 and FIX-08.
10. **Death.** A cache for three days (ADD-11), or vanilla's five minutes as part of the danger?

---

## 10. Considered and left out

- **Thirst and temperature.** ARK had them and its players turned them down in settings. Meals and accessories
  already carry cold, water and breath (Fur Mantle, Snowshoes, Diver Stones) as choices instead of chores.
- **Raids and decay.** Nothing attacks a base and nothing should by default with two players who log off
  together; the Hunter's Moon (9.7) is the opt-in.
- **Teleports and cloud storage** (obelisks, cryopods). The theme removed teleportation; distance is what gives
  mounts, cargo and flyers their worth here.
- **A tek tier.** F17's Iron Age is the end of this tree; the three dragons give it a finale.
- **More species.** 41 are enough; 39 of them do nothing yet (ADD-03).
- **Enchanting back.** Quality tiers (ADD-08) do its work inside the theme.

---

## 11. What is good as it is

Keep these out of the way of the plans above:
- The wildlife: animals that persist, hunt, sleep and age (C05, P00) are the best thing in the pack and the
  reason a hunt can be a story. Nothing above changes how a wild animal lives.
- Accessories from trophies (F13): the idea is right, only the prices are missing (FIX-12).
- Mass and cargo (P03): the reason to own an Ankylosaurus. FIX-14 eases building only.
- The downed state and the bandage revive (P02): the one mechanic that already needs two players.
- Benches as graphs (P14) and the recipe Control page (F39): every recipe change above is an edit of data.
- Dried meat that improves with days (F04): patience rewarded; ADD-14 and FIX-15 use the same idea.
- Narcoberries: 3.5 % of grass gives one, so a bush plot is worth planting once taming costs berries (FIX-04).

---

## 12. What was not audited

- Anything that needs a client: how long a real first evening takes, how a fight feels, the screens.
- The 36 accessories' effect code (`AccessoryEffects`): read for what each does, not for defects.
- Tin, sulphur and loose rock amounts on real terrain (F02 and P23 leave them "for play to tell").
- Villagers and their trades: they stay, with an emerald economy beside dinosaurs. Whether they belong is the
  user's; nothing above depends on them, and FIX-07 closes the crafting table they offer.
- The sea as a place to play: six species nobody can tame yet (BUG-17) and nothing to find on the sea bed
  until the wreck den of ADD-09.
- The Iron Age items (F17): planned, not registered; FIX-08 line 5 asks for the steel set first.

---

## 13. How to reproduce

```bash
# The five sweeps of this phase (read sources, generated data and build/resources/main)
python Ark/tools/audit_static.py combat sedation yield roles grid
# The level wall and the sleep against the feeding, from phase 2
python Ark/tools/audit_static.py levels taming
# Every bench craft with its level and cost comes from the built bench data:
#   Ark/build/resources/main/data/arksurvivalreturns/workstation/*.json  (./gradlew processResources refreshes it)
```

Where the numbers come from: species in `feature/creature/Species.java`; scaling in `LevelScaling.java` and
`Config.java` (levels); zones in `feature/spawn/DangerTier.java` and `DangerBands.java`; armour in
`feature/primitive/PrimitiveContent.java` and `feature/bronze/BronzeContent.java`; carcass loot in
`Ark/src/generated/resources/data/arksurvivalreturns/loot_table/entities`; saddles in
`Ark/tools/build_saddle_items.py`; the recipe plan in `Ark/tools/workstation_plan.py`.
