# Giga painting rules, v5

Run `python Creatures/Giganotosaur/skin_v5/build_skin_v5.py` from the repository root.
Open Giganotosaur_Textured.bbmodel in Blockbench. Mesh, skeleton and animations
are verified unchanged against the source. This is a texture experiment.

The design separates anatomical masks, shape rules and palette lookup.
The longitudinal coordinate follows the curved tail-to-head spine: a curved
equivalent of the Z axis. Height across the body determines belly/flank/back.
Legs use height within their region; head details use a head-oriented frame.

Painting layers: cream belly, gold transition, warm brown flank, dark dorsal
cap, tapered dark bars, amber flank highlights, lighter inner limbs, jaw and
mouth, square eye markings, terminal-digit nail masks. Surface colour is
chosen directly from a role palette, avoiding mouth colours leaking into skin.
No random grain or dither is used. Lighting is supplied by the renderer.

Body brush cells are 1.5 model units, with 2.4-unit longitudinal steps. Facial
cells are 0.6 units. These sizes are independent of atlas islands, so marks do
not restart on every cube. Small edge fragments can still occur where a mark
intersects a cube. This is not a guarantee that every atlas fragment is 4x4.

paint_regions.json lists bone assignments. painting_rules.json records the
brush settings and nail landmarks. Terminal bones have no cubes of their own;
nails use distance masks around their pivots on existing digit geometry.
Those masks are approximate and merit inspection in Blockbench.

Passing atlas checks only verifies a usable export, not artistic quality.
The reference models have different geometry and lighting; this pass retains
Giga's existing 247 cubes and should be judged as a texture on that mesh.
