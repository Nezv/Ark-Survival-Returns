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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One playthrough in progress: the player it drives, the steps still to come and the events file they write to.
 * Every step writes what it saw as one JSON line in {@code events.jsonl}; nothing is judged during the run, the
 * report reads the file afterwards.
 */
public final class PlayRun {
    final MinecraftServer server;
    final UUID playerId;
    final BlockPos origin;
    final Path folder;
    public Path folder() { return folder; }
    private final List<Step> steps;
    private final BufferedWriter out;
    private int index, ticks;
    private String stage = "-";
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
        write(start);
    }

    public ServerPlayer player() { return server.getPlayerList().getPlayer(playerId); }
    public ServerLevel level() { return server.overworld(); }
    public BlockPos at(int dx, int dy, int dz) { return origin.offset(dx, dy, dz); }
    public String stage() { return stage; }
    void stage(String name) { stage = name; }
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

    void tick() {
        if (finished) return;
        if (player() == null) { stop("player left"); return; }
        if (index >= steps.size()) { stop("done"); return; }
        var step = steps.get(index);
        boolean done;
        try { done = step.tick(this, ticks++); }
        catch (RuntimeException e) {
            ArkSurvivalReturns.LOGGER.error("Playthrough: step {} failed", step.name(), e);
            var failed = event("step_error");
            failed.addProperty("error", String.valueOf(e));
            write(failed);
            done = true;
        }
        if (!done && ticks > step.timeout()) {
            write(event("step_timeout"));
            done = true;
        }
        if (done) { index++; ticks = 0; }
    }

    void stop(String why) {
        if (finished) return;
        finished = true;
        var end = event("run_end");
        end.addProperty("why", why);
        write(end);
        try { out.close(); } catch (IOException ignored) {}
        ArkSurvivalReturns.LOGGER.info("Playthrough: {} ({})", why, folder);
    }
}
