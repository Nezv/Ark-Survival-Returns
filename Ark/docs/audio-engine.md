# Unified audio engine

Ark Survival Returns embeds three complementary audio systems behind one client-only runtime:

- Sound Physics Remastered supplies OpenAL EFX reverberation, occlusion, absorption, and reflected directionality.
- Presence Footsteps supplies the positional material recordings used by the stride engine.
- AmbientSounds supplies the streamed environmental recordings selected from world context.

The Java runtime is native NeoForge 26.1.2 code. It does not load Fabric, CreativeCore, or a second mod container. Dedicated servers can load the jar without touching client audio classes.

## Resource-pack API

Override `assets/arksurvivalreturns/audio/catalog.json` in a resource pack to change the system without recompiling. `footsteps` defines event IDs and gain/pitch behavior. `soundTypes` maps the public static names from Minecraft's `SoundType` class to those materials. `ambience` entries are eligible when every supplied condition matches; omitted conditions are wildcards.

Sound event IDs may target any namespace present in the active resource stack. This makes dinosaur-specific packs possible without hard-coding them into the engine. Long ambience files should be declared with `"stream": true` in their namespace's `sounds.json`.

The physics defaults deliberately skip events whose IDs begin with `ambient.` to avoid spending reflection rays on non-positional beds. Positional creature calls and footsteps receive the full physics path.

## Provenance and license

The embedded physics source is derived from Sound Physics Remastered at commit `ce6c71b8a5fcffc50e75d0407c9c516b0bfca515` (GPL-3.0). Footstep assets are from Presence Footsteps at commit `975736d208e0fd7edb20b49eb57ef2c4f4cafda4` (MIT). Ambient assets are from AmbientSounds at commit `c9a07218c5581171adf4b0a9575f5a506eb4fd27` (LGPL-3.0). The corresponding license texts are retained under `third_party/`.

## Original creature audio

All 41 registered creatures use species-specific sound events from the installed Steam copy of ARK: Survival Evolved and ARK Additions (workshop 1522327484). The mod includes 1,106 original recordings, about 19.2 MiB, with 492 event definitions covering attacks, hurt, death, sleep, waking, warning calls, footsteps, eating and flight effects. Creature playback uses the original pitch. Sleep and wake playback is paced to avoid overlapping recordings.

Available originals cover attacks for 41 species, hurt for 40, death for 39 and sleep for 29. The installed Ceratosaurus assets have no dedicated hurt or death recording; Kaprosuchus has no dedicated death recording. Missing categories remain silent. Sleep uses the creature's original torpid idle or breathing recordings where available, including idle cues explicitly referenced by original torpid-loop animations. Mosasaurus and Deinosuchus retain the shared cues referenced by their original character and animation packages.

`assets/arksurvivalreturns/sounds.json` maps the events to recordings under `sounds/creature/`. The small `audio/creature_catalog.json` records available categories and maximum clip durations for the common/server runtime. `audio/creature_sources.json` preserves source package paths, dependency references, source and output hashes, sample rates, channels and missing categories.

Reimport with `python Ark/tools/import_creature_audio.py` from the repository root; use `--inspect` to check source coverage without replacing resources. The script requires NumPy and SoundFile and accepts `--content` and `--workshop` for alternate installations. It reads the game files without modifying them, unpacks the workshop's chunked zlib data in memory, extracts complete Ogg streams, and decodes every recording before replacing resources. Mono originals are copied verbatim; stereo originals are downmixed to mono at their original sample rate for positional playback. The recordings retain their ARK/ARK Additions provenance.
