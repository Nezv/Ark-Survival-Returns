# Workspace Standards

## Source of truth

`Dashboard.csv` (root) tracks every feature/patch: ID, scope, proposer, executor, status and open issues.
Read it before starting work; update the row's Status when a task finishes. Do not create new
planning, proposal, audit or report markdown files — put status and open issues in the dashboard.
`Sync-Dashboard.ps1` syncs it with Google Drive (rclone).

Read only what the task needs. Skip generated/ignored trees: `Ark/src/generated/`, `Creatures/*/source/`,
`graphify-out/`, `.work/`, `Scratch/`, `Integration/`.

## Mod (Ark/)

NeoForge 26.1.2, Java 25, Gradle wrapper 9.2.1, GeckoLib 5.5.2.
- Use `./gradlew`, never system Gradle.
- Run `./gradlew runData` after data provider changes. Commit generated resources; never hand-edit them.
- Run `./gradlew build` and `./gradlew runGameTestServer` for gameplay changes.
- Do not launch `runClient`; the user does interactive visual testing.
- Keep common/server code free of client imports.
- Import runtime assets from `../Creatures` with `python tools/import_creatures.py`. Python asset scripts belong in `tools/`.
- Spawning must never force chunk loads; respect biome tags, collision, population caps and game rules.

## Integrations and the pack

`Ark/config/integrations.json` lists every third-party mod, how it ships (release, source fork, embedded) and what Ark changes. `python Ark/tools/build_pack.py` builds the branded Modrinth pack into `Ark/pack/out` (forks: `Ark/pack/patches`, `Ark/pack/overlays`). `./gradlew runGameTestServer -ParkPack` tests the shipped jars. `python Ark/tools/build_showcase.py` rebuilds `Ark-Survival-Returns.html`.

## GitHub backup

After every completed task: `git add` only the task's paths (gitignore anything too big), `git commit -m "<concise description>"`, `git push`. No re-verification needed if the push reports success. Authorized without repeated confirmation.
