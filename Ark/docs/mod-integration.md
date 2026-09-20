# Mod integration plan: Ancient Remnants and Icy's Better Horses

Two third-party mods join Ark Survival Returns as **separate jars** — never merged into the Ark jar.
Both were built locally from source in `Integration/` for the 26.1.2 runtime and staged in
`Integration/jars/`. This plan covers the dependency closure, how to load them for development,
the compatibility audit and the acceptance checks.

## Built artifacts (2026-09-20)

| Mod | Jar | Size | Built from | License |
|---|---|---|---|---|
| Icy's Better Horses 2.0.0 | `Integration/jars/icys-better-horses-2.0.0.jar` | 2.8 MB | `Integration/icy-better-horses`, branch `26.1.2-neo` (NeoForge port of the Fabric project) | ARR |
| Ancient Remnants 1.3.1 | `Integration/jars/ancient_remnants-neoforge-26.1-1.3.1.jar` | 12.1 MB | `Integration/ancient-remnants`, `:neoforge:build` | Obscuria Modding License (OML) |

Declared requirements read from their metadata:

- Icy's: Minecraft `[26.1.2]`, NeoForge `[26.1.2.71,)`, GeckoLib `[5.5.1,)`, Modonomicon `[2.2.0,)`,
  Cloth Config `[26.1.154,)`.
- Ancient Remnants: Minecraft `[26.1, 26.2)`, NeoForge `[26.1.0.1-beta,)`, Fragmentum `[4.0.5,)`.

Ark runs NeoForge 26.1.2.109 and GeckoLib 5.5.2, so both ranges are satisfied. Both builds pass from
a clean checkout with JDK 25: Icy's in ~40 s once dependencies are cached, Ancient Remnants in
~10 min on the first run (dependency downloads).

## Runtime dependency closure

In addition to Ark, the shared FTB/JEI stack and the client pack, an integrated instance needs:

| Dependency | Version for 26.1.2 | Source |
|---|---|---|
| GeckoLib | 5.5.2 | Already pinned by Ark (`shared-mods.lock.json`) |
| Modonomicon | `modonomicon-26.1.2-neoforge-2.5.1.jar` | Modrinth |
| Cloth Config | `cloth-config-26.1.154.jar` | Modrinth (`26.1.154+neoforge`) |
| Fragmentum | `26.1-4.0.5` (`dev.obscuria:fragmentum-neoforge`) | Obscuria maven: `https://raw.githubusercontent.com/ObscuriaLithium/modding/main/maven/` |
| YACL | `3.9.3+26.1-neoforge` only if Ancient Remnants' config screen demands it | Xander maven / Modrinth |

The two published Modrinth artifacts are good candidates for `Install-Ark-Extras` pins later; the
locally built jars (Icy's NeoForge branch and Ancient Remnants) are not redistributed — they stay in
`Integration/`, which is gitignored.

## Loading them in development

`runGameTestServer` must never see foreign content, and `run/mods` is shared by every run config.
Plan:

1. Add dedicated run configs to `Ark/build.gradle`:
   - `runClientIntegration` and `runServerIntegration` with `gameDirectory = run/integration`.
   - A copy task that places `Integration/jars/*.jar` plus the dependency jars (Modonomicon, Cloth
     Config, Fragmentum, YACL if needed) into `run/integration/mods`.
   - GeckoLib and the FTB stack stay on the classpath through the existing Gradle dependency wiring.
2. First acceptance: a dedicated server starts with Ark + both mods and logs no missing dependency,
   mixin or registration error. Capture the loaded mod list.
3. Optional smoke: run `/arkwildlife`, spawn a tamed creature, fence a horse, locate a monolith
   structure id; no GameTest suite change.
4. Client playtest is the visual gate and is tracked in [Verify.md](../Verify.md).

## Compatibility audit

What Ark does that could touch them:

- **Theme guards are vanilla-scoped.** `LootGuard` only rewrites the loot tables it targets and logs
  every untouched table (including modded ones), `ThemePolicy` lists vanilla entity/item/structure
  ids, and `RecipeGuard` / `MechanicGuard` / `DimensionGuard` act on vanilla content. After the first
  load, grep the log for `ancient_remnants:` / `icys_better_horses:` to confirm nothing was rewritten.
- **Spawning** only owns Ark species; both mods bring their own biome modifiers and structures. The
  risk is density stacking, not registry conflict.
- **Riding** paths are separate: Ark mounts its own tamed `CreatureEntity`; Icy's overhauls the
  vanilla horse. Test a horse mount with a tamed Ark creature following, and dismount edges.
- **GeckoLib** is shared at runtime: one 5.5.2 jar must satisfy both Ark and Icy's (compiled against
  5.5.1). Watch the log for API mismatches on first load.
- **Config libraries** coexist (Cloth Config for Icy's, YACL for Ancient Remnants).

What they add that interacts with Ark's design (decisions for P03/P04):

- Ancient Remnants' research/blessings, monument loot and villager trades are a parallel power curve
  that bypasses the Primitive/Camp progression (extend `progression-bypass-audit.md` with the
  decision: accept as side content, tag-gate it, or disable specific trades via its config).
- Icy's horse bonding, tack and carts are thematically native. Optional later work: a journal chapter
  for horse bonding, tag bridging so Ark trough foods accept its feed, and FTB objectives.
- Ancient Remnants generates its own structures and loot; verify they do not collide with Ark's
  disabled-structure filtering (which is vanilla-scoped by design).

## Risks and open items

- Ancient Remnants was built against NeoForge 26.1.0.1-beta; if the 26.1.2.109 runtime rejects it,
  bump `neoforge_version` in its `gradle.properties` and rebuild.
- Cloth Config and Modonomicon versions must match 26.1.2 exactly (pins above).
- Both are ARR/custom-license; keep them out of the Ark jar, out of git, and out of the installer's
  redistributed files unless the license permits a download pin.
- The integrated instance changes spawn/visual balance; keep the headless suite (Ark-only) as the
  regression gate and treat the integrated server/client as a playtest environment.

## Acceptance checklist

1. Integrated server starts with no missing dependency, mixin or registry error.
2. Horse: bond grows, command wheel, roster, cart; Ark's companion whistle ignores horses.
3. Horse ride + tamed Ark creature follow + dismount: no AI errors.
4. Monolith: structure generates, research step completes, blessing applies; Ark HUD, downed state
   and recovery cache still behave.
5. Death with both mods loaded: recovery cache and horse state both persist.
6. Two-player party: shared quests + horse roster + monolith visit in one session.
