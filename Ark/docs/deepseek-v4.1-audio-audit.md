# Audio Loader Conflict Audit (DeepSeek V4.1)

## Auditor and provenance

This report was produced by the external API model alias `deepseek-flash` (DeepSeek V4.1 Flash). This statement records the configured model identity; it does not by itself prove the serving provider. The parent retains the API event log for that proof.

The audited commit is `31d5225` (`Fix embedded sound physics loader conflict`).

This document-only audit uses the complete evidence set supplied by the parent. The external worker had no direct checkout access and did not rerun commands. The evidence below is parent-supplied; the findings and conclusion are the audit analysis.

## Parent-Supplied Repository Evidence

- `git log -3 --oneline` reported `31d5225 Fix embedded sound physics loader conflict`, followed by `36b2644 Integrate unified spatial audio engine` and `c1c7da7 Generate anatomy-based creature texture variants`.
- This scan was run after the fix: `rg -n "com\.sonicether\.soundphysics" Ark/src/main/java Ark/src/main/resources/arksurvivalreturns.mixins.json`. It exited `1` with no matches.
- `Ark/src/main/resources/arksurvivalreturns.mixins.json` sets the mixin package to `dev.nez.arksurvivalreturns.client.audio.physics.mixin`.
- `Ark/src/main/java/dev/nez/arksurvivalreturns/client/ArkClient.java` imports `dev.nez.arksurvivalreturns.client.audio.physics.SoundPhysicsMod`.
- The integrated tree contains 29 Java source files under `Ark/src/main/java/dev/nez/arksurvivalreturns/client/audio/physics/`.
- Packaged-jar inspection reported `old_package_entries=0` and `private_package_entries=39`.
- `Ark/build.gradle` defines the following standalone-jar exclusion patterns: `AmbientSounds_*.jar`, `CreativeCore_*.jar`, `sound-physics-remastered-*.jar`, `PresenceFootsteps*.jar`, and `Presence-Footsteps*.jar`. It builds `installedPack` from `client-mods` while excluding those patterns.
- Validation reported `./gradlew.bat build verifyClientPack --no-daemon`, `Verified 4 presentation mods on client classpath only.`, and `BUILD SUCCESSFUL in 39s`.

## Findings

- **PASS - no stale old-package references:** The supplied source and mixin scan found no occurrence of `com.sonicether.soundphysics` in the Java sources or mixin configuration.
- **PASS - private replacement namespace confirmed:** Ark uses `dev.nez.arksurvivalreturns.client.audio.physics` for its replacement classes and `dev.nez.arksurvivalreturns.client.audio.physics.mixin` for mixins.
- **PASS - standalone Sound Physics is excluded in development:** The directly relevant Gradle exclusion is `sound-physics-remastered-*.jar`; the related standalone audio patterns above are also excluded from `installedPack`.
- **PASS - integrated implementation remains present:** The 29 replacement Java sources remain under Ark's private audio physics tree, and packaged-jar inspection found 39 private-package entries with zero old-package entries. This contradicts accidental removal or an unreplaced mixin namespace.
- **PASS - parent validation:** The build and `verifyClientPack` task completed successfully, with four presentation mods confined to the client classpath.

## Conclusion

**PASS.** Based on the supplied evidence, commit `31d5225` correctly removes package/classloader overlap with the standalone Sound Physics Remastered jar, keeps Ark's integrated implementation, and passes the supplied build and client-pack verification. This conclusion is limited to the evidence supplied by the parent; no independent live launcher or runtime test was performed here.
