package dev.nez.arksurvivalreturns.feature.recorder;

import java.util.ArrayList;
import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeMind;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What one decision pass of a wild creature saw and chose, written as a single {@code d} record.
 *
 * <p>The controller reports the results it already computed; nothing here senses, paths, rolls a random
 * number or steps the mind again. A player gets its own entry saying how far it got: shut out by game
 * mode or Peaceful, filtered by the day routine, cut by the 24-candidate cap, scored but not detected,
 * detected but outscored by another candidate, or sensed.
 */
public final class DecisionTrace {
    /** How far one player got through the candidate funnel on this pass. */
    private static final class PlayerNote {
        final Player player;
        final double body;
        String stage;
        WildlifeSenses.Detail detail;
        double score = Double.NaN;
        PlayerNote(Player player, double body, String stage) { this.player = player; this.body = body; this.stage = stage; }
    }

    private final Session session;
    private final CreatureEntity mob;
    private final Row row = new Row("d");
    private final ArrayList<PlayerNote> players = new ArrayList<>(2);
    private ArrayList<Row> navigation;
    private int navigationExtra, admitted, scored;
    private LivingEntity sensed;
    private double bestScore;
    private String branch, forced;

    DecisionTrace(Session session, CreatureEntity mob) {
        this.session = session;
        this.mob = mob;
    }

    private PlayerNote note(LivingEntity candidate, String stage) {
        if (!(candidate instanceof Player player)) return null;
        for (var note : players) if (note.player == player) { note.stage = stage; return note; }
        var note = new PlayerNote(player, WildlifeSenses.bodyDistance(mob, player), stage);
        players.add(note);
        return note;
    }

    /** A player the scan box held but the entity filter refused: creative, spectator, dead or Peaceful. */
    public void excluded(LivingEntity candidate, String why) { note(candidate, why); }

    /** The day-and-night filter: irrelevant to this species, or outside what its routine reacts to. */
    public void gate(LivingEntity candidate, boolean relevant, boolean allowed) {
        if (allowed) admitted++;
        note(candidate, allowed ? "admitted" : relevant ? "day_routine" : "irrelevant");
    }

    /** The nearest candidates kept for sensing; an admitted player missing from it was cut by the cap. */
    public void candidates(double scan, List<LivingEntity> kept) {
        row.put("scan", scan).put("cand", kept.size());
        if (admitted > kept.size()) row.put("capped", admitted - kept.size());
        for (var note : players) if (note.stage.equals("admitted") && !kept.contains(note.player)) note.stage = "capped";
        for (var candidate : kept) note(candidate, "candidate");
    }

    /** One sense check and its score, exactly as the controller computed them. */
    public void scored(LivingEntity candidate, WildlifeSenses.Detection check, double score) {
        scored++;
        if (score > bestScore) bestScore = score;
        var note = note(candidate, check.strength() > 0 ? "detected" : "undetected");
        if (note != null) {
            note.detail = WildlifeSenses.LAST.copy();
            note.score = score;
        }
    }

    /** The attacker or the herd's threat replaced the scored choice. */
    public void forced(String why) { forced = why; }

    public void sensed(LivingEntity chosen, WildlifeSenses.Detection detection, boolean alarm) {
        sensed = chosen;
        row.put("checked", scored);
        if (chosen != null) {
            row.put("sensed", session.sid(chosen)).put("s_gap", (float) WildlifeSenses.bodyDistance(mob, chosen))
                    .flag("s_vis", detection.visible()).put("s_str", detection.strength())
                    .put("s_by", detection.visible() ? "sight" : detection.strength() >= 0.65 ? "hearing"
                            : detection.strength() > 0 ? "scent" : "none");
            if (bestScore > 0) row.put("score", bestScore);
            if (forced != null) row.put("forced", forced);
        }
        row.flag("alarm", alarm);
        // A detected player that was not chosen lost to a better score, the attacker or the herd's threat.
        for (var note : players) {
            if (note.player == chosen) note.stage = "sensed";
            else if (note.stage.equals("detected")) note.stage = "displaced";
        }
    }

    public void noise(Vec3 position) { row.xyz("noise", position.x, position.y, position.z); }

    /** The inputs the mind was stepped with, and the mind's own counters after the step. */
    public void step(BehaviorState before, BehaviorState after, WildlifeMind mind, WildlifeMind.Observation o,
            WildlifeMind.Routine routine, boolean guardedPrey, boolean interrupted) {
        row.put("st0", before);
        row.put("sig", o.signal()).flag("vis", o.visible()).flag("prey", o.prey()).flag("intr", o.intruding())
                .flag("atk", o.attacked()).flag("intim", o.intimidating()).flag("far", o.farFromHome())
                .flag("water", o.water()).flag("forage", o.forage()).flag("dark", o.night()).put("hpr", o.health());
        row.flag("cycle", routine.enabled()).flag("night_r", routine.night()).flag("sleepy", routine.sleepWanted())
                .flag("unsafe", !routine.safeToSleep()).flag("danger", routine.danger()).flag("defended", routine.defended())
                .flag("cornered", routine.cornered()).flag("regroup_r", routine.regroup());
        row.flag("guarded", guardedPrey).flag("woken", interrupted).flag("changed", before != after);
    }

    public void entered(boolean urgent) { row.flag("urgent", urgent); }

    /** Which movement the pass ended in: strike, hold, chase, investigate, flee, roam and so on. */
    public void branch(String name) { branch = name; }

    void navigation(String outcome, Vec3 point, int failedPaths) {
        if (navigation == null) navigation = new ArrayList<>(4);
        if (navigation.size() >= 12) { navigationExtra++; return; }
        var entry = new Row("nav").put("o", outcome);
        if (point != null) entry.xyz("dest", point.x, point.y, point.z);
        if (failedPaths > 0) entry.put("fail", failedPaths);
        navigation.add(entry);
    }

    /** Called once when the pass ends, also when it ended early. */
    public void commit() {
        try {
            var track = session.track(mob);
            if (track == null) return;
            track.lastDecision = session.tick();
            track.focusOnPlayer = sensed instanceof Player;
            // Ground navigation refuses every path request of a body that is neither on the ground nor in a liquid.
            row.put("e", track.sid).xyz("p", mob.getX(), mob.getY(), mob.getZ()).put("yr", mob.getYRot())
                    .put("age", mob.tickCount).flag("ground", mob.onGround()).flag("in_water", mob.isInWater());
            mob.record(row);
            var target = mob.getTarget();
            if (target != null) row.put("tg", session.sid(target));
            if (branch != null) row.put("br", branch);
            if (navigation != null) row.list("nav", navigation);
            if (navigationExtra > 0) row.put("nav_more", navigationExtra);
            var notes = new ArrayList<Row>(players.size() + 1);
            for (var note : players) notes.add(describe(note));
            // A tracked player the entity filter never saw was outside the scan box.
            for (var player : session.playersNear(mob, 128)) {
                boolean seen = false;
                for (var note : players) if (note.player == player) { seen = true; break; }
                if (seen) continue;
                notes.add(new Row("pl").put("e", session.sid(player)).put("stage", "out_of_scan")
                        .put("body", WildlifeSenses.bodyDistance(mob, player)).put("dist", mob.distanceTo(player)));
            }
            if (!notes.isEmpty()) row.list("pl", notes);
            session.timed(row);
        } catch (Throwable t) {
            session.fail("decision trace", t);
        }
    }

    private Row describe(PlayerNote note) {
        var out = new Row("pl").put("e", session.sid(note.player)).put("stage", note.stage).put("body", note.body);
        if (note.stage.equals("day_routine")) out.put("wake", Config.WAKE_DISTANCE.get());
        if (note.stage.equals("mode")) out.flag("creative", note.player.isCreative()).flag("spectator", note.player.isSpectator())
                .flag("dead", !note.player.isAlive());
        if (!Double.isNaN(note.score)) out.put("score", note.score);
        var d = note.detail;
        if (d == null) return out;
        if (d.invalid || d.disguised) return out.flag("invalid", d.invalid).flag("disguised", d.disguised);
        out.put("dist", d.distance).put("sight", d.sight).put("near", d.near).put("fov", d.facing)
                .flag("in_range", d.distance < d.sight).flag("route_loaded", d.loaded).flag("los", d.clear)
                .flag("los_eye", d.eyeClear).put("los_rays", d.sightRays)
                .flag("invisible", d.invisible).flag("seen", d.visible)
                .put("hear", d.hearing).flag("moving", d.moving).flag("crouch", d.crouching)
                .put("scent", d.scentRange).put("downwind", d.downwind).flag("smelt", d.smelled).flag("wet", d.wet)
                .put("str", d.strength);
        return out;
    }
}
