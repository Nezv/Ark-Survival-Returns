# Debug Spyglass

Give yourself `/give @s arksurvivalreturns:debug_spyglass`, or find **Debug Spyglass** in the Ark creative tab. It uses the vanilla scope model with an enchanted glint. No survival recipe is added for this debug tool.

Hold the use button in first person and aim at an Ark dinosaur. A dark green terminal panel shows live server values; scroll the mouse wheel while scoped to browse pages. Either hand works. Releasing use, changing worlds, losing the target or disconnecting clears the data. The ordinary Spyglass is unaffected.

The first page contains species, level, current/max health, behavior, night activity, position, velocity and combat target. Further pages expose current attribute values, the mod's creature/AI/flight fields (including private timers and needs), species configuration and recursively listed saved entity data. Technical variable paths stay in English; item names and overlay instructions support English and Brazilian Portuguese. Flying creatures' preserved land needs are inactive, as in the existing flight implementation.

`DinoDebugSnapshot.capture` is the extension point for additional computed values. Mod-owned runtime fields are discovered automatically. Entity references are shown as UUID/type rather than traversed. This is a read-only inspector, not a variable editor. It does not expose every private Minecraft/GeckoLib engine field or animation-cache internals.

The server selects the closest visible living Ark creature intersecting the eye ray within 96 blocks. Solid collision shapes and unloaded chunks block inspection. The client checks target identity and dimension before displaying received values and expires stale snapshots after two seconds. A player's inspected target and page are independent of other players.

The server scans only while the debug scope is in use and sends one 12-line page every 10 ticks (twice per second at 20 TPS). Page requests carry only target identity and direction, are validated against the active scope/view, and are rate-limited. Mouse-wheel requests never initiate extra captures. A page contains at most 12 bounded strings; ordinary pages stay below 4 KiB, giving less than 8 KiB/s of payload per continuously active viewer. This is a payload budget, not a measured multiplayer benchmark. Snapshot work scales with the inspected creature's data and number of active viewers. Saved data is capped at 4,096 display lines and nesting depth 16, with visible limit markers.

Restart Minecraft after building. The new item requires the updated mod on both server and clients. No dinosaur save migration is needed.

## Verification

On 2026-09-13, Gradle `runData`, `build` (26 unit tests) and `runGameTestServer` (all 14 required tests) passed. The packaged JAR includes the item model, debug classes and generated test definition. The client was not launched; visual and live multiplayer checks remain pending.

The `debug_spyglass` headless GameTest covers scope activation in both hands, vanilla item isolation, all species' snapshot contents and unchanged saved state, packet round trips and malformed row-count rejection, nearest targeting, invisibility, walls, loss of target, range and loaded-chunk counts.

Interactive checklist (the project reserves client launches for the user):

1. Obtain the item, hold use and inspect a land dinosaur and a flying creature. Confirm zoom, held model, green panel and live values.
2. Scroll through runtime and saved-data pages. Test window sizes and GUI scales; check text and page instructions are legible.
3. Release use, switch items, look away/behind walls, change dimensions and reconnect. Confirm old values disappear.
4. Repeat with two players inspecting different dinosaurs/pages; change health and confirm each viewer sees the correct update.
5. Check the Portuguese language, offhand use, third person and hidden HUD.
