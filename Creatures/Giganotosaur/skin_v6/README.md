# Giga v6: diagnose before painting

Open `Giganotosaur_Textured.bbmodel`. Run `python Creatures/Giganotosaur/skin_v6/build_skin_v6.py` from the repository root to reproduce the diagnostics and final output. The script imports the existing v5 palette and brush helper plus scripts/skin_studio.py; keep those dependencies.

## What caused the flecks

1. **Preview sampling bug:** UV coordinates are texel boundaries, but the shared preview renderer rounds them. The last half-texel can sample outside its face. Using floor changed 24,337 screen pixels on the same v5 texture, same geometry, same camera. This count measures all changed pixels, not only defective pixels. The correction is local to this experiment. Compare diagnostics/v5_original_sampling.png with diagnostics/v5_corrected_sampling.png.
2. **Atlas padding collision:** old shelves leave a single pixel between islands. Both islands write into that pixel; corner padding is also absent. V6 reserves two independent padding texels on each side and fills the corners. The atlas is now 512 square for padding, not finer painting.
3. **Paint fragments:** stepped spatial masks intersect narrow rotated faces, creating small disconnected colour patches. V6 merges components smaller than 12 texels into the most common neighbouring colour on skin regions. This is a heuristic, not a proof of seam continuity; tiny islands and facial details are exceptions.
4. **Misassigned anatomy:** shoulder blades were painted as arms, interrupting the flank. They now use the torso paint role without changing their bone membership.
5. **Overlapping masks:** spherical nail masks affected arbitrary nearby surfaces. V6 selects the distal fitted cube nearest each terminal digit pivot and paints that whole cube muted horn brown. Selection remains an approximation; see paint_regions.json and the region preview.
6. **Facial fragments:** the old global lip-height and snapped eye masks produced cheek marks. V6 uses a head-oriented square eye and broad facial colour bands; it deliberately omits the unreliable painted tooth and nostril masks pending better surface annotations.

## What is painted, and with which variables

| Region | Paint rule | Coordinates / controls |
|---|---|---|
| Torso, shoulder blades, neck, tail | Cream underside through amber flank to dark brown back | Curved tail-to-head coordinate s, normalized dorsal height d |
| Dorsal bars | Broad bands descending into the upper flank | 12-unit spacing, 3.2-unit width, lower limit d=0.48 |
| Arms and legs | Three restrained value bands | Height normalized within each limb region |
| Head | Broad warm bands and a square eye | Head forward/up/lateral frame and eye landmarks |
| Jaw / tongue | Solid cream / muted pink | Anatomical region |
| Nails | Whole distal fitted cube in horn brown | Terminal digit pivot and nearest cube centre |

Brush steps: 2.4 model units longitudinally and 1.6 vertically. They do not reset at each UV island. No random grain, dithering, face-edge outlines or per-cube random shading is painted. Variables are defined in RULES in the script and exported to painting_rules.json; paint_regions.json lists all 247 cube assignments.

## Review evidence and limits

diagnostics/flat/previews/three_quarter.png uses one texture colour with renderer lighting. Bright slivers still visible there are due to exposed cube surfaces and their lighting; they cannot be attributed to paint noise. diagnostics/regions/previews/three_quarter.png shows the anatomical assignments. Both diagnostics include Blockbench projects for inspecting the same geometry directly.

Export checks verify positive disjoint UV islands, atlas bounds, and unchanged mesh transforms, skeleton and animation data. These checks do not certify visual quality. The final preview is reviewed for scattered dark marks, facial fragmentation and whole-model readability. Geometry-induced faceting remains, and nail placement still merits close inspection in Blockbench.
