# From Camp to Civilization - proposal 02

Assets-only progression from Prehistoric through Iron. No game integration.

## Deliverables

- `technology-tree.png`: horizontal overview, 8140 x 1610 pixels.
- `prehistoric-age.png`, `bronze-age.png`, `iron-age.png`: readable age crops.
- `technology-tree.json`: editable requirements, connections, layout and assumptions.
- `icons/`: approved model renders and existing game sprites.
- `technology-tree-assets.zip`: current PNGs, used icons, data and notes.
- `block-ideas.txt`: unchanged names-only candidates from the previous proposal.

Regenerate from Ark with `python tools/build_technology_tree.py`. Requires Pillow, numpy, Windows fonts, approved camp models and the local Minecraft source jar used by `render_camp_assets.py`.

## Required progression

Monkeys starts Prehistoric. Its three paths converge at Narcotraffic.
Prepare for it! starts Bronze. Its three paths converge at Rawr!.
Greed starts Iron. Its three paths converge at Subdue Nature.

Every finale requires the endpoints of all three paths. Food bonuses never gate a finale. Solid lines are required progression; dashed lines associate optional boosting foods with their age.

## Layout choices and inherited requirements

- Monkeys and its Gather stone requirement are retained from the original sketch as the unnamed Prehistoric starter. This overlaps Tha rock; kept pending author refinement.
- Prehistoric column counts: 2-3-3-3-3-1.
- Bronze column counts: 2-3-3-3-1. The following singleton is Greed, drawn once as Iron's starter. This preserves the user's extra terminal 1 without duplicating Greed inside Bronze.
- Iron column counts: 2-3-3-3-1-1. The additional singleton preserves Stronger, the fourth entry in the middle path, before the finale.
- Multi-tool retains the Therizinosaurus taming task. Slavery inherits Working Giants: return with a hunt and resource haul from tames. Other renamed entries retain their previous requirements.
- Sparklers is crafting gunpowder; Greed is having gunpowder. They are distinct milestones as requested.

## Food bonuses

- Prehistoric: Dried Meat III. Eat meat dried for three full in-game days; acquisition still requires drying.
- Bronze: Golden Raptor Meat. Craft it after the tranquilizer milestone.
- Iron: Cocaine. Fictional, humorously named boosting food combining gunpowder, narcotics and yellow berry. Existing sugar sprite illustrates white dust.

Food position above a starter is a layout convention, not a promise that the food is immediately obtainable. No buff values or real-world recipes are defined.

## Art scope

Feeding trough moves to Bronze. Hearth & Home is removed. Finally Goodnight requires a prehistoric Mattress and remains text-only until its design is chosen. The approved bed asset appears only for Iron's Home Sweet Home.

No new mattress, weapon, vault, forging table, spike or engineered-boot models are created. Vanilla boots and armor are diagram placeholders; dinosaur eggs, saddle and materials reuse existing sprites. Other camp icons render the approved native models. No runtime, recipe or quest files are changed by this generator.
