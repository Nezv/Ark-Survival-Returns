# Unified audio engine

Ark Survival Returns embeds three complementary audio systems behind one client-only runtime:

- Sound Physics Remastered supplies OpenAL EFX reverberation, occlusion, absorption, and reflected directionality.
- Presence Footsteps supplies the positional material recordings used by the stride engine.
- AmbientSounds supplies the streamed environmental recordings selected from world context.

The Java runtime is native NeoForge 26.2 code. It does not load Fabric, CreativeCore, or a second mod container. Dedicated servers can load the jar without touching client audio classes.

## Resource-pack API

Override `assets/arksurvivalreturns/audio/catalog.json` in a resource pack to change the system without recompiling. `footsteps` defines event IDs and gain/pitch behavior. `soundTypes` maps the public static names from Minecraft's `SoundType` class to those materials. `ambience` entries are eligible when every supplied condition matches; omitted conditions are wildcards.

Sound event IDs may target any namespace present in the active resource stack. This makes dinosaur-specific packs possible without hard-coding them into the engine. Long ambience files should be declared with `"stream": true` in their namespace's `sounds.json`.

The physics defaults deliberately skip events whose IDs begin with `ambient.` to avoid spending reflection rays on non-positional beds. Positional creature calls and footsteps receive the full physics path.

## Provenance and license

The embedded physics source is derived from Sound Physics Remastered at commit `ce6c71b8a5fcffc50e75d0407c9c516b0bfca515` (GPL-3.0). Footstep assets are from Presence Footsteps at commit `975736d208e0fd7edb20b49eb57ef2c4f4cafda4` (MIT). Ambient assets are from AmbientSounds at commit `c9a07218c5581171adf4b0a9575f5a506eb4fd27` (LGPL-3.0). The corresponding license texts are retained under `third_party/`.
