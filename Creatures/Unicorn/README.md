# Unicorn

ARK asset: `Equus`. 77 original mesh bones, 148 fitted cubes, 42 original animation clips.

Open `Unicorn_GeckoLib.bbmodel` in Blockbench with the GeckoLib plugin. Geometry, palette texture and all animations are embedded.

Game resources: `geo/unicorn.geo.json`, `animations/unicorn.animation.json`, `textures/entity/unicorn.png`.

Run `python rebuild.py` to rebuild using the shared scripts and saved source exports. This overwrites generated files, so save manual edits under another name first.

`source/` includes original extracted PSK/PSA and glTF, a skeleton-only GeckoLib JSON, bone transforms, extraction logs and input hashes in `build_report.json`.

Keep bone names, pivots and parent links when editing cubes. The imported clips already target these bones. Choose clips in your mod animation controller; assigning matching names does not create entity behavior automatically.

The mesh is an automatic cuboid approximation with an original palette. Inspect shoulders, mouths and wing joints before final art approval. Loop flags are inferred from clip names. Unreal notifies, sounds, physics and AI are outside this conversion.

## Mesh refinement

Reduced slice counts and trimmed 5th/95th-percentile bounds make the body and limbs lighter. The horn and original rig remain attached.
