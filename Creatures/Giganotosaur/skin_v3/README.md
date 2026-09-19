# Giganotosaur skin v3

This is a low-noise procedural skin pass built on the existing Giganotosaur
mesh, skeleton, UV island layout, and animation set. It keeps the extracted
Ark proportions and GeckoLib-compatible model while changing only the texture
painting method.

## Painting method

`build_skin_v3.py` uses direct, deterministic regions:

- a 17-colour palette with hard Minecraft-style colour steps;
- broad belly, flank, dorsal, jaw, tongue, eye, claw, tooth, and mouth zones;
- a few large axial bands sampled from the rig-derived body field;
- a restrained dorsal ridge rhythm;
- no hash grain, random patches, scale speckles, sinusoidal micro-patterns,
  or Bayer dithering.

The shared `skin_studio.py` pipeline still handles face-aware UV allocation,
head orientation, model export, animation preservation, previews, and report
validation. Regenerate from the repository root with:

```powershell
python Creatures\Giganotosaur\skin_v3\build_skin_v3.py
```

## Files

- `Giganotosaur_Textured.bbmodel` — textured Blockbench model with the native
  skeleton and animations.
- `giganotosaur.geo.json` — GeckoLib geometry export.
- `skin.png` — 384×384 atlas.
- `previews/` — front, side, three-quarter, posed, UV, and atlas previews.
- `skin_report.json` — UV, palette, seam, and symmetry checks.
