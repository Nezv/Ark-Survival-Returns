# Dev-run dependencies and settings

Reviewed 2026-09-20 for Minecraft 26.1.2 / NeoForge 26.1.2.109, Java 25.

| Dependency | Dev status | Scope |
|---|---|---|
| [Iris 1.11.4](https://modrinth.com/mod/iris/version/qE5Y7GrZ) | Installed; pinned SHA-512 verified | Interactive client |
| [Sodium 0.9.2](https://modrinth.com/mod/sodium/version/zg4YQ9EL) | Installed; Iris dependency; pinned SHA-512 verified | Interactive client |
| [FTB Quests 26.1.2.8](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge) | Installed; pinned SHA-512 verified | Client and server gameplay |
| [FTB Teams 26.1.2.4](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge) | Installed; party identity for the survival journal | Client and server gameplay |
| [FTB Library 26.1.2.8](https://www.curseforge.com/minecraft/mc-mods/ftb-library-forge) | Installed; FTB Quests and Teams dependency | Client and server gameplay |
| [FTB XMod Compat 26.1.2.4](https://www.curseforge.com/minecraft/mc-mods/ftb-xmod-compat) | Installed; links FTB Quests to JEI recipes | Client and server gameplay |
| [JEI 29.34.0.90](https://modrinth.com/mod/jei/version/AisPQmQz) | Installed; pinned SHA-512 verified | Client and server gameplay |
| [River Redux](https://modrinth.com/mod/river-redux/versions) | No published 26.1.2 NeoForge artifact; not installed | Future world generation |
| [TerraBlender](https://modrinth.com/mod/terrablender) | Deferred with River Redux | Future world generation dependency |

`config/dev-dependencies.json` is the dependency/status list. Exact active downloads and hashes remain in `config/client-mods.lock.json` (presentation mods, client only) and `config/shared-mods.lock.json` (gameplay stack, both sides). `Install-Ark-Extras.bat` fetches or reuses those compatible files; `Install-Ark-Extras.ps1 -VerifyOnly` checks them offline. The installer reports the unavailable/deferred entries. Third-party JARs, shader archives and saves are excluded from Git.

The FTB stack and JEI load on both the client and the dedicated server; only the presentation mods stay client-only. FTB Quests loads its quest pack from `<instance>/config/ftbquests/quests/`, so the authored pack is copied into the development instance's config by `prepareDevRuntime`; see [the journal stack](journal-tribe.md). FTB artifacts are hosted on FTB's Maven CDN and are downloaded, not redistributed. JEI is required on both sides for recipe sync.

River Redux's available NeoForge line targets 1.21.x, not this 26.1.2 runtime. Do not install its older JAR or silently change Minecraft versions. The land habitat planner works with ordinary exposed water blocks and requires no River Redux API. When a compatible release appears, verify the artifact, exact TerraBlender requirement, loader sides and world-generation behavior before adding it. New terrain generation must be tested in a disposable world first.

Iris 1.11.4's published dependency link names the NeoForge Sodium 0.9.2 artifact this pack already installs, satisfying the Iris JAR's embedded version range. The existing development GL-validation workaround remains client-only. See [the client pack](client-pack.md) for that compatibility detail and the other presentation mods.

## Runtime defaults

Edit `config/dev-runtime.properties` to change the dev defaults:

- Client maximum heap: 16 GB; dedicated-server maximum heap: 16 GB. These are separate run profiles, not a combined 16 GB allocation.
- Client render distance / dedicated-server view distance: 24 chunks.
- Simulation distance: 12 chunks, independently configurable.

Gradle reads the heap limits into the client/server JVM arguments. `prepareDevRuntime` applies the distance settings to ignored `run/options.txt` and `run/server.properties`, preserving other keys. Both normal client/server launches depend on it; it reapplies the explicit dev values if those distance keys change. `Start-Ark-Mod.ps1 -Check` prepares these settings and verifies dependencies without opening Minecraft. No EULA acceptance is written.

From `Ark`, run:

```powershell
./gradlew.bat prepareDevRuntime prepareClientRun verifyDevDependencies verifyClientPack
```

`runClient` checks every pinned hash and the classpath split before launch. Dedicated servers, headless GameTests and datagen exclude the presentation JARs and load the shared gameplay stack. A future River Redux install needs its own common runtime scope; it must not be placed in the client-only presentation directory.

Verification passed for the local installer, all pinned hashes, the client/shared classpath split and client preparation. No graphical launch or 24-chunk multiplayer performance measurement was performed.
