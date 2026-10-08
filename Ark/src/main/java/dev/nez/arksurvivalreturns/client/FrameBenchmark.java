package dev.nez.arksurvivalreturns.client;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.recorder.RecorderPayloads;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Frame-time benchmark of the real client with nobody at the keyboard ({@code tools/session_bench.py}).
 * Off unless the client was launched with {@code -Darksurvivalreturns.benchmark=<output folder>}, and only in
 * a single-player world. Once the player is in control it becomes creative at the world spawn under a
 * fixed noon and clear sky, and the same camera path is driven by the clock: a pause for chunks and
 * shaders, a full turn on the ground, a lift, a flight that follows the terrain in the direction with the
 * least water (the same one for a given seed). Every frame's
 * duration is kept with its phase; once a second a sample of position, window state, thread load and heap.
 * {@code -Darksurvivalreturns.benchmark.mobs=false} empties the world of every creature, for the cost of drawing them.
 * The folder gets frames.csv, samples.csv, meta.json and a screenshot per measured phase; the game then closes.
 *
 * <p>{@code -Darksurvivalreturns.benchmark.path=return} flies out and back instead, for what happens to the land a
 * player leaves: after the pause at the world spawn the player is taken {@code benchmark.teleport} blocks on (768),
 * stands there while the chunks load and the land is settled ({@code benchmark.arrive} seconds), lifts, flies
 * straight until that place is {@code benchmark.out} chunks behind (the render distance and eleven more, so the
 * land within 128 blocks of it is no longer loaded), stays away {@code benchmark.hold} seconds, flies back and hovers over the same
 * spot with the same view for {@code benchmark.home} seconds. Each leg is a phase of the frame times; a picture
 * is taken before leaving and after the return, and each turn of the path is marked in a running session
 * recording, whose roll calls of the wildlife register say who was there before and after.
 *
 * <p>When the world is prepared, {@code -Darksurvivalreturns.benchmark.biome=minecraft:plains} moves the world spawn,
 * and with it the start of every later run, to the middle of the nearest patch of that biome, and
 * {@code -Darksurvivalreturns.benchmark.pregen=<chunks>} has Chunky generate that many chunks around it in every
 * direction before the path is flown (the file {@code pregen} in the folder holds its progress; nothing is
 * measured meanwhile). A start that cannot be made writes the file {@code error} and closes the game.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class FrameBenchmark {
    private static final String OUT = System.getProperty("arksurvivalreturns.benchmark", "");
    private static final int SETTLE_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.settle", 30),
            PAN_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.pan", 30),
            CLIMB_SECONDS = 8, FLIGHT_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.flight", 40);
    private static final boolean MOBS = !"false".equals(System.getProperty("arksurvivalreturns.benchmark.mobs"));
    private static final String BIOME = System.getProperty("arksurvivalreturns.benchmark.biome", "");
    private static final int PREGEN_CHUNKS = Integer.getInteger("arksurvivalreturns.benchmark.pregen", 0);
    private static final boolean RETURN = "return".equals(System.getProperty("arksurvivalreturns.benchmark.path"));
    private static final int TELEPORT_BLOCKS = Integer.getInteger("arksurvivalreturns.benchmark.teleport", 768),
            ARRIVE_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.arrive", 60),
            OUT_CHUNKS = Integer.getInteger("arksurvivalreturns.benchmark.out", 0),
            HOLD_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.hold", 150),
            HOME_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.home", 60);
    private static final float FLIGHT_PITCH = 12;
    private static final int LIFT = 30, CLEARANCE_LOW = 22, CLEARANCE_HIGH = 38;
    private static final int BIOME_SEARCH = 6400, PATCH_REACH = 384, PATCH_AROUND = 160, PATCH_STEP = 32;
    /** A leg flown to a place ends there; should it never arrive, it ends after this long. */
    private static final int LEG_SECONDS = 180;

    private enum Phase {
        // PREGEN ends when Chunky reports the end, never by the clock.
        WAIT(0), PREGEN(Integer.MAX_VALUE), SETTLE(SETTLE_SECONDS), PAN(PAN_SECONDS), CLIMB(CLIMB_SECONDS), FLIGHT(FLIGHT_SECONDS),
        // The out-and-back path. OUT and BACK end where the player is, not by the clock.
        ARRIVE(ARRIVE_SECONDS), OUT(LEG_SECONDS), AWAY(HOLD_SECONDS), BACK(LEG_SECONDS), HOME(HOME_SECONDS), DONE(0);

        final long nanos;

        Phase(int seconds) { nanos = seconds * 1_000_000_000L; }

        /** The phase that follows on the path being flown. */
        Phase next() {
            if (!RETURN) return this == FLIGHT ? DONE : values()[ordinal() + 1];
            return switch (this) {
                case SETTLE -> ARRIVE;
                case ARRIVE -> CLIMB;
                case CLIMB -> OUT;
                default -> values()[ordinal() + 1];
            };
        }

        boolean flying() { return this == CLIMB || this == FLIGHT || this == OUT || this == AWAY || this == BACK || this == HOME; }

        boolean moving() { return this == FLIGHT || this == OUT || this == BACK; }
    }

    private static final class Drive extends ClientInput {
        boolean forward, up, down;

        @Override public void tick() {
            keyPresses = new Input(forward, false, false, false, up, down, false);
            moveVector = new Vec2(0, forward ? 1 : 0);
        }
    }

    private static final Drive DRIVE = new Drive();
    private static final LongArrayList FRAMES = new LongArrayList();
    private static final ByteArrayList FRAME_PHASES = new ByteArrayList();
    private static final StringBuilder SAMPLES = new StringBuilder(
            "t_ms,phase,fps,x,y,z,focused,iconified,fullscreen,throttle,entities,creatures,heap_mb,render_cpu,server_cpu,"
                    + "process_cpu,system_cpu,server_tick_ms,gc_count,gc_ms,creature_far\n");
    private static final JsonObject PHASES = new JsonObject();
    private static volatile Phase phase = Phase.WAIT;
    private static volatile double renderLoad, serverLoad, processLoad, systemLoad;
    private static volatile boolean pregenDone, failed;
    private static volatile String pregenProgress = "";
    private static long began, phaseBegan, lastFrame, lastSample;
    private static int framesInSample, unseenSamples, quitIn = -1, pregenTicks;
    private static boolean windowSet;
    private static volatile float heading = -90;
    private static String start = "";
    /** The out-and-back path: where it starts and returns to, the way out, how far, and whether the server has put the player there. */
    private static volatile double anchorX, anchorZ;
    private static volatile float outYaw;
    private static volatile boolean anchored;
    private static volatile String anchor = "";
    private static int outBlocks;

    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if (OUT.isEmpty()) return;
        var mc = Minecraft.getInstance();
        // A full-screen window that was opened without the focus minimises itself and draws nothing.
        if (!windowSet && mc.getWindow() != null) {
            windowSet = true;
            GLFW.glfwSetWindowAttrib(mc.getWindow().handle(), GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
        }
        if (quitIn >= 0 && quitIn-- == 0) mc.stop();
        if (failed && quitIn < 0) {
            phase = Phase.DONE;
            quitIn = 20;
        }
        var player = mc.player;
        if (player == null || mc.level == null || phase == Phase.DONE) return;
        if (mc.screen != null && mc.screen.isPauseScreen()) mc.setScreen(null);
        if (phase == Phase.WAIT) {
            if (mc.screen == null && mc.getOverlay() == null && !mc.isPaused() && mc.getSingleplayerServer() != null) begin(mc);
            return;
        }
        if (phase == Phase.PREGEN) {
            if (pregenTicks++ % 200 == 0) note("pregen", pregenProgress);
            if (pregenDone) settle();
        }
        if (player.input != DRIVE) player.input = DRIVE;
        DRIVE.forward = DRIVE.up = DRIVE.down = false;
        if (!phase.flying()) return;
        var abilities = player.getAbilities();
        if (abilities.mayfly && !abilities.flying) { abilities.flying = true; player.onUpdateAbilities(); }
        if (!phase.moving()) return;
        // A leg of the out-and-back path ends where it leads: far enough out, or back over where it began.
        double along = (player.getX() - anchorX) * -Mth.sin(outYaw * Mth.DEG_TO_RAD) + (player.getZ() - anchorZ) * Mth.cos(outYaw * Mth.DEG_TO_RAD);
        if (phase == Phase.OUT && along >= outBlocks || phase == Phase.BACK && along <= 0) {
            next(System.nanoTime(), false);
            return;
        }
        DRIVE.forward = true;
        double gap = player.getY() - groundAhead(mc, player);
        DRIVE.up = player.horizontalCollision || gap < CLEARANCE_LOW;
        DRIVE.down = !DRIVE.up && gap > CLEARANCE_HIGH;
    }

    @SubscribeEvent public static void frameStart(RenderFrameEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player == null || phase == Phase.WAIT || phase == Phase.DONE) return;
        long now = System.nanoTime();
        // The stand at the far start begins once the server has put the player there.
        if (phase == Phase.ARRIVE && !anchored) phaseBegan = now;
        while (phase != Phase.DONE && now - phaseBegan >= phase.nanos) next(now, true);
        if (phase == Phase.DONE) return;
        // The view follows the clock, not the frame count, so every setup is measured on the same path.
        float turn = phase == Phase.PAN ? 360f * (now - phaseBegan) / phase.nanos : 0;
        player.setYRot(Mth.wrapDegrees(heading + turn));
        player.setXRot(phase.flying() ? FLIGHT_PITCH : 0);
    }

    @SubscribeEvent public static void frameEnd(RenderFrameEvent.Post event) {
        if (phase == Phase.WAIT || phase == Phase.PREGEN || phase == Phase.DONE) return;
        long now = System.nanoTime();
        if (lastFrame != 0) {
            FRAMES.add(now - lastFrame);
            FRAME_PHASES.add((byte) phase.ordinal());
            framesInSample++;
        }
        lastFrame = now;
        if (now - lastSample >= 1_000_000_000L) sample(Minecraft.getInstance(), now);
    }

    private static void begin(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var level = server.overworld();
            var spawn = BIOME.isEmpty() ? server.getRespawnData().pos() : patchCentre(level, server.getRespawnData().pos());
            if (spawn == null) {
                fail("no " + BIOME + " within " + BIOME_SEARCH + " blocks of the world spawn; prepare with another seed");
                return;
            }
            int y = level.getChunk(spawn).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) + 1;
            if (!BIOME.isEmpty()) level.setRespawnData(LevelData.RespawnData.of(level.dimension(), new BlockPos(spawn.getX(), y, spawn.getZ()), 0, 0));
            start = spawn.getX() + " " + y + " " + spawn.getZ();
            heading = landward(level, spawn);
            var source = server.createCommandSourceStack();
            if (!MOBS) server.getCommands().performPrefixedCommand(source, "gamerule spawn_mobs false");
            for (String command : List.of("gamemode creative @a", "gamerule advance_time false", "gamerule advance_weather false",
                    "weather clear", "time set 6000", "execute in minecraft:overworld run tp @a " + (spawn.getX() + 0.5) + " " + y + " "
                            + (spawn.getZ() + 0.5) + " " + heading + " 0"))
                server.getCommands().performPrefixedCommand(source, command);
            if (PREGEN_CHUNKS > 0) pregenerate(spawn.getX(), spawn.getZ());
        });
        began = phaseBegan = lastSample = System.nanoTime();
        // Far enough that the place left behind, 128 blocks around it, is beyond the chunks the server keeps loaded:
        // the render distance, the eight chunks of the place and a margin for the ring the server holds on to.
        outBlocks = (OUT_CHUNKS > 0 ? OUT_CHUNKS : mc.options.getEffectiveRenderDistance() + 11) * 16;
        phase = PREGEN_CHUNKS > 0 ? Phase.PREGEN : Phase.SETTLE;
        watchLoad(Thread.currentThread().threadId(), server.getRunningThread().threadId());
        mark(phase);
        ArkSurvivalReturns.LOGGER.info("Frame benchmark: started, output {}", OUT);
    }

    /**
     * The middle of the nearest patch of the wanted biome: of the points around the nearest find, the one with the
     * most of that biome within 160 blocks. Null when the biome is not within reach of the world spawn.
     */
    private static BlockPos patchCentre(ServerLevel level, BlockPos from) {
        var wanted = ResourceKey.create(Registries.BIOME, Identifier.parse(BIOME));
        var found = level.findClosestBiome3d(biome -> biome.is(wanted), from, BIOME_SEARCH, 32, 64);
        if (found == null) return null;
        var biomes = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        BlockPos near = found.getFirst();
        int side = (PATCH_REACH + PATCH_AROUND) / PATCH_STEP, span = PATCH_AROUND / PATCH_STEP;
        boolean[][] is = new boolean[2 * side + 1][2 * side + 1];
        for (int i = 0; i <= 2 * side; i++)
            for (int j = 0; j <= 2 * side; j++)
                is[i][j] = biomes.getNoiseBiome(QuartPos.fromBlock(near.getX() + (i - side) * PATCH_STEP), QuartPos.fromBlock(from.getY()),
                        QuartPos.fromBlock(near.getZ() + (j - side) * PATCH_STEP), sampler).is(wanted);
        BlockPos centre = near;
        int most = -1;
        for (int i = span; i <= 2 * side - span; i++)
            for (int j = span; j <= 2 * side - span; j++) {
                if (!is[i][j]) continue;
                int count = 0;
                for (int di = -span; di <= span; di++)
                    for (int dj = -span; dj <= span; dj++) if (di * di + dj * dj <= span * span && is[i + di][j + dj]) count++;
                if (count > most) {
                    most = count;
                    centre = new BlockPos(near.getX() + (i - side) * PATCH_STEP, from.getY(), near.getZ() + (j - side) * PATCH_STEP);
                }
            }
        ArkSurvivalReturns.LOGGER.info("Frame benchmark: {} found at {}, start moved to {}", BIOME, near.toShortString(), centre.toShortString());
        return centre;
    }

    /** Chunky is not part of the game: the runner puts it in run/mods for this launch, and it is reached by name. */
    private static void pregenerate(double x, double z) {
        try {
            Object chunky = Class.forName("org.popcraft.chunky.ChunkyProvider").getMethod("get").invoke(null);
            Object api = chunky.getClass().getMethod("getApi").invoke(chunky);
            Class<?> type = Class.forName("org.popcraft.chunky.api.ChunkyAPI");
            type.getMethod("onGenerationProgress", Consumer.class).invoke(api, (Consumer<Object>) event -> pregenProgress = event.toString());
            type.getMethod("onGenerationComplete", Consumer.class).invoke(api, (Consumer<Object>) event -> pregenDone = true);
            double radius = PREGEN_CHUNKS * 16.0;
            Object started = type.getMethod("startTask", String.class, String.class, double.class, double.class, double.class, double.class, String.class)
                    .invoke(api, "minecraft:overworld", "square", x, z, radius, radius, "region");
            if (!Boolean.TRUE.equals(started)) fail("Chunky did not start its task");
            else ArkSurvivalReturns.LOGGER.info("Frame benchmark: Chunky generates {} chunks around {} {} in every direction", PREGEN_CHUNKS, x, z);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ArkSurvivalReturns.LOGGER.error("Frame benchmark: Chunky could not be started", e);
            fail("Chunky is not loaded or does not answer: " + e);
        }
    }

    /** The generated world is settled on like any other: the clock of the path starts here. */
    private static void settle() {
        note("pregen", "done " + pregenProgress);
        phaseBegan = lastSample = System.nanoTime();
        lastFrame = 0;
        phase = Phase.SETTLE;
        mark(Phase.SETTLE);
    }

    private static void fail(String message) {
        ArkSurvivalReturns.LOGGER.error("Frame benchmark: {}", message);
        note("error", message);
        failed = true;
    }

    private static void note(String file, String text) {
        try {
            Files.createDirectories(Path.of(OUT));
            Files.writeString(Path.of(OUT, file), text);
        } catch (IOException e) {
            ArkSurvivalReturns.LOGGER.warn("Frame benchmark: could not write {} to {}", file, OUT, e);
        }
    }

    /** Of the eight compass directions, the one whose next 480 blocks hold the fewest ocean and river biomes. */
    private static float landward(ServerLevel level, BlockPos from) {
        var biomes = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        float best = heading;
        int least = Integer.MAX_VALUE;
        for (int step = 0; step < 8; step++) {
            float yaw = -90 + step * 45;
            int water = 0;
            for (int ahead = 32; ahead <= 480; ahead += 32) {
                var biome = biomes.getNoiseBiome(QuartPos.fromBlock(Mth.floor(from.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * ahead)),
                        QuartPos.fromBlock(from.getY()), QuartPos.fromBlock(Mth.floor(from.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * ahead)), sampler);
                if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) water++;
            }
            if (water < least) { least = water; best = yaw; }
        }
        return Mth.wrapDegrees(best);
    }

    /** The load figures are slow to read (tens of milliseconds), so another thread reads them once a second. */
    private static void watchLoad(long renderThread, long serverThread) {
        var watcher = new Thread(() -> {
            var threads = ManagementFactory.getThreadMXBean();
            var system = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long render = threads.getThreadCpuTime(renderThread), server = threads.getThreadCpuTime(serverThread), at = System.nanoTime();
            while (phase != Phase.DONE) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.nanoTime(), renderNow = threads.getThreadCpuTime(renderThread), serverNow = threads.getThreadCpuTime(serverThread);
                renderLoad = (renderNow - render) / (double) (now - at);
                serverLoad = (serverNow - server) / (double) (now - at);
                processLoad = system.getProcessCpuLoad();
                systemLoad = system.getCpuLoad();
                render = renderNow;
                server = serverNow;
                at = now;
            }
        }, "Ark frame benchmark");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** Ends the phase: by the clock when it ran its time, so the next begins where it ended; now when a leg reached its end. */
    private static void next(long now, boolean byClock) {
        var mc = Minecraft.getInstance();
        Phase ended = phase;
        // What the phase showed last: the turn, the flight, and on the way out and back the same view before and after.
        if (ended == Phase.PAN || ended == Phase.FLIGHT || ended == Phase.HOME || RETURN && ended == Phase.CLIMB)
            Screenshot.grab(new File(OUT), (ended == Phase.CLIMB ? "depart" : ended.name().toLowerCase(Locale.ROOT)) + ".png",
                    mc.getMainRenderTarget(), 1, message -> {});
        phaseBegan = byClock ? phaseBegan + ended.nanos : now;
        phase = ended.next();
        // The frame that takes the screenshot or follows a teleport is not the setup's own.
        lastFrame = 0;
        var server = mc.getSingleplayerServer();
        if (phase == Phase.ARRIVE) server.execute(() -> anchorAt(server));
        if (phase == Phase.CLIMB)
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "execute as @a at @s run tp @s ~ ~" + LIFT + " ~"));
        if (phase == Phase.BACK) heading = Mth.wrapDegrees(outYaw + 180);
        if (phase == Phase.HOME) heading = outYaw;
        // The turns of the way out and back, flagged in a session recording: it answers each with a roll call of the register.
        String turn = ended == Phase.ARRIVE ? "depart" : phase == Phase.AWAY ? "far" : phase == Phase.BACK ? "turn"
                : phase == Phase.HOME ? "home" : ended == Phase.HOME ? "settled" : null;
        var connection = mc.getConnection();
        if (RETURN && turn != null && connection != null && connection.hasChannel(RecorderPayloads.Mark.TYPE))
            ClientPacketDistributor.sendToServer(new RecorderPayloads.Mark(turn));
        if (phase == Phase.DONE) finish(mc);
        else mark(phase);
    }

    /**
     * Puts the player where the way out and back starts: as far from the world spawn as asked, in the direction, of
     * sixteen, with the most land within 256 blocks of the place, land of the spawn's own biome counting double (the
     * first heading where several have as much): there are animals to leave and to come back to, and on the open
     * ground of a plains world they are in the pictures. The way out is the landward one from there.
     */
    private static void anchorAt(MinecraftServer server) {
        var level = server.overworld();
        var spawn = server.getRespawnData().pos();
        var biomes = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        int x = spawn.getX(), z = spawn.getZ(), most = -1;
        var home = biomes.getNoiseBiome(QuartPos.fromBlock(spawn.getX()), QuartPos.fromBlock(spawn.getY()), QuartPos.fromBlock(spawn.getZ()), sampler);
        for (int step = 0; step < 16; step++) {
            float way = heading + step * 22.5f;
            int cx = Mth.floor(spawn.getX() - Mth.sin(way * Mth.DEG_TO_RAD) * TELEPORT_BLOCKS);
            int cz = Mth.floor(spawn.getZ() + Mth.cos(way * Mth.DEG_TO_RAD) * TELEPORT_BLOCKS);
            int land = 0;
            for (int dx = -256; dx <= 256; dx += 64)
                for (int dz = -256; dz <= 256; dz += 64) {
                    var biome = biomes.getNoiseBiome(QuartPos.fromBlock(cx + dx), QuartPos.fromBlock(spawn.getY()), QuartPos.fromBlock(cz + dz), sampler);
                    if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER))
                        land += biome.equals(home) ? 2 : 1;
                }
            if (land > most) {
                most = land;
                x = cx;
                z = cz;
            }
        }
        var at = new BlockPos(x, spawn.getY(), z);
        int y = level.getChunk(at).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        float yaw = landward(level, at);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "execute in minecraft:overworld run tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " " + yaw + " 0");
        anchorX = x + 0.5;
        anchorZ = z + 0.5;
        outYaw = heading = yaw;
        anchor = x + " " + y + " " + z;
        anchored = true;
        ArkSurvivalReturns.LOGGER.info("Frame benchmark: the way out and back starts at {}, heading {}", anchor, yaw);
    }

    private static void mark(Phase started) {
        var row = new JsonObject();
        row.addProperty("epoch_ms", System.currentTimeMillis());
        row.addProperty("seconds", started.nanos / 1_000_000_000L);
        row.addProperty("gc_count", gc(false));
        row.addProperty("gc_ms", gc(true));
        PHASES.add(started.name().toLowerCase(Locale.ROOT), row);
        // The runner reads where the path is while the game is still open.
        try {
            Files.createDirectories(Path.of(OUT));
            Files.writeString(Path.of(OUT, "phase"), started.name().toLowerCase(Locale.ROOT));
        } catch (IOException e) {
            ArkSurvivalReturns.LOGGER.warn("Frame benchmark: could not write the phase to {}", OUT, e);
        }
    }

    private static long gc(boolean millis) {
        long total = 0;
        // A concurrent collector (ZGC, Shenandoah) reports its cycles beside its pauses; only a pause stops the game.
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
            if (!bean.getName().endsWith("Cycles")) total += Math.max(0, millis ? bean.getCollectionTime() : bean.getCollectionCount());
        return total;
    }

    /** The highest ground under the player and on the next blocks of the flight line. */
    private static double groundAhead(Minecraft mc, LocalPlayer player) {
        double dx = -Mth.sin(heading * Mth.DEG_TO_RAD), dz = Mth.cos(heading * Mth.DEG_TO_RAD);
        int ground = Integer.MIN_VALUE;
        for (int ahead = 0; ahead <= 48; ahead += 12)
            ground = Math.max(ground, mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(player.getX() + dx * ahead),
                    Mth.floor(player.getZ() + dz * ahead)));
        return ground;
    }

    private static void sample(Minecraft mc, long now) {
        var window = mc.getWindow();
        var server = mc.getSingleplayerServer();
        double seconds = (now - lastSample) / 1e9;
        var limiter = mc.getFramerateLimitTracker();
        // Nobody touches the keyboard; without this the game would slow itself down as for an absent player.
        limiter.onInputReceived();
        boolean seen = window.isFocused() && !window.isIconified();
        if (!seen) {
            unseenSamples++;
            GLFW.glfwRestoreWindow(window.handle());
            GLFW.glfwFocusWindow(window.handle());
        }
        if (!MOBS && server != null) server.execute(() -> {
            List<Entity> gone = new ArrayList<>();
            for (Entity entity : server.overworld().getAllEntities()) if (!(entity instanceof Player)) gone.add(entity);
            gone.forEach(Entity::discard);
        });
        var player = mc.player;
        var runtime = Runtime.getRuntime();
        // The creatures the client holds are the ones it can draw; the farthest shows where the server stops sending them.
        int creatures = 0;
        double farthest = 0;
        for (Entity entity : mc.level.entitiesForRendering())
            if (entity instanceof CreatureEntity) {
                creatures++;
                farthest = Math.max(farthest, Math.hypot(entity.getX() - player.getX(), entity.getZ() - player.getZ()));
            }
        SAMPLES.append(String.format(Locale.ROOT, "%d,%s,%.1f,%.1f,%.1f,%.1f,%b,%b,%b,%s,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.2f,%d,%d,%.0f%n",
                (now - began) / 1_000_000, phase.name().toLowerCase(Locale.ROOT), framesInSample / seconds, player.getX(), player.getY(),
                player.getZ(), window.isFocused(), window.isIconified(), window.isFullscreen(), limiter.getThrottleReason(),
                mc.level.getEntityCount(), creatures, (runtime.totalMemory() - runtime.freeMemory()) >> 20, renderLoad, serverLoad,
                processLoad, systemLoad, server == null ? 0 : server.getCurrentSmoothedTickTime(), gc(false), gc(true), farthest));
        lastSample = now;
        framesInSample = 0;
    }

    private static void finish(Minecraft mc) {
        mark(Phase.DONE);
        var window = mc.getWindow();
        var device = RenderSystem.getDevice();
        var meta = new JsonObject();
        meta.addProperty("variant", System.getProperty("arksurvivalreturns.benchmark.variant", ""));
        meta.addProperty("window", window.getWidth() + "x" + window.getHeight());
        meta.addProperty("fullscreen", window.isFullscreen());
        meta.addProperty("refresh_rate", window.getRefreshRate());
        meta.addProperty("gpu", device.getRenderer());
        meta.addProperty("gpu_vendor", device.getVendor());
        meta.addProperty("gpu_version", device.getVersion());
        meta.addProperty("shaders", SessionRecorderClient.shaders());
        meta.addProperty("distant_horizons", ModList.get().isLoaded("distanthorizons"));
        meta.addProperty("render_distance", mc.options.renderDistance().get());
        meta.addProperty("simulation_distance", mc.options.simulationDistance().get());
        meta.addProperty("vsync", mc.options.enableVsync().get());
        meta.addProperty("fps_limit", mc.options.framerateLimit().get());
        meta.addProperty("heap_max_mb", Runtime.getRuntime().maxMemory() >> 20);
        meta.addProperty("processors", Runtime.getRuntime().availableProcessors());
        meta.addProperty("start", start);
        meta.addProperty("start_biome", BIOME);
        meta.addProperty("pregen_chunks", PREGEN_CHUNKS);
        meta.addProperty("heading", RETURN ? outYaw : heading);
        if (RETURN) {
            meta.addProperty("path", "return");
            meta.addProperty("anchor", anchor);
            meta.addProperty("teleport_blocks", TELEPORT_BLOCKS);
            meta.addProperty("out_blocks", outBlocks);
            meta.addProperty("hold_seconds", HOLD_SECONDS);
        }
        meta.addProperty("unseen_samples", unseenSamples);
        meta.add("phases", PHASES);
        meta.add("creature_draw", dev.nez.arksurvivalreturns.client.draw.CreatureDraw.report());
        var frames = new StringBuilder("phase,frame_ns\n");
        for (int i = 0; i < FRAMES.size(); i++)
            frames.append(Phase.values()[FRAME_PHASES.getByte(i)].name().toLowerCase(Locale.ROOT)).append(',').append(FRAMES.getLong(i)).append('\n');
        try {
            Path folder = Path.of(OUT);
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("frames.csv"), frames);
            Files.writeString(folder.resolve("samples.csv"), SAMPLES);
            Files.writeString(folder.resolve("meta.json"), new GsonBuilder().setPrettyPrinting().create().toJson(meta));
            ArkSurvivalReturns.LOGGER.info("Frame benchmark: {} frames written to {}", FRAMES.size(), folder);
        } catch (IOException e) {
            ArkSurvivalReturns.LOGGER.error("Frame benchmark: could not write {}", OUT, e);
        }
        // A moment for the screenshots to reach the disk, then the game ends.
        quitIn = 60;
    }

    private FrameBenchmark() {}
}
