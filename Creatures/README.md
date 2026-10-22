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

The Mammoth, the Megapithecus, the Sabertooth, the Ankylosaurus, the
Tyrannosaurus, the Triceratops, the Direwolf, the Parasaur, the Velociraptor,
the Spinosaurus, the Allosaurus, the Carnotaurus, the Giganotosaur, the
Ceratosaurus, the Acrocanthosaurus and the Dilophosaur are hand-made:
`scripts/handmade_models.py` builds their boxes, skins, Blockbench
projects and previews from `scripts/handmade_<id>.py` on the unchanged rig and
clips, and the workflow runs it again after refitting them. Rebuild and import
them with:

```powershell
python scripts/handmade_models.py mammoth megapithecus sabertooth ankylosaurus tyrannosaurus triceratops direwolf parasaur velociraptor spinosaurus allosaurus carnotaurus giganotosaurus ceratosaurus acrocanthosaurus dilophosaur
python Ark/tools/import_creatures.py --species mammoth megapithecus sabertooth ankylosaurus tyrannosaurus triceratops direwolf parasaur velociraptor spinosaurus allosaurus carnotaurus giganotosaurus ceratosaurus acrocanthosaurus dilophosaur
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
the sixteen hand-made species above. Review shoulders, mouths, wing joints and other
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
| Direwolf | `Direwolf_New` | `Direwolf_GeckoLib.bbmodel` | `direwolf` | 89 | 79 | 34 |
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
| Parasaur | `Para` | `Parasaur_GeckoLib.bbmodel` | `parasaur` | 85 | 161 | 35 |
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
| Triceratops | `Trike` | `Triceratops_GeckoLib.bbmodel` | `triceratops` | 96 | 211 | 41 |
| Tusoteuthis | `Tusoteuthis` | `Tusoteuthis_GeckoLib.bbmodel` | `tusoteuthis` | 79 | 295 | 29 |
| Tyranosaur | `Rex` | `Rex_Ravager_GeckoLib.bbmodel` | `rex_ravager` | 75 | 209 | 34 |
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
- **Tyrannosaurus (hand-made):** 209 boxes, 1 texel per runtime unit: a trunk of
  five tiers per segment measured on the ARK mesh's cross-sections, a neck of
  sloped slabs laid along its back and throat lines, drumstick thighs, long shins
  and three-toed feet, two-fingered arms and a tail tapering in two segments per
  bone. The skull is broad over the jaw muscles, with a brow ledge and horned
  brows over the eye, a cheek below it and a snout narrowing to its ridge; the
  teeth are cut-out rows hanging from the lip outside the narrower lower jaw,
  which is designed shut as the idle holds it. A ridge of scutes runs from the
  crown to the hips and small spikes line the end of the tail. Its skin: pebbled
  scales, a dark back lightening down the flanks, tiger bands drawn as pixel runs
  that sweep back down the flanks and ring the tail, paired osteoderms along the
  spine, a pale underside with belly scutes and throat folds, lip scales, a dark
  band back from the eye and the nostrils.
- **Triceratops (hand-made):** 211 boxes: a five-tier barrel of a body, columnar
  legs bowed at the elbows with hoofed toes, and a tapering tail. The frill is a
  fan of radial plates laid in its dished surface, its rim carrying the ARK
  mesh's spikes; the brow, nose and frill horns follow the mesh's measured centre
  lines in tapering spans. The face, frill and horns are painted at 2 texels per
  unit; the hooked beak narrows in steps and is designed shut in the idle (the
  ARK idle holds it a little open). Its skin: big pebbled scales with broad
  plates along the spine, dark saddles as pixel runs, pale flank speckles, a
  wrinkled pale underside, ringed legs; the frill coloured per variant with a
  light rib down every plate, a row of ringed spots and a dark rim; horns ridged
  and dark at the base, pale to the tip.
- **Direwolf (hand-made):** 79 boxes, 1 texel per runtime unit, fitted to the
  ARK mesh's fur coat: a solid core per spine segment under a cape that hangs
  over the upper flank and a fringe under the belly, both cut into ragged locks
  at their hems, so the dark guard hairs fall over the grey flank and the buff
  belly fur hangs below it. The coat rises over the withers into a mane and wraps
  the back of the head as a hood, its crest standing up behind the ears and a
  ruff hanging from the throat to the chest; shaggy breeches over the thighs and
  a bushy brush of a tail with a dark top and tip. The head, painted at 2 texels
  per unit, has forward-facing eyes beside a narrow bridge over the broader upper
  lip, a black nose, pale brows and cheek tufts, stepped ears pale inside and two
  fangs over the narrow chin; the jaw is designed shut as the idle holds it.
- **Parasaur (hand-made):** 161 boxes: a trunk of five and six tiers per segment
  measured on the ARK mesh's cross-sections, a tail tapering in two segments per
  bone, a neck that runs forward from the chest and turns up into the back of the
  skull, thick thighs over long shins, three-toed hoofed feet with a heel spur,
  and arms with two spurs, a mitten hand and four fingers. The head, crest and
  ridge are painted at 2 texels per unit: a skull broad over the cheeks and
  narrower at the eyes, a snout stepping down to the duck bill, a lower bill shut
  as the idle holds it with two keratin points, and the crest swept back along
  the ARK mesh's centre line. The dorsal ridge is a cut-out card per bone from the
  neck to the tail tip, with half as many blades as the mesh's spikes so it stays
  crisp at game scale, and spikes pointing back up the nape. Its skin: pebbled
  scales over a dark back lightening down the flanks, dark bands drawn as pixel
  runs, pale flank spots, a pale underside with belly scales, a dark stripe back
  through the eye; the crest and ridge in an accent per variant (orange-red,
  coral, crimson, gold, teal, bone) with a pale line along the crest's groove.
- **Allosaurus (hand-made):** 233 boxes: a trunk of five tiers per segment and a
  tail of three measured on the ARK mesh's cross-sections, an S-curved neck of
  sloped slabs, heavy thighs over long shins and three-toed feet, and long arms
  with three hooked claws. The head is painted at 2 texels per unit: a long
  narrow skull, a red blade of a horn before each eye over a ledged brow,
  knobbed nasal ridges, cut-out rows of teeth and a jaw shut as the idle holds
  it. Paired rows of scutes run from the crown to the hips. Its skin: pebbled
  scales, narrow dark bands drawn as pixel runs, a pale underside and throat,
  a dark stripe back through the eye.
- **Carnotaurus (hand-made):** 197 boxes: a barrel trunk of five tiers per
  segment, a thin tapering tail, a short steep neck, slim thighs on the twist
  bone over long shins, and stub arms with tiny claws. The head is painted at 2
  texels per unit: short, deep and narrow, a thick horn sweeping out and up over
  each eye, cut-out rows of teeth and a jaw shut as the idle holds it. Low
  scutes run in pairs along the back and a row of bosses down each flank. Its
  skin: red-brown (or umber, tan, charcoal) scales with short dark bands, a pale
  underside and throat.
- **Giganotosaur (hand-made):** 237 boxes: a deep trunk of five tiers per
  segment, a long tail whose segments are pitched along its fall so they meet
  without steps, a thick neck rising to a long low skull broad at the cheeks,
  a short horn before each eye, knobs along the snout, cut-out rows of teeth and
  a jaw designed shut (the idle holds it a little open, JAW_REFERENCE 42 degrees).
  A single row of spikes runs from the crown to the tail tip, tallest on the
  neck. Its skin: slate (or umber, rust, sand) scales under broad dark saddles.
- **Ceratosaurus (hand-made):** 211 boxes on the suffixed `_L`/`_R` rig: a deep
  trunk, a long level tail, a short neck, a deep skull with a red blade of a
  horn on the nose and a knob over each eye, a jaw and dewlap shut as the idle
  holds them, short arms with four small fingers. Tall bone-coloured spikes run
  along the midline from the nape to the tail tip and a row stands out of each
  side of the tail. Its skin: olive (or umber, rust, charcoal) with narrow bands.
- **Acrocanthosaurus (hand-made):** 268 boxes: a trunk of six tiers per segment
  rising into the tall narrow ridge over the hips and shoulders, a tail of four
  tiers per bone tapering from it, a steep neck, heavy thighs, thick calves, long
  feet with a dewclaw and three-clawed arms. The long skull has rugose brow
  ridges, cut-out rows of teeth and a lower jaw (the rig's `lowerLipJoint0_M`,
  pivoted at the chin) shut as the idle holds it. Tall thin spines stand along
  the nape and smaller ones along the end of the tail. Its skin: umber (or
  olive, rust, slate) under broad dark bands down the ridge and flanks.
- **Dilophosaur (hand-made):** 185 boxes: a short trunk on long slim legs, a
  thin level tail, an S-curved neck and a narrow head designed symmetric in its
  own frame (the bind pose holds it turned 4 degrees and shifted). The head,
  crests and frill are painted at 3 texels per unit: twin crests cut to their
  profile, cut-out teeth, a jaw shut as the idle holds it. The frill is cut-out
  membrane cards on each side's own rib bones (frill A to D), trimmed to a
  scalloped disc round the neck with a spine at each rib's end, so it folds and
  spreads with the clips; quills run down the back. Its skin: green (or olive,
  umber, sand), the frill red to yellow (blue on the sand variant).
