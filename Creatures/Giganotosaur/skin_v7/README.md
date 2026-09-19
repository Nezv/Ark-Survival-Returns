# Theropod texture painter — v7

This v7 replaces sparse stamps with the requested stone-style random fill.
Only Giganotosaur's output has been rebuilt. The same script accepts a batch.

## Painting algorithm

1. Assign five vertical skin layers, belly to back/head.
2. Read five base colors in that order from batch_palettes.json.
3. Build five local shades per base: RGB offsets -6, -3, 0, +3, +6.
4. Pair fitted geometry across X=0 and resolve reflected face orientation.
5. Divide each face into **3×3 actual texture pixels**.
6. For every brush cell, pick uniformly from that layer's five shades.
7. Copy the complete paint grid onto the paired face; mirror center-spanning
   faces internally. Expand each chosen cell to its full 3×3 footprint.

No low-density point sampling, world-unit brush, dither, smoothing, minimum
area rejection, or speckle cleanup is applied. Neighboring equal colors may
join into larger shapes. Layer boundaries also follow the 3-pixel grid.
Skin has exactly five palettes of five colors. Tongue, nails, and eyes retain
separate material palettes; eye landmarks also occupy whole brush cells.

Vertical position follows the curved spine for body/tail/neck, the head frame
for head surfaces, and local height for limbs. The five light-to-dark colors
are shared. There are no additional random stripes or patches.

## Run

From the repository root:

~~~powershell
# Rebuild Giga in place.
python Creatures/Giganotosaur/skin_v7/build_skin_v7.py

# Build Ivory, Darken, Emerald, Midnight, and Burgundy under skin_v7/<Variant>/.
python Creatures/Giganotosaur/skin_v7/build_skin_v7.py --all-variants

# Build selected variants only.
python Creatures/Giganotosaur/skin_v7/build_skin_v7.py --variants Ivory Emerald

# Run named creature folders later.
python Creatures/Giganotosaur/skin_v7/build_skin_v7.py --creatures Giganotosaur Tyranosaur Allosaurus

# Validate the supported batch in memory without replacing any models.
python Creatures/Giganotosaur/skin_v7/build_skin_v7.py --all-theropods --dry-run

# Export the batch without preview rendering.
python Creatures/Giganotosaur/skin_v7/build_skin_v7.py --all-theropods --no-previews

# Choose another reproducible random fill.
python Creatures/Giganotosaur/skin_v7/build_skin_v7.py --seed 42
~~~

The batch list is Giganotosaur, Tyranosaur (the repository spelling),
Spinosaurus, Ceratosaurus, Dilophosaur, Acrochantosaur (repository spelling),
Allosaurus, Carnotaurus, and Velociraptor. --creatures also accepts other
compatible creature folders. The script continues after a creature failure,
prints the error, and exits with a nonzero status if any failed.

Each output goes to Creatures/<name>/skin_v7, replacing that version only.
Root source models are read, never edited. The source mesh, skeleton, and all
animations are checked after export. UVs are rebuilt to give every face whole
3-pixel cells with independent two-pixel gutters, including corners.
The corrected preview sampler remains local to the script.

`--all-variants` reads the five named palettes in `batch_palettes.json`. Each
variant is written inside the selected creature's `skin_v7` folder and keeps
the same 3-pixel brush, mirrored face layout, and native model data.

## Palette settings

Edit batch_palettes.json. defaults applies to every creature. A creature entry
overrides only its specified values, for example:

~~~json
{
  "creatures": {
    "Allosaurus": {
      "base_colors": ["#dace9c", "#b2af70", "#89955a", "#626d41", "#424b31"]
    }
  }
}
~~~

All five colors run light to dark. shade_offsets adjusts variation around
each color. seed changes the random fill. pixels_per_unit and
head_density_multiplier set UV density, not brush size. The brush remains
exactly 3 pixels. Use --config to load a different configuration file.

The current base ramp is shifted two steps darker: the former third, fourth,
and fifth colors followed by two darker browns. Belly to dorsal:
#ba7b40, #965529, #6c3b26, #4d2a1b, #361e14. Local offsets remain ±6.

## Deliverables and checks

- Giganotosaur_Textured.bbmodel: ready to open with embedded texture.
- giganotosaur.geo.json and skin.png: geometry/texture exports.
- region_palettes.json / region_palettes.png: the 5×5 skin palette.
- painting_rules.json: the actual parameters used for this output.
- skin_report.json: brush counts, palette, validation, and unmatched geometry.
- previews/nose_comparison.png: actual nose UV, flat versus random-filled.
- previews/nose_uv.png: unscaled nose face for pixel inspection.
- previews/: full-model views and an animated pose check.

Paired texture grids are verified equal after reflection. Giga has six fitted
cubes without one-to-one counterparts: these receive the same method but
cannot be claimed to have exact mirrored partners. Their identities are
recorded in skin_report.json. The script preserves the asymmetric mesh.

Tests cover full shade coverage, exact 3-pixel cell expansion, odd/even mirror
grids, seed reproducibility, and five distinct shades per layer:

~~~powershell
python -m unittest discover -s Creatures/Giganotosaur/skin_v7 -p test_brush.py
~~~

Requires Python, NumPy, Pillow and SciPy, plus the repository's
scripts/skin_studio.py geometry/export helper and creature source files.
No earlier skin-version scripts or outputs are required.

Validation performed for this revision: all four brush tests pass. Texture
generation and atlas/brush/symmetry checks passed in memory for all nine listed
theropods. Only Giganotosaur was exported and visually reviewed; dry runs do
not claim that every species has had an artistic review or a game runtime test.
