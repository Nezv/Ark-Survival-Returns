# Optional client pack and difficulty map

Installed and checked on 20 September 2026 for Minecraft 26.1.2 / NeoForge 26.1.2.109. The normal `Start-Ark-Mod.bat` launcher loads the extras. No separate launcher or VS Code is needed.

| Component | Pinned version | Purpose |
|---|---|---|
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map/version/9x5f2L12) | 1.46.0, NeoForge 26.1.2 | Fullscreen terrain map; includes XaeroLib |
| [Xaero World Map Bridge](https://modrinth.com/mod/xaero-world-map-bridge/version/4UvyllQl) | 0.1.2, NeoForge 26.1.2 | Hook for Ark's colored danger overlay |
| [Sodium](https://modrinth.com/mod/sodium/version/zg4YQ9EL) | 0.9.2, NeoForge 26.1.2 | Rendering performance and Iris dependency |
| [Iris](https://modrinth.com/mod/iris/version/qE5Y7GrZ) | 1.11.4, NeoForge 26.1.2 | Shader loader |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined/version/111gsk0f) | r5.9 | Shader pack, available in Video Settings → Shader Packs |

The shader ZIP is downloaded but is not forced on. Select **Complementary Reimagined** in Video Settings → Shader Packs; start with its Medium preset and adjust during play. Existing graphics, sound and shader settings are preserved. Environmental ambience, material-aware footsteps and sound physics are built into Ark Survival Returns; do not install their standalone source mods alongside it. Ark's server-side hearing decisions remain governed by its own behavior model.

No terrain generator, extra creature pack, minimap entity radar or combat overhaul was added. The existing biome layout, spawn ecology and combat rules stay consistent with the Ark work.

## Installation and reproducibility

The local installation is already complete. On another checkout, double-click **`Install-Ark-Extras.bat`**, then **`Start-Ark-Mod.bat`**. The installer downloads fixed files from Modrinth's HTTPS CDN, verifies SHA-512 hashes, and reuses matching files. It never overwrites changed files silently or modifies saves/configs.

`config/client-mods.lock.json` records versions, download URLs, file sizes and hashes; the client-and-server gameplay stack (FTB Quests, FTB Teams, FTB Library, FTB XMod Compat and JEI) is pinned the same way in `config/shared-mods.lock.json`. Third-party JARs are kept in `Ark/client-mods` and `Ark/shared-mods`, excluded from source control and from Ark's published JAR. The shader resides in `Ark/run/shaderpacks`. The Gradle `immersiveClient` runtime includes the presentation mods only for `runClient`; `runServer`, GameTests and the interactive client all load the shared stack. Installing the Ark JAR into another launcher does not automatically install either pack; copy the pinned JARs into that instance's `mods` folder and the shader ZIP into `shaderpacks`.

Old standalone AmbientSounds, CreativeCore, Presence Footsteps and Sound Physics Remastered JARs are ignored by the managed development runtime because those systems now live inside Ark Survival Returns. They can be deleted after Minecraft and Gradle release their file handles.

Offline verification:

```powershell
./Install-Ark-Extras.ps1 -VerifyOnly
./Start-Ark-Mod.ps1 -Check
# From Ark:
python tools/verify_client_pack.py
```

The selected Iris release declares Sodium `zg4YQ9EL`, which is the stable NeoForge 0.9.2 artifact this pack installs; the mismatched Fabric Sodium metadata link from the earlier release line no longer applies. This metadata check is not a substitute for a graphical compatibility test.

Xaero is pinned to 1.46.0 with bridge 0.1.2, whose bytecode-verified compatibility baseline covers the 1.46 release line. The installed bridge's bytecode insertion descriptor matches exactly one invocation in the installed Xaero `GuiMap` renderer; camera, scale and viewed-dimension fields also exist. The bridge labels NeoForge support experimental. Keep the pair pinned until a later combination is checked. [Bridge API and compatibility](https://github.com/billstark001/xaero-world-map-bridge).

## Difficulty map and unlock

The **whole Xaero fullscreen map is open while `progression.mapRequiresUnlock` is `false`** (the development default). Set it to `true` and restart/rejoin to restore the progression gate; the following unlock rules apply only when that gate is enabled. Pressing its map key (normally **M**) displays the unlock condition. The milestone is **taming a creature that originated in a difficulty-5 region**, recorded when the creature first spawned — not a creature whose individual level happens to be 5.

Completing the first tame whose saved origin band is 5 grants the entitlement automatically, and the same completion awards the hidden `journal/rank5_tame` discovery advancement. A rank-1 creature never grants the map, however it is transported later. Operators can still grant or revoke access for testing:

```text
/arkmap unlock
/arkmap status
/arkmap lock
/arkmap unlock PlayerName
```

All three commands also accept a player argument; commands require vanilla gamemaster permissions. The entitlement is stored per player UUID in world SavedData and survives death, relogging and dimension changes. With the gate enabled, a new world begins locked. Revoking access closes an already-open map. A server-to-client packet synchronizes the permission and the saved difficulty origin/width; locked players receive zeroed profile fields. Client state clears on disconnect. There is no client-to-server unlock message.

When map access is available, the **Difficulty: off/on** button at the top left toggles a translucent filter; it starts off each client session. Colors cover only explored terrain from Xaero's active dimension/cave-layer cache. Unknown territory stays untouched. The legend and cursor difficulty appear only while enabled, and the cursor requires explored terrain. Panning and zooming use the same server difficulty formula.

A bounded raster reads cached exploration heights without loading chunks or generating terrain. Separate GUI strata put the filter above the terrain image. The exploration mask refreshes at most every 250 ms; coarse pixels spanning unknown chunks are omitted, conservatively hiding the filter at extreme zoom. Blocking the map screen does not disable Xaero's background terrain recording.

The entitlement is granted by the taming completion path through `DangerMapSync.setUnlocked`. Each creature saves the danger band of the position where it first appeared, so relocating an animal never changes its provenance, and operators can always fall back to `/arkmap`.

## Verification and playtest

### Iris development-run crash workaround

The user's 7 September crash occurred after enabling Complementary, in `GlCommandEncoder.validateDraw`, with `Index 1 out of bounds for length 1`. This matches [Iris issue 3304](https://github.com/IrisShaders/Iris/issues/3304): Iris supplies a short vertex-binding array while the development validator checks 16 slots. The local Minecraft source confirms that `neoforge.disableGlValidation` controls this validator. The interactive client run now sets `-Dneoforge.disableGlValidation=true`, matching the reported workaround. Iris, Sodium, the selected shader and graphics preferences remain intact. This disables development GL validation for that run; it does not patch Iris itself. The generated client JVM arguments and build checks pass; confirming shader rendering after this change still requires a client playtest.

The build, 41 JUnit tests, all 39 headless GameTests, installer checksum checks, classpath checks, and client preparation pass. Automated coverage includes regional raster correspondence across panning/zoom, invalid-view bounds, map entitlement save/load, player isolation, revocation, network codec round trips, and the rank-5-origin tame unlock. Datagen and the dedicated GameTest server run without Xaero or the presentation mods; the shared journal stack loads on both sides.

No interactive client was launched. Check these in the normal launcher:

1. With the default development settings, M opens immediately. To test the progression gate, enable `progression.mapRequiresUnlock` and restart/rejoin; `/arkmap unlock` grants access, `/arkmap lock` revokes it, and taming a creature from a rank-5 region grants it through normal play.
2. Toggle Difficulty on and off. Confirm only explored map receives the tint, including after exploring more terrain without moving the map camera. Compare the cursor's difficulty with the biome announcement at the same coordinates, including negative coordinates. Pan, zoom and change viewed dimensions; inspect the legend against Xaero controls.
3. With the progression gate enabled, reconnect and respawn: the saved unlock survives. A second player stays locked until independently granted access.
4. Enable the shader pack and inspect dinosaur materials, water, shadows and frame rate. Confirm warning calls remain audible with both sound mods enabled.

The map gate is a progression feature for this client pack, not an anti-cheat boundary against modified clients.

## Habitat markers removed

The flying and land habitat marker overlays were removed together with the habitat stores. The map now
shows only the difficulty tint; finding nests and herds is done by exploring. See
[Flying Ecosystem](flying-ecosystem.md) and [collection ecosystems](collection-ecosystem.md) for the
current spawn and nesting rules.

## Dev settings

See the [dev dependency list](dev-dependencies.md) for the 16 GB / 24-chunk settings and River Redux
availability.
