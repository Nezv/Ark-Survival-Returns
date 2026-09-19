# Giganotosaur skin v2

Second-generation skin built with the upgraded `scripts/skin_studio.py`, applying
the review suggestions from the other model. The v1 folder (`../skin/`) is left
untouched for comparison.

## What changed

| Suggestion | Implementation |
| --- | --- |
| Body centerline coordinate | `BodyField` builds the tail-to-snout spine from bone pivots, then gives every surface point an arc length `s` and dorsal radial `r`. Stripes, folds, scutes and the dorsal/belly ramp now follow the body instead of global y/z. |
| Quantized palette | The atlas is Bayer-dithered into a fixed 24-colour palette derived from the creature's ARK colours and the body ramp (v1 had ~4,672 unique colours). |
| Explicit region masks | `region_overrides` in the species style plus `eyelid`/`lidmain`/lip/twist handling; v2's Giga report has zero unclassified bones. |
| Rig-inferred face features | `HeadFrame` computes the snout axis, up and lateral axes from the head mesh; eye discs, eye mask stripe and nostrils are placed in that frame. |
| Neighbor-aware seams | Ramp/stripes/plates are evaluated from the shared 3D field, and the report now measures border texel deltas at 1,976 shared corners. |
| Adaptive detail density | Head/jaw/tongue/eye/claw get 2.6-4.0x texel density and a minimum island size; leg/arm/foot/tail islands have a 2 px floor. 1x1 islands went from 246 to 0. |
| Species style config | `SPECIES_STYLE` / `GIGA_STYLE` expose palette, stripe frequency, belly width, scute density, detail scales, region overrides and quantization. |
| Expanded validation | `skin_report.json` now records island overlap, seam deltas, palette size, bilateral symmetry, 1x1 island count and all checks; previews add an animated idle-pose frame and `pose.gif`. |

## v2 metrics (skin_report.json)

- atlas: 384x384, packed height 234, 64,516 used pixels (43.8%)
- palette: 24 colours, Bayer-dithered
- islands: 1,482, disjoint, no 1x1 faces
- seam delta at shared corners: mean 15.2 (includes baked face-orientation shading)
- bilateral symmetry delta: 5.2 / 255
- checks: all passed (`all_faces_in_bounds`, `islands_disjoint`, `palette_within_target`, ...)

## Files

```
skin.png                    384x384 quantized atlas
giganotosaur.geo.json       textured geometry (per-face UV islands)
Giganotosaur_Textured.bbmodel
skin_report.json            metrics + checks
previews/
  side.png  three_quarter.png  front.png
  uv_check.png  texture_atlas.png
  pose.png  pose.gif        animated idle-pose check
  compare_v1_v2.png         v1 vs v2 side-by-side + atlases
```

## Regenerate

```powershell
python scripts/build_skins.py --species Giganotosaur --suffix _v2 --skip-overview
```

The batch runner can apply the same engine to the rest of the roster by running
without `--suffix`; the existing `skin/` outputs stay until then.

## Tuning knobs

- `GIGA_STYLE` in `scripts/skin_studio.py`: `base_scale`, `detail_scale`,
  `min_island`, `stripe_period`, `stripe_weight`, `dither`, `quantize`.
- `palette_roles()` maps the eight-colour ARK palette onto the painter roles if
  a species needs different contrast.
