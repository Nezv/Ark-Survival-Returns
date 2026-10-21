# Creature source projects

`Creatures/` contains the editable GeckoLib source projects and preserved ARK
exports for all 41 runtime species. The shared conversion implementation lives
in `../scripts/`; creature directories contain species-specific inputs and
outputs, not separate build systems.

The independently documented source-only collection is described in
[`Collection/README.md`](Collection/README.md).

## Rebuild

Run the shared workflow from the repository root. A targeted rebuild reuses the
preserved exports and validates the resulting project:

```powershell
python scripts/workflow.py --reuse-exports --species Argentavis
```

Pass multiple names after `--species`, or omit the option to rebuild every
creature. Omit `--reuse-exports` only when fresh extraction from the installed
ARK/Workshop data is intended. Rebuilds overwrite generated geometry, textures,
animations, Blockbench projects and previews, so preserve manual artwork under
another name first.

The Mammoth, the Megapithecus, the Sabertooth and the Ankylosaurus are
hand-made: `scripts/handmade_models.py` builds their boxes, skins, Blockbench
projects and previews from `scripts/handmade_<id>.py` on the unchanged rig and
clips, and the workflow runs it again after refitting them. Rebuild and import
them with:

```powershell
python scripts/handmade_models.py mammoth megapithecus sabertooth ankylosaurus
python Ark/tools/import_creatures.py --species mammoth megapithecus sabertooth ankylosaurus
```

## Directory contract

Each species directory contains:

- the Blockbench project listed in the catalog below;
- `geo/<resource>.geo.json`, `animations/<resource>.animation.json`, and
  `textures/entity/<resource>.png` for GeckoLib;
- `source/` with preserved PSK/PSA and glTF exports, skeleton data, transforms,
  extraction logs, and input hashes;
- `previews/` generated from the exported model and animation data;
- `build_report.json` with source hashes, clip inventory and conversion data;
- `validation.json` with measured hierarchy, binding and animation checks.

Keep bone names, pivots and parent links stable when editing cubes. Imported
clips already target those bones. Animation-name matching does not create entity
behavior, sounds, physics, AI, or Unreal notify handling.

The models are automated cuboid approximations with generated palettes, except
the four hand-made species above. Review shoulders, mouths, wing joints and other
silhouettes before final art approval. Loop flags are inferred from clip names.

## Catalog

The resource stem expands to `geo/<stem>.geo.json`,
`animations/<stem>.animation.json`, and `textures/entity/<stem>.png` inside the
species directory.

| Species directory | ARK asset | Blockbench project | Resource stem | Bones | Cubes | Clips |
| --- | --- | --- | --- | ---: | ---: | ---: |
| Acrochantosaur | `Acro_Mesh` | `Acrochantosaur_GeckoLib.bbmodel` | `acrochantosaur` | 306 | 418 | 44 |
| Allosaurus | `Allosaurus` | `Allosaurus_GeckoLib.bbmodel` | `allosaurus` | 79 | 167 | 32 |
| Ankylosaurus | `Ankylo` | `Ankylosaurus_GeckoLib.bbmodel` | `ankylosaurus` | 76 | 396 | 33 |
| Archaeopteryx | `Archaeopteryx` | `Archaeopteryx_GeckoLib.bbmodel` | `archaeopteryx` | 135 | 494 | 35 |
| Argentavis | `Argentavis` | `Argentavis_GeckoLib.bbmodel` | `argentavis` | 131 | 421 | 36 |
| Brontosaur | `Sauropod` | `Brontosaur_GeckoLib.bbmodel` | `brontosaur` | 65 | 301 | 35 |
| Carnotaurus | `Carno` | `Carnotaurus_GeckoLib.bbmodel` | `carnotaurus` | 67 | 162 | 31 |
| Ceratosaurus | `CeratosaurusAA_Mesh` | `Ceratosaurus_GeckoLib.bbmodel` | `ceratosaurus` | 113 | 235 | 53 |
| Cnidaria | `Cnidaria` | `Cnidaria_GeckoLib.bbmodel` | `cnidaria` | 85 | 292 | 5 |
| Deinosuchus | `Deinosuchus_TLC_Rig` | `Deinosuchus_GeckoLib.bbmodel` | `deinosuchus` | 63 | 190 | 46 |
| Dilophosaur | `Dilo` | `Dilophosaur_GeckoLib.bbmodel` | `dilophosaur` | 105 | 275 | 31 |
| Direwolf | `Direwolf_New` | `Direwolf_GeckoLib.bbmodel` | `direwolf` | 89 | 298 | 34 |
| Dragon | `Dragon` | `Dragon_GeckoLib.bbmodel` | `dragon` | 149 | 412 | 30 |
| Giganotosaur | `Giganotosaurus` | `Giganotosaur_GeckoLib.bbmodel` | `giganotosaur` | 90 | 247 | 31 |
| Kaprosuchus | `Kaprosuchus` | `Kaprosuchus_GeckoLib.bbmodel` | `kaprosuchus` | 78 | 275 | 37 |
| Liopleurodon | `Liopleurodon` | `Liopleurodon_GeckoLib.bbmodel` | `liopleurodon` | 43 | 133 | 25 |
| Lystrosaurus | `Lystrosaurus` | `Lystrosaurus_GeckoLib.bbmodel` | `lystrosaurus` | 72 | 156 | 33 |
| Mammoth | `SK_Mammoth_new` | `Mammoth_GeckoLib.bbmodel` | `mammoth` | 93 | 98 | 41 |
| Megalocerus | `Stag` | `Megalocerus_GeckoLib.bbmodel` | `megalocerus` | 59 | 397 | 36 |
| Megalodon | `Megalodon` | `Megalodon_GeckoLib.bbmodel` | `megalodon` | 28 | 103 | 23 |
| Megapithecus | `Gorilla` | `Megapithecus_GeckoLib.bbmodel` | `megapithecus` | 93 | 93 | 28 |
| Mosasaurus | `Mosasaurus` | `Mosasaurus_GeckoLib.bbmodel` | `mosasaurus` | 30 | 126 | 24 |
| Paraceratherium | `Paraceratherium` | `Paraceratherium_GeckoLib.bbmodel` | `paraceratherium` | 84 | 222 | 32 |
| Parasaur | `Para` | `Parasaur_GeckoLib.bbmodel` | `parasaur` | 85 | 279 | 35 |
| Pegomastax | `Pegomastax` | `Pegomastax_GeckoLib.bbmodel` | `pegomastax` | 87 | 261 | 42 |
| Piterodon | `Ptero` | `Piterodon_GeckoLib.bbmodel` | `piterodon` | 133 | 287 | 57 |
| Plesiosaur | `Plesiosaur` | `Plesiosaur_GeckoLib.bbmodel` | `plesiosaur` | 42 | 163 | 22 |
| Quetzal | `Quetzalcoatlus` | `Quetzal_GeckoLib.bbmodel` | `quetzal` | 133 | 355 | 34 |
| Ravager | `CaveWolf` | `Ravager_GeckoLib.bbmodel` | `ravager` | 93 | 274 | 73 |
| Sabertooth | `Saber` | `Sabertooth_GeckoLib.bbmodel` | `sabertooth` | 91 | 71 | 35 |
| Sarco | `Sarco-New` | `Sarco_GeckoLib.bbmodel` | `sarco` | 74 | 191 | 41 |
| Spinosaurus | `Spino` | `Spinosaurus_GeckoLib.bbmodel` | `spinosaurus` | 91 | 258 | 49 |
| Terrorbird | `TerrorBird` | `Terrorbird_GeckoLib.bbmodel` | `terrorbird` | 59 | 317 | 33 |
| Therezinosaur | `Therizinosaurus` | `Therezinosaur_GeckoLib.bbmodel` | `therezinosaur` | 88 | 302 | 37 |
| Titanoboa | `BoaFrill` | `Titanoboa_GeckoLib.bbmodel` | `titanoboa` | 55 | 198 | 21 |
| Titanosaur | `Titanosaur` | `Titanosaur_GeckoLib.bbmodel` | `titanosaur` | 66 | 319 | 35 |
| Triceratops | `Trike` | `Triceratops_GeckoLib.bbmodel` | `triceratops` | 96 | 258 | 41 |
| Tusoteuthis | `Tusoteuthis` | `Tusoteuthis_GeckoLib.bbmodel` | `tusoteuthis` | 79 | 295 | 29 |
| Tyranosaur | `Rex` | `Rex_Ravager_GeckoLib.bbmodel` | `rex_ravager` | 75 | 232 | 34 |
| Unicorn | `Equus` | `Unicorn_GeckoLib.bbmodel` | `unicorn` | 77 | 148 | 42 |
| Velociraptor | `Raptor` | `Velociraptor_GeckoLib.bbmodel` | `velociraptor` | 79 | 226 | 35 |

Folder spellings are stable project identifiers. `Velociraptor` uses ARK's
Raptor, `Piterodon` is the Pteranodon project, `Tyranosaur` is Tyrannosaurus rex,
`Therezinosaur` is Therizinosaurus, and `Acrochantosaur` is Acrocanthosaurus.
The Acrocanthosaurus and Ceratosaurus assets originate from ARK Additions.

## Species-specific refinement notes

- **Megalocerus:** antlers are separated by source mesh topology and fitted as
  connected height bands to preserve branching gaps. All antler cubes remain on
  `c_neck3`.
- **Unicorn:** reduced slice counts and trimmed 5th/95th-percentile bounds make
  the body and limbs lighter. The horn and original rig remain attached.
- **Mammoth (hand-made):** 98 boxes fitted to the ARK mesh's outline: a domed
  skull over the neck dip, the shoulder hump, a rump sloping to the tail, hair
  fringes with cut-out tips along the flanks, rump, chest and jaw, a trunk of seven
  rings and tusks bent along a spline. Its skin paints strands and layered locks
  texel by texel in world space, 1 texel per runtime unit, in the five variants.
  `geo/mammoth.geo.json` stays the procedural fit and is only read for its rig.
- **Megapithecus (hand-made):** 93 boxes on the knuckle-walking bind pose: the
  back rising to the hump, deltoids, long-haired forearms, fists of curled
  fingers, a crested head with deep-set forward-facing eyes, a broad nose and a
  jaw designed shut as the idle holds it (its fangs show when it roars). Bare
  face, chest, hands and feet; a feathered silver saddle on the lower back. The
  face is painted at 2 texels per unit.
- **Sabertooth (hand-made):** 71 boxes: a deep chest, high withers and a waist
  tucking up to heavy haunches, a shaggy mane in cut-out fringes from the cheeks
  over the shoulders whose hem takes the coat's colour, a long ringed tail with a
  dark tip. The face, painted at 2 texels per unit, has forward-facing eyes beside
  a narrow nose bridge, whisker pads, a pink-brown nose and rounded ears; the two
  sabers hang from the upper jaw past a narrow chin, the jaw designed shut as the
  idle holds it. Stripes, forehead and cheek marks are drawn as pixel runs.
- **Ankylosaurus (hand-made):** 396 boxes: a shell of five tiers per segment
  (dome, body, flaring rim, lower body, belly) in bands of osteoderms, and every
  spike of the ARK mesh measured and rebuilt as a tapering stack: the median row
  fanning back and forward, the rows beside it and along the rim, the long neck
  spikes, the saw along the tail and the horned lobes of the club. The beaked
  head carries its horns and the two spikes that hang from the jaw, designed shut
  as the idle holds it.
