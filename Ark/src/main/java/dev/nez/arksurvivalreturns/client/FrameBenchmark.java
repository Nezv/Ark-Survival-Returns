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
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
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
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class FrameBenchmark {
    private static final String OUT = System.getProperty("arksurvivalreturns.benchmark", "");
    private static final int SETTLE_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.settle", 30),
            PAN_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.pan", 30),
            CLIMB_SECONDS = 8, FLIGHT_SECONDS = Integer.getInteger("arksurvivalreturns.benchmark.flight", 40);
    private static final boolean MOBS = !"false".equals(System.getProperty("arksurvivalreturns.benchmark.mobs"));
    private static final float FLIGHT_PITCH = 12;
    private static final int LIFT = 30, CLEARANCE_LOW = 22, CLEARANCE_HIGH = 38;

    private enum Phase {
        WAIT(0), SETTLE(SETTLE_SECONDS), PAN(PAN_SECONDS), CLIMB(CLIMB_SECONDS), FLIGHT(FLIGHT_SECONDS), DONE(0);

        final long nanos;

        Phase(int seconds) { nanos = seconds * 1_000_000_000L; }
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
                    + "process_cpu,system_cpu,server_tick_ms,gc_count,gc_ms\n");
    private static final JsonObject PHASES = new JsonObject();
    private static volatile Phase phase = Phase.WAIT;
    private static volatile double renderLoad, serverLoad, processLoad, systemLoad;
    private static long began, phaseBegan, lastFrame, lastSample;
    private static int framesInSample, unseenSamples, quitIn = -1;
    private static boolean windowSet;
    private static volatile float heading = -90;
    private static String start = "";

    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if (OUT.isEmpty()) return;
        var mc = Minecraft.getInstance();
        // A full-screen window that was opened without the focus minimises itself and draws nothing.
        if (!windowSet && mc.getWindow() != null) {
            windowSet = true;
            GLFW.glfwSetWindowAttrib(mc.getWindow().handle(), GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
        }
        if (quitIn >= 0 && quitIn-- == 0) mc.stop();
        var player = mc.player;
        if (player == null || mc.level == null || phase == Phase.DONE) return;
        if (mc.screen != null && mc.screen.isPauseScreen()) mc.setScreen(null);
        if (phase == Phase.WAIT) {
            if (mc.screen == null && mc.getOverlay() == null && !mc.isPaused() && mc.getSingleplayerServer() != null) begin(mc);
            return;
        }
        if (player.input != DRIVE) player.input = DRIVE;
        DRIVE.forward = DRIVE.up = DRIVE.down = false;
        if (phase != Phase.CLIMB && phase != Phase.FLIGHT) return;
        var abilities = player.getAbilities();
        if (abilities.mayfly && !abilities.flying) { abilities.flying = true; player.onUpdateAbilities(); }
        if (phase != Phase.FLIGHT) return;
        DRIVE.forward = true;
        double gap = player.getY() - groundAhead(mc, player);
        DRIVE.up = player.horizontalCollision || gap < CLEARANCE_LOW;
        DRIVE.down = !DRIVE.up && gap > CLEARANCE_HIGH;
    }

    @SubscribeEvent public static void frameStart(RenderFrameEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player == null || phase == Phase.WAIT || phase == Phase.DONE) return;
        long now = System.nanoTime();
        while (phase != Phase.DONE && now - phaseBegan >= phase.nanos) next(now);
        if (phase == Phase.DONE) return;
        // The view follows the clock, not the frame count, so every setup is measured on the same path.
        float turn = phase == Phase.PAN ? 360f * (now - phaseBegan) / phase.nanos : 0;
        player.setYRot(Mth.wrapDegrees(heading + turn));
        player.setXRot(phase == Phase.CLIMB || phase == Phase.FLIGHT ? FLIGHT_PITCH : 0);
    }

    @SubscribeEvent public static void frameEnd(RenderFrameEvent.Post event) {
        if (phase == Phase.WAIT || phase == Phase.DONE) return;
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
        var spawn = server.getRespawnData().pos();
        server.execute(() -> {
            var level = server.overworld();
            int y = level.getChunk(spawn).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) + 1;
            start = spawn.getX() + " " + y + " " + spawn.getZ();
            heading = landward(level, spawn);
            var source = server.createCommandSourceStack();
            if (!MOBS) server.getCommands().performPrefixedCommand(source, "gamerule spawn_mobs false");
            for (String command : List.of("gamemode creative @a", "gamerule advance_time false", "gamerule advance_weather false",
                    "weather clear", "time set 6000", "execute in minecraft:overworld run tp @a " + (spawn.getX() + 0.5) + " " + y + " "
                            + (spawn.getZ() + 0.5) + " " + heading + " 0"))
                server.getCommands().performPrefixedCommand(source, command);
        });
        began = phaseBegan = lastSample = System.nanoTime();
        phase = Phase.SETTLE;
        watchLoad(Thread.currentThread().threadId(), server.getRunningThread().threadId());
        mark(Phase.SETTLE);
        ArkSurvivalReturns.LOGGER.info("Frame benchmark: started, output {}", OUT);
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

    private static void next(long now) {
        var mc = Minecraft.getInstance();
        if (phase == Phase.PAN || phase == Phase.FLIGHT)
            Screenshot.grab(new File(OUT), phase.name().toLowerCase(Locale.ROOT) + ".png", mc.getMainRenderTarget(), 1, message -> {});
        phaseBegan += phase.nanos;
        phase = Phase.values()[phase.ordinal() + 1];
        // The frame that takes the screenshot or follows a teleport is not the setup's own.
        lastFrame = 0;
        if (phase == Phase.CLIMB) {
            var server = mc.getSingleplayerServer();
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "execute as @a at @s run tp @s ~ ~" + LIFT + " ~"));
        }
        if (phase == Phase.DONE) finish(mc);
        else mark(phase);
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
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
            total += Math.max(0, millis ? bean.getCollectionTime() : bean.getCollectionCount());
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
        int creatures = 0;
        for (Entity entity : mc.level.entitiesForRendering()) if (entity instanceof CreatureEntity) creatures++;
        SAMPLES.append(String.format(Locale.ROOT, "%d,%s,%.1f,%.1f,%.1f,%.1f,%b,%b,%b,%s,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.2f,%d,%d%n",
                (now - began) / 1_000_000, phase.name().toLowerCase(Locale.ROOT), framesInSample / seconds, player.getX(), player.getY(),
                player.getZ(), window.isFocused(), window.isIconified(), window.isFullscreen(), limiter.getThrottleReason(),
                mc.level.getEntityCount(), creatures, (runtime.totalMemory() - runtime.freeMemory()) >> 20, renderLoad, serverLoad,
                processLoad, systemLoad, server == null ? 0 : server.getCurrentSmoothedTickTime(), gc(false), gc(true)));
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
        meta.addProperty("heading", heading);
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
