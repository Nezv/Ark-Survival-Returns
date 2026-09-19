# Giganotosaur skin v4: brush-sized regions

This pass tests a more direct texture model against the supplied Minecraft
references. It keeps the extracted mesh, native skeleton, UV layout, and
animations intact and changes the texture painter only.

The painter works in two stages:

1. It classifies each surface as body/tail, head/jaw/tongue, leg, arm, foot,
   nail, tooth, eye, horn, or feather.
2. It paints the body from snapped 3-D brush cells rather than independent
   texels. Body marks are 2.8 model units along the tail-to-snout field and
   0.13 of the dorsal-to-belly field. UV islands are then grouped into a
   visible 4×4 atlas brush where their size permits.

The result has a broad dark dorsal cap, light belly and jaw, large tail bars,
one controlled flank mark, segmented limb bands, and explicit nail/eye/mouth
roles. There is no hash grain, random patching, scale speckle, or dithering.

Regenerate from the repository root:

```powershell
python Creatures\Giganotosaur\skin_v4\build_skin_v4.py
```

Files in this folder include the Blockbench project, GeckoLib geometry,
`skin.png`, previews, and `skin_report.json`. The report records the brush
settings and validates the atlas. The palette is intentionally local to this
experiment so later passes can change colour without affecting other species.
