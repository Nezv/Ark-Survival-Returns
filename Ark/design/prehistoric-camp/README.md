# The First Hearth

Four original Minecraft / Blockbench assets for the Prehistoric camp. This is a standalone design pack, not a gameplay patch.

## Deliverables

- `prehistoric-camp.png`: collection preview, rendered from the exported models.
- `rotation-review.png`: four-direction review of the four objects and the assembled pot/fire.
- `assets/arksurvivalreturns/models/block/prehistoric/`: native editable 3D model JSONs, including visual state variants and the assembled pot/fire.
- `assets/arksurvivalreturns/textures/block/prehistoric/`: original 32 x 32 pixel material tiles.
- `orthographic/`: transparent 512 x 512 orthographic renders for every model; the four main items also have 64 x 64 icons.
- `assets/arksurvivalreturns/models/item/` and `items/`: native item-model parents and item definitions for the four main objects. These are supplied as assets, not installed into the live mod.
- `manifest.json`: model IDs, state sequences and placement information.
- `prehistoric-camp-assets.zip`: the complete pack.

Open the native block-model JSONs in Blockbench using the Java block/item format. Keep the accompanying texture paths when importing. Geometry uses cuboids and vanilla-supported rotations; no external model loader is needed.

## The objects

**Narcotraffic — mortar & pestle.** An open stone bowl with a worn lip, cord grip and wooden pestle with a stone grinding head. Empty, whole, crushed and paste variants. Berry and meat ingredients have separate sequences: pieces shrink and flatten, their color changes, and the pestle changes its pose. All variants remain below 8/16 block height.

**Goodnight — reed/fur bedroll.** The same 12 x 16 model-unit footprint as the approved bed, made from grass reeds with cross bindings, frayed ends, a rolled-grass headrest and an irregular fur cover. No wooden frame, cloth pillow or modern mattress. Height is 3.35/16 of a block.

**A Little Warmth — stone fire.** Two courses of individual hearth stones, split fuel logs, low coals and a removable hand-turned spit. Empty, fueled, lit, raw, seared and cooked states are included. Meat darkens and shrinks on the spit; fat marks turn into roasting marks. The complete spit and meat remain below slab height (8/16 block).

**Delicious — clay pot.** A hollow earthenware bowl, rolled rim, open loop handles, short clay feet and a soot-darkened underside. Empty and stew variants. The standalone pot is 7.65/16 block high. It is added directly to the stone fire: remove the spit and offset the pot upward by 3.35 model units. Feet rest on the stones, and the bowl clears the low flames. The supplied assembly is 11/16 block high; each individual object remains slab height or less.

## Later integration

- Mortar ingredient and pestle variants are discrete visual stages. Hook them to processing progress; this pack does not implement grinding or animation timing.
- The new fire takes the furnace role and uses the same crafting recipe, as requested. Connect raw-meat input and wood fuel to the supplied cooking states. No furnace recipe, registration or runtime behavior is changed here.
- Adding a pot switches to the fire-without-spit assembly. There is no pot bench or separate cooking station.
- Inventory slots, quantities, processing durations, collision shapes and food effects remain for the game patch.
- Offline renders verify the actual geometry and material references. Client lighting and interactive placement have not been tested; no game client was launched.

Regenerate from the Ark directory with `python tools/build_prehistoric_camp.py`. Requires Pillow, numpy, and the existing `build_camp_assets.py` / `render_camp_assets.py` helpers. The generator writes only into this design-pack directory.
