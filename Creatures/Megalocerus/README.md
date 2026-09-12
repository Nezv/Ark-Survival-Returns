# Megalocerus

ARK asset: `Stag`. 59 original mesh bones, 4574 fitted cubes, 36 original animation clips.

Open `Megalocerus_GeckoLib.bbmodel` in Blockbench with the GeckoLib plugin. Geometry, palette texture and all animations are embedded.

Game resources: `geo/megalocerus.geo.json`, `animations/megalocerus.animation.json`, `textures/entity/megalocerus.png`.

Run `python rebuild.py` to rebuild using the shared scripts and saved source exports. This overwrites generated files, so save manual edits under another name first.

`source/` includes original extracted PSK/PSA and glTF, a skeleton-only GeckoLib JSON, bone transforms, extraction logs and input hashes in `build_report.json`.

Keep bone names, pivots and parent links when editing cubes. The imported clips already target these bones. Choose clips in your mod animation controller; assigning matching names does not create entity behavior automatically.

The mesh is an automatic cuboid approximation with an original palette. Inspect shoulders, mouths and wing joints before final art approval. Loop flags are inferred from clip names. Unreal notifies, sounds, physics and AI are outside this conversion.
