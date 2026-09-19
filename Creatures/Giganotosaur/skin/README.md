# Giganotosaur skin studio

A hand-designed, Minecraft-style skin for the Giganotosaur model, with **real
per-face UVs**. This folder is self-contained: the source model in `../geo/`
and `../textures/` is untouched.

## What is inside

| File | Purpose |
| --- | --- |
| `giganotosaurus.png` | 256x256 painted atlas (the skin) |
| `giganotosaur.geo.json` | GeckoLib geometry with true per-face UV islands |
| `Giganotosaurus_Textured.bbmodel` | Blockbench project with the skin embedded |
| `build_skin.py` | generator: repaints the atlas and rewrites the UVs |
| `preview_skin.py` | software renderer used for the review images |
| `skin_report.json` | atlas statistics and validation checks |
| `previews/` | side / three-quarter / front / head / jaw / tail renders |

## How the texture works

The converter mesh is 247 rigid cubes fitted to the ARK skeleton, and each cube
face already had its own UV slot pointed at a flat palette band. The studio
replaces that with a classic box unwrap:

1. Every cube is decoded into bind-pose world space.
2. Each of the six face keys gets its own rectangular island, packed into one
   256x256 atlas at 2 pixels per model unit (roughly 32 px per block).
3. Island texels are mapped back to their 3D surface point, and the paint is
   evaluated in world space: dorsal-to-ventral gradient, back and tail
   stripes, belly plates, neck folds, dorsal scutes, scale speckling, a lip
   line with alternating teeth, amber eyes with dark sockets, nostrils, and
   dark claws on the feet and hands.
4. The UV axes per face key follow the Blockbench/Bedrock convention
   (`UVToLocal` / `mapAutoUV`), so the model displays correctly in Blockbench,
   GeckoLib and Minecraft.

The palette reuses the original creature colours (olive greens, cream belly,
bone jaw, amber eye, rust tongue) so this skin stays consistent with the rest
of the collection.

## Regenerate

```powershell
python build_skin.py
```

This rewrites the atlas, geometry, Blockbench model, report and previews.

## Open in Blockbench

Open `Giganotosaurus_Textured.bbmodel` with the GeckoLib plugin (model format
`geckolib_model`). Geometry, texture and all 31 clips are embedded.

## Install into the mod

Copy the two game resources over the generated palette versions:

```powershell
Copy-Item giganotosaurus.geo.json ..\..\..\Ark\src\main\resources\assets\arksurvivalreturns\geckolib\models\entity\giganotosaurus.geo.json
Copy-Item giganotosaurus.png    ..\..\..\Ark\src\main\resources\assets\arksurvivalreturns\textures\entity\giganotosaurus.png
```

The model identifier (`geometry.giganotosaur`) and texture dimensions are
already updated inside the geometry.

## Validation

`skin_report.json` records the atlas layout and checks:

- all 1482 face islands are inside the 256x256 atlas
- no island is smaller than 1x1 pixel
- the atlas packs within 256 px height (currently 248)
- face count equals 247 cubes x 6 faces

Rendering verification is software-only (`preview_skin.py`); the geometry has
not been loaded inside Minecraft from this folder yet.

## Known limitations

- The rigid automatic cubes overlap, so interior faces can show as thin light
  seams at grazing angles. That is a property of the converter mesh, not the
  UV layout.
- Eyes, mouth and claws are painted in world space. On the jumbled head cubes
  they read best at normal viewing distance.
- The atlas uses the top ~67% of the 256x256 sheet; the rest is left as
  background for future detail regions.
