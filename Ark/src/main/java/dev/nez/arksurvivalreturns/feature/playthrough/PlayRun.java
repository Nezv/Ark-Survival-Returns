package dev.nez.arksurvivalreturns.feature.playthrough;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.recovery.DownedHandler;
import dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * One playthrough in progress: the player it drives, the steps still to come and the events file they write to.
 * Every step writes what it saw as one JSON line in {@code events.jsonl}; nothing is judged during the run, the
 * report reads the file afterwards.
 *
 * <p>Between steps the run keeps the player alive the way a player would: it eats earned food from the bags when
 * hungry ({@link Verbs#eat}), and a player who went down or died anyway is written down (red in the report) and
 * revived or respawned so the run goes on.
 */
public final class PlayRun {
    final MinecraftServer server;
    final UUID playerId;
    final BlockPos origin;
    final Path folder;
    public Path folder() { return folder; }
    /** The camp: every station the player has put down (block id to where it stands), in the order placed. */
    final java.util.Map<String, BlockPos> camp = new java.util.LinkedHashMap<>();
    /** Storage crates the player has put down for what the bags cannot hold. */
    final List<BlockPos> crates = new java.util.ArrayList<>();
    /** No crate can be earned: the bags are not emptied again (one blocked event, not one every tick). */
    boolean noCrate;
    /** XP the run lifted the player by to meet a level gate it had not earned (written as level_lift events). */
    long suppliedXp;
    /**
     * What the run's hunts have measured, so a validated grind is never repeated: by "species|item" the kills made and
     * the items those carcasses gave, and by species the kills and the XP they earned. Once a pair has enough kills
     * behind it, a later need is filled from the measured rate, with the matching XP, and written down as supplied
     * ({@link Gathering.Killing}).
     */
    final java.util.Map<String, int[]> huntDrops = new java.util.HashMap<>();
    final java.util.Map<String, long[]> hunted = new java.util.HashMap<>();
    /** A fight is under way: upkeep waits, so the hand and the stand are the fight's. */
    boolean busy;
    /**
     * Between two actions (a goal choosing what to do next, a sweep between its steps): only here does the run stop to
     * eat or to earn food, so a harvest, a hunt or a station being worked is never cut into.
     */
    boolean safe = true;
    /** The tamed companion kept for the tame's-life steps (work, a kill, the trough), or null. */
    dev.nez.arksurvivalreturns.feature.creature.CreatureEntity companion;
    private final List<Step> steps;
    private final BufferedWriter out;
    private int index, ticks;
    private Step upkeep;
    private int upkeepTicks, life;
    /** Meals in a row that did not go down: after three the run stops trying for the stage (written as hungry). */
    int eatFails;
    private String stage = "-", hungerStage = "";
    private volatile boolean finished;

    PlayRun(MinecraftServer server, ServerPlayer player, BlockPos origin, Path folder, String routine, List<Step> steps) throws IOException {
        this.server = server;
        this.playerId = player.getUUID();
        this.origin = origin;
        this.folder = folder;
        this.steps = steps;
        Files.createDirectories(folder);
        out = Files.newBufferedWriter(folder.resolve("events.jsonl"), StandardCharsets.UTF_8);
        var start = event("run_start");
        start.addProperty("routine", routine);
        start.addProperty("steps", steps.size());
        start.addProperty("origin", origin.toShortString());
        start.addProperty("level", ArkLevels.get(player).level());
        write(start);
    }

    public ServerPlayer player() { return server.getPlayerList().getPlayer(playerId); }
    public ServerLevel level() { return server.overworld(); }
    public BlockPos at(int dx, int dy, int dz) { return origin.offset(dx, dy, dz); }
    public String stage() { return stage; }
    void stage(String name) { stage = name; eatFails = 0; }
    public boolean finished() { return finished; }
    public String progress() { return finished ? "finished" : (index + 1) + "/" + steps.size() + " " + (index < steps.size() ? steps.get(index).name() : ""); }

    /** A new event carrying the game time, the stage and the step it belongs to. */
    public JsonObject event(String type) {
        var event = new JsonObject();
        event.addProperty("t", server.overworld().getGameTime());
        event.addProperty("wall", System.currentTimeMillis());
        event.addProperty("stage", stage);
        event.addProperty("step", index < steps.size() ? steps.get(index).name() : "");
        event.addProperty("type", type);
        return event;
    }

    public void write(JsonObject event) {
        try { out.write(event.toString()); out.newLine(); out.flush(); }
        catch (IOException e) { ArkSurvivalReturns.LOGGER.error("Playthrough: could not write an event", e); }
    }

    /** The XP the player earned by playing: all of it but what the run lifted them by. */
    long earnedXp(ServerPlayer player) { return ArkLevels.totalXp(ArkLevels.get(player)) - suppliedXp; }

    void tick() {
        if (finished) return;
        var player = player();
        if (player == null) { stop("player left"); return; }
        if (index >= steps.size()) { stop("done"); return; }
        if (!busy && recover(player)) return;
        if (upkeep != null) {
            if (Step.drive(this, upkeep, upkeepTicks++)) upkeep = null;
            return;
        }
        if (!busy && safe && life++ % 20 == 19 && player.getFoodData().getFoodLevel() <= 12 && eatFails < 3) {
            upkeepTicks = 0;
            if (Verbs.hasFood(player)) upkeep = Verbs.eat();
            else if (!stage.equals(hungerStage)) {
                // No meal in the bags: the player goes and earns one, once a stage (a second failure is just written down).
                hungerStage = stage;
                var event = event("hungry");
                String meal = Gathering.provision(this);
                event.addProperty("earning", meal);
                write(event);
                upkeep = Gathering.obtain(meal, 4, false);
            }
            if (upkeep != null) return;
        }
        var step = steps.get(index);
        safe = false;
        if (Step.drive(this, step, ticks++)) { index++; ticks = 0; safe = true; }
    }

    /** A player down or dead is written down and put back on their feet; true while that takes this tick. */
    private boolean recover(ServerPlayer player) {
        boolean downed = player.hasData(RecoveryAttachments.DOWNED) && player.getData(RecoveryAttachments.DOWNED).downed();
        if (!downed && !player.isDeadOrDying()) return false;
        var event = event(downed ? "player_down" : "player_dead");
        var source = player.getLastDamageSource();
        event.addProperty("cause", source == null ? "" : source.getMsgId());
        event.addProperty("food", player.getFoodData().getFoodLevel());
        write(event);
        if (downed) DownedHandler.revive(player, player);
        else server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);   // alive again; keepInventory keeps the bags
        return true;
    }

    void stop(String why) {
        if (finished) return;
        finished = true;
        busy = false;
        Playthrough.watch(null);
        Playthrough.useHeld(false);
        var end = event("run_end");
        end.addProperty("why", why);
        var player = player();
        if (player != null) {
            end.addProperty("level", ArkLevels.get(player).level());
            end.addProperty("earned_xp", earnedXp(player));
            end.addProperty("supplied_xp", suppliedXp);
        }
        write(end);
        try { out.close(); } catch (IOException ignored) {}
        ArkSurvivalReturns.LOGGER.info("Playthrough: {} ({})", why, folder);
    }
}
