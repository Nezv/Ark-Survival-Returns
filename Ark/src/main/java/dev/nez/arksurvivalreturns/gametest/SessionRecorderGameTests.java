package dev.nez.arksurvivalreturns.gametest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.GZIPInputStream;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.recorder.SessionRecorder;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Records a scripted encounter with the real AI, navigation and world ticks, then reads the file back.
 *
 * <p>An afternoon Carnotaurus first has a survival player well outside the eight-block wake distance, then
 * inside it; a pig is held in place while its navigation wants to walk. The recording has to explain both:
 * the player filtered by the day routine, then sensed, warned and attacked, and a stall with the blocks
 * around the body. The file must be complete, in sequence and loaded without any extra chunk. A water
 * creature leaves and comes back under the same identity. Two short recordings follow: one that waits and
 * then ends on its real-time deadline, and one stopped by hand.
 */
final class SessionRecorderGameTests {
    private static final int RECORDED_TICKS = 260, APPROACH_TICK = 50, LEAVE_TICK = 100, RETURN_TICK = 130;

    static void run(GameTestHelper h) {
        var world = h.getLevel();
        var server = world.getServer();
        var clock = world.dimensionType().defaultClock().orElseThrow();
        long oldTime = world.getDefaultClockTime();
        for (int x = 16; x < 112; x++) for (int z = 16; z < 112; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        // Afternoon: carnivores are awake but only react to a player within the wake distance.
        double sleepShare = Config.DAY_SLEEP.get();
        int transition = Config.NIGHT_TRANSITION.get();
        Config.DAY_SLEEP.set(0.5);
        Config.NIGHT_TRANSITION.set(0);
        world.clockManager().setTotalTicks(clock, 9000);

        var carno = ModContent.CREATURES.get(Species.CARNOTAURUS).get().create(world, EntitySpawnReason.COMMAND);
        carno.getRandom().setSeed(7L);
        carno.initializeLevel(1);
        carno.setPersistenceRequired();
        carno.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(64, 2, 64))));
        // A new body counts as airborne for its first two ticks (the first move is zero, gravity lands it on the
        // second), and ground navigation refuses an airborne body every path: a decision pass in that window gives up
        // on roaming for good. Landed and already falling, this animal is on the ground from its first tick.
        carno.setOnGround(true);
        carno.setDeltaMovement(0, -0.08, 0);
        world.addFreshEntity(carno);

        // Calm and fed, so the first decisions are plain roaming.
        carno.wildlife().mind().restoreNeeds(0.3, 0.1, 0.05);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        // Enough health to be bitten for the whole recording.
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024);
        player.setHealth(1024);
        player.setPos(carno.position().add(0, 0, 16));
        world.addFreshEntity(player);

        // A trunk and a canopy block beside the pig, so the stall's terrain capture has a tree to show.
        h.setBlock(41, 2, 40, Blocks.OAK_LOG);
        // Persistent, or the lone leaf block decays before the recording ends.
        h.setBlock(41, 3, 40, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        var pig = EntityType.PIG.create(world, EntitySpawnReason.COMMAND);
        Vec3 pen = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(40, 2, 40)));
        pig.setPos(pen);
        world.addFreshEntity(pig);

        // The other realms: a flying Pteranodon and a water-bound Plesiosaur (kept still, there is no pool here).
        var ptero = ModContent.CREATURES.get(Species.PTERANODON).get().create(world, EntitySpawnReason.COMMAND);
        ptero.initializeLevel(1);
        ptero.setPersistenceRequired();
        ptero.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(84, 8, 44))));
        world.addFreshEntity(ptero);
        var plesio = ModContent.CREATURES.get(Species.PLESIOSAUR).get().create(world, EntitySpawnReason.COMMAND);
        plesio.setNoAi(true);
        plesio.setPersistenceRequired();
        plesio.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(84, 2, 84))));
        world.addFreshEntity(plesio);

        var loads = new ArrayList<String>();
        var area = h.getBounds().inflate(16);
        java.util.function.Consumer<ChunkEvent.Load> listener = event -> {
            var pos = event.getChunk().getPos();
            if (event.getLevel() == world && pos.getMinBlockX() <= area.maxX && pos.getMaxBlockX() >= area.minX
                    && pos.getMinBlockZ() <= area.maxZ && pos.getMaxBlockZ() >= area.minZ) loads.add(pos.toString());
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, listener);
        GameTestCleanup.onFinish(h, () -> {
            NeoForge.EVENT_BUS.unregister(listener);
            SessionRecorder.stop("test_cleanup");
            carno.discard(); player.discard(); pig.discard(); ptero.discard(); plesio.discard();
            world.clockManager().setTotalTicks(clock, oldTime);
            Config.DAY_SLEEP.set(sleepShare);
            Config.NIGHT_TRANSITION.set(transition);
        });

        String[] session = {SessionRecorder.start(server, player, 0, 3600, RECORDED_TICKS, "test")};
        h.assertTrue(session[0] != null, "Another recording was already running");
        long started = h.getTick();
        boolean[] approached = new boolean[1];
        int[] phase = new int[1];
        long[] phaseStart = new long[1];
        var swimmerId = plesio.getUUID();
        h.onEachTick(() -> {
            long tick = h.getTick() - started;
            if (carno.isAlive() && player.isAlive()) {
                // Far: follows the roaming animal at sixteen blocks. Near: stands seven blocks in front of it.
                if (tick < APPROACH_TICK) player.setPos(carno.position().add(0, 0, 16));
                else if (!approached[0]) {
                    approached[0] = true;
                    player.setPos(carno.position().add(Vec3.directionFromRotation(0, carno.yBodyRot).scale(7)));
                }
            }
            // The pig wants to walk east and is put back every tick.
            if (pig.isAlive()) {
                if (pig.getNavigation().isDone()) pig.getNavigation().moveTo(pen.x + 10, pen.y, pen.z, 1.0);
                pig.setPos(pen);
            }
            // The swimmer goes away and the same animal, a new object under its old UUID, comes back.
            if (tick == LEAVE_TICK) plesio.discard();
            if (tick == RETURN_TICK) {
                var back = ModContent.CREATURES.get(Species.PLESIOSAUR).get().create(world, EntitySpawnReason.COMMAND);
                back.setUUID(swimmerId);
                back.setNoAi(true);
                back.setPersistenceRequired();
                back.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(84, 2, 84))));
                world.addFreshEntity(back);
                GameTestCleanup.onFinish(h, back::discard);
            }
            var result = SessionRecorder.result(session[0]);
            try {
                switch (phase[0]) {
                    case 0 -> {
                        if (tick <= RECORDED_TICKS || result == null) return;
                        var rows = read(h, result, "tick_limit");
                        check(h, rows, carno.getUUID().toString(), player.getUUID().toString(), pig.getUUID().toString(),
                                ptero.getUUID().toString(), swimmerId.toString());
                        h.assertTrue(loads.isEmpty(), "Recording loaded chunks around its plot: " + loads);
                        // Kept as the fixture the Python analyzer is checked against.
                        Path kept = server.getServerDirectory().resolve("diagnostics").resolve("gametest");
                        Files.createDirectories(kept);
                        Files.copy(result.file(), kept.resolve("session.jsonl.gz"), StandardCopyOption.REPLACE_EXISTING);
                        discard(result.file());
                        // Waits 100 ms, then records until 200 ms of real time have passed.
                        session[0] = SessionRecorder.startTimed(server, player, 100, 200, 0, "test");
                        phase[0] = 1;
                    }
                    case 1 -> {
                        if (result == null) return;
                        var rows = read(h, result, "deadline");
                        var end = rows.get(rows.size() - 2);
                        h.assertTrue(rows.getFirst().get("waited_ms").getAsLong() >= 100, "The countdown was skipped: " + rows.getFirst().get("waited_ms"));
                        h.assertTrue(end.get("active_ms").getAsLong() >= 200 && end.get("ticks").getAsInt() > 0, "The deadline came early: " + end);
                        discard(result.file());
                        session[0] = SessionRecorder.start(server, player, 0, 3600, 0, "test");
                        phaseStart[0] = h.getTick();
                        phase[0] = 2;
                    }
                    case 2 -> {
                        if (h.getTick() - phaseStart[0] == 6) h.assertTrue(SessionRecorder.stop("command"), "Nothing to stop");
                        if (result == null) return;
                        var rows = read(h, result, "command");
                        int ticks = rows.get(rows.size() - 2).get("ticks").getAsInt();
                        h.assertTrue(ticks >= 4 && ticks <= 6, "A recording stopped by hand kept " + ticks + " ticks");
                        discard(result.file());
                        phase[0] = 3;
                        h.succeed();
                    }
                    default -> {}
                }
            } catch (IOException e) {
                h.fail("Recording unreadable: " + e);
            }
        });
    }

    private static void discard(Path file) throws IOException {
        Files.deleteIfExists(file);
        Files.deleteIfExists(file.getParent());
    }

    /** Every record of a finished recording, after the checks any recording must pass: whole, compressed, in sequence. */
    private static List<JsonObject> read(GameTestHelper h, dev.nez.arksurvivalreturns.feature.recorder.SessionWriter.Result result,
            String reason) throws IOException {
        Path file = result.file();
        h.assertTrue(file != null && result.complete(), "The recording reports missing records: " + result);
        h.assertTrue(result.compressed() && file.getFileName().toString().endsWith(".gz"), "The recording was not compressed: " + file);
        h.assertFalse(Files.exists(file.resolveSibling("session.jsonl")), "The plain file outlived its archive");
        var rows = new ArrayList<JsonObject>();
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                String repeated = repeatedKey(line);
                h.assertTrue(repeated == null, "A record names the field " + repeated + " twice: " + line);
                rows.add(JsonParser.parseString(line).getAsJsonObject());
            }
        }
        var header = rows.getFirst();
        h.assertTrue(type(header).equals("header") && header.get("schema").getAsInt() == 1, "The first record is not the header");
        h.assertTrue(header.getAsJsonObject("config").has("nighttime.playerWakeDistance")
                && header.getAsJsonObject("gamerules").size() > 10 && header.getAsJsonArray("species").size() == Species.values().length,
                "The header lacks the config, the game rules or the species table");
        var footer = rows.getLast();
        h.assertTrue(type(footer).equals("footer") && footer.get("dropped").getAsLong() == 0
                && footer.get("rows").getAsLong() == rows.size() - 1 && footer.get("reason").getAsString().equals(reason),
                "Footer does not match the file or the stop reason " + reason + ": " + footer);
        var end = rows.get(rows.size() - 2);
        h.assertTrue(type(end).equals("end") && end.get("reason").getAsString().equals(reason) && end.get("errors").getAsInt() == 0,
                "End record: " + end);
        long expected = 0;
        for (var row : rows.subList(0, rows.size() - 1)) {
            h.assertTrue(row.get("s").getAsLong() == expected, "Sequence broken at " + expected + ": " + row);
            expected++;
        }
        return rows;
    }

    private static void check(GameTestHelper h, List<JsonObject> rows, String carnoId, String playerId, String pigId, String pteroId,
            String plesioId) {
        h.assertTrue(rows.size() > 500, "Suspiciously few records: " + rows.size());
        int carno = -1, player = -1, pig = -1, ptero = -1, plesio = -1, ticks = 0, statuses = 0, motions = 0, playerRows = 0;
        int swimmerRecords = 0, left = -1, returned = -1, lastSwim = -1;
        boolean flight = false, swimmer = false;
        var stages = new ArrayList<String>();
        var reasons = new HashSet<String>();
        var branches = new HashSet<String>();
        boolean targeted = false, stalled = false, tree = false, pathSeen = false, senseDetail = false;
        for (var row : rows) {
            String type = type(row);
            switch (type) {
                case "ent" -> {
                    String uuid = row.get("uuid").getAsString();
                    int sid = row.get("e").getAsInt();
                    if (uuid.equals(carnoId)) {
                        carno = sid;
                        h.assertTrue(row.get("species").getAsString().equals("carnotaurus") && row.get("origin").getAsString().equals("present")
                                && row.get("w").getAsDouble() > 3, "Carnotaurus entity record wrong: " + row);
                    } else if (uuid.equals(playerId)) player = sid;
                    else if (uuid.equals(pigId)) pig = sid;
                    else if (uuid.equals(pteroId)) { ptero = sid; h.assertTrue(row.get("realm").getAsString().equals("AIR"), "Pteranodon realm: " + row); }
                    else if (uuid.equals(plesioId)) {
                        plesio = sid;
                        swimmerRecords++;
                        h.assertTrue(row.get("realm").getAsString().equals("WATER"), "Plesiosaur realm: " + row);
                    }
                }
                case "tick" -> ticks++;
                case "s" -> {
                    int sid = row.get("e").getAsInt();
                    if (sid == carno) statuses++;
                    if (sid == ptero) flight |= row.has("phase") && row.has("st");
                    if (sid == plesio) {
                        swimmer |= row.has("st") && row.has("tier");
                        lastSwim = row.get("k").getAsInt();
                    }
                }
                case "p" -> { if (row.get("e").getAsInt() == player) playerRows++; }
                case "m" -> {
                    for (var sample : row.getAsJsonArray("rows"))
                        if (sample.getAsJsonArray().get(0).getAsInt() == carno) motions++;
                }
                case "path" -> pathSeen |= row.has("nodes");
                case "d" -> {
                    if (row.get("e").getAsInt() != carno) break;
                    reasons.add(row.get("why").getAsString());
                    if (row.has("br")) branches.add(row.get("br").getAsString());
                    if (!row.has("pl")) break;
                    for (var entry : row.getAsJsonArray("pl")) {
                        var note = entry.getAsJsonObject();
                        if (note.get("e").getAsInt() != player) continue;
                        String stage = note.get("stage").getAsString();
                        if (stages.isEmpty() || !stages.getLast().equals(stage)) stages.add(stage);
                        if (stage.equals("day_routine"))
                            h.assertTrue(note.get("body").getAsDouble() > note.get("wake").getAsDouble(), "Filtered inside the wake distance: " + note);
                        if (stage.equals("sensed")) senseDetail |= note.has("sight") && note.has("los") && note.has("fov");
                    }
                }
                case "ev" -> {
                    String event = row.get("ev").getAsString();
                    if (event.equals("target") && row.get("e").getAsInt() == carno && row.get("tg").getAsInt() == player) targeted = true;
                    if (event.equals("stall") && row.get("e").getAsInt() == pig) stalled = true;
                    if (event.equals("leave") && row.get("e").getAsInt() == plesio && row.get("why").getAsString().equals("DISCARDED"))
                        left = row.get("k").getAsInt();
                    if (event.equals("return") && row.get("e").getAsInt() == plesio) returned = row.get("k").getAsInt();
                }
                case "terrain" -> {
                    if (row.get("e").getAsInt() != pig) break;
                    String palette = row.getAsJsonArray("pal").toString();
                    tree |= palette.contains("minecraft:oak_log") && palette.contains("minecraft:oak_leaves")
                            && palette.contains("minecraft:grass_block") && row.getAsJsonArray("b").size() % 5 == 0;
                }
                default -> {}
            }
        }
        h.assertTrue(carno >= 0 && player >= 0 && pig >= 0 && ptero >= 0 && plesio >= 0, "An actor has no entity record");
        h.assertTrue(flight && swimmer, "The flying or the water-bound creature has no status with its own fields");
        h.assertTrue(ticks == RECORDED_TICKS, "Tick records: " + ticks);
        h.assertTrue(playerRows == RECORDED_TICKS, "Player records: " + playerRows);
        h.assertTrue(statuses == RECORDED_TICKS / 10, "Carnotaurus status records: " + statuses);
        h.assertTrue(motions == RECORDED_TICKS, "Carnotaurus per-tick samples: " + motions);
        h.assertTrue(pathSeen, "No path was copied");
        h.assertTrue(stages.size() >= 2 && stages.getFirst().equals("day_routine") && stages.contains("sensed"),
                "The player's way through the candidate funnel is not explained: " + stages);
        h.assertTrue(senseDetail, "A sensed player carries no sense detail");
        h.assertTrue(reasons.contains("ROUTINE") && reasons.contains("WARNING") && reasons.contains("INTRUDER"),
                "Decision reasons recorded: " + reasons);
        h.assertTrue(branches.contains("chase") || branches.contains("strike"), "The attack was not recorded: " + branches);
        h.assertTrue(targeted, "The target change to the player has no event");
        h.assertTrue(stalled, "The held pig produced no stall event");
        h.assertTrue(swimmerRecords == 1 && left >= 0 && returned > left && lastSwim > returned,
                "Leaving and returning under one identity: entity records " + swimmerRecords + ", left " + left + ", returned " + returned
                        + ", last status " + lastSwim);
        h.assertTrue(tree, "The stall has no terrain capture with the tree beside it");
    }

    private static String type(JsonObject row) { return row.get("t").getAsString(); }

    /** The first field name used twice inside one object of the line, or null. Parsers disagree on what that means. */
    private static String repeatedKey(String line) throws IOException {
        var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(line));
        var open = new java.util.ArrayDeque<HashSet<String>>();
        while (true) {
            switch (reader.peek()) {
                case BEGIN_OBJECT -> { reader.beginObject(); open.push(new HashSet<>()); }
                case END_OBJECT -> { reader.endObject(); open.pop(); }
                case BEGIN_ARRAY -> { reader.beginArray(); open.push(new HashSet<>()); }
                case END_ARRAY -> { reader.endArray(); open.pop(); }
                case NAME -> {
                    String name = reader.nextName();
                    if (!open.peek().add(name)) return name;
                }
                case END_DOCUMENT -> { return null; }
                default -> reader.skipValue();
            }
        }
    }

    private SessionRecorderGameTests() {}
}
