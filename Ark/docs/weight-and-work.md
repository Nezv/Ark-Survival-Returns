# Weight and Working Tames

Carried mass, cargo rigs, overload behavior and harvest work for players and tames (P03). Everything
is server-authoritative and configurable; the settings live under `[mass]`, `[work]` and `[tribe]` in
the server config, and item categories are data-pack tags.

## Mass model

- **Units are abstract.** Item mass resolves in order: the `arksurvivalreturns:mass/*` item tags, then
  a stack-size fallback (`6.4 / maxStackSize`, clamped 0.05–4.0). Block items default to one unit, so
  an untagged modded item is never free.
- **Tag categories** (`data/arksurvivalreturns/tags/item/mass/`): `light` 0.05, `bulk` 0.1, `food` 0.2,
  `unit` 1, `ore`/`tool` 2, `armor` 3. `unknown_container` is a flat configurable mass for container
  items the model cannot inspect.
- **Containers** are inspected one level deep with a capped content sum (shulker boxes, bundles). A
  container nested inside another contributes only its own base mass, so content sums and specialist
  reductions can never multiply recursively.
- **Player load** is the main inventory plus worn equipment; the body is abstracted.
- **Mount load** is the creature's cargo plus the fitted harness plus, while mounted, the rider's
  carried load. The rider's own on-foot penalty is suspended while mounted, so nothing is counted
  twice.
- **Recompute is event-driven.** Pickup, toss, craft, smelt, equipment change, container close, login,
  respawn, dimension change and mount/dismount mark the entity; the service coalesces to one pass per
  tick and adds a one-second watchdog while a container menu is open. Inventories are never scanned
  every tick, and mass is derived after reload rather than persisted.

## Thresholds

| Load | Effect |
|---|---|
| ≤ 75% | none |
| 75–100% | warning only: gauge color and one actionbar message, no mechanical penalty |
| 100–125% | sprint denied exactly at 100%; movement slows with load |
| > 125% | slowdown reaches the configured floor (35% by default); walking and dropping always work |

Presets: **STANDARD** uses the configured ratios; **RELAXED** raises capacities by half and starts
every penalty 25 points later (warning 100%, slowdown 125%, floor 60%); **OFF** disables mass, the
gauge and every penalty.

## Capacity is a movement budget

Capacity never blocks an item move. Manual drag, shift-click, feeding and transfers all work at any
load, so players overload deliberately to rearrange or drop cargo. Capacity only supplies the
movement curve, the HUD denominator and the automation stop:

- Without its required harness a tame uses `mass.bareCapacity` (100 by default).
- `mass.automationCeiling` (1.0 by default, up to 1.25) caps automated filling: Fast Load and work
  jobs stop before a stack would cross it. Fast Unload is always allowed.

## Cargo rigs and transfer

- **Pack harness** (leather + plant fiber) and **reinforced harness** (pack harness + leather + flint
  + fiber) fit the rig slot in the tame screen. Each species declares a harness tier and capacity in
  `CargoProfiles`, tunable per species under `[mass.<species>]`.
- Heavy haulers (Brontosaurus, Titanosaur, Paraceratherium, Mammoth, Quetzal) and the giants require
  the reinforced harness; a lighter rig leaves only the bare allowance.
- **Load** and **Unload** in the tame screen move whole stacks between the hold and nearby storage.
  The scan is bounded (radius, position and container caps) and reads already-loaded chunks only; it
  never requests a chunk. The screen shows `Cargo X / Y`, and the HUD switches to the mount bar while
  riding.

## Overload safety

- Land: one movement-speed modifier scales the creature down to the configured floor.
- Flight: an overloaded bird refuses to take off, and one already airborne descends under powered
  control instead of falling. Autonomous tamed flyers also steer slower under load.
- Water: an overloaded swimmer cannot dive and becomes slightly buoyant; when the rider's air falls
  to a third, it surfaces outright, so overburdening is never an unavoidable drowning.
- Riders receive rate-limited actionbar warnings for takeoff, descent, dive and surface.

## Work orders

Sneak-use the companion whistle cycles FOLLOW → STAY → WANDER → **WORK**; WORK is offered only to
species with a work profile.

- **Triceratops (forage):** grazes grass in place, cuts tufts and ferns (destroyed, no vanilla drops)
  and resets ripe sweet-berry bushes. Yields plant fiber every action and a chance at a mod berry,
  weighted like the grass route.
- **Ankylosaurus (mineral):** mines `#arksurvivalreturns:work/mineral` — natural stone (`#c:stones`)
  and ores (`#c:ores`) — with vanilla drops and a configurable bonus drop on ore blocks.

Bounds and protections:

- Jobs anchor where the WORK order was given; the search radius and sample count are capped and every
  candidate must be in a loaded chunk. No chunk is ever requested.
- Blocks with block entities are never targeted, and player-placed blocks are recorded per dimension
  in `WorkProtection` SavedData (bounded FIFO) so a worker never dismantles what a survivor built.
- A job runs only while the owner or a WORK-permitted tribe member stays within
  `work.supervisionRadius`; walking away pauses it, and there is no offline catch-up. The job stops at
  the cargo automation ceiling and reports a full hold once.
- `/arktribe perm <player> work on|off` and `[tribe] defaultWork` control who may supervise; issuing
  any order still requires the COMMANDS permission.

## Config quick reference

| Key | Default | Meaning |
|---|---|---|
| `mass.enabled` | true | Master switch |
| `mass.preset` | STANDARD | STANDARD / RELAXED / OFF |
| `mass.playerCapacity` | 100 | Player movement budget |
| `mass.bareCapacity` | 100 | Tame budget without its required harness |
| `mass.warningRatio` / `slowRatio` / `heavyRatio` | 0.75 / 1.00 / 1.25 | Band boundaries |
| `mass.speedFloor` | 0.35 | Lowest movement fraction |
| `mass.automationCeiling` | 1.00 | Fast Load and work-job stop |
| `mass.transferRadius` | 8 | Nearby-storage scan radius |
| `work.enabled` | true | Master switch for work orders |
| `work.radius` / `work.supervisionRadius` | 12 / 48 | Job reach and supervision reach |
| `work.respectPlacedBlocks` | true | Never harvest player placements |

## Automated coverage

`MassRulesTest` covers the threshold math, the warning-only band, the overload line and the forced
surface rule. `TribePermissionTest` covers the WORK flag. Headless GameTests cover mass accounting and
the off switch, harness gating and mount overload, ceiling-limited transfer, chunk safety, flight
takeoff refusal and controlled descent, the swim no-dive rule, work yields, placed-block protection
and the supervision gate. The full in-client checklist lives in [Verify.md](../Verify.md).
