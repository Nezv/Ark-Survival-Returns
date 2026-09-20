# Journal and tribe stack

The survival journal is the [FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge)
book, and the tribe is the [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge)
party. Ark Survival Returns adds only what those mods do not: the Field Journal item, the
permission flags for tames, discovery advancements and the authored quest pack.

## Installed stack

| Mod | Pin | Side | Purpose |
|---|---|---|---|
| FTB Quests | 26.1.2.8 | Client and server | Quest book, team progress, per-player rewards |
| FTB Teams | 26.1.2.4 | Client and server | Party identity, invitations, team chat |
| FTB Library | 26.1.2.8 | Client and server | Shared FTB UI and config library |
| FTB XMod Compat | 26.1.2.4 | Client and server | Links quest tasks to JEI recipes |
| JEI | 29.34.0.90 | Client and server | Recipe lookup, required on both sides for sync |

Exact files, URLs and SHA-512 hashes are in `config/shared-mods.lock.json`; the installer downloads
them into `shared-mods/`, which loads on every dev run. FTB publishes under an All Rights Reserved
license, so the artifacts are downloaded at runtime and never redistributed with the mod.

## Playing

1. **Create the tribe.** One player runs `/ftbteams party create <name>` (or opens the FTB Teams
   screen from the FTB Library sidebar) and invites the other. The invited player accepts with
   `/ftbteams` or the invitation screen. FTB Teams owns membership, ranks and team chat.
2. **Open the journal.** Craft the Field Journal from a book and two leather, then press **J** or
   right-click with it. The Primitive chapter starts with camp, tools, forage, sedation, the first
   tame and the journal itself.
3. **Share progress.** Quest progress is per tribe; rewards are per player and granted exactly once.
   Pin an objective in the book to keep it on the HUD tracker.
4. **Grant tame access.** `/arktribe status` shows the party and the caller's resolved flags.
   `/arktribe perm <player> <ride|cargo|commands|breeding> <on|off>` changes one flag; `/arktribe
   reset <player>` returns to defaults. Only the party owner or a gamemaster may change flags.
   The owner of a tame always keeps every permission, and a flag never grants access without party
   membership. Leaving or changing a team clears that player's flags.

Default flags live in the server config under `[tribe]`: riding, cargo and orders are on for party
members, breeding is reserved for the husbandry work and defaults off.

## Discovery records

Advancements are the per-player record. Taming a first creature awards the hidden
`journal/first_tame`; a tame whose saved origin band is 5 also awards `journal/rank5_tame` and
grants the map entitlement. The origin band is recorded when a creature first spawns, so
transporting an animal later never changes its provenance.

## Authoring the quest pack

FTB Quests reads its pack from `<instance>/config/ftbquests/quests/` — never from a data pack — so
the authored pack lives in `Ark/config/ftbquests/quests/` and `prepareDevRuntime` copies it into
`run/config/ftbquests/` before client, server and GameTest runs.

- `data.json5` holds the file version (13) and file-wide defaults.
- `chapters/*.json5` hold chapters, quests, tasks and rewards. Object ids are 16-digit uppercase
  hex; keep them stable once players have progress.
- `lang/<locale>/*.json5` hold titles, subtitles and descriptions keyed as
  `<type>.<ID>.<key>` (for example `quest.0000000000000101.title`).
- Progression lives in `<world>/ftbquests/`, never in the config pack; editing the pack does not
  reset player progress as long as ids do not change.

`JournalGameTests.journal_pack` fails the build if the pack stops loading, changes size or leaves an
item task unresolved; the server log line `Loaded 1 chapter groups, 1 chapters, 6 quests` confirms a
clean load. Add later chapters the same way as their patches land.

## Installing elsewhere

The Gradle launcher loads `shared-mods/` automatically. Another launcher or a real server needs the
five pinned JARs copied into its `mods` folder, plus the same Minecraft 26.1.2 / NeoForge 26.1.2.109
runtime. The quest pack then belongs in that instance's `config/ftbquests/quests/`.
