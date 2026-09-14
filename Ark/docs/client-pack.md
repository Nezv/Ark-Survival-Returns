# Optional client pack and difficulty map

Installed and checked on 7 September 2026 for Minecraft 26.2 / NeoForge 26.2.0.11-beta. The normal `Start-Ark-Mod.bat` launcher loads the extras. No separate launcher or VS Code is needed.

| Component | Pinned version | Purpose |
|---|---|---|
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map/version/pKatKnls) | 1.44.2, NeoForge 26.2 | Fullscreen terrain map; includes XaeroLib 1.7.1 |
| [Xaero World Map Bridge](https://modrinth.com/mod/xaero-world-map-bridge/version/xvbOh80a) | 0.1.1, NeoForge 26.2 | Hook for Ark's colored danger overlay |
| [Sodium](https://modrinth.com/mod/sodium/version/KHPycol7) | 0.9.1, NeoForge 26.2 | Rendering performance and Iris dependency |
| [Iris](https://modrinth.com/mod/iris/version/bXt6zsZT) | 1.11.2, NeoForge 26.2 | Shader loader |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined/version/111gsk0f) | r5.9 | Shader pack, available in Video Settings → Shader Packs |
| [AmbientSounds](https://modrinth.com/mod/ambientsounds/version/odflTtI0) | 6.3.6, NeoForge 26.2 | Environmental ambience |
| [CreativeCore](https://modrinth.com/mod/creativecore/version/Mwk5iw3d) | 2.14.16, NeoForge 26.2 | AmbientSounds dependency |
| [Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered/version/T2rk5I7r) | 1.5.1, NeoForge 26.2 | Sound occlusion, attenuation and reverberation |

The shader ZIP is downloaded but is not forced on. Select **Complementary Reimagined** in Video Settings → Shader Packs; start with its Medium preset and adjust during play. Existing graphics, sound and shader settings are preserved. AmbientSounds offers `/cmdclientconfig` and `/ambient-debug`; keep ambience quiet enough that dinosaur warnings remain audible. Sound Physics changes presentation, while Ark's server-side hearing decisions remain governed by its own behavior model.

No terrain generator, extra creature pack, minimap entity radar or combat overhaul was added. The existing biome layout, spawn ecology and combat rules stay consistent with the Ark work.

## Installation and reproducibility

The local installation is already complete. On another checkout, double-click **`Install-Ark-Extras.bat`**, then **`Start-Ark-Mod.bat`**. The installer downloads fixed files from Modrinth's HTTPS CDN, verifies SHA-512 hashes, and reuses matching files. It never overwrites changed files silently or modifies saves/configs.

`config/client-mods.lock.json` records versions, download URLs, file sizes and hashes. Third-party JARs are kept in `Ark/client-mods`, excluded from source control and from Ark's published JAR. The shader resides in `Ark/run/shaderpacks`. The Gradle `immersiveClient` runtime includes these mods only for `runClient`; dedicated servers, GameTests and datagen use the core mod and GeckoLib. Installing the Ark JAR into another launcher does not automatically install this optional pack; copy the seven pinned JARs into that instance's `mods` folder and the shader ZIP into `shaderpacks`.

Offline verification:

```powershell
./Install-Ark-Extras.ps1 -VerifyOnly
./Start-Ark-Mod.ps1 -Check
# From Ark:
python tools/verify_client_pack.py
```

The selected Iris release's Modrinth dependency link points to Sodium `vf7UgZpC`, which is a Fabric 26.1.2 artifact. That file is deliberately not installed. This pack instead uses stable Sodium 0.9.1 for NeoForge 26.2, satisfying the Iris JAR's declared Sodium version range. This metadata correction is not a substitute for a graphical compatibility test.

Xaero is pinned to 1.44.2 because the bridge's published compatibility matrix covers that series. The installed bridge's bytecode insertion descriptor matches exactly one invocation in the installed Xaero `GuiMap` renderer; camera, scale and viewed-dimension fields also exist. The bridge labels NeoForge support experimental. Keep the pair pinned until a later combination is checked. [Bridge API and compatibility](https://github.com/billstark001/xaero-world-map-bridge).

## Difficulty map and unlock

The **whole Xaero fullscreen map is temporarily open to everyone**, including existing saves, because `progression.mapRequiresUnlock` defaults to `false`. Set it to `true` and restart/rejoin to restore the progression gate. The following unlock rules apply only when that gate is enabled. Pressing its map key (normally **M**) displays the unlock condition. The intended future milestone is **taming a creature originating in a difficulty-5 region**, not taming any creature whose individual level happens to be 5.

There is no taming implementation or taming event listener in this change. Consequently normal play cannot yet unlock the map. For testing, an operator or a singleplayer user with cheats can run:

```text
/arkmap unlock
/arkmap status
/arkmap lock
/arkmap unlock PlayerName
```

All three commands also accept a player argument; commands require vanilla gamemaster permissions. The entitlement is stored per player UUID in world SavedData and survives death, relogging and dimension changes. With the gate enabled, a new world begins locked. Revoking access closes an already-open map. A server-to-client packet synchronizes the permission and the saved difficulty origin/width; locked players receive zeroed profile fields. Client state clears on disconnect. There is no client-to-server unlock message.

When map access is available, the **Difficulty: off/on** button at the top left toggles a translucent filter; it starts off each client session. Colors cover only explored terrain from Xaero's active dimension/cave-layer cache. Unknown territory stays untouched. The legend and cursor difficulty appear only while enabled, and the cursor requires explored terrain. Panning and zooming use the same server difficulty formula.

A bounded raster reads cached exploration heights without loading chunks or generating terrain. Separate GUI strata put the filter above the terrain image. The exploration mask refreshes at most every 250 ms; coarse pixels spanning unknown chunks are omitted, conservatively hiding the filter at extreme zoom. Blocking the map screen does not disable Xaero's background terrain recording.

The future taming system can grant the entitlement through `DangerMapSync.setUnlocked(player, true)` after it verifies the creature's recorded difficulty-5 origin. Recording creature provenance and deciding how taming grants credit are intentionally deferred with taming.

## Verification and playtest

### Iris development-run crash workaround

The user's 7 September crash occurred after enabling Complementary, in `GlCommandEncoder.validateDraw`, with `Index 1 out of bounds for length 1`. This matches [Iris issue 3304](https://github.com/IrisShaders/Iris/issues/3304): Iris supplies a short vertex-binding array while the development validator checks 16 slots. The local Minecraft source confirms that `neoforge.disableGlValidation` controls this validator. The interactive client run now sets `-Dneoforge.disableGlValidation=true`, matching the reported workaround. Iris, Sodium, the selected shader and graphics preferences remain intact. This disables development GL validation for that run; it does not patch Iris itself. The generated client JVM arguments and build checks pass; confirming shader rendering after this change still requires a client playtest.

The build, 18 JUnit tests, all eight headless GameTests, installer checksum checks, client-only classpath check, and client preparation pass. Automated coverage includes regional raster correspondence across panning/zoom, invalid-view bounds, map entitlement save/load, player isolation, revocation, and network codec round trips. Datagen and the dedicated GameTest server load successfully without Xaero or the presentation mods.

No interactive client was launched. Check these in the normal launcher:

1. With the default development settings, M opens immediately. To test the progression gate, enable `progression.mapRequiresUnlock` and restart/rejoin; `/arkmap unlock` grants access and `/arkmap lock` revokes it.
2. Toggle Difficulty on and off. Confirm only explored map receives the tint, including after exploring more terrain without moving the map camera. Compare the cursor's difficulty with the biome announcement at the same coordinates, including negative coordinates. Pan, zoom and change viewed dimensions; inspect the legend against Xaero controls.
3. With the progression gate enabled, reconnect and respawn: the saved unlock survives. A second player stays locked until independently granted access.
4. Enable the shader pack and inspect dinosaur materials, water, shadows and frame rate. Confirm warning calls remain audible with both sound mods enabled.

The map gate is a progression feature for this client pack, not an anti-cheat boundary against modified clients.

## Flying habitat markers

The separate **Nests: on/off** toggle displays one nest glyph per discovered flying colony on the fullscreen World Map. Hover for species and coordinates. Discovery is saved per player and dimension; the difficulty toggle can remain off. The current dimension's nearest 128 discoveries are available, and unexplored terrain remains hidden. No minimap API is used. See [Flying Ecosystem](flying-ecosystem.md) for placement rules and validation limits.

## Land habitat markers and dev settings

The separate **Land habitats: on/off** control uses leaf/fang symbols for discovered land homes. Hover shows species, coordinates and site status; nearby screen markers cluster. See [land behavior](land-ecosystem.md) and the [dev dependency list](dev-dependencies.md) for the 16 GB / 24-chunk settings and River Redux availability.
