# Dev-run dependencies and settings

Reviewed 2026-09-14 for Minecraft 26.2 / NeoForge 26.2.0.11-beta, Java 25.

| Dependency | Dev status | Scope |
|---|---|---|
| [Iris 1.11.2](https://modrinth.com/mod/iris/version/bXt6zsZT) | Installed; pinned SHA-512 verified | Interactive client |
| [Sodium 0.9.1](https://modrinth.com/mod/sodium/version/KHPycol7) | Installed; Iris dependency; pinned SHA-512 verified | Interactive client |
| [River Redux](https://modrinth.com/mod/river-redux/versions) | No published 26.2 NeoForge artifact; not installed | Future world generation |
| [TerraBlender](https://modrinth.com/mod/terrablender) | Deferred with River Redux | Future world generation dependency |

`config/dev-dependencies.json` is the dependency/status list. Exact active downloads and hashes remain in `config/client-mods.lock.json`. `Install-Ark-Extras.bat` fetches or reuses those compatible files; `Install-Ark-Extras.ps1 -VerifyOnly` checks them offline. The installer reports the unavailable/deferred entries. Third-party JARs, shader archives and saves are excluded from Git.

River Redux's available NeoForge line targets 1.21.x, not this 26.2 runtime. Do not install its older JAR or silently change Minecraft versions. The land habitat planner works with ordinary exposed water blocks and requires no River Redux API. When a compatible release appears, verify the artifact, exact TerraBlender requirement, loader sides and world-generation behavior before adding it. New terrain generation must be tested in a disposable world first.

Iris's published dependency link names a mismatched Fabric Sodium artifact; this pack uses the correct NeoForge Sodium release and satisfies the Iris JAR's embedded version range. The existing development GL-validation workaround remains client-only. See [the client pack](client-pack.md) for that compatibility detail and the other presentation mods.

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

`runClient` checks the active Iris/Sodium hashes and client-only classpath before launch. Dedicated servers, headless GameTests and datagen exclude all seven presentation JARs. A future River Redux install needs its own common runtime scope; it must not be placed in the client-only presentation directory.

Verification passed for the local installer, dependency hashes, classpath isolation and client preparation. No graphical launch or 24-chunk multiplayer performance measurement was performed.
