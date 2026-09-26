# Tribe Chronicle — implementation handoff

## What is implemented

The native client menu opens through a registered **P** key mapping (`key.arksurvivalreturns.tech_tree`, Ark Progression category). It is discoverable in Minecraft Controls and by remapping tools. It opens only while playing, closes with Escape or its currently assigned key, and does not pause multiplayer.

The menu uses parchment, separate moss/bronze/iron age colors, fine connections and transparent item illustrations. Entries have broken ink rings, never rectangular cards. Missing artwork leaves an empty ring. Approved orthographic camp art is used for the mattress, stone fire, clay pot, mortar, drying rack, trough and Iron bed. The old bed model is not repurposed as the prehistoric mattress.

Navigation: mouse wheel/trackpad, drag the map, drag/click the bottom rail, Left/Right, Home/End, or click an age heading. Coordinates, picking and scroll bounds share the same transform. Hovering shows the quest name, objective and state. The Quest book link opens the existing FTB journal.

There are **44 nodes / three ages**. The Prehistoric has four paths (Arms & Armour added 2026-09-26: Sharp thinking, Tough as horn, Pointy end, Thick skin) and the later ages three; every path endpoint is required by each finale. The map is 344 units tall, with lanes 76 apart centred on the gate row. Locked icons are subdued, available icons have warm ink, and complete icons acquire a gold check. Opening the screen and each subsequent second requests a fresh server view. Changing completion via the server or FTB is visible without reopening.

The three food extras display `???` until complete. Their hover shows only the name and `???`. The server omits their icon, objective and prerequisites from the snapshot; this is not merely a client-side opacity effect. Their dependency lines are omitted from the map. Completion reveals the food's normal art/objective. FTB mirror descriptions also contain only `???` for secrets. This is an in-game discovery rule, not protection against someone reading the installed mod's source/assets.

## File ownership

| File | Purpose |
| --- | --- |
| `tools/build_tech_menu_assets.py` | Rebuilds runtime tree JSON, GUI PNGs, FTB mirror chapters, stable quest-ID manifest and offline art proof. |
| `design/technology-tree/technology-tree.json` | The approved proposal 02 sequence and text used by that exporter. |
| `src/main/resources/data/arksurvivalreturns/tech_tree/tree.json` | Runtime server tree, schema 2. GUI boxes contain center x/y followed by icon bounds, not old poster-card positions. |
| `src/main/resources/assets/arksurvivalreturns/textures/gui/tech/` | Runtime parchment, status rings and 64px node icon textures. |
| `client/TechClient.java`, `client/TechScreen.java` | Key mapping, native menu, navigation, rendering and hover notes. |
| `feature/tech/TechView.java` | Server projection and secret redaction. |
| `feature/tech/TechPayload.java`, `TechSync.java` | Bounded request/snapshot protocol, tied to the requesting player's current team. |
| `feature/tech/TechFtbBridge.java` | FTB completion authority and grant/reset synchronization. |
| `config/ftbquests/quests/chapters/ark_tech_*.json5` | Three dedicated, invisible mirror chapters containing CustomTasks. |
| `design/tech-menu/quest-id-map.json` | Exact node-to-FTB quest ID mapping. Task IDs are quest ID + 1. |
| `datagen/ArkData.java` | English/Portuguese menu strings and existing GameTest definitions. |

Java paths in the table are relative to `src/main/java/dev/nez/arksurvivalreturns/`.

## Synchronization contract

1. Resolve the FTB Teams team via the existing `TechService.tribeOf`; use the player's UUID only when no team is available. Never accept a team UUID from the client.
2. When all mirror quests exist, **FTB Quests owns the completed set**. Pull its exact set into Ark's progress cache. Do not union these sets: union resurrects FTB admin resets.
3. `/arktech unlock <id>` updates both stores. `/arktech reset` resets only this tree's mirror quests and Ark v2 progress. Other journal chapters are untouched. FTB admin grants/resets are read back into the custom screen.
4. CustomTasks have no player checkmark button, item submission or automatic detector. There are no rewards to duplicate. The mirror chapters are invisible in the ordinary FTB book because this menu presents them; existing FTB journal chapters still appear there.
5. The UI sends only a snapshot request. There is no completion, reward, progress-edit or arbitrary-team packet. Requests are rate limited to one every ten server ticks. A screen instance token discards late responses belonging to an earlier screen.
6. If the mirror pack is absent, the menu shows **Quest link unavailable** and reads the local Ark store. Install the mirror configuration before testing shared progress. The current bridge does not import local-only completions into FTB when a missing pack later appears.
7. FTB owns party merge/join/leave policy. The next pull follows the player's new team; no permanent client progress cache survives closing the screen. Real multiplayer join/leave/reconnect behavior still needs interactive acceptance testing.

The bridge has a narrow incomplete edge for later gameplay work: `TechService.notify(level, offlineActor, event)` cannot push a completion to FTB using an online player context. Before enabling offline-tame triggers, implement a server-side team-context completion path and test it. All shipped v2 triggers are deferred, so this does not affect the current manual/UI foundation.

## Deployment

The mod JAR carries tree data, GUI code and textures. **FTB quest configuration is a separate pack artifact**: ship `config/ftbquests/quests/chapters/ark_tech_*.json5` and its English translation files with the instance/server config. The repository's `prepareDevRuntime` task copies authored quest configuration into the dev instance; rebuilding the JAR alone does not install that config on an external server.

## Deliberately unfinished gameplay

Prehistoric nodes have real triggers (authored as `trigger` in design/technology-tree/technology-tree.json, exported by the generator); every Bronze and Iron node still uses `{"type":"future"}`. This intentionally prevents the previous 24-node trigger rules from completing revised objectives incorrectly. Existing event hooks and trigger codecs remain available; the menu can be tested with operator grants or FTB administration. No recipe locks, item restrictions, rewards or game-mechanic implementations are added by this UI pass.

Implement the next stage in this order:

1. Establish precise semantics and authority for the new content. Wire only existing, correct predicates first; do not treat possession as crafting where the objective says craft.
2. Add exact trigger events for dinosaur damage, crafting a leash/knife/mattress, lighting a torch in the new fire, mortar output, smelting alloys/iron, scoped carnivore kills, engineered boots, vault insertion, spike placement count and ammunition crafting.
3. Preserve historical/team provenance where needed: distinct berries, three-day drying timestamp, tame work, tame kills, scoped weapon identity, crafting vs possession. Check the existing `TechTrigger`, `TechEventKind` and creature/camp hooks before extending them.
4. Keep availability and fulfillment separate: all required dependencies must be complete even if the tribe satisfied an objective earlier. Extras never gate an age.
5. Finish offline-team completion and integration tests, then decide whether event-driven pushes should supplement the current one-second open-screen polling.
6. Add new item art only when the owner supplies/approves it. The stone knife, authored weapons, forging table, glass presentation, vault, spikes, ammunition and Golden Raptor Meat remain blank where no approved icon was available. Vanilla boot/armor and dinosaur-egg images are presentation placeholders.

## Preserved design decisions and ambiguities

- Prehistoric: Monkeys → four four-step paths → Narcotraffic. Monkeys is retained from the original sketch; its stone-gathering task overlaps Tha rock and still needs author refinement.
- Bronze: Prepare for it! → three three-step paths → Rawr!. Sparklers crafts gunpowder. Slavery inherits the prior Working Giants objective (hunt + resource haul from tames).
- Iron: Greed (have gunpowder) → three paths → Subdue Nature. The middle path preserves all four Harder/Better/Faster/Stronger entries, giving it an extra column.
- The Iron scoped-kill objective precedes the craft-ammunition finale. The future gameplay designer must provide a way to obtain ammunition before crafting it, or ask the author to revise that dependency. No substitute order is silently invented here.
- Dried Meat III sits above the age starter but actually requires Long-lasting. Golden Raptor Meat follows Prepare for it!. Cocaine is the fictional gunpowder/narcotics/yellow-berry food; its normal description stays hidden until completion.

## Save migration

The former 24-node layout reused IDs whose meaning/age changed (`greed`, `rawr`, `home`, etc.). V2 uses a separate `arksurvivalreturns:tech_progress_v2` SavedData entry and distinct FTB IDs. The old `tech_progress` data is left intact; it is not interpreted as v2 completion or deleted. An explicit migration should map only semantically equivalent completed objectives and never carry obsolete gates over automatically.

## Verification and interactive acceptance

Automated coverage lives in `TechGameTests`: loaded 40-node/three-age graph, codecs and persistence, deferred triggers, all-three-path prerequisite checks, hidden/revealed secret payloads, FTB grant/reset round trips and separation between progress IDs. The native client code is compile-checked; offline PNGs are layout/art proofs, not game screenshots.

Run sequentially from Ark:

```powershell
./gradlew.bat runData
./gradlew.bat build
./gradlew.bat runGameTestServer
```

Do not launch the client automatically; the repository reserves that for its owner. In the client verify:

- P appears in Controls, remaps cleanly, and opens/closes the menu in a world.
- GUI scales and a narrow window preserve drag, wheel, age tabs, hover alignment and scroll limits.
- All three ages can be reached. Unknown artwork is blank, not a fabricated weapon or station.
- Hovering each unfinished food extra shows only its name and `???`.
- `/arktech unlock monkeys` changes its appearance within a second. `/arktech unlock cocaine` reveals that extra; reset conceals it again.
- Two real players in one FTB team share state; another team does not. Join/leave, reconnect, FTB admin reset and `/reload` do not display stale progress.
- Missing FTB mirror configuration is visibly reported and restored configuration follows the documented authority rule.
