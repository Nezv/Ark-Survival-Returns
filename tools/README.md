# ARK extractor

`umodel.exe` is the UE Viewer executable used for this workflow, preserved from the existing local ARK export tools. The original game files are read only. A package-header adapter in `scripts/extract_dinosaurs.py` writes normalized copies under `.work/normalized` before calling UE Viewer.

SHA-256: `44eca8f7e799b29b9864db69382ab6da38265acd0cb541677b57ad3e534e5a4f`.

The adjacent local source checkout identifies itself as [floxay/UEViewer](https://github.com/floxay/UEViewer), commit `604c387c2ab26f0ba91be706eb6b662cd0a5a4a9`. The executable was not rebuilt in this task, so that checkout is provenance context rather than a claim of a reproducible binary build. This fork exports PSA scale keys. Its license is preserved in `UEViewer-LICENSE.txt`.

Upstream [UE Viewer](https://github.com/gildor2/UEViewer) documents the [ActorX export formats](https://github.com/gildor2/UEViewer/blob/master/Exporters/Psk.h). Keep the executable hash with regenerated extraction manifests, since changing extractor builds can change coordinate or scale behavior.

The glTF mesh export log includes `ERROR: glTF animation could be exported from mesh viewer only.` in this executable. The mesh and skeleton still export successfully. This workflow takes animations from the separate PSA export, not glTF animation. Missing material warnings are also expected with `-notex`; the delivered cuboid models use generated palette textures.
