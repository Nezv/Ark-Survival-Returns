# Homestead Economy

Farm stations, food processing, field medicine, the primitive forge and storage (P04). Everything is
server-authoritative, ticks on a shared batch cadence and never advances in unloaded chunks, so an
absence never produces a windfall. Settings live under `[farm]`, `[kitchen]` and `[forge]`.

## Stations and the batch cadence

Every station is a block entity that processes once per `farm.batchTicks` (default 100) only while its
chunk is loaded. A station has no offline catch-up, and player-built awareness from P03 still applies
to worker jobs. Batch counts are per station and configurable.

## Berry bushes

- The four Ark berries are plantable: use a berry on dirt, grass, farmland, coarse or rooted dirt,
  podzol, mycelium or moss (`#arksurvivalreturns:farm/plantable_on`) to plant its bush.
- Bushes grow by random tick through four vanilla-stage ages (brightness 9+, chance
  `farm.berryGrowthChance`). A ripe bush yields two to three of its berry on use and resets to age
  one; breaking a ripe bush drops the same through its loot table.
- Bushes are the renewable taming stock; planting and harvest produce no items beyond the berries.

## Feeding trough

- Holds one food stack, capped by `farm.troughCapacity`. It accepts `#arksurvivalreturns:farm/trough_food`
  (the four berries, wheat and seeds, carrot, potato, beetroot, `#minecraft:meat`, `#minecraft:fishes`).
- Every batch it feeds up to `farm.troughMaxPerBatch` hungry **tamed** creatures within
  `farm.troughRadius`, removing `farm.troughFeedAmount` hunger and healing `farm.troughHeal`.
- Wild creatures are never fed: taming stays a player action. An empty hand takes the food back, and
  the stack drops when the trough is removed.

## Drying rack

- Holds raw food (`#arksurvivalreturns:farm/drying_inputs`) and converts one item per
  `farm.dryingBatches` batch into a **dried ration** (nutrition 6, travels in stacks of 64).
- The output fills first; an empty hand takes the ration, then the remaining raw input.

## Cooking pot

- Four ingredient slots and one meal slot, opened through its own menu (vanilla panel reused).
- All four slots must be filled and each loses exactly one item per meal. Fixed recipes:
  - **Hearty stew** — two dried rations, one meat (`#minecraft:meat`), one carrot. Regeneration for
    10 seconds. Nutrition 8, stacks to 16.
  - **Trail mix** — two dried rations, two Ark berries. Speed for 15 seconds. Nutrition 6.
- `kitchen.cookBatches` sets the batch count; `kitchen.enabled` disables the station.

## Field medicine

No station is required. Three narcoberries and a plant fiber craft a **concentrated sedative**
(`concentratedSedativePotency`, default 100), which can be eaten, swung or brewed into ammunition
through every route the narcoberry has. Four base tranquilizer arrows plus one concentrate craft four
**improved tranquilizer arrows** carrying that dose. The berry remains the cheap route.

## Forge stations

- **Charcoal kiln** — logs, planks and saplings reduce to charcoal with no fuel (kilnBatches).
- **Primitive forge** — raw iron, copper and gold, sand, cobblestone, deepslate, clay and dried
  rations process without fuel (forgeBatches). The vanilla furnace stays fully usable; the forge is an
  efficiency route, not a gate.
- Both are two-slot stations: a valid input is inserted by hand, progress accumulates per batch, and
  an empty hand takes the output first, then the input back. Contents drop when the block is removed.

## Storage crate

A fixed 27-slot container crafted from a chest, planks and fiber. It opens a vanilla-layout screen,
holds items and nothing else, has no ticker, and drops its contents when broken. It is the intended
pairing for the P03 Load/Unload buttons.

## Config quick reference

| Key | Default | Meaning |
|---|---|---|
| `farm.enabled` | true | Master switch for stations and bushes |
| `farm.batchTicks` | 100 | Shared station cadence |
| `farm.troughRadius` | 8 | Feeding reach |
| `farm.troughCapacity` | 32 | Food held per trough stack |
| `farm.troughHungerThreshold` | 60 | When a tame is fed |
| `farm.dryingBatches` | 6 | Batches per dried ration |
| `farm.berryGrowthChance` | 0.2 | Growth chance per random tick |
| `kitchen.enabled` / `kitchen.cookBatches` | true / 4 | Cooking pot |
| `forge.enabled` | true | Kiln and forge |
| `forge.kilnBatches` / `forge.forgeBatches` | 4 / 6 | Processing time |

## Automated coverage

`farm_batch` covers trough feeding and wild exclusion, drying cycles, bush harvest and chunk safety;
`medicine_dose` covers the dose order (berry < concentrate, arrow uses its configured dose);
`kitchen_cook` covers both recipes, one-item-per-slot consumption and chunk safety; `forge_batch`
covers kiln charcoal, forge ingots, rejected inputs and chunk safety; `storage_crate` covers the
27-slot contract and a save/load round trip. The full in-client checklist is in
[Verify.md](../Verify.md).
