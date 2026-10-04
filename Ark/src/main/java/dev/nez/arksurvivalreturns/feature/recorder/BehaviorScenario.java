package dev.nez.arksurvivalreturns.feature.recorder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorAction;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeMind;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.*;

/**
 * Opt-in experiment ({@code scenario=behavior}): the behaviour model put through its cases one after another on
 * open ground, each with what is expected of it. Animals of different hunger, thirst and fatigue side by side;
 * a hunter with and without an appetite; blows from a survival and from a creative observer on the timid, the
 * herd that stands together, the apex and the wounded; a herd mobbing an apex; a hunter beside a bigger one.
 *
 * <p>The animals keep their ordinary wild AI. The scenario only places them, sets their needs and health, sets
 * the hour, strikes the blows in the observer's name, and judges what it saw: every trial ends in a result with
 * its verdict, the states each animal went through and how its needs moved. Natural spawning is off, and every
 * other mob near the observer is removed before each trial.
 */
final class BehaviorScenario {
    /** Where an animal looks when it appears: at the observer, away from it, or at the first animal of the trial. */
    private enum Face { OBSERVER, AWAY, FIRST }

    /**
     * @param ahead blocks from the observer along the trial's heading
     * @param side  blocks to the right of that line
     * @param pack  animals of a trial with the same number are one pack
     * @param after seconds into the trial at which it appears
     */
    private record Actor(String role, Species species, double ahead, double side, int pack, double hunger, double thirst,
            double fatigue, double health, int after, Face face) {
        Actor(String role, Species species, double ahead, double side, int pack) {
            this(role, species, ahead, side, pack, 0.1, 0.05, 0.05, 1, 0, Face.OBSERVER);
        }
        Actor needs(double food, double water, double tired) {
            return new Actor(role, species, ahead, side, pack, food, water, tired, health, after, face);
        }
        Actor hurt(double share) { return new Actor(role, species, ahead, side, pack, hunger, thirst, fatigue, share, after, face); }
        Actor facing(Face way) { return new Actor(role, species, ahead, side, pack, hunger, thirst, fatigue, health, after, way); }
    }

    /** A blow on an animal in the observer's name, so many seconds into the trial. */
    private record Hit(int actor, int at) {}

    /**
     * @param whole the trial runs its full time (or until an animal dies) even when the verdict is already in
     * @param check what is wrong with what was seen, or null when all is as expected
     */
    private record Trial(String name, String expect, int dayTime, int seconds, GameType mode, boolean pond, boolean whole,
            List<Actor> actors, List<Hit> hits, Function<Run, String> check) {}

    /** What one animal was seen doing. */
    private static final class Seen {
        final Actor actor;
        CreatureEntity mob;
        Vec3 start;
        final List<BehaviorState> states = new ArrayList<>();
        final EnumMap<BehaviorState, Integer> first = new EnumMap<>(BehaviorState.class);
        final EnumSet<WildlifeMind.Reason> reasons = EnumSet.noneOf(WildlifeMind.Reason.class);
        final EnumSet<BehaviorAction> actions = EnumSet.noneOf(BehaviorAction.class);
        double moved, gap0 = -1, gap = -1, gapMin = Double.MAX_VALUE, hp0, hp;
        double[] needs0 = new double[3], needs = new double[3];
        int hitAt = Integer.MAX_VALUE;
        boolean died;
        Seen(Actor actor) { this.actor = actor; }
        boolean saw(BehaviorState state) { return first.containsKey(state); }
        int first(BehaviorState state) { return first.getOrDefault(state, Integer.MAX_VALUE); }
    }

    /** A trial in progress. */
    private static final class Run {
        final List<Seen> seen = new ArrayList<>();
        int struck, age;
        String role(int i) { return seen.get(i).actor.role(); }
    }

    private static final int HUNGER = 0, THIRST = 1, FATIGUE = 2;
    private static final int PAUSE_TICKS = 60, CLEAR_RADIUS = 160, MIN_TICKS = 240, POND_AHEAD = 26;

    // ------------------------------------------------------------------------- expectations

    private static String all(String... failures) {
        for (var failure : failures) if (failure != null) return failure;
        return null;
    }
    private static String saw(Run r, int i, BehaviorState state) {
        return r.seen.get(i).saw(state) ? null : r.role(i) + " never " + state;
    }
    private static String sawAny(Run r, int i, BehaviorState... states) {
        for (var state : states) if (r.seen.get(i).saw(state)) return null;
        return r.role(i) + " never " + List.of(states);
    }
    private static String never(Run r, int i, BehaviorState state) {
        return r.seen.get(i).saw(state) ? r.role(i) + " went " + state : null;
    }
    private static String before(Run r, int i, BehaviorState earlier, BehaviorState later) {
        var s = r.seen.get(i);
        return s.first(earlier) < s.first(later) || !s.saw(later) && s.saw(earlier) ? null : r.role(i) + " " + later + " before " + earlier;
    }
    /** The state came only with the blow, not from the observer standing there. */
    private static String onHit(Run r, int i, BehaviorState... states) {
        var s = r.seen.get(i);
        for (var state : states) {
            if (!s.saw(state)) continue;
            return s.first(state) >= s.hitAt ? null : r.role(i) + " went " + state + " before the blow";
        }
        return r.role(i) + " never " + List.of(states) + " after the blow";
    }
    private static String moved(Run r, int i, double blocks) {
        return r.seen.get(i).moved >= blocks ? null : r.role(i) + " moved " + Math.round(r.seen.get(i).moved) + " of " + (int) blocks + " blocks";
    }
    private static String below(Run r, int i, int need, double level) {
        return r.seen.get(i).needs[need] < level ? null : r.role(i) + " need " + need + " still " + Math.round(r.seen.get(i).needs[need] * 100) / 100.0;
    }
    private static String struck(Run r) { return r.struck > 0 ? null : "the observer was never struck"; }
    private static String anyHurt(Run r, int from, int to) {
        for (int i = from; i <= to; i++) if (r.seen.get(i).hp < r.seen.get(i).hp0 || r.seen.get(i).died) return null;
        return "none of " + r.role(from) + ".." + r.role(to) + " was bitten";
    }
    private static String reason(Run r, int i, WildlifeMind.Reason why) {
        return r.seen.get(i).reasons.contains(why) ? null : r.role(i) + " never decided by " + why;
    }

    // ------------------------------------------------------------------------------- trials

    private static Actor a(String role, Species species, double ahead, double side, int pack) {
        return new Actor(role, species, ahead, side, pack);
    }
    private static Trial trial(String name, String expect, int dayTime, int seconds, GameType mode, List<Actor> actors, List<Hit> hits,
            Function<Run, String> check) {
        return new Trial(name, expect, dayTime, seconds, mode, false, false, actors, hits, check);
    }

    private static final int MORNING = 3000, EARLY = 2000, AFTERNOON = 11000, NIGHT = 15000, MIDNIGHT = 18000;
    private static final Species PARA = Species.PARASAUR, TRIKE = Species.TRICERATOPS, GIGA = Species.GIGANOTOSAURUS, CARNO = Species.CARNOTAURUS;

    private static final List<Trial> TRIALS = List.of(
            new Trial("needs_day", "six grazers side by side: the thirsty drink, water before food and rest, the tired lie up, the sated do neither",
                    MORNING, 60, GameType.CREATIVE, true, false, List.of(
                    a("sated", PARA, 14, -25, 0).needs(0.05, 0.05, 0.05), a("hungry", PARA, 14, -15, 1).needs(0.9, 0.05, 0.05),
                    a("thirsty", PARA, 14, -5, 2).needs(0.05, 0.9, 0.05), a("hungry_thirsty", PARA, 14, 5, 3).needs(0.9, 0.9, 0.05),
                    a("tired", PARA, 14, 15, 4).needs(0.05, 0.05, 0.9), a("spent", PARA, 14, 25, 5).needs(0.9, 0.9, 0.9)), List.of(),
                    r -> all(never(r, 0, DRINK), never(r, 0, SEEK_WATER), never(r, 0, REST),
                            saw(r, 1, FORAGE), below(r, 1, HUNGER, 0.8),
                            saw(r, 2, DRINK), below(r, 2, THIRST, 0.3),
                            saw(r, 3, DRINK), before(r, 3, DRINK, FORAGE), below(r, 3, THIRST, 0.3),
                            saw(r, 4, REST), never(r, 4, DRINK),
                            saw(r, 5, DRINK), before(r, 5, DRINK, REST))),
            new Trial("needs_night", "four grazers at night: the sated sleep, the parched drink first, the starving feed first, a little thirst waits",
                    MIDNIGHT, 60, GameType.CREATIVE, true, false, List.of(
                    a("sated", PARA, 14, -15, 0).needs(0.05, 0.05, 0.3), a("parched", PARA, 14, -5, 1).needs(0.05, 0.95, 0.3),
                    a("starving", PARA, 14, 5, 2).needs(0.95, 0.05, 0.3), a("a_little_thirsty", PARA, 14, 15, 3).needs(0.05, 0.5, 0.3)), List.of(),
                    r -> all(saw(r, 0, SLEEP), never(r, 0, DRINK),
                            saw(r, 1, DRINK), before(r, 1, DRINK, SLEEP),
                            saw(r, 2, FORAGE), before(r, 2, FORAGE, SLEEP),
                            saw(r, 3, SLEEP), never(r, 3, DRINK))),
            trial("hunter_no_cover", "a hunter with no trees to sleep under stays up and loafs: it does not stand still all morning",
                    EARLY, 100, GameType.CREATIVE, List.of(a("giga", GIGA, 20, 0, 0)), List.of(),
                    r -> all(never(r, 0, SLEEP), r.seen.get(0).actions.size() >= 2 ? null : "giga showed only " + r.seen.get(0).actions)),
            trial("hunt_hungry", "a hungry hunter at night goes for the grazers it sees",
                    NIGHT, 45, GameType.CREATIVE, List.of(a("carno", CARNO, 8, 0, 0).needs(0.8, 0.05, 0.05).facing(Face.AWAY),
                    a("prey_a", PARA, 32, -2, 1), a("prey_b", PARA, 32, 2, 1)), List.of(),
                    r -> all(saw(r, 0, HUNT), r.seen.get(1).saw(FLEE) || r.seen.get(2).saw(FLEE) || r.seen.get(1).died || r.seen.get(2).died
                            ? null : "the prey neither ran nor died")),
            new Trial("hunt_sated", "a fed hunter at night leaves the same grazers alone",
                    NIGHT, 30, GameType.CREATIVE, false, true, List.of(a("carno", CARNO, 8, 0, 0).needs(0.1, 0.05, 0.05).facing(Face.AWAY),
                    a("prey_a", PARA, 32, -2, 1), a("prey_b", PARA, 32, 2, 1)), List.of(),
                    r -> all(never(r, 0, HUNT), never(r, 0, THREATEN))),
            trial("hit_timid_herd", "a struck Parasaur bolts and its herd bolts with it",
                    MORNING, 25, GameType.SURVIVAL, List.of(a("struck", PARA, 20, 0, 0), a("mate_a", PARA, 20, -4, 0), a("mate_b", PARA, 20, 4, 0)),
                    List.of(new Hit(0, 6)),
                    r -> all(onHit(r, 0, FLEE), moved(r, 0, 10), saw(r, 1, FLEE), saw(r, 2, FLEE))),
            trial("hit_defensive_herd", "a struck Triceratops turns on the observer and its herd joins it",
                    MORNING, 30, GameType.SURVIVAL, List.of(a("struck", TRIKE, 20, 0, 0), a("mate_a", TRIKE, 20, -6, 0), a("mate_b", TRIKE, 20, 6, 0)),
                    List.of(new Hit(0, 6)),
                    r -> all(onHit(r, 0, DEFEND), struck(r), saw(r, 1, DEFEND), saw(r, 2, DEFEND))),
            trial("hit_apex", "a Giganotosaurus struck from behind by day turns and bites",
                    AFTERNOON, 30, GameType.SURVIVAL, List.of(a("giga", GIGA, 16, 0, 0).facing(Face.AWAY)), List.of(new Hit(0, 5)),
                    r -> all(onHit(r, 0, DEFEND), struck(r))),
            trial("hit_sleeping_herd", "a Triceratops struck in its sleep wakes and answers, and so does its herd",
                    MIDNIGHT, 40, GameType.SURVIVAL, List.of(a("struck", TRIKE, 20, 0, 0), a("mate_a", TRIKE, 20, -6, 0), a("mate_b", TRIKE, 20, 6, 0)),
                    List.of(new Hit(0, 16)),
                    r -> all(saw(r, 0, SLEEP), onHit(r, 0, FLEE, DEFEND), sawAny(r, 1, FLEE, DEFEND, ALERT), sawAny(r, 2, FLEE, DEFEND, ALERT))),
            trial("hit_creative_timid", "a Parasaur struck by a creative observer bolts all the same",
                    MORNING, 20, GameType.CREATIVE, List.of(a("struck", PARA, 8, 0, 0)), List.of(new Hit(0, 4)),
                    r -> all(onHit(r, 0, FLEE), moved(r, 0, 10))),
            trial("hit_creative_apex", "a Giganotosaurus struck by a creative observer comes for it",
                    AFTERNOON, 25, GameType.CREATIVE, List.of(a("giga", GIGA, 14, 0, 0).facing(Face.AWAY)), List.of(new Hit(0, 4)),
                    r -> all(onHit(r, 0, DEFEND), r.seen.get(0).gapMin < 3 ? null : "giga came no nearer than " + Math.round(r.seen.get(0).gapMin))),
            trial("hit_wounded_hunter", "a badly hurt Carnotaurus runs from the blow",
                    AFTERNOON, 25, GameType.SURVIVAL, List.of(a("carno", CARNO, 16, 0, 0).hurt(0.2)), List.of(new Hit(0, 4)),
                    r -> all(saw(r, 0, FLEE), moved(r, 0, 10))),
            trial("hit_on_the_run", "struck again and again on the run, the hurt Carnotaurus turns and fights",
                    AFTERNOON, 30, GameType.SURVIVAL, List.of(a("carno", CARNO, 16, 0, 0).hurt(0.2)),
                    List.of(new Hit(0, 4), new Hit(0, 8), new Hit(0, 12), new Hit(0, 16)),
                    r -> all(saw(r, 0, FLEE), saw(r, 0, DEFEND), before(r, 0, FLEE, DEFEND), reason(r, 0, WildlifeMind.Reason.CORNERED))),
            new Trial("mobbed_apex", "three Triceratops go for a Giganotosaurus beside them; it fights back",
                    AFTERNOON, 60, GameType.CREATIVE, false, true, List.of(a("giga", GIGA, 24, 0, 0).facing(Face.AWAY),
                    a("trike_a", TRIKE, 19, 8, 1).facing(Face.FIRST), a("trike_b", TRIKE, 24, 9, 1).facing(Face.FIRST),
                    a("trike_c", TRIKE, 29, 8, 1).facing(Face.FIRST)), List.of(),
                    r -> all(sawAny(r, 1, DEFEND), saw(r, 0, DEFEND), anyHurt(r, 1, 3), anyHurt(r, 0, 0))),
            new Trial("mobbed_hurt_apex", "the same herd and a Giganotosaurus already badly hurt: it gives ground, and turns when it cannot shake them",
                    AFTERNOON, 45, GameType.CREATIVE, false, true, List.of(a("giga", GIGA, 24, 0, 0).hurt(0.4).facing(Face.AWAY),
                    a("trike_a", TRIKE, 19, 8, 1).facing(Face.FIRST), a("trike_b", TRIKE, 24, 9, 1).facing(Face.FIRST),
                    a("trike_c", TRIKE, 29, 8, 1).facing(Face.FIRST)), List.of(),
                    r -> all(sawAny(r, 1, DEFEND), saw(r, 0, FLEE))),
            trial("bigger_predator", "a Carnotaurus beside a Giganotosaurus clears off; the giant does not care",
                    AFTERNOON, 25, GameType.CREATIVE, List.of(a("giga", GIGA, 22, 0, 0).facing(Face.AWAY), a("carno", CARNO, 22, 12, 1).facing(Face.FIRST)),
                    List.of(),
                    r -> all(saw(r, 1, FLEE), moved(r, 1, 15), never(r, 0, HUNT), never(r, 0, DEFEND), never(r, 0, FLEE))),
            trial("player_near_hunter", "a hungry Giganotosaurus at night comes for a survival observer thirty blocks off",
                    NIGHT, 45, GameType.SURVIVAL, List.of(a("giga", GIGA, 30, 0, 0).needs(0.7, 0.05, 0.05)), List.of(),
                    r -> all(saw(r, 0, HUNT), struck(r))),
            trial("player_near_timid", "a Parasaur ten blocks from a survival observer bolts without being touched",
                    MORNING, 20, GameType.SURVIVAL, List.of(a("para", PARA, 10, 0, 0)), List.of(),
                    r -> all(saw(r, 0, FLEE), moved(r, 0, 10))));

    /** The trials of this run: all of them, or the ones the arm file names. */
    private final List<Trial> trials;
    private Vec3 origin;
    private Trial current;
    private Run run;
    private final List<Hit> pending = new ArrayList<>();
    private int index, started, nextTrial, passed, stamp = -1;
    private boolean finished, spawnRule = true, pondDug;

    /** @param only comma-separated trial names to run; empty runs every trial */
    BehaviorScenario(String only) {
        var names = Set.of(only.trim().isEmpty() ? new String[0] : only.trim().split("\s*,\s*"));
        trials = names.isEmpty() ? TRIALS : TRIALS.stream().filter(trial -> names.contains(trial.name())).toList();
    }

    Row settings() {
        var trials = new ArrayList<Row>();
        for (var trial : this.trials) {
            var actors = new ArrayList<Row>();
            for (var actor : trial.actors())
                actors.add(new Row("actor").put("role", actor.role()).put("species", actor.species().id).put("ahead", actor.ahead())
                        .put("side", actor.side()).put("pack", actor.pack()).put("hunger", actor.hunger()).put("thirst", actor.thirst())
                        .put("fatigue", actor.fatigue()).put("health", actor.health()).put("after", actor.after()).put("face", actor.face()));
            var hits = new ArrayList<Row>();
            for (var hit : trial.hits()) hits.add(new Row("hit").put("actor", hit.actor()).put("at", hit.at()));
            trials.add(new Row("trial").put("name", trial.name()).put("expect", trial.expect()).put("day_time", trial.dayTime())
                    .put("seconds", trial.seconds()).put("observer", trial.mode().getName()).flag("pond", trial.pond())
                    .list("actors", actors).list("hits", hits));
        }
        return new Row("scenario").put("name", "behavior").put("version", 1).list("trials", trials)
                .put("observer", "standing_at_origin_survival_healed_every_tick_or_creative").put("spawn_mobs", false)
                .put("cleared_radius", CLEAR_RADIUS).put("creature", "command_spawn_level_1_persistent_normal_wild_AI");
    }

    void tick(Session session) {
        if (finished || !(session.subject() instanceof ServerPlayer player)) return;
        var world = player.level();
        if (origin == null) {
            origin = player.position();
            spawnRule = world.getGameRules().get(GameRules.SPAWN_MOBS);
            world.getGameRules().set(GameRules.SPAWN_MOBS, false, world.getServer());
            log(session, "setup", null, new Row("setup").xyz("origin", origin.x, origin.y, origin.z)
                    .put("spawn_mobs_was", spawnRule).put("cleared", clear(world, player)));
            nextTrial = session.tick() + PAUSE_TICKS;
        }
        if (current != null && current.mode() == GameType.SURVIVAL) {
            // A strike lands for real and is counted; the observer is whole again on the next tick.
            if (player.getLastHurtByMob() instanceof CreatureEntity && player.getLastHurtByMobTimestamp() != stamp) {
                stamp = player.getLastHurtByMobTimestamp();
                run.struck++;
            }
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
        }
        if (current != null) {
            watch(session, world, player);
            return;
        }
        if (session.tick() < nextTrial) return;
        if (index == trials.size()) {
            finished = true;
            world.getGameRules().set(GameRules.SPAWN_MOBS, spawnRule, world.getServer());
            log(session, "complete", null, new Row("result").put("trials", index).put("passed", passed));
            session.stop("scenario_complete");
            return;
        }
        begin(session, world, player, trials.get(index++));
    }

    /** Pond trials look one way, the others the opposite way, so no thirsty animal of theirs finds the water. */
    private Vec3 heading() { return new Vec3(0, 0, current.pond() ? 1 : -1); }

    private Vec3 place(Actor actor) {
        Vec3 heading = heading(), across = new Vec3(-heading.z, 0, heading.x);
        return origin.add(heading.scale(actor.ahead())).add(across.scale(actor.side()));
    }

    private void begin(Session session, ServerLevel world, ServerPlayer player, Trial trial) {
        current = trial;
        run = new Run();
        pending.clear();
        pending.addAll(trial.hits());
        float yaw = trial.pond() ? 0 : 180;
        player.teleportTo(world, origin.x, origin.y, origin.z, Set.of(), yaw, 0, true);
        if (player.gameMode() != trial.mode()) player.setGameMode(trial.mode());
        player.setLastHurtByMob(null);
        stamp = player.getLastHurtByMobTimestamp();
        var clock = world.dimensionType().defaultClock();
        clock.ifPresent(c -> world.clockManager().setTotalTicks(c,
                world.getDefaultClockTime() - Math.floorMod(world.getDefaultClockTime(), 24000L) + 24000L + trial.dayTime()));
        int cleared = clear(world, player);
        int water = trial.pond() && !pondDug ? pond(world) : 0;
        pondDug |= trial.pond();
        for (var actor : trial.actors()) run.seen.add(new Seen(actor));
        started = session.tick();
        log(session, "begin", null, new Row("trial").put("trial", trial.name()).put("expect", trial.expect()).put("day_time", trial.dayTime())
                .put("seconds", trial.seconds()).put("observer", trial.mode().getName()).put("cleared", cleared).put("water", water));
        session.mark(player, "test:behavior " + trial.name());
        spawnDue(session, world, player);
    }

    private void spawnDue(Session session, ServerLevel world, ServerPlayer player) {
        var packs = new HashMap<Integer, UUID>();
        for (var seen : run.seen) if (seen.mob != null) packs.put(seen.actor.pack(), seen.mob.packId());
        for (var seen : run.seen) {
            var actor = seen.actor;
            if (seen.mob != null || run.age < actor.after() * 20) continue;
            Vec3 spot = place(actor);
            var surface = SpawnRules.surface(world, Mth.floor(spot.x), Mth.floor(spot.z));
            var mob = ModContent.CREATURES.get(actor.species()).get().create(world, EntitySpawnReason.COMMAND);
            if (surface == null || mob == null) {
                log(session, "setup_failed", null, new Row("actor").put("trial", current.name()).put("role", actor.role())
                        .put("why", surface == null ? "no_ground" : "entity_create"));
                seen.died = true;
                continue;
            }
            Vec3 at = Vec3.atBottomCenterOf(surface);
            Vec3 look = switch (actor.face()) {
                case OBSERVER -> origin.subtract(at);
                case AWAY -> at.subtract(origin);
                case FIRST -> run.seen.getFirst().mob == null ? origin.subtract(at) : run.seen.getFirst().mob.position().subtract(at);
            };
            float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
            mob.initializeLevel(1);
            mob.setPos(at); mob.setYRot(yaw); mob.yBodyRot = yaw; mob.yHeadRot = yaw;
            mob.setOnGround(true); mob.setDeltaMovement(0, -0.08, 0);
            mob.setPersistenceRequired();
            mob.joinPack(packs.computeIfAbsent(actor.pack(), k -> UUID.randomUUID()));
            mob.wildlife().mind().restoreNeeds(actor.hunger(), actor.thirst(), actor.fatigue());
            if (actor.health() < 1) mob.setHealth((float) (mob.getMaxHealth() * actor.health()));
            session.note(mob, "behavior_trial_" + current.name() + "_" + actor.role());
            if (!world.addFreshEntity(mob)) {
                log(session, "setup_failed", null, new Row("actor").put("trial", current.name()).put("role", actor.role()).put("why", "entity_add_refused"));
                seen.died = true;
                continue;
            }
            seen.mob = mob; seen.start = at; seen.hp0 = seen.hp = mob.getHealth();
            seen.needs0 = new double[]{actor.hunger(), actor.thirst(), actor.fatigue()};
            seen.needs = seen.needs0.clone();
            seen.gap0 = seen.gap = WildlifeSenses.bodyDistance(mob, player);
            log(session, "spawn", mob, new Row("actor").put("trial", current.name()).put("role", actor.role()).put("species", actor.species().id)
                    .xyz("at", at.x, at.y, at.z).put("yaw", yaw).put("gap", seen.gap0).put("pack", actor.pack())
                    .put("hunger", actor.hunger()).put("thirst", actor.thirst()).put("fatigue", actor.fatigue()).put("hp", mob.getHealth()));
        }
    }

    private void watch(Session session, ServerLevel world, ServerPlayer player) {
        run.age = session.tick() - started;
        spawnDue(session, world, player);
        for (var it = pending.iterator(); it.hasNext();) {
            var hit = it.next();
            if (run.age < hit.at() * 20) continue;
            it.remove();
            var seen = run.seen.get(hit.actor());
            if (seen.mob == null || !seen.mob.isAlive()) continue;
            seen.mob.invulnerableTime = 0;
            boolean landed = seen.mob.hurtServer(world, world.damageSources().playerAttack(player), 2);
            seen.hitAt = Math.min(seen.hitAt, run.age);
            log(session, "hit", seen.mob, new Row("hit").put("trial", current.name()).put("role", seen.actor.role()).put("landed", landed)
                    .put("observer", player.gameMode().getName()).put("state", seen.mob.behavior()).put("gap", WildlifeSenses.bodyDistance(seen.mob, player)));
        }
        boolean dead = false;
        for (var seen : run.seen) {
            var mob = seen.mob;
            if (mob == null || seen.died) continue;
            if (!mob.isAlive() || mob.isRemoved()) { seen.died = true; seen.hp = 0; dead = true; continue; }
            var state = mob.behavior();
            if (seen.states.isEmpty() || seen.states.getLast() != state) seen.states.add(state);
            seen.first.putIfAbsent(state, run.age);
            seen.actions.add(mob.action());
            var mind = mob.wildlife().mind();
            seen.reasons.add(mind.reason());
            seen.needs = new double[]{mind.hunger(), mind.thirst(), mind.fatigue()};
            seen.hp = mob.getHealth();
            seen.moved = Math.max(seen.moved, Math.sqrt(mob.position().subtract(seen.start).horizontalDistanceSqr()));
            seen.gap = WildlifeSenses.bodyDistance(mob, player);
            seen.gapMin = Math.min(seen.gapMin, seen.gap);
        }
        boolean out = run.age >= current.seconds() * 20;
        // Once what was expected has been seen a trial is over, unless it is one to be watched to the end.
        boolean settled = pending.isEmpty() && run.age >= MIN_TICKS && current.check().apply(run) == null;
        if (out || current.whole() && dead || !current.whole() && settled) finish(session, out ? "timeout" : dead ? "death" : "settled");
    }

    private void finish(Session session, String ended) {
        String wrong = current.check().apply(run);
        if (wrong == null) passed++;
        var actors = new ArrayList<Row>();
        for (var seen : run.seen) {
            var states = new StringBuilder();
            for (var state : seen.states) states.append(states.isEmpty() ? "" : ">").append(state.name());
            var row = new Row("actor").put("role", seen.actor.role()).put("species", seen.actor.species().id).put("states", states.toString())
                    .put("reasons", seen.reasons.toString()).put("actions", seen.actions.toString())
                    .put("moved", round(seen.moved)).put("gap0", round(seen.gap0)).put("gap", round(seen.gap)).put("gap_min", round(seen.gapMin))
                    .put("hp0", round(seen.hp0)).put("hp", round(seen.hp)).flag("died", seen.died)
                    .put("hunger0", round(seen.needs0[HUNGER])).put("hunger", round(seen.needs[HUNGER]))
                    .put("thirst0", round(seen.needs0[THIRST])).put("thirst", round(seen.needs[THIRST]))
                    .put("fatigue0", round(seen.needs0[FATIGUE])).put("fatigue", round(seen.needs[FATIGUE]));
            if (seen.hitAt != Integer.MAX_VALUE) row.put("hit_s", seen.hitAt / 20.0);
            var firsts = new Row("first");
            seen.first.forEach((state, tick) -> firsts.put(state.name(), tick / 20.0));
            actors.add(row.row("first_s", firsts));
        }
        log(session, "result", null, new Row("result").put("trial", current.name()).put("pass", wrong == null).put("wrong", wrong)
                .put("ended", ended).put("seconds", run.age / 20.0).put("observer_struck", run.struck).put("expect", current.expect())
                .list("actors", actors));
        session.mark(session.subject(), "test:behavior " + current.name() + (wrong == null ? " pass" : " FAIL " + wrong));
        for (var seen : run.seen) if (seen.mob != null) { session.note(seen.mob, "behavior_cleanup"); seen.mob.discard(); }
        current = null;
        run = null;
        nextTrial = session.tick() + PAUSE_TICKS;
    }

    private static double round(double value) { return value == Double.MAX_VALUE ? -1 : Math.round(value * 100) / 100.0; }

    void close(Session session, String reason) {
        if (current != null) finish(session, reason);
        if (!finished && session.subject() instanceof ServerPlayer player)
            player.level().getGameRules().set(GameRules.SPAWN_MOBS, spawnRule, player.level().getServer());
        finished = true;
    }

    /** Every mob but the observer's own pets near the observer goes, so a trial holds only its own animals. */
    private static int clear(ServerLevel world, ServerPlayer player) {
        var mobs = world.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(CLEAR_RADIUS),
                mob -> !(mob instanceof CreatureEntity c && c.isTamed()));
        mobs.forEach(Mob::discard);
        return mobs.size();
    }

    /** A strip of water across the lanes of the needs trials, two blocks wide and one deep, twelve blocks past the animals. */
    private int pond(ServerLevel world) {
        int filled = 0;
        for (int side = -34; side <= 34; side++) for (int depth = 0; depth < 2; depth++) {
            var surface = SpawnRules.surface(world, Mth.floor(origin.x) + side, Mth.floor(origin.z) + POND_AHEAD + depth);
            if (surface != null && world.setBlockAndUpdate(surface.below(), Blocks.WATER.defaultBlockState())) filled++;
        }
        return filled;
    }

    private static void log(Session session, String phase, CreatureEntity mob, Row details) {
        var text = new StringBuilder(); RowJson.line(text, details);
        session.timed(new Row("ev").put("ev", "test_behavior").put("r", phase).put("e", mob == null ? -1 : session.sid(mob))
                .put("note", text.toString()));
        ArkSurvivalReturns.LOGGER.info("Behavior test {}: {}", phase, text);
    }
}
