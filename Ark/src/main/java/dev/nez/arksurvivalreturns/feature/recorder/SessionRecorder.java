package dev.nez.arksurvivalreturns.feature.recorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeMind;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Debug recorder of a real play session: where every loaded creature was, what its AI decided and why,
 * and what its navigation and collisions did, for analysis in Python afterwards
 * ({@code tools/session_analyze.py}).
 *
 * <p>Off unless armed. {@code /arkrecord arm} (or the file {@code diagnostics/arm} in the game folder)
 * arms the next world join once: the recording starts a minute after the client reports that the player
 * controls the world and runs for three minutes of unpaused real time, or until the player leaves. The
 * file is {@code diagnostics/<session>/session.jsonl}, compressed when the recording ends.
 *
 * <p>The game code calls the static hooks below. They do nothing but a flag test while no session
 * records, and while one does they only copy what the caller already computed: no sense, path, random
 * number or mind step is ever run for the recording's sake.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class SessionRecorder {
    public static final int DELAY_SECONDS = 60, RECORD_SECONDS = 180;
    private static volatile boolean recording;
    private static Session session;
    /** Writers still closing their file; a stopping server waits for them. */
    private static final List<SessionWriter> CLOSING = new ArrayList<>();
    private static volatile SessionWriter lastWriter;
    private static volatile String lastId;

    /** True while a session writes records; every hook tests this first. */
    public static boolean on() { return recording; }

    static void recording(boolean value) { recording = value; }

    static void closed(Session ended) {
        synchronized (CLOSING) { CLOSING.add(ended.writer); }
        lastId = ended.id;
        lastWriter = ended.writer;
        if (session == ended) session = null;
    }

    /** The recording session, only on the server thread that owns it. */
    private static Session live() {
        var current = session;
        return recording && current != null && Thread.currentThread() == current.thread ? current : null;
    }

    // --------------------------------------------------------------------------- lifecycle

    private static Path armFile(MinecraftServer server) { return server.getServerDirectory().resolve("diagnostics").resolve("arm"); }

    /** Arms the next world join once. Seconds of zero or less keep the defaults. */
    public static void arm(MinecraftServer server, int delaySeconds, int recordSeconds) throws IOException {
        Path file = armFile(server);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "delaySeconds=" + (delaySeconds < 0 ? DELAY_SECONDS : delaySeconds) + "\nrecordSeconds="
                + (recordSeconds <= 0 ? RECORD_SECONDS : recordSeconds) + "\n");
    }

    public static boolean armed(MinecraftServer server) { return Files.isRegularFile(armFile(server)); }

    /** Starts a session for this player; the countdown begins on the next server tick. Null when one is running. */
    public static String start(MinecraftServer server, Player subject, int delaySeconds, int recordSeconds, int tickLimit, String by) {
        return startTimed(server, subject, delaySeconds * 1000L, recordSeconds * 1000L, tickLimit, by);
    }

    /** As {@link #start}, in milliseconds of unpaused real time; a tick limit above zero also ends the recording. */
    public static String startTimed(MinecraftServer server, Player subject, long delayMillis, long recordMillis, int tickLimit, String by) {
        if (session != null) return null;
        session = new Session(server, subject, subject instanceof ServerPlayer, delayMillis * 1_000_000L, recordMillis * 1_000_000L, tickLimit, by);
        session.ready(by, null);
        return session.id;
    }

    /** Stops the running session. False when there is none. */
    public static boolean stop(String reason) {
        var current = session;
        if (current == null) return false;
        current.stop(reason);
        return true;
    }

    public static String status() {
        var current = session;
        if (current == null) return "idle";
        return current.phase.name().toLowerCase(java.util.Locale.ROOT) + " " + current.id + ", " + current.remainingSeconds() + " s left";
    }

    /** Blocks until closing files are written, for tests and for a stopping server. */
    public static boolean awaitClosed(long millis) {
        List<SessionWriter> writers;
        synchronized (CLOSING) { writers = new ArrayList<>(CLOSING); }
        boolean done = true;
        for (var writer : writers) done &= writer.await(millis);
        if (done) synchronized (CLOSING) { CLOSING.removeAll(writers); }
        return done;
    }

    /** What this session left on disk; null while it runs, while its file is still being written, or once a later one ended. */
    public static SessionWriter.Result result(String id) {
        var writer = lastWriter;
        return writer == null || !id.equals(lastId) ? null : writer.result();
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isFakePlayer()) return;
        var server = player.level().getServer();
        if (session != null || server == null || !armed(server)) return;
        int delay = DELAY_SECONDS, seconds = RECORD_SECONDS;
        try {
            var settings = new Properties();
            try (var in = Files.newInputStream(armFile(server))) { settings.load(in); }
            delay = Math.clamp(Integer.parseInt(settings.getProperty("delaySeconds", "" + DELAY_SECONDS).trim()), 0, 3600);
            seconds = Math.clamp(Integer.parseInt(settings.getProperty("recordSeconds", "" + RECORD_SECONDS).trim()), 1, 3600);
            // One shot: the next join is an ordinary one again.
            Files.delete(armFile(server));
        } catch (IOException | NumberFormatException e) {
            ArkSurvivalReturns.LOGGER.warn("Session recorder: unreadable arm file, using the defaults", e);
            try { Files.deleteIfExists(armFile(server)); } catch (IOException ignored) {}
        }
        session = new Session(server, player, true, delay * 1_000_000_000L, seconds * 1_000_000_000L, 0, "arm_file");
        ArkSurvivalReturns.LOGGER.info("Session recorder armed for {}: waiting for the client, then {} s, then {} s of recording",
                player.getGameProfile().name(), delay, seconds);
    }

    /** The client reports that the loading screens are gone and the player can move. */
    static void clientReady(ServerPlayer player, RecorderPayloads.Ready info) {
        var current = session;
        if (current == null || !current.isSubject(player)) return;
        current.ready("client", new Row("client").put("render_distance", info.renderDistance())
                .put("simulation_distance", info.simulationDistance()).put("shaders", info.shaders())
                .flag("distant_horizons", info.distantHorizons()).put("fps", info.fps())
                .put("gui_scale", info.guiScale()).put("window", info.window()));
    }

    static void clientMark(ServerPlayer player) {
        var current = session;
        if (current != null && current.recording() && current.isSubject(player)) current.mark(player, "key");
    }

    public static boolean mark(Player player, String text) {
        var current = session;
        if (current == null || !current.recording()) return false;
        current.mark(player, text);
        return true;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var current = session;
        if (current != null && current.isSubject(event.getEntity())) current.stop("player_left");
    }

    @SubscribeEvent public static void before(ServerTickEvent.Pre event) {
        var current = session;
        if (current != null && current.server == event.getServer()) current.pre();
    }

    @SubscribeEvent public static void after(ServerTickEvent.Post event) {
        var current = session;
        if (current != null && current.server == event.getServer()) current.post();
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        var current = session;
        if (current != null) current.stop("server_stopping");
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        // Leaving the world must not cut the file short: wait for the footer and the archive.
        if (!awaitClosed(30_000)) ArkSurvivalReturns.LOGGER.warn("Session recorder: the file was still being written when the server stopped");
        session = null;
        recording = false;
    }

    // ------------------------------------------------------------------------------ events

    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        var current = live();
        if (current == null || event.getLevel().isClientSide()) return;
        try { current.join(event.getEntity(), event.loadedFromDisk()); } catch (Throwable t) { current.fail("join", t); }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        var current = live();
        if (current == null || event.getLevel().isClientSide()) return;
        try { current.leave(event.getEntity()); } catch (Throwable t) { current.fail("leave", t); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void target(LivingChangeTargetEvent event) {
        var current = live();
        if (current == null || event.isCanceled()) return;
        try { current.target(event.getEntity(), event.getNewAboutToBeSetTarget()); } catch (Throwable t) { current.fail("target", t); }
    }

    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        var current = live();
        if (current == null) return;
        try {
            current.damage(event.getEntity(), event.getSource(), event.getOriginalDamage(), event.getHealthDamage());
        } catch (Throwable t) { current.fail("damage", t); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        var current = live();
        if (current == null || event.isCanceled()) return;
        try { current.death(event.getEntity(), event.getSource()); } catch (Throwable t) { current.fail("death", t); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void attack(AttackEntityEvent event) {
        var current = live();
        if (current == null || event.isCanceled()) return;
        try {
            current.timed(new Row("ev").put("ev", "attack").put("e", current.sid(event.getEntity())).put("at", current.sid(event.getTarget())));
        } catch (Throwable t) { current.fail("attack", t); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void broken(BreakBlockEvent event) {
        var current = live();
        if (current == null || event.isCanceled()) return;
        try {
            var pos = event.getPos();
            current.timed(new Row("ev").put("ev", "break").put("e", current.sid(event.getPlayer())).block("pos", pos.getX(), pos.getY(), pos.getZ())
                    .put("block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString()));
        } catch (Throwable t) { current.fail("break", t); }
    }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        var current = live();
        if (current == null) return;
        try {
            current.timed(new Row("ev").put("ev", "respawn").put("e", current.sid(event.getEntity())));
        } catch (Throwable t) { current.fail("respawn", t); }
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        var current = live();
        if (current == null) return;
        try {
            current.timed(new Row("ev").put("ev", "dimension").put("e", current.sid(event.getEntity()))
                    .put("from", event.getFrom().identifier().toString()).put("to", event.getTo().identifier().toString()));
        } catch (Throwable t) { current.fail("dimension", t); }
    }

    // ------------------------------------------------------------- hooks called by game code

    /** A trace for one decision pass, or null while nothing records. */
    public static DecisionTrace decision(CreatureEntity mob) {
        var current = live();
        return current == null ? null : new DecisionTrace(current, mob);
    }

    /** One path request and how it ended: found, reused, deferred by the budget, unloaded, no ground, no path. */
    public static void navigation(CreatureEntity mob, DecisionTrace trace, String outcome, Vec3 point, int failedPaths) {
        var current = live();
        if (current == null) return;
        try { current.navigation(mob, trace, outcome, point, failedPaths); } catch (Throwable t) { current.fail("navigation", t); }
    }

    /** The shared path budget granted or deferred a request this tick. */
    public static void pathBudget(boolean granted) {
        var current = live();
        if (current != null) current.pathBudget(granted);
    }

    /** A synced state, action, tier or flight phase is about to change. */
    public static void changed(Entity creature, String what, Enum<?> from, Enum<?> to) {
        var current = live();
        if (current == null || from == to) return;
        try { current.creatureEvent(what, creature, from.name(), to.name()); } catch (Throwable t) { current.fail(what, t); }
    }

    /** A plain event about a creature, e.g. the ambient routine's next step. */
    public static void event(Entity creature, String what, String detail) {
        var current = live();
        if (current == null) return;
        try { current.creatureEvent(what, creature, null, detail); } catch (Throwable t) { current.fail(what, t); }
    }

    /** A melee attack started, was refused, landed or whiffed. */
    public static void strike(CreatureEntity creature, Entity target, String result, int windup, int cooldown) {
        var current = live();
        if (current == null) return;
        try { current.strike(creature, target, result, windup, cooldown); } catch (Throwable t) { current.fail("strike", t); }
    }

    /** A noise a player made that wildlife may hear. */
    public static void noise(Player source, Vec3 position, double radius) {
        var current = live();
        if (current == null) return;
        try {
            current.timed(new Row("ev").put("ev", "noise").put("e", current.sid(source))
                    .xyz("p", position.x, position.y, position.z).put("radius", radius));
        } catch (Throwable t) { current.fail("noise", t); }
    }

    /** The code about to add or remove an entity says why, so a budget cull is not mistaken for a despawn. */
    public static void note(Entity entity, String text) {
        var current = live();
        if (current != null) current.note(entity, text);
    }

    /** Session id of an entity for a record: -1 none, -2 not tracked. */
    public static int sid(Entity entity) {
        var current = session;
        return current == null ? -1 : current.sid(entity);
    }

    /** The mind's needs and timers; a controller that has not thought yet has no mind and writes nothing. */
    public static void mind(Row row, WildlifeMind mind) {
        if (mind == null) return;
        row.put("why", mind.reason()).put("hunger", (float) mind.hunger()).put("thirst", (float) mind.thirst())
                .put("fatigue", (float) mind.fatigue()).put("aware", (float) mind.awareness()).put("s_age", mind.age());
        if (mind.memory() > 0) row.put("mem", mind.memory());
        if (mind.warning() > 0) row.put("warn", mind.warning());
        if (mind.provoked() > 0) row.put("prov", mind.provoked());
        if (mind.chase() > 0) row.put("chase", mind.chase());
        if (mind.recovery() > 0) row.put("rec", mind.recovery());
        if (mind.feeding() > 0) row.put("feed", mind.feeding());
        if (mind.calmTicksRemaining() > 0) row.put("calm", mind.calmTicksRemaining());
        if (mind.flight() > 0) row.put("flight", mind.flight());
    }

    private SessionRecorder() {}
}
