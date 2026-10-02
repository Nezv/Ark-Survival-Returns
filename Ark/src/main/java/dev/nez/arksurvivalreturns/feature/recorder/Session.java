package dev.nez.arksurvivalreturns.feature.recorder;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryAttributes;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorAction;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorTier;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeMind;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * One recording: the clock, the tracked entities and everything written each tick. Lives on the server
 * thread; the only other thread involved is the {@link SessionWriter}.
 *
 * <p>The clock counts real time while the server is not paused. After the client reports that the
 * player controls the world, a countdown runs, then every loaded mob is recorded: a full status every
 * {@link #SNAPSHOT_TICKS} ticks, the player and everything near the player every tick, and decisions,
 * target changes, strikes, damage, joins and leaves on the tick they happen.
 */
final class Session {
    enum Phase { WAIT_READY, COUNTDOWN, RECORDING, CLOSED }

    static final int SCHEMA = 1;
    static final int SNAPSHOT_TICKS = 10;
    static final double NEAR_RADIUS = 64;
    /** Ticks a creature keeps its per-tick samples after it left the player's surroundings. */
    static final int NEAR_LINGER_TICKS = 100;
    static final int READY_TIMEOUT_TICKS = 2400;
    static final int QUEUE_ROWS = 1 << 16;
    static final int STALL_TICKS = 20;
    static final double STALL_BLOCKS = 0.25;
    static final int TERRAIN_PER_TICK = 3, TERRAIN_PER_SESSION = 600, TERRAIN_REPEAT_TICKS = 200;
    static final int PATH_NODES = 64;
    static final int MAX_ERRORS = 100;

    /** Mob flags of the status and motion records; the header repeats this legend. */
    static final int ON_GROUND = 1, IN_WATER = 2, H_COLLIDE = 4, V_COLLIDE = 8, MINOR_H_COLLIDE = 16, SPRINTING = 32,
            NO_AI = 64, PATHING = 128, NAV_STUCK = 256, PIVOT = 512, LEASHED = 1024, PASSENGER = 2048, VEHICLE = 4096,
            TICKING = 8192, PERSISTENT = 16384, AGGRESSIVE = 32768, SLEEP_POSE = 65536, DYING = 131072;
    /** Player flags of the {@code p} records. */
    static final int P_ON_GROUND = 1, P_SPRINT = 2, P_CROUCH = 4, P_SWIM = 8, P_FLYING = 16, P_IN_WATER = 32,
            P_CREATIVE = 64, P_SPECTATOR = 128, P_SLEEPING = 256, P_USING = 512, P_PASSENGER = 1024, P_DEAD = 2048,
            P_INVISIBLE = 4096;

    /** A mob or player seen during the session. The session id survives an unload and a later reload. */
    static final class Track {
        final int sid;
        final boolean player;
        Entity entity;
        boolean loaded = true;
        int targetSid = -1;
        Path path;
        int pathId;
        boolean pathEnded;
        int nearUntil = Integer.MIN_VALUE;
        String goals;
        float maxHealth = Float.NaN;
        float width, height;
        double anchorX, anchorY, anchorZ;
        int anchorTick = -1, collideTicks, lastTerrainTick = Integer.MIN_VALUE;
        long lastTerrainAt;
        boolean wasStuck;
        int lastDecision = Integer.MIN_VALUE;
        boolean focusOnPlayer;
        String note;
        Track(int sid, Entity entity) {
            this.sid = sid;
            this.entity = entity;
            this.player = entity instanceof Player;
            this.width = entity.getBbWidth();
            this.height = entity.getBbHeight();
        }
    }

    final MinecraftServer server;
    final Thread thread;
    final String id;
    final java.nio.file.Path directory;
    final SessionWriter writer;
    final UUID subjectId;
    private final boolean listed;
    private Player subject;
    final long delayNanos, durationNanos;
    final int tickLimit;
    final String armedBy;
    private String readyBy = "pending";
    /** Setup the arm file asked for before the countdown: the time of day the clock was moved to, a healed player. */
    int dayTimeSet = -1;
    boolean healed;
    /** Explicit arm-file intervention; ordinary recording never instantiates a scenario. */
    WaterTestScenario waterTest;
    private Row client;
    Phase phase = Phase.WAIT_READY;

    private long lastTickNanos, activeNanos, pausedSeen, phaseStartActive, recordStartNanos, recordStartActive, recordStartPaused;
    private long tickStartNanos;
    private int tick, waitTicks, recordedTicks;
    private long nextSeq, gapFrom = -1, gapCount;
    private int gapTick;
    private long captureTotal, captureMax;
    private int errors, terrainThisTick, terrainTotal, terrainSuppressed, pathsGranted, pathsDeferred, nextPathId;
    private int nextSid;
    private final IdentityHashMap<Entity, Track> tracks = new IdentityHashMap<>();
    private final HashMap<UUID, Track> known = new HashMap<>();
    private final ArrayList<Track> playerTracks = new ArrayList<>();
    private final ArrayList<Object[]> motion = new ArrayList<>();
    private final IdentityHashMap<Entity, String> notes = new IdentityHashMap<>();

    Session(MinecraftServer server, Player subject, boolean listed, long delayNanos, long durationNanos, int tickLimit, String armedBy) {
        this.server = server;
        this.thread = Thread.currentThread();
        this.subject = subject;
        this.subjectId = subject.getUUID();
        this.listed = listed;
        this.delayNanos = delayNanos;
        this.durationNanos = durationNanos;
        this.tickLimit = tickLimit;
        this.armedBy = armedBy;
        this.id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-"
                + String.format("%04x", ThreadLocalRandom.current().nextInt(0x10000));
        this.directory = server.getServerDirectory().resolve("diagnostics").resolve(id);
        this.writer = new SessionWriter(directory, QUEUE_ROWS, server::isPaused);
        this.writer.start();
    }

    int tick() { return tick; }
    boolean recording() { return phase == Phase.RECORDING; }
    long remainingSeconds() {
        return switch (phase) {
            case COUNTDOWN -> Math.max(0, (delayNanos - (activeNanos - phaseStartActive)) / 1_000_000_000L);
            case RECORDING -> Math.max(0, (durationNanos - (activeNanos - recordStartActive)) / 1_000_000_000L);
            default -> 0;
        };
    }

    // ------------------------------------------------------------------------------- clock

    /** Real time since the previous tick, minus what the writer thread saw the server spend paused. */
    private void clock() {
        long now = System.nanoTime(), paused = writer.pausedNanos();
        if (lastTickNanos != 0) {
            long gap = now - lastTickNanos;
            activeNanos += gap - Math.min(gap, Math.max(0, paused - pausedSeen));
        }
        pausedSeen = paused;
        lastTickNanos = now;
    }

    void pre() {
        clock();
        tick = server.getTickCount();
        if (writer.failure() != null) { stop("writer_failed"); return; }
        switch (phase) {
            case WAIT_READY -> { if (++waitTicks >= READY_TIMEOUT_TICKS) ready("timeout", null); }
            case COUNTDOWN -> { if (activeNanos - phaseStartActive >= delayNanos) begin(); }
            default -> {}
        }
        if (phase == Phase.RECORDING) {
            tickStartNanos = lastTickNanos;
            terrainThisTick = 0;
            pathsGranted = 0;
            pathsDeferred = 0;
            if (waterTest != null) {
                try { waterTest.tick(this); } catch (Throwable t) { fail("water_test", t); waterTest.close(this, "error"); waterTest = null; }
            }
        }
    }

    void post() {
        if (phase != Phase.RECORDING) return;
        long started = System.nanoTime();
        try {
            capture(started);
        } catch (Throwable t) {
            fail("capture", t);
        }
        long cost = System.nanoTime() - started;
        captureTotal += cost;
        captureMax = Math.max(captureMax, cost);
        recordedTicks++;
        notes.clear();
        if (phase != Phase.RECORDING) return;
        if (activeNanos - recordStartActive >= durationNanos) stop("deadline");
        else if (tickLimit > 0 && recordedTicks >= tickLimit) stop("tick_limit");
    }

    /** The client says the player can move, or the wait timed out: the countdown starts. */
    void ready(String by, Row clientInfo) {
        if (phase != Phase.WAIT_READY) return;
        readyBy = by;
        client = clientInfo;
        phase = Phase.COUNTDOWN;
        phaseStartActive = activeNanos;
        if (delayNanos > 0) tell("Session recorder armed: recording starts in " + delayNanos / 1_000_000_000L
                + " s and runs for " + durationNanos / 1_000_000_000L + " s of unpaused play.", ChatFormatting.YELLOW);
    }

    private void begin() {
        phase = Phase.RECORDING;
        // The tick that starts the recording is its time zero.
        recordStartNanos = lastTickNanos;
        recordStartActive = activeNanos;
        recordStartPaused = writer.pausedNanos();
        recordedTicks = 0;
        SessionRecorder.recording(true);
        try {
            emit(header());
            for (ServerLevel level : server.getAllLevels())
                for (Entity entity : level.getAllEntities())
                    if (trackable(entity)) register(entity, "present");
        } catch (Throwable t) {
            fail("header", t);
        }
        tell("Session recorder: recording " + durationNanos / 1_000_000_000L + " s. /arkrecord mark or the marker key flags a moment.",
                ChatFormatting.GREEN);
        SessionRecorder.status(subject(), true, true, id);
        ArkSurvivalReturns.LOGGER.info("Session recorder started: {}", directory);
    }

    void stop(String reason) {
        if (phase == Phase.CLOSED) return;
        boolean recorded = phase == Phase.RECORDING;
        if (recorded && waterTest != null) waterTest.close(this, "session_" + reason);
        phase = Phase.CLOSED;
        SessionRecorder.recording(false);
        SessionRecorder.closed(this);
        if (recorded) {
            long real = System.nanoTime() - recordStartNanos;
            emit(new Row("end").put("reason", reason).put("ticks", recordedTicks)
                    .put("real_ms", real / 1_000_000).put("active_ms", (activeNanos - recordStartActive) / 1_000_000)
                    .put("paused_ms", (writer.pausedNanos() - recordStartPaused) / 1_000_000)
                    .put("emitted", nextSeq + 1).put("dropped", writer.dropped())
                    .put("capture_us_avg", recordedTicks == 0 ? 0 : captureTotal / recordedTicks / 1000)
                    .put("capture_us_max", captureMax / 1000)
                    .put("entities", nextSid).put("errors", errors)
                    .put("terrain", terrainTotal).put("terrain_suppressed", terrainSuppressed));
            if (!reason.equals("deadline") && !reason.equals("tick_limit"))
                tell("Session recorder stopped early (" + reason + "); saving.", ChatFormatting.YELLOW);
        }
        writer.finish(reason, result -> report(result, recorded));
    }

    /** Writer thread: the file is closed and compressed, or it failed. */
    private void report(SessionWriter.Result result, boolean recorded) {
        if (!recorded || result.file() == null) {
            // Cancelled before anything was written: leave no empty folder behind.
            try { Files.deleteIfExists(directory); } catch (Exception ignored) {}
            return;
        }
        String where = server.getServerDirectory().toAbsolutePath().normalize().relativize(result.file().toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        boolean whole = result.complete() && errors == 0;
        String text = whole
                ? "Session saved: " + where + " (" + result.rows() + " records, none missing)."
                : "Session saved INCOMPLETE: " + where + " (" + result.rows() + " records, " + result.dropped()
                        + " dropped, " + errors + " capture errors" + (result.failure() == null ? "" : ", " + result.failure()) + ").";
        ArkSurvivalReturns.LOGGER.info("Session recorder: {}", text);
        if (server.isRunning()) server.execute(() -> {
            tell(text, whole ? ChatFormatting.GREEN : ChatFormatting.RED);
            SessionRecorder.status(subject(), false, whole, where);
        });
    }

    void tell(String text, ChatFormatting color) {
        if (subject() instanceof ServerPlayer player && !player.hasDisconnected())
            player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    /** A capture bug must never take the game down with it: count it, log the first few, carry on. */
    void fail(String where, Throwable t) {
        if (++errors <= 5) ArkSurvivalReturns.LOGGER.error("Session recorder failed in {}", where, t);
        if (phase == Phase.RECORDING) {
            emit(new Row("ev").put("ev", "recorder_error").put("where", where).put("error", String.valueOf(t)));
            if (errors >= MAX_ERRORS) stop("recorder_errors");
        }
    }

    // ------------------------------------------------------------------------------ output

    /** Hands a row to the writer. Every row takes a sequence number, so a dropped one leaves a visible hole. */
    void emit(Row row) {
        if (gapCount > 0) {
            var gap = new Row("gap").put("from", gapFrom).put("n", gapCount).put("k0", gapTick).put("why", "queue_full");
            gap.seq = nextSeq++;
            gap.tick = tick;
            if (writer.offer(gap)) { gapCount = 0; gapFrom = -1; } else gapCount++;
        }
        row.seq = nextSeq++;
        row.tick = tick;
        if (!writer.offer(row)) {
            if (gapCount++ == 0) { gapFrom = row.seq; gapTick = tick; }
        }
    }

    /** A row that happened at its own moment inside or between ticks. */
    void timed(Row row) {
        row.nanos = System.nanoTime() - recordStartNanos;
        emit(row);
    }

    // ---------------------------------------------------------------------------- tracking

    static boolean trackable(Entity entity) { return entity instanceof Mob || entity instanceof Player; }

    Track track(Entity entity) { return entity == null ? null : tracks.get(entity); }

    int sid(Entity entity) {
        if (entity == null) return -1;
        var track = tracks.get(entity);
        if (track == null) track = known.get(entity.getUUID());
        return track == null ? -2 : track.sid;
    }

    boolean isSubject(Entity entity) { return entity != null && entity.getUUID().equals(subjectId); }

    Player subject() {
        if (listed) {
            var player = server.getPlayerList().getPlayer(subjectId);
            if (player != null) subject = player;
        }
        return subject;
    }

    /** Tracked players in the creature's level within the radius. */
    List<Player> playersNear(Entity around, double radius) {
        var near = new ArrayList<Player>(1);
        for (var track : playerTracks)
            if (track.loaded && track.entity instanceof Player player && player.level() == around.level()
                    && player.distanceToSqr(around) <= radius * radius) near.add(player);
        return near;
    }

    /** First sight of an entity, or its return under a new object after an unload or a respawn. */
    Track register(Entity entity, String origin) {
        var track = tracks.get(entity);
        if (track != null) return track;
        track = known.get(entity.getUUID());
        String note = notes.remove(entity);
        if (track != null) {
            if (track.entity != null) tracks.remove(track.entity);
            track.entity = entity;
            track.loaded = true;
            track.path = null;
            track.anchorTick = -1;
            track.collideTicks = 0;
            track.nearUntil = Integer.MIN_VALUE;
            tracks.put(entity, track);
            if (track.player && !playerTracks.contains(track)) playerTracks.add(track);
            timed(new Row("ev").put("ev", "return").put("e", track.sid).put("origin", origin).put("note", note)
                    .put("dim", dimension(entity)).xyz("p", entity.getX(), entity.getY(), entity.getZ()));
            return track;
        }
        track = new Track(nextSid++, entity);
        tracks.put(entity, track);
        known.put(entity.getUUID(), track);
        if (track.player) playerTracks.add(track);
        var row = new Row("ent").put("e", track.sid).put("uuid", entity.getUUID().toString())
                .put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()).put("mc", entity.getId())
                .put("origin", origin).put("note", note).put("dim", dimension(entity))
                .put("w", entity.getBbWidth()).put("h", entity.getBbHeight()).put("eye", entity.getEyeHeight())
                .xyz("p", entity.getX(), entity.getY(), entity.getZ()).put("age", entity.tickCount);
        if (entity instanceof LivingEntity living) {
            track.maxHealth = living.getMaxHealth();
            row.put("hp", living.getHealth()).put("mhp", track.maxHealth);
            attribute(row, "speed", living, Attributes.MOVEMENT_SPEED);
            attribute(row, "follow", living, Attributes.FOLLOW_RANGE);
            attribute(row, "step", living, Attributes.STEP_HEIGHT);
            attribute(row, "dmg", living, Attributes.ATTACK_DAMAGE);
        }
        if (entity instanceof Mob mob) {
            row.put("cat", mob.getType().getCategory()).put("spawn", mob.getSpawnType())
                    .flag("persistent", mob.isPersistenceRequired());
            var target = mob.getTarget();
            if (target != null) { track.targetSid = sid(target); row.put("tg", track.targetSid); }
        }
        if (entity instanceof CreatureEntity creature) {
            var species = creature.species();
            row.put("ark", true).put("species", species.id).put("realm", species.realm()).put("lvl", creature.creatureLevel())
                    .put("pack", creature.packId().toString()).flag("predator", species.predator)
                    .flag("natural", creature.isNaturalWildlife()).flag("tamed", creature.isTamed())
                    .put("danger", creature.originDanger());
        }
        if (entity instanceof Player player) row.put("name", player.getGameProfile().name()).flag("subject", isSubject(player));
        timed(row);
        return track;
    }

    private static void attribute(Row row, String key, LivingEntity living, net.minecraft.core.Holder<Attribute> attribute) {
        // getAttributeValue reads the default; getAttribute would create the instance and change the save.
        if (living.getAttributes().hasAttribute(attribute)) row.put(key, (float) living.getAttributeValue(attribute));
    }

    private static String dimension(Entity entity) { return entity.level().dimension().identifier().toString(); }

    void join(Entity entity, boolean fromDisk) {
        if (!trackable(entity)) return;
        register(entity, fromDisk ? "load" : "spawn");
    }

    void leave(Entity entity) {
        var track = tracks.remove(entity);
        if (track == null) return;
        track.loaded = false;
        track.entity = null;
        track.path = null;
        if (track.player) playerTracks.remove(track);
        var reason = entity.getRemovalReason();
        timed(new Row("ev").put("ev", "leave").put("e", track.sid).put("why", reason == null ? "UNKNOWN" : reason.name())
                .put("note", track.note).xyz("p", entity.getX(), entity.getY(), entity.getZ()));
        track.note = null;
    }

    /** Why the entity is about to appear or disappear, said by the code doing it (the population budget). */
    void note(Entity entity, String text) {
        var track = tracks.get(entity);
        if (track != null) track.note = text;
        else notes.put(entity, text);
    }

    void target(LivingEntity mob, LivingEntity target) {
        var track = tracks.get(mob);
        if (track == null) return;
        int to = sid(target);
        if (to == track.targetSid) return;
        track.targetSid = to;
        var row = new Row("ev").put("ev", "target").put("e", track.sid).put("tg", to);
        if (target != null && isPlayerTrack(target)) track.nearUntil = tick + NEAR_LINGER_TICKS;
        timed(row);
    }

    private boolean isPlayerTrack(Entity entity) {
        var track = tracks.get(entity);
        return track != null && track.player;
    }

    void damage(LivingEntity victim, DamageSource source, float original, float dealt) {
        int v = sid(victim), a = sid(source.getEntity());
        if (v < 0 && a < 0) return;
        // A creature fighting a player keeps its per-tick samples wherever it is.
        var attacker = tracks.get(source.getEntity());
        var hurt = tracks.get(victim);
        if (attacker != null && hurt != null && (attacker.player || hurt.player))
            attacker.nearUntil = hurt.nearUntil = tick + NEAR_LINGER_TICKS;
        var row = new Row("ev").put("ev", "damage").put("e", v).put("by", a).put("src", sourceName(source))
                .put("orig", original).put("dmg", dealt).put("hp", victim.getHealth());
        var direct = source.getDirectEntity();
        if (direct != null && direct != source.getEntity())
            row.put("via", BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).toString());
        timed(row);
    }

    void death(LivingEntity victim, DamageSource source) {
        int v = sid(victim);
        if (v < 0) return;
        timed(new Row("ev").put("ev", "death").put("e", v).put("by", sid(source.getEntity())).put("src", sourceName(source))
                .xyz("p", victim.getX(), victim.getY(), victim.getZ()));
    }

    private static String sourceName(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.identifier().toString()).orElseGet(source::getMsgId);
    }

    /** A state, action, tier or flight phase changed, or a strike started or resolved. */
    void creatureEvent(String kind, Entity creature, String from, String to) {
        int e = sid(creature);
        if (e < 0) return;
        timed(new Row("ev").put("ev", kind).put("e", e).put("from", from).put("to", to));
    }

    void strike(CreatureEntity creature, Entity target, String result, int windup, int cooldown) {
        int e = sid(creature);
        if (e < 0) return;
        var row = new Row("ev").put("ev", "strike").put("e", e).put("at", sid(target)).put("r", result);
        if (windup > 0) row.put("windup", windup);
        if (cooldown > 0) row.put("cd", cooldown);
        if (target != null) row.put("gap", creature.distanceTo(target));
        timed(row);
    }

    void navigation(CreatureEntity mob, DecisionTrace trace, String outcome, Vec3 point, int failedPaths) {
        if (trace != null) trace.navigation(outcome, point, failedPaths);
        else {
            int e = sid(mob);
            if (e < 0) return;
            var row = new Row("ev").put("ev", "nav").put("e", e).put("o", outcome);
            if (point != null) row.xyz("dest", point.x, point.y, point.z);
            if (failedPaths > 0) row.put("fail", failedPaths);
            timed(row);
        }
        if (outcome.equals("no_path") && failedPaths >= 2) {
            var track = tracks.get(mob);
            if (track != null) stall(track, mob, "path_failed", Double.NaN, point);
        }
    }

    void pathBudget(boolean granted) {
        if (granted) pathsGranted++;
        else pathsDeferred++;
    }

    void mark(Player player, String text) {
        var row = new Row("ev").put("ev", "mark").put("e", sid(player)).put("note", text);
        if (player != null) row.xyz("p", player.getX(), player.getY(), player.getZ());
        timed(row);
        tell("Marked at " + (activeNanos - recordStartActive) / 100_000_000L / 10.0 + " s: " + text, ChatFormatting.GRAY);
    }

    // ----------------------------------------------------------------------------- capture

    private void capture(long started) {
        boolean snapshot = recordedTicks % SNAPSHOT_TICKS == 0;
        Player subject = subject();
        for (var track : playerTracks)
            if (track.loaded && track.entity instanceof Player player) playerRows(track, player, snapshot);
        motion.clear();
        int mobs = 0;
        for (var track : tracks.values()) {
            if (track.player || !(track.entity instanceof Mob mob) || mob.isRemoved()) continue;
            mobs++;
            boolean near = near(track, mob);
            if (near) {
                paths(track, mob);
                motion.add(sample(track, mob));
                watchProgress(track, mob);
            }
            if (snapshot) {
                if (!near) paths(track, mob);
                emit(status(track, mob));
            }
        }
        if (!motion.isEmpty()) emit(new Row("m").list("rows", new ArrayList<>(motion)));
        if (snapshot) for (ServerLevel level : server.getAllLevels()) world(level);
        long now = System.nanoTime();
        var row = new Row("tick").put("gt", server.overworld().getGameTime())
                .put("act_us", (activeNanos - recordStartActive) / 1000)
                .put("paused_us", (writer.pausedNanos() - recordStartPaused) / 1000)
                .put("dur_us", (started - tickStartNanos) / 1000).put("cap_us", (now - started) / 1000)
                .put("mspt", server.getCurrentSmoothedTickTime()).put("mobs", mobs).put("near", motion.size())
                .put("q", writer.depth()).put("drop", writer.dropped());
        if (pathsGranted > 0) row.put("paths", pathsGranted);
        if (pathsDeferred > 0) row.put("deferred", pathsDeferred);
        row.flag("frozen", server.tickRateManager().isFrozen()).flag("snap", snapshot);
        if (subject == null) row.flag("no_subject", true);
        row.nanos = tickStartNanos - recordStartNanos;
        emit(row);
    }

    /** Near any tracked player, or bound to one as hunter, target or recent opponent. */
    private boolean near(Track track, Mob mob) {
        for (var other : playerTracks) {
            if (!other.loaded || !(other.entity instanceof Player player) || player.level() != mob.level()) continue;
            if (mob.distanceToSqr(player) <= NEAR_RADIUS * NEAR_RADIUS || mob.getTarget() == player
                    || track.focusOnPlayer && tick - track.lastDecision <= 20) {
                track.nearUntil = tick + NEAR_LINGER_TICKS;
                break;
            }
        }
        return track.nearUntil >= tick;
    }

    private static int flags(Mob mob, boolean pathing) {
        int flags = 0;
        if (mob.onGround()) flags |= ON_GROUND;
        if (mob.isInWater()) flags |= IN_WATER;
        if (mob.horizontalCollision) flags |= H_COLLIDE;
        if (mob.verticalCollision) flags |= V_COLLIDE;
        if (mob.minorHorizontalCollision) flags |= MINOR_H_COLLIDE;
        if (mob.isSprinting()) flags |= SPRINTING;
        if (mob.isNoAi()) flags |= NO_AI;
        if (pathing) flags |= PATHING;
        if (mob.getNavigation().isStuck()) flags |= NAV_STUCK;
        if (mob instanceof CreatureEntity creature && creature.isPivoting()) flags |= PIVOT;
        if (mob.isPassenger()) flags |= PASSENGER;
        if (mob.isVehicle()) flags |= VEHICLE;
        return flags;
    }

    /** One line of the per-tick motion table; the header names the columns. */
    private Object[] sample(Track track, Mob mob) {
        var path = mob.getNavigation().getPath();
        boolean pathing = path != null && !path.isDone();
        var v = mob.getDeltaMovement();
        return new Object[]{track.sid, mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.yBodyRot, mob.yHeadRot,
                (float) v.x, (float) v.y, (float) v.z, flags(mob, pathing), mob.zza, mob.getSpeed(),
                path == null ? -1 : path.getNextNodeIndex()};
    }

    static final List<String> MOTION_COLUMNS = List.of("e", "x", "y", "z", "yaw", "body", "head", "vx", "vy", "vz",
            "flags", "zza", "speed", "node");

    private Row status(Track track, Mob mob) {
        var navigation = mob.getNavigation();
        var path = navigation.getPath();
        boolean pathing = path != null && !path.isDone();
        int flags = flags(mob, pathing);
        if (mob.isLeashed()) flags |= LEASHED;
        if (mob.isPersistenceRequired()) flags |= PERSISTENT;
        if (mob.isAggressive()) flags |= AGGRESSIVE;
        if (mob.isSleeping()) flags |= SLEEP_POSE;
        if (mob.isDeadOrDying()) flags |= DYING;
        if (mob.level() instanceof ServerLevel level && level.isPositionEntityTicking(mob.blockPosition())) flags |= TICKING;
        var v = mob.getDeltaMovement();
        var row = new Row("s").put("e", track.sid).xyz("p", mob.getX(), mob.getY(), mob.getZ())
                .put("yr", mob.getYRot()).put("br", mob.yBodyRot).put("hr", mob.yHeadRot).put("xr", mob.getXRot())
                .xyz("v", (float) v.x, (float) v.y, (float) v.z).put("hp", mob.getHealth()).put("age", mob.tickCount)
                .put("f", flags).put("zza", mob.zza).put("spd", mob.getSpeed());
        float maxHealth = mob.getMaxHealth();
        if (maxHealth != track.maxHealth) { track.maxHealth = maxHealth; row.put("mhp", maxHealth); }
        // The entity record holds the collision box it had when first seen; a later size is written when it changes.
        if (mob.getBbWidth() != track.width || mob.getBbHeight() != track.height) {
            track.width = mob.getBbWidth();
            track.height = mob.getBbHeight();
            row.put("w", track.width).put("h", track.height);
        }
        var target = mob.getTarget();
        if (target != null) row.put("tg", sid(target));
        if (path != null) row.put("pid", track.pathId).put("pi", path.getNextNodeIndex()).put("pn", path.getNodeCount());
        if (track.nearUntil >= tick) row.flag("near", true);
        if (track.lastDecision != Integer.MIN_VALUE) row.put("dk", track.lastDecision);
        String goals = goals(mob);
        if (!goals.equals(track.goals)) { track.goals = goals; row.put("goals", goals); }
        if (mob instanceof CreatureEntity creature) creature.record(row);
        return row;
    }

    /** The goals running right now, by class name; written when the set changes. */
    private static String goals(Mob mob) {
        StringBuilder names = null;
        for (var goal : mob.goalSelector.getAvailableGoals()) {
            if (!goal.isRunning()) continue;
            if (names == null) names = new StringBuilder();
            else names.append(',');
            names.append(goal.getGoal().getClass().getSimpleName());
        }
        for (var goal : mob.targetSelector.getAvailableGoals()) {
            if (!goal.isRunning()) continue;
            if (names == null) names = new StringBuilder();
            else names.append(',');
            names.append(goal.getGoal().getClass().getSimpleName());
        }
        return names == null ? "" : names.toString();
    }

    /** Path nodes are copied once, when the navigation holds a different path object than last time. */
    private void paths(Track track, Mob mob) {
        var path = mob.getNavigation().getPath();
        if (path == track.path) {
            // The navigation keeps a finished path; the moment it ran out of nodes is worth its own record.
            if (path != null && !track.pathEnded && path.isDone()) {
                track.pathEnded = true;
                emit(new Row("path").put("e", track.sid).put("id", track.pathId).put("pi", path.getNextNodeIndex())
                        .put("pn", path.getNodeCount()).flag("ended", true)
                        .xyz("p", mob.getX(), mob.getY(), mob.getZ()));
            }
            return;
        }
        var before = track.path;
        track.path = path;
        track.pathEnded = false;
        if (path == null) {
            emit(new Row("path").put("e", track.sid).put("id", 0).put("prev", track.pathId)
                    .put("pi", before.getNextNodeIndex()).put("pn", before.getNodeCount())
                    .flag("done", before.isDone()).flag("stuck", mob.getNavigation().isStuck()));
            return;
        }
        track.pathId = ++nextPathId;
        int count = path.getNodeCount(), copied = Math.min(count, PATH_NODES);
        int[] nodes = new int[copied * 3];
        for (int i = 0; i < copied; i++) {
            var node = path.getNode(i);
            nodes[i * 3] = node.x;
            nodes[i * 3 + 1] = node.y;
            nodes[i * 3 + 2] = node.z;
        }
        var goal = path.getTarget();
        emit(new Row("path").put("e", track.sid).put("id", track.pathId).put("pn", count).put("pi", path.getNextNodeIndex())
                .flag("reach", path.canReach()).put("left", path.getDistToTarget())
                .block("goal", goal.getX(), goal.getY(), goal.getZ()).ints("nodes", nodes).flag("trunc", copied < count));
    }

    /** A body that wants to follow a path and does not get anywhere is a stall; so is a long push against blocks. */
    private void watchProgress(Track track, Mob mob) {
        var navigation = mob.getNavigation();
        boolean stuck = navigation.isStuck();
        if (stuck && !track.wasStuck) stall(track, mob, "nav_stuck", Double.NaN, null);
        track.wasStuck = stuck;
        if (!navigation.isInProgress()) {
            track.anchorTick = -1;
            track.collideTicks = 0;
            return;
        }
        if (track.anchorTick < 0) {
            track.anchorTick = tick;
            track.anchorX = mob.getX(); track.anchorY = mob.getY(); track.anchorZ = mob.getZ();
        } else if (tick - track.anchorTick >= STALL_TICKS) {
            double dx = mob.getX() - track.anchorX, dy = mob.getY() - track.anchorY, dz = mob.getZ() - track.anchorZ;
            double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (moved < STALL_BLOCKS) stall(track, mob, "no_progress", moved, null);
            track.anchorTick = tick;
            track.anchorX = mob.getX(); track.anchorY = mob.getY(); track.anchorZ = mob.getZ();
        }
        if (!mob.horizontalCollision) track.collideTicks = 0;
        else if (++track.collideTicks % 100 == 10) stall(track, mob, "collision", Double.NaN, null);
    }

    private void stall(Track track, Mob mob, String why, double moved, Vec3 toward) {
        var path = mob.getNavigation().getPath();
        var row = new Row("ev").put("ev", "stall").put("e", track.sid).put("why", why)
                .xyz("p", mob.getX(), mob.getY(), mob.getZ()).put("yr", mob.getYRot());
        if (!Double.isNaN(moved)) row.put("moved", moved).put("over", STALL_TICKS);
        row.flag("hcol", mob.horizontalCollision).flag("ground", mob.onGround());
        if (mob instanceof CreatureEntity creature) row.flag("pivot", creature.isPivoting()).put("act", creature.action());
        if (path != null && !path.isDone()) {
            var node = path.getNextNode();
            row.put("pid", track.pathId).put("pi", path.getNextNodeIndex()).put("pn", path.getNodeCount())
                    .block("node", node.x, node.y, node.z);
        }
        if (toward != null) row.xyz("dest", toward.x, toward.y, toward.z);
        // A turn in place is slow on purpose; the blocks only matter when the body is not pivoting or is pushing.
        boolean pivoting = mob instanceof CreatureEntity creature && creature.isPivoting() && !mob.horizontalCollision;
        long at = mob.blockPosition().asLong();
        boolean repeat = at == track.lastTerrainAt && tick - track.lastTerrainTick < TERRAIN_REPEAT_TICKS;
        if (pivoting || repeat) {
            timed(row.flag("same_place", repeat));
            return;
        }
        if (terrainThisTick >= TERRAIN_PER_TICK || terrainTotal >= TERRAIN_PER_SESSION || !(mob.level() instanceof ServerLevel level)) {
            terrainSuppressed++;
            timed(row.flag("no_terrain", true));
            return;
        }
        terrainThisTick++;
        terrainTotal++;
        track.lastTerrainAt = at;
        track.lastTerrainTick = tick;
        timed(row.flag("terrain", true));
        emit(TerrainProbe.capture(level, mob.getBoundingBox()).put("e", track.sid).put("why", why));
    }

    private void playerRows(Track track, Player player, boolean snapshot) {
        int flags = 0;
        if (player.onGround()) flags |= P_ON_GROUND;
        if (player.isSprinting()) flags |= P_SPRINT;
        if (player.isShiftKeyDown()) flags |= P_CROUCH;
        if (player.isSwimming()) flags |= P_SWIM;
        if (player.getAbilities().flying) flags |= P_FLYING;
        if (player.isInWater()) flags |= P_IN_WATER;
        if (player.isCreative()) flags |= P_CREATIVE;
        if (player.isSpectator()) flags |= P_SPECTATOR;
        if (player.isSleeping()) flags |= P_SLEEPING;
        if (player.isUsingItem()) flags |= P_USING;
        if (player.isPassenger()) flags |= P_PASSENGER;
        if (!player.isAlive()) flags |= P_DEAD;
        if (player.isInvisible()) flags |= P_INVISIBLE;
        emit(new Row("p").put("e", track.sid).xyz("p", player.getX(), player.getY(), player.getZ())
                .put("yr", player.getYRot()).put("xr", player.getXRot()).put("f", flags).put("hp", player.getHealth()));
        if (!snapshot) return;
        var row = new Row("ps").put("e", track.sid).put("dim", dimension(player)).put("eye", player.getEyeHeight())
                .put("w", player.getBbWidth()).put("h", player.getBbHeight())
                .put("mode", player instanceof ServerPlayer online ? online.gameMode().getName()
                        : player.isCreative() ? "creative" : player.isSpectator() ? "spectator" : "survival")
                .flag("valid_target", WildlifeSenses.validTarget(player))
                .put("food", player.getFoodData().getFoodLevel()).put("armor", player.getArmorValue())
                .put("held", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString())
                .put("visibility", sense(player, AccessoryAttributes.VISIBILITY))
                .put("noise", sense(player, AccessoryAttributes.NOISE)).put("scent", sense(player, AccessoryAttributes.SCENT));
        if (player.getVehicle() != null) row.put("vehicle", sid(player.getVehicle()));
        if (player instanceof ServerPlayer online) row.put("view", online.requestedViewDistance());
        emit(row);
    }

    /** A sense multiplier of the player's gear, read without creating the attribute instance the game has not made yet. */
    private static float sense(Player player, net.minecraft.core.Holder<Attribute> attribute) {
        return player.getAttributes().hasAttribute(attribute) ? (float) player.getAttributeValue(attribute) : 1f;
    }

    private void world(ServerLevel level) {
        if (level.players().isEmpty() && level != server.overworld()) return;
        emit(new Row("w").put("dim", level.dimension().identifier().toString()).put("gt", level.getGameTime())
                .put("day", level.getDefaultClockTime()).flag("rain", level.isRaining()).flag("thunder", level.isThundering())
                .flag("bright", level.isBrightOutside()).put("wind", WildlifeSenses.windAngle(level))
                .put("diff", level.getDifficulty()).put("chunks", level.getChunkSource().getLoadedChunksCount()));
    }

    // ------------------------------------------------------------------------------ header

    private Row header() {
        var overworld = server.overworld();
        var row = new Row("header").put("schema", SCHEMA).put("session", id).put("started", OffsetDateTime.now().toString())
                .put("armed_by", armedBy).put("ready_by", readyBy)
                .put("delay_s", delayNanos / 1.0e9).put("record_s", durationNanos / 1.0e9)
                .put("tick_limit", tickLimit).put("snapshot_ticks", SNAPSHOT_TICKS).put("near_radius", NEAR_RADIUS)
                .put("near_linger_ticks", NEAR_LINGER_TICKS).put("stall_ticks", STALL_TICKS).put("stall_blocks", STALL_BLOCKS)
                .put("subject", subjectId.toString()).put("tick0", tick)
                .put("waited_ms", activeNanos / 1_000_000).row("build", build()).row("server", new Row("server")
                        .flag("dedicated", server.isDedicatedServer()).flag("singleplayer", server.isSingleplayer())
                        .put("view_distance", server.getPlayerList().getViewDistance())
                        .put("simulation_distance", server.getPlayerList().getSimulationDistance())
                        .put("tick_rate", server.tickRateManager().tickrate()).put("difficulty", overworld.getDifficulty())
                        .flag("hardcore", server.isHardcore()).put("game_mode", server.getDefaultGameType().getName())
                        .put("seed", Long.toString(overworld.getSeed())).put("players", server.getPlayerList().getPlayerCount()));
        if (client != null) row.row("client", client);
        if (dayTimeSet >= 0) row.put("day_time_set", dayTimeSet);
        if (healed) row.flag("healed", true);
        if (waterTest != null) row.row("scenario", WaterTestScenario.settings());
        var rules = new Row("rules");
        overworld.getGameRules().availableRules().sorted(java.util.Comparator.comparing(rule -> rule.id()))
                .forEach(rule -> rules.put(rule.id(), String.valueOf(overworld.getGameRules().get(rule))));
        row.row("gamerules", rules);
        var config = new Row("config");
        config(config, "", Config.SPEC.getValues().valueMap());
        row.row("config", config);
        var species = new ArrayList<Row>();
        for (var s : Species.values())
            species.add(new Row("species").put("id", s.id).put("realm", s.realm()).flag("predator", s.predator)
                    .flag("timid", s.timid()).flag("herd", s.herd()).flag("solitary", s.solitary()).flag("apex", s.apex())
                    .put("w", s.width).put("h", s.height).put("danger", s.minimumDanger())
                    .put("family", s.flyer() ? null : s.family().name()).put("health", s.health).put("damage", s.damage));
        row.list("species", species);
        var mods = new ArrayList<String>();
        for (var mod : ModList.get().getMods()) mods.add(mod.getModId() + "@" + mod.getVersion());
        row.list("mods", mods);
        row.row("legend", new Row("legend").list("motion", MOTION_COLUMNS)
                .list("states", names(BehaviorState.values())).list("actions", names(BehaviorAction.values()))
                .list("tiers", names(BehaviorTier.values())).list("reasons", names(WildlifeMind.Reason.values()))
                .list("mob_flags", List.of("on_ground", "in_water", "h_collide", "v_collide", "minor_h_collide", "sprinting",
                        "no_ai", "pathing", "nav_stuck", "pivot", "leashed", "passenger", "vehicle", "ticking", "persistent",
                        "aggressive", "sleep_pose", "dying"))
                .list("player_flags", List.of("on_ground", "sprint", "crouch", "swim", "flying", "in_water", "creative",
                        "spectator", "sleeping", "using_item", "passenger", "dead", "invisible"))
                .list("terrain_kinds", List.of("none", "full", "partial", "fluid")));
        return row;
    }

    private static List<String> names(Enum<?>[] values) {
        var names = new ArrayList<String>(values.length);
        for (var value : values) names.add(value.name());
        return names;
    }

    private static void config(Row out, String prefix, Map<String, Object> values) {
        for (var entry : new java.util.TreeMap<>(values).entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested) config(out, key, nested.valueMap());
            else if (value instanceof ModConfigSpec.ConfigValue<?> configured) {
                switch (configured.get()) {
                    case Boolean flag -> out.put(key, flag.booleanValue());
                    case Integer number -> out.put(key, number.intValue());
                    case Long number -> out.put(key, number.longValue());
                    case Double number -> out.put(key, number.doubleValue());
                    case null -> {}
                    case Object other -> out.put(key, other.toString());
                }
            }
        }
    }

    /** Which code produced the recording: the commit the launcher saw, and a checksum of the classes that decide behaviour. */
    private Row build() {
        var row = new Row("build");
        ModList.get().getModContainerById(ArkSurvivalReturns.MOD_ID)
                .ifPresent(mod -> row.put("mod", mod.getModInfo().getVersion().toString()));
        row.put("commit", System.getProperty("arksurvivalreturns.build.commit", "unknown"))
                .put("dirty", System.getProperty("arksurvivalreturns.build.dirty", "unknown"));
        var crc = new CRC32();
        try {
            for (String name : List.of("feature/behavior/WildlifeGoal", "feature/behavior/WildlifeMind", "feature/behavior/WildlifeSenses",
                    "feature/behavior/Choreographer", "feature/creature/CreatureEntity", "feature/creature/CreatureMoveControl",
                    "feature/land/LandWildlife", "feature/spawn/NaturalPopulations", "feature/recorder/Session")) {
                try (var in = Session.class.getResourceAsStream("/dev/nez/arksurvivalreturns/" + name + ".class")) {
                    if (in == null) throw new java.io.FileNotFoundException(name);
                    crc.update(in.readAllBytes());
                }
            }
            row.put("classes_crc", Long.toHexString(crc.getValue()));
        } catch (Exception e) {
            row.put("classes_crc", "unavailable");
        }
        return row;
    }
}
