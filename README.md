# ARK → GeckoLib dinosaur workflow

To play the mod on Windows, double-click **Start-Ark-Mod.bat** in this folder. It builds and launches Minecraft with the mod; VS Code is not required. See [the mod README](Ark/README.md) for gameplay and configuration.

The optional Xaero difficulty map, shader loader, shader pack and ambience mods are installed locally. **Install-Ark-Extras.bat** reproduces the pinned downloads on another checkout. The map is temporarily open to everyone while taming is deferred. See [client pack and map instructions](Ark/docs/client-pack.md).

The editable projects in `Creatures/` retain the installed ARK mesh skeletons and original animation clips bound to those exact bone names. `Tyranosaur` retains its original `rex_ravager` resource filenames and geometry identifier. Nineteen creatures are integrated into the playable mod; see [creature expansion](Ark/docs/creature-expansion.md).

The newer [ice, flying, aquatic and swamp collection](Creatures/Collection/README.md) adds twenty-one detailed source projects. These models have no runtime entity, behavior or spawn registration. A reserved Dreadnoughtus folder records the missing Ascended source. The table below lists the nineteen runtime creatures.

| Folder | ARK asset | Common name |
| --- | --- | --- |
| Titanosaur | Titanosaur | Titanosaur |
| Giganotosaur | Giganotosaurus | Giganotosaurus |
| Therezinosaur | Therizinosaurus | Therizinosaurus |
| Brontosaur | Sauropod | Brontosaurus |
| Triceratops | Trike | Triceratops |
| Velociraptor | Raptor | ARK Raptor |
| Argentavis | Argentavis | Argentavis |
| Piterodon | Ptero | Pteranodon |
| Tyranosaur | Rex | Tyrannosaurus rex |
| Spinosaurus | Spino | Spinosaurus |
| Parasaur | Para | Parasaur |
| Ceratosaurus | CeratosaurusAA_Mesh (ARK Additions) | Ceratosaurus |
| Dilophosaur | Dilo | Dilophosaurus |
| Acrochantosaur | Acro_Mesh (ARK Additions) | Acrocanthosaurus |
| Allosaurus | Allosaurus | Allosaurus |
| Ankylosaurus | Ankylo | Ankylosaurus |
| Carnotaurus | Carno | Carnotaurus |
| Pegomastax | Pegomastax | Pegomastax |
| Lystrosaurus | Lystrosaurus | Lystrosaurus |

All folders listed above are inside `Creatures/`. Folder spellings follow the request. ARK's Raptor is used for the requested Velociraptor; `Acrochantosaur` maps to the mod entity `acrocanthosaurus`.

## Open and edit

Open a folder's `*_GeckoLib.bbmodel` in Blockbench with the GeckoLib plugin enabled. The file contains the cube mesh, bone hierarchy, palette texture, and all converted clips. Select a clip in the Animate workspace to preview it.

Edit cubes within their existing bone groups. Keep bone names, pivots, and parent links to retain the animation binding. Extra decorative cubes can be placed under the appropriate existing bone. Adding a new animated bone requires creating animation tracks for it.

Every folder contains:

- `geo/*.geo.json`: the completed cuboid geometry and original bone hierarchy.
- `animations/*.animation.json`: converted original clips, keyed by original bone names.
- `textures/entity/*.png`: a generated eight-color palette, also embedded in the Blockbench project.
- `source/*.skeleton.geo.json`: the skeleton-only intermediate before adding the mesh.
- `source/skeleton.json`: local bind transforms and world pivots at full conversion precision.
- `source/actorx/`: extracted mesh, animation clips, and missing-track configuration.
- `source/gltf/`: source mesh and skeleton for inspection in other 3D tools.
- `source/logs/`: game input hashes and extraction logs.
- `previews/`: bind-pose renders and an animated GIF rendered from the actual exported JSON.
- `build_report.json` and `validation.json`: source hashes, clip inventory, conversion statistics, and validation results.
- `rebuild.py`: convenience entry point into the shared scripts.

## Rebuild

From this repository, install Python 3.12+ dependencies once:

```powershell
python -m pip install -r scripts/requirements.txt
```

Rebuild all available models from the preserved extracted inputs, without needing to read ARK:

```powershell
python scripts/workflow.py --reuse-exports
```

Extract fresh data from the installed game, rebuild, render previews, and validate:

```powershell
python scripts/workflow.py
```

Rebuild only selected dinosaurs:

```powershell
python scripts/workflow.py --reuse-exports --species Argentavis Piterodon
```

Rebuild Rex alone using the same mesh fitting and animation conversion:

```powershell
python scripts/workflow.py --reuse-exports --species Tyranosaur
```

Revalidate the saved JSON against all source animation frames:

```powershell
python scripts/validate_dinosaurs.py
```

For another game location, supply `--content "D:/SteamLibrary/steamapps/common/ARK/ShooterGame/Content"`. The extractor executable is preserved in `tools/`; another build can be selected with `--umodel`.

Ceratosaurus and Acrocanthosaurus are read from the locally downloaded ARK Additions Workshop item `1522327484`. If needed, set `--workshop "D:/SteamLibrary/steamapps/workshop/content/346110/1522327484/WindowsNoEditor"`. Compressed `.uasset.z` packages are decoded and checked in workspace copies. ARK 404/10 mod export tables are retained; the 405/10 base-game tables are adapted as before.

After rebuilding source projects, update the playable mod's assets from the `Ark` directory:

```powershell
python tools/import_creatures.py
python tools/build_item_assets.py
python tools/verify_assets.py
python tools/verify_expansion.py
```

The expansion's selected runtime clips and behavior mappings are retained in `Ark/tools/expansion_catalog.py`; Java entity defaults are in `Species.java`. Data generation is required when adding an entity or biome preference.

Rebuilds overwrite generated geometry, textures, animation JSON, Blockbench projects and previews. Save manual modeling changes under another name before rebuilding. Preserved input exports are reused unless a fresh extraction is requested.

## Conversion stages and scripts

1. `extract_dinosaurs.py` discovers each requested adult mesh, skeleton and animation packages. It adapts the installed ARK 405/10 package export table in temporary copies, then exports ActorX and glTF with UE Viewer. It never writes to the Steam game directory. An incompatible package header stops extraction instead of guessing offsets.
2. `ark_geometry.py` reads PSK vertex influences and full-precision bind transforms. glTF provides a cross-check of the bone names and hierarchy. World pivots are reconstructed with forward kinematics. Scale is 0.1 Blockbench units per Unreal centimeter, matching the existing workflow.
3. `build_dinosaurs.py` writes a skeleton-only GeckoLib JSON and partitions source triangles by material and bone influence. The original nineteen use oriented cuboid slices. The source-only collection uses `mesh_detail.py` to sample triangle surfaces into finer cells and merge adjacent cells into cuboids while retaining bone ownership. This preserves curved tusks, antler branches, fins and tentacles. Secondary Gorilla components share the original animation skeleton; Unicorn's horn uses its exported attachment socket.
4. `ark_animation.py` reads original PSA clips and their `.config` missing-track rules. It retains exact original names and parent links. Ceratosaurus, Acrocanthosaurus and the source-only collection retain native local bind axes and bone rotations so nonuniform animated scaling is preserved. Earlier models keep baked bind axes. Full affine hierarchy checks verify the scaled models. Unused extra animation skeleton bones are listed in the report and excluded when the mesh has no matching bone.
5. Linear key reduction keeps rotation error within 0.03 degrees and translation error within 0.0005 model units per channel. Source sample timing and clip duration are retained. Loop flags are inferred from names and should be reviewed for gameplay use.
   An exception is Rex's `Rex-Blink`: ARK sets `RateScale=0`. The converter uses its stored native `SequenceLength` to make an editable pose clip and records the timing adjustment in `build_report.json`.
6. `preview_dinosaurs.py` decodes the resulting JSON and renders static/motion previews. `validate_dinosaurs.py` checks all source frames, hierarchy, Blockbench UUID binding, geometry equivalence, and accumulated world-space bone error. `workflow.py` coordinates the entire process.

## Integration scope

The original clips are already assigned to the matching node structure in both output formats. Copy the geometry, animation and texture resources into your mod's asset namespace, then have your GeckoLib entity model/controller choose the relevant clip names. Bone matching does not create the entity class, movement logic or animation state machine. Unreal animation notifies, sounds, physics, AI and montages are not converted.

These are automatically fitted, editable cuboid models. They approximate the original smooth skin with rigid blocks; they are not a hand-sculpted recreation of the Tyrannosaur artwork. Palette textures are newly generated. Inspect wing folds, joints, mouths and intersecting cubes when doing the final art pass. The original extracted source meshes remain available for comparison.

Validation runs in Python against the exported resources. It is not a Minecraft runtime or Blockbench UI test. Consult each folder's `validation.json` for measured errors and the exact checks performed.

## Format references

- [UE Viewer ActorX structures](https://github.com/gildor2/UEViewer/blob/master/Exporters/Psk.h) and [glTF coordinate conversion](https://github.com/gildor2/UEViewer/blob/master/Exporters/ExportGLTF.cpp).
- [GeckoLib Blockbench plugin](https://github.com/JannisX11/blockbench-plugins/blob/master/plugins/geckolib/geckolib.js): geometry format and animation X/Y sign conversion.
- [GeckoLib animation processor](https://github.com/bernie-g/geckolib-core/blob/master/src/main/java/software/bernie/geckolib3/core/processor/AnimationProcessor.java): animation channels applied to matching bones relative to their initial pose.

The package adapter is specific to this installed ARK build. Tool provenance and binary hash are in `tools/README.md`. `.work/` is disposable extraction cache; generated species folders contain the inputs needed for offline rebuilds.
