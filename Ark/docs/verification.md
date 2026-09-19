# Verification — 2026-09-07

This is the historical baseline. Current flying behavior and September 11 test results are documented in [Flying Ecosystem](flying-ecosystem.md).

- `./gradlew runData`: passed; 65 generated resources, including English and Portuguese map messages.
- `./gradlew build runGameTestServer prepareClientRun`: passed with NeoForge 26.2.0.11-beta and GeckoLib 5.5.3.
- **18 JUnit tests passed**: three growth tests, four recurring-difficulty tests, six behavior-model tests, three map raster tests and two movement calibration tests.
- **All eight headless GameTests passed**: seven Ark tests plus the framework's built-in test.
- `python tools/verify_assets.py`: passed; nine models, 54 clips, thirteen complete item asset chains, valid resource references and unchanged original creature hashes.
- `../Install-Ark-Extras.ps1 -VerifyOnly`: all seven mod JARs and the shader ZIP match pinned SHA-512 hashes.
- `python tools/verify_client_pack.py`: passed; checks hashes, sizes, NeoForge metadata, required mod IDs, embedded XaeroLib and absence of duplicate installed JARs.
- `../Start-Ark-Mod.ps1 -Check`: passed; client classes, run preparation and client-pack classpath check. Seven presentation JARs belong only to the interactive client runtime. Generated JVM args contain `-Xmx12G` and the Iris GL-validator workaround. Windows high-performance GPU preferences are registered for both selected/resolved Java executable paths; actual renderer selection needs a client launch.
- `graphify update .`: completed, 560 nodes and 1,201 edges. Its Groovy parser still cannot fully extract `build.gradle`; Gradle builds and checks successfully.

## Gameplay coverage

Saved level, injury, max HP, damage, pack identity, home and needs persist for all nine species. Reloading older entities upgrades their movement-speed base to the configured player-relative baseline without stacking multipliers or healing them. Hitbox dimensions match the requested scaled species dimensions.

Difficulty tests check recurrence in every cardinal direction, near-equal area for all five ranks, adjacent and diagonal rank-step bounds (including tile seams), negative coordinates and world-border arithmetic. The full default 1,024²-block tile measures 19.92%, 20.00%, 20.05%, 19.87%, 20.16% for ranks 1–5. Raster tests compare the map against the server formula across different pans and zooms and bound invalid or extreme views. See `docs/difficulty-map.png`.

Spawn tests cover all registered biome eligibility against displayed danger. The stale easy-biome-tag veto no longer blocks apex spawns in high-danger terrain. Danger 1 still rejects Rex, Giga and Titanosaur. The population test actually spawns each of those species in a suitable rank-5 arena after resizing; it also verifies three full small packs, cap and budget compliance, overlapping players, replenishment, gamerules and unchanged loaded-chunk counts. It also checks that common groups cannot starve the first high-danger encounter, while an established population does not expand into a roster of all large species. This confirms eligibility and clearance behavior, not encounter frequency in natural terrain.

Behavior tests exercise warning time before hunting, pursuit expiry and return, sight occlusion, investigation without attacks through walls, memory expiry, fear/injury flight, herbivore intrusion thresholds, foraging/drinking/rest hysteresis and feeding after a kill. A real predator/pig GameTest verifies sight → hunt → occluded investigation → kill/satiety, with no extra chunks loaded. Bronto encounter tests verify a complete herd with linked Rex, group defense after an attack and low-health predator retreat. Movement probes measure actual flat-ground and water travel for Rex, Giga, Raptor and Titanosaur, within 4% of configured targets. Pure tests cover size/distance-based animation cadence. Damage formulas and berry use remain unchanged.

Berry tests sample 2,000 real loot rolls per grass/tool case, including shears, tall grass and vanilla seeds. All four item assets remain complete.

## Map and client pack coverage

Map GameTest assertions verify entitlement defaults, UUID-specific unlocks, serialization round trips, revocation and profile packet codec round trips. An exploration-mask raster test verifies unknown terrain gets no tint. Xaero cache lookups and GUI strata compile against the pinned map artifact, but the actual toggle/filter rendering requires user playtesting. Common/server code does not import client classes. Datagen and the dedicated GameTest server run without Xaero or the presentation mods.

The installed Xaero bridge's bytecode injection descriptor matches exactly one target invocation in Xaero 1.44.2's renderer. Expected camera, zoom and viewed-dimension fields exist. The pack pins the supported pair and corrects the Iris download metadata's wrong Fabric Sodium dependency to stable NeoForge 26.2 Sodium. This validates artifacts and structural integration; it does not prove graphical compatibility. See `docs/client-pack.md` for exact versions, commands and remaining client checks.

No interactive client was launched, following `AGENTS.md`. User playtesting remains necessary for map UI placement and visual alignment, full shader compatibility/performance, sound balance, dinosaur forward walking and animation transitions, natural terrain encounters and multiplayer delivery. The optional bridge labels NeoForge support experimental.

## World compatibility and scope

Difficulty overlays existing terrain; it does not regenerate biomes. The saved original world-spawn anchor remains rank 1 and is fixed across later world-spawn changes. New recurring ranks replace the old monotonic calculation automatically, while existing player locations and creature levels remain unchanged. Bed/command respawns are not overridden. Existing or manually spawned apex creatures can remain in or wander into low-danger regions; restrictions govern new natural spawns.

The current ground wildlife model is implemented. Flight, taming, riding, breeding, tracking/carcass ecology, player conditions and custom dinosaur audio remain separate future systems. Map access is saved and server-granted, but no taming listener exists. Map access is temporarily open to everyone through `progression.mapRequiresUnlock=false`. Enabling the gate restores the per-player entitlement requirement.

Copied development files remain outside the mod in `../.work/copied-mod-recovery`, excluded from source control and from the JAR. Third-party client-pack files are also excluded from the Ark JAR.
