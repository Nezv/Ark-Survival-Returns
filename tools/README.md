# ARK extractor

## Authored dragon animation retarget

`python tools/retarget_dragon.py` adapts the 30 clips in
`Creatures/Dragon/Old/animations/dragon.animation.json` to the edited
`Creatures/Dragon/Dragon/dragon.geo.json`. It uses Python, NumPy, SciPy and Pillow
(the same dependencies as the creature conversion tools). Source artwork is
read only; outputs go to `Creatures/Dragon/Dragon/retargeted/`.

Open `retargeted/Dragon_Wyvern_Retargeted.bbmodel` in Blockbench for the model,
embedded original texture and all 30 editable animation timelines. The paired
`dragon.geo.json`, `dragon.animation.json` and `dragon.png` are the GeckoLib
export. Use the paired geometry: it binds the torso to the moving body and adds
two independently editable wing-claw bones. Clip names and durations remain
compatible with the old dragon's animation catalog; death holds its final pose.

The body, neck, jaw, tail and flight motion are retargeted through calibrated
rotation matrices. Ground movement is rebuilt around wing-claw and hind-foot
contacts, using the source cadence and hind-foot swing phase. Long wing spars
need a new folded-to-raised transition and a braced collapse. This is an adapted
animation set, not a one-to-one preservation of the six-limbed skeleton.

`python tools/validate_dragon_retarget.py` checks the saved files independently:
clip bindings, Blockbench timeline parity, unchanged artwork/bind silhouette,
contact placement, ground clearance at keys and interpolation midpoints, and
loop seams. Results and source hashes are recorded beside the assets.
`python tools/preview_dragon.py --overview` renders textured GIFs and contact
sheets from the exported files. No command installs assets into the mod;
runtime behavior and artistic approval require a separate integration review.

## Extractor provenance

`umodel.exe` is the UE Viewer executable used for this workflow, preserved from the existing local ARK export tools. The original game files are read only. A package-header adapter in `scripts/extract_dinosaurs.py` writes normalized copies under `.work/normalized` before calling UE Viewer.

SHA-256: `44eca8f7e799b29b9864db69382ab6da38265acd0cb541677b57ad3e534e5a4f`.

The adjacent local source checkout identifies itself as [floxay/UEViewer](https://github.com/floxay/UEViewer), commit `604c387c2ab26f0ba91be706eb6b662cd0a5a4a9`. The executable was not rebuilt in this task, so that checkout is provenance context rather than a claim of a reproducible binary build. This fork exports PSA scale keys. Its license is preserved in `UEViewer-LICENSE.txt`.

Upstream [UE Viewer](https://github.com/gildor2/UEViewer) documents the [ActorX export formats](https://github.com/gildor2/UEViewer/blob/master/Exporters/Psk.h). Keep the executable hash with regenerated extraction manifests, since changing extractor builds can change coordinate or scale behavior.

The glTF mesh export log includes `ERROR: glTF animation could be exported from mesh viewer only.` in this executable. The mesh and skeleton still export successfully. This workflow takes animations from the separate PSA export, not glTF animation. Missing material warnings are also expected with `-notex`; the delivered cuboid models use generated palette textures.
