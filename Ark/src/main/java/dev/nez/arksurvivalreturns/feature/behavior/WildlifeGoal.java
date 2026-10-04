package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.SplittableRandom;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.land.*;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.creature.TreeTrample;
import dev.nez.arksurvivalreturns.feature.recorder.DecisionTrace;
import dev.nez.arksurvivalreturns.feature.recorder.Row;
import dev.nez.arksurvivalreturns.feature.recorder.SessionRecorder;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.feature.spawn.TreeShelter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Server adapter of the land behaviour model, by distance tier.
 *
 * <p>FULL: bounded perception, the decision model ({@link WildlifeMind}), the timed bridges between states
 * ({@link Choreographer}), pack signals that ripple with individual delays, and collision-aware navigation.
 * AMBIENT: the same calm routine on the schedule alone, with no senses and frozen needs. DORMANT: only the
 * sleep pose follows the schedule, on a slow timer.
 *
 * <p>While nothing presses it, an animal spends its time in bouts ({@link CalmRoutine}): the hour says what it
 * is about ({@link DailySchedule.Activity}), the mind turns that into a state, and the planner fills the state
 * with grazing, a few steps, a look around, a long stand, a walk. Walks go on from where the body faces,
 * with the herd's heading for a follower, so nothing paces back and forth.
 */
public final class WildlifeGoal extends WildlifeController {
    private WildlifeMind mind;
    private Choreographer choreo;
    private BlockPos home, transientHome, shelterDestination;
    private long nextShelterSearch;
    private int shelterSearchIndex;
    private static final int[] SHELTER_RADII = {8, 16, 24, 32, 48, 64, 96};
    private Vec3 lastKnown, destination, waterDestination, pendingAlarm, facing;
    private Vec3 approach, lastPathFailure;
    /** Outcome of the last path request, as told to the session recorder. */
    private String lastRoute;
    private double approachSpeed;
    private long retryPathAt, lastFailureTick = Long.MIN_VALUE;
    private BlockPos waterSearchOrigin;
    private int waterSearchIndex, waterAttempts, drySweeps;
    private static final java.util.List<BlockPos> WATER_OFFSETS = waterOffsets();
    private LivingEntity focus;
    private LivingEntity herdThreat;
    /** What struck this animal, for as long as the blow is on its mind; answered whatever its game mode. */
    private LivingEntity provoker;
    /**
     * What this animal has been watching without being pressed by it: for how long, from how near, and when it
     * was last there. Watched long enough it is let be, until it comes closer.
     */
    private LivingEntity watched;
    private int watchTicks;
    private double watchedGap;
    private long watchedSeen;
    private int herdThreatTicks, pendingAlarmTicks, alarmEvents;
    private java.util.UUID preyHerd;
    private int damageStamp = -1, failedPaths, nextRoutine, alarmTicks;
    private long nextAlarm, nextWaterSearch;
    private int corneredTicks;
    private int failedEscapes;
    private boolean interrupted, regrouping;
    /** Whether the alarm waiting, and the one in force, came from a mate in flight or a fight rather than one that only looked up. */
    private boolean pendingFlight, alarmFlight;
    private java.util.List<CreatureEntity> packCache;
    private BehaviorTier tier = BehaviorTier.FULL;
    /** The bout of the calm routine in progress and the one before it; the same planner near and far. */
    private CalmRoutine.Plan plan;
    private CalmRoutine.Step lastStep;
    private int planTicks;
    private float planYaw;
    private SplittableRandom routineRandom;
    /** The session recorder's view of the decision pass in progress; null outside one and while nothing records. */
    private DecisionTrace trace;
    public WildlifeGoal(CreatureEntity mob) { super(mob); setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public WildlifeMind mind() {
        if (mind == null) {
            mind = new WildlifeMind(mob.species().predator, mob.species().timid(), mob.species() == Species.VELOCIRAPTOR);
            long variation = mob.getUUID().getLeastSignificantBits();
            mind.restoreNeeds(0.4 + (variation & 255) / 1024.0, 0.15 + ((variation >>> 8) & 255) / 512.0,
                    0.05 + ((variation >>> 16) & 255) / 1024.0);
        }
        return mind;
    }
    /** The timed performance of the current state; per individual, so herd mates never move in lockstep. */
    public Choreographer choreo() {
        if (choreo == null) {
            choreo = new Choreographer(mob.behaviorProfile(), mob.getUUID().getLeastSignificantBits());
            choreo.reset(mind().state());
        }
        return choreo;
    }
    @Override public BlockPos home() {
        if (transientHome != null) return transientHome;
        if (home == null) home = mob.blockPosition();
        return home;
    }
    @Override public boolean canUse() { return mob.isAlive() && mob.species().landHabitat() && !mob.isTamed(); }
    @Override public boolean canContinueToUse() { return mob.isAlive() && mob.species().landHabitat() && !mob.isTamed(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        // A rider owns the mount's movement; the wild routine must not fight the input or target the rider.
        if (mob.isRidden()) { mob.getNavigation().stop(); mob.setTarget(null); return; }
        var now = mob.behaviorTier();
        if (now != tier) changeTier(now);
        switch (tier) {
            case FULL -> {
                if (Math.floorMod(mob.tickCount + mob.getId(), 10) == 0) think();
                // Ground navigation consumes a wide body's final node early. Finish a clear, short final leg.
                if (approach != null && !choreo().holding()) {
                    var target = mob.getTarget();
                    if (arrived(approach) || target != null && mob.isWithinMeleeAttackRange(target)) {
                        approach = null; mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
                    } else if (canNavigate() && clearApproach(world(), approach))
                        mob.getMoveControl().setWantedPosition(approach.x, approach.y, approach.z, approachSpeed);
                    else approach = null;
                }
                // Facing a stimulus turns the whole body in place, at the creature's own rate.
                if (facing != null && choreo().action().motion() == BehaviorAction.Motion.FACE && mob.getNavigation().isDone())
                    turnToward(yawTo(facing), false);
                // A turning bout ends when the body faces the new way.
                else if (plan != null && plan.step() == CalmRoutine.Step.TURN && choreo().action() == BehaviorAction.TURN
                        && turnToward(planYaw, false)) { choreo().release(); plan = null; }
            }
            case AMBIENT -> ambientTick();
            case DORMANT -> dormantTick();
        }
    }
    @Override public void stop() {
        mob.getNavigation().stop(); mob.setTarget(null); mob.setTrampling(false); packCache = null; approach = null;
    }
    /** An alarm from a pack mate reaches this animal after its own delay, rippling outward from the caller. */
    @Override public void receiveAlarm(Vec3 position) { receiveAlarm(position, true); }
    /**
     * A mate that runs or fights sets the herd off; one that has only lifted its head makes the others look the
     * same way, no more. Otherwise a herd bolts from what each of its members alone would only watch.
     */
    @Override public void receiveAlarm(Vec3 position, boolean flight) {
        int delay = Desync.reactionDelay(mob.getUUID().getLeastSignificantBits(), ++alarmEvents,
                mob.position().distanceTo(position), mob.species().timid());
        if (pendingAlarm == null) { pendingAlarm = position; pendingAlarmTicks = delay; pendingFlight = flight; return; }
        if (delay < pendingAlarmTicks || flight && !pendingFlight) pendingAlarm = position;
        pendingAlarmTicks = Math.min(pendingAlarmTicks, delay);
        pendingFlight |= flight;
    }
    @Override public void interruptSleep() {
        if (!mob.species().landHabitat()) return;
        interrupted = true;
        mind().interruptSleep(Config.SLEEP_CALM.get());
        mob.setBehavior(mind().state());
    }
    @Override public void followPreyHerd(java.util.UUID herd) { preyHerd = herd; }
    @Override public java.util.UUID preyHerd() { return preyHerd; }
    @Override public void receiveHerdThreat(LivingEntity threat) {
        if (!mob.species().defensiveHerd() || !WildlifeSenses.answerable(threat)) return;
        // What the herd answers, every member answers: a creative player who struck one of them too.
        if (!WildlifeSenses.validTarget(threat)) provoker = threat;
        herdThreat = threat; herdThreatTicks = 100; focus = threat; lastKnown = threat.position(); mind().defendHerd();
    }

    // ----------------------------------------------------------------------------------- tiers

    private void changeTier(BehaviorTier next) {
        if (SessionRecorder.on()) SessionRecorder.changed(mob, "tier", tier, next);
        if (next == BehaviorTier.FULL) {
            // Resume the full routine from what the cheaper routine was showing, without replaying a bridge.
            var shown = mob.behavior();
            if (shown == BehaviorState.SLEEP || shown == BehaviorState.ROAM || shown == BehaviorState.FORAGE) mind().resumeAs(shown);
            choreo().reset(mind().state());
            mob.setAction(choreo().action());
        } else {
            // Leaving full detail drops pursuit and bridges; needs stay frozen until the creature is near again.
            mob.setTarget(null); focus = null; lastKnown = null; pendingAlarm = null; destination = null; facing = null; approach = null;
            mob.setTrampling(false);
            mob.getNavigation().stop();
            choreo().reset(mind().state().sleeping() ? mind().state() : BehaviorState.ROAM);
        }
        plan = null;
        planTicks = 0;
        tier = next;
    }

    private DailySchedule.Phase schedule(ServerLevel world) {
        if (!WildlifeSenses.hasNightCycle(mob)) return DailySchedule.Phase.ROAM;
        return DailySchedule.phase(world.getDefaultClockTime(), mob.getUUID().getLeastSignificantBits(), mob.species().predator,
                Config.NIGHT_START.get(), Config.NIGHT_END.get(), Config.NIGHT_TRANSITION.get(), Config.DAY_SLEEP.get());
    }

    /** What the hour asks of this animal; without a day and night cycle, nothing in particular. */
    private DailySchedule.Activity activity(ServerLevel world) {
        if (!WildlifeSenses.hasNightCycle(mob)) return DailySchedule.Activity.ROAM;
        long clock = world.getDefaultClockTime(), own = mob.getUUID().getLeastSignificantBits();
        var activity = DailySchedule.activity(clock, own, mob.species().predator,
                Config.NIGHT_START.get(), Config.NIGHT_END.get(), Config.NIGHT_TRANSITION.get(), Config.DAY_SLEEP.get());
        // Not every member of a herd lies up at midday: some stay on their feet and keep feeding, a different few each day.
        if (activity == DailySchedule.Activity.REST && !mob.species().predator
                && Desync.unit(own, 31 + Math.floorDiv(clock, NighttimeCycle.DAY_TICKS)) >= 0.7) return DailySchedule.Activity.FEED;
        return activity;
    }

    private SplittableRandom routineRandom() {
        if (routineRandom == null) routineRandom = new SplittableRandom(mob.getUUID().getMostSignificantBits());
        return routineRandom;
    }

    /** Tier 2: the calm routine on the schedule alone, one decision per bout. */
    private void ambientTick() {
        if (!(mob.level() instanceof ServerLevel world)) return;
        if (plan != null && plan.step() == CalmRoutine.Step.SLEEP
                && Math.floorMod(mob.tickCount + mob.getId(), 20) == 0
                && (schedule(world) != DailySchedule.Phase.SLEEP || !canSleepHere(world))) plan = null;
        if (plan == null || --planTicks <= 0) { nextAmbient(world); return; }
        switch (plan.step()) {
            case TURN -> { if (turnToward(planYaw, false)) planTicks = Math.min(planTicks, 10); }
            case WALK, STEP -> { if (mob.getNavigation().isDone()) planTicks = Math.min(planTicks, 1); }
            default -> {}
        }
    }

    private void nextAmbient(ServerLevel world) {
        var activity = activity(world);
        boolean sleepTime = activity == DailySchedule.Activity.SLEEP;
        if (sleepTime && !canSleepHere(world)) {
            if (TreeShelter.required(mob.species()) && seekShelter(world)) {
                plan = new CalmRoutine.Plan(CalmRoutine.Step.WALK, 40, 0, 0, 1);
                planTicks = 40;
                mob.setBehavior(BehaviorState.ROAM);
                mob.setAction(BehaviorAction.WALK);
                mob.setNightActive(false);
                return;
            }
            // Nowhere to lie down: it stays up and loafs instead of standing like a statue until the hour passes.
            sleepTime = false;
        }
        if (sleepTime && TreeShelter.required(mob.species())) adoptShelter();
        double leash = LandWildlife.roam(mob.species()) * 0.8;
        boolean homeward = LandWildlife.distanceSqr(mob.blockPosition(), home()) > leash * leash;
        var mode = switch (activity) {
            case HUNT -> CalmRoutine.Mode.PATROL;
            case FEED -> mob.behaviorProfile().grazer() && LandWildlife.forage(world, mob.species(), mob.blockPosition())
                    ? CalmRoutine.Mode.GRAZE : CalmRoutine.Mode.LOAF;
            case REST -> canSleepHere(world) ? CalmRoutine.Mode.REST : CalmRoutine.Mode.LOAF;
            default -> CalmRoutine.Mode.LOAF;
        };
        plan = sleepTime ? CalmRoutine.sleep(routineRandom()) : homeward ? CalmRoutine.homeward(mob.behaviorProfile(), routineRandom())
                : CalmRoutine.next(mode, lastStep, mob.behaviorProfile(), routineRandom());
        lastStep = plan.step();
        planTicks = Math.max(1, plan.ticks());
        mob.getNavigation().stop();
        switch (plan.step()) {
            case WALK, STEP -> {
                if (!stroll(world, plan, homeward)) {
                    plan = new CalmRoutine.Plan(CalmRoutine.Step.STAND, 60, 0, 0, 0);
                    planTicks = 60;
                }
            }
            case TURN -> planYaw = mob.getYRot() + (float) plan.turn();
            case LOOK -> mob.playCue(BehaviorAction.Cue.LOOK);
            case SNIFF -> mob.playCue(BehaviorAction.Cue.SNIFF);
            case POOP -> mob.playCue(BehaviorAction.Cue.POOP);
            default -> {}
        }
        if (SessionRecorder.on()) SessionRecorder.event(mob, "ambient", plan.step() + " " + planTicks + (homeward ? " homeward" : ""));
        mob.setBehavior(CalmRoutine.state(plan.step()));
        mob.setAction(CalmRoutine.action(plan.step()));
        mob.setNightActive(activity == DailySchedule.Activity.HUNT);
    }

    /**
     * Sets off on a walked bout. The way is straight on with a bend, never a turn back on itself: a follower
     * takes the heading of its herd's leader, so a herd drifts one way, and an animal at the edge of its range
     * heads for home. Wider bends are tried when the ground ahead cannot be walked.
     */
    private boolean stroll(ServerLevel world, CalmRoutine.Plan bout, boolean homeward) {
        if (!canNavigate()) return false;
        var random = routineRandom();
        var leader = homeward || mob.species().solitary() ? null : leader(64);
        float base = homeward ? yawTo(Vec3.atBottomCenterOf(home())) : leader != null ? leader.getYRot() : mob.getYRot();
        double speed = mob.strollModifier(bout.pace()), range = LandWildlife.roam(mob.species());
        for (int attempt = 0; attempt < 5; attempt++) {
            float spread = homeward ? 25 : 40 + attempt * 25;
            float heading = base + (float) ((random.nextDouble() * 2 - 1) * spread);
            Vec3 target = mob.position().add(Vec3.directionFromRotation(0, heading).scale(bout.distance()));
            var ground = SpawnRules.surface(world, Mth.floor(target.x), Mth.floor(target.z));
            if (!standable(world, ground, 4)) continue;
            if (!homeward && LandWildlife.distanceSqr(ground, home()) > range * range) continue;
            // An animal does not stroll up to what it has been keeping an eye on.
            if (watched != null && watched.isAlive()) {
                double keep = flightDistance(watched) * 1.5;
                if (Vec3.atBottomCenterOf(ground).distanceToSqr(watched.position()) < keep * keep) continue;
            }
            if (move(world, Vec3.atBottomCenterOf(ground), speed)) return true;
        }
        return false;
    }

    /**
     * The calm routine near a player. The mind has chosen the state (grazing, on its rounds, nothing to do);
     * the planner fills it with bouts, and the choreographer holds each one for its time.
     */
    private void calm(ServerLevel world, CalmRoutine.Mode mode) {
        // A herd keeps together: a member left behind closes up before anything else.
        if (!mob.species().solitary()) {
            var leader = leader(64);
            if (leader != null && mob.distanceTo(leader) > mob.species().cohesionDistance()) {
                if (trace != null) trace.branch("follow_leader");
                plan = null;
                move(world, slot(leader), Math.min(1, mob.wanderModifier() * 1.15));
                return;
            }
        }
        if (plan != null && plan.step().travels()) {
            // Still on the way: a walk ends where it was going, or when it has taken too long.
            if (travelling() && (planTicks -= 10) > 0) {
                if (trace != null) trace.branch("bout_walk");
                return;
            }
            mob.getNavigation().stop(); approach = null;
        }
        double leash = LandWildlife.roam(mob.species()) * 0.8;
        boolean homeward = LandWildlife.distanceSqr(mob.blockPosition(), home()) > leash * leash;
        plan = homeward ? CalmRoutine.homeward(mob.behaviorProfile(), routineRandom())
                : CalmRoutine.next(mode, lastStep, mob.behaviorProfile(), routineRandom());
        lastStep = plan.step();
        planTicks = plan.ticks();
        if (trace != null) trace.branch("bout_" + plan.step().name().toLowerCase(java.util.Locale.ROOT));
        if (SessionRecorder.on()) SessionRecorder.event(mob, "ambient", plan.step() + " " + planTicks + (homeward ? " homeward" : ""));
        switch (plan.step()) {
            case WALK, STEP -> {
                // No walkable ground that way: stand a while, then look again.
                if (!stroll(world, plan, homeward)) { plan = null; choreo().perform(BehaviorAction.IDLE, 60); }
            }
            case TURN -> { planYaw = mob.getYRot() + (float) plan.turn(); choreo().perform(BehaviorAction.TURN, plan.ticks()); }
            default -> choreo().perform(CalmRoutine.action(plan.step()), plan.ticks());
        }
    }

    /** Tier 3: no routine; within the outer radius the pose still follows the sleep schedule. */
    private void dormantTick() {
        if (Math.floorMod(mob.tickCount + mob.getId(), 100) != 0) return;
        if (!mob.getNavigation().isDone()) mob.getNavigation().stop();
        if (!(mob.level() instanceof ServerLevel world) || !mob.posedWhenDormant()) return;
        boolean asleep = schedule(world) == DailySchedule.Phase.SLEEP && canSleepHere(world);
        mob.setBehavior(asleep ? BehaviorState.SLEEP : BehaviorState.ROAM);
        mob.setAction(asleep ? BehaviorAction.SLEEP : BehaviorAction.IDLE);
    }

    private boolean canSleepHere(ServerLevel world) {
        return mob.onGround() && !mob.isInWater() && !mob.isInLava() && !mob.isOnFire()
                && TreeShelter.sheltered(world, mob.species(), mob.blockPosition());
    }

    private void adoptShelter() {
        home = mob.blockPosition();
        transientHome = null;
        shelterDestination = null;
    }

    /**
     * Twelve loaded terrain candidates per five seconds; paths retain the existing global navigation budget.
     * False while there is no cover to walk to: the caller goes on with the calm routine, whose walks this
     * leaves alone between two searches.
     */
    private boolean seekShelter(ServerLevel world) {
        mob.setTrampling(false);
        if (!canNavigate()) return false;
        if (shelterDestination != null) {
            if (TreeShelter.sheltered(world, mob.species(), shelterDestination)
                    && move(world, Vec3.atBottomCenterOf(shelterDestination), mob.wanderModifier(), true)) return true;
            shelterDestination = null;
            mob.getNavigation().stop();
            approach = null;
        }
        if (world.getGameTime() < nextShelterSearch) return false;
        nextShelterSearch = world.getGameTime() + 100;
        for (int attempt = 0; attempt < 12; attempt++) {
            int index = shelterSearchIndex++;
            int ring = Math.floorMod(index / 12, SHELTER_RADII.length);
            double angle = (index % 12) * Math.PI / 6
                    + Math.floorMod(mob.getUUID().getLeastSignificantBits(), 360) * Math.PI / 180
                    + (index / (12 * SHELTER_RADII.length)) * 0.37;
            int x = mob.getBlockX() + (int) Math.round(Math.cos(angle) * SHELTER_RADII[ring]);
            int z = mob.getBlockZ() + (int) Math.round(Math.sin(angle) * SHELTER_RADII[ring]);
            var ground = SpawnRules.surface(world, x, z);
            if (ground == null || !TreeShelter.sheltered(world, mob.species(), ground)) continue;
            if (move(world, Vec3.atBottomCenterOf(ground), mob.wanderModifier(), true)) {
                shelterDestination = ground;
                plan = null;
                return true;
            }
        }
        return false; // Stay awake if no reachable canopy exists; never teleport or sleep in the open.
    }

    /** Rotates the facing toward a yaw with the weight of the creature's size; true once aligned. */
    private boolean turnToward(float yaw, boolean running) {
        return mob.steerYaw(yaw, running) || Math.abs(Mth.wrapDegrees(yaw - mob.getYRot())) < 2f;
    }

    private float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - mob.getZ(), point.x - mob.getX()) * (180.0 / Math.PI)) - 90.0f;
    }

    // ------------------------------------------------------------------------------ full tier

    @Override public void think() {
        if (!(mob.level() instanceof ServerLevel world) || !mob.species().landHabitat()) return;
        // Non-null only while the session recorder runs; it is told what this pass computes, never asked.
        trace = SessionRecorder.decision(mob);
        try {
            decide(world);
        } finally {
            if (trace != null) { trace.commit(); trace = null; }
        }
    }
    private void decide(ServerLevel world) {
        var t = trace;
        var brain = mind();
        var dance = choreo();
        dance.advance(10, travelling());
        // One pack scan per decision is reused by the herd, alarm and regroup routines.
        packCache = world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(64),
                c -> c != mob && c.isAlive() && c.packId().equals(mob.packId()));
        boolean cycle = WildlifeSenses.hasNightCycle(mob), night = cycle && WildlifeSenses.night(mob);
        boolean quietRoutine = cycle && (mob.species().predator ? !night : night);
        corneredTicks = Math.max(0, corneredTicks - 10);
        mob.setNightActive(night && mob.species().predator);
        home();
        if (preyHerd != null) {
            var herd = world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(96),
                    c -> c.isAlive() && c.species() == Species.BRONTOSAURUS && c.packId().equals(preyHerd));
            // A linked prey herd may attract a bounded search, never move the permanent habitat.
            if (!herd.isEmpty()) transientHome = herd.getFirst().blockPosition();
        }
        if ((herdThreatTicks -= 10) <= 0) herdThreat = null;
        if (pendingAlarm != null && (pendingAlarmTicks -= 10) <= 0) {
            lastKnown = pendingAlarm; alarmTicks = 40; alarmFlight = pendingFlight; pendingAlarm = null;
        }
        boolean peaceful = world.getDifficulty() == Difficulty.PEACEFUL;
        var hurtBy = mob.getLastHurtByMob();
        boolean attacked = hurtBy != null && mob.getLastHurtByMobTimestamp() != damageStamp && WildlifeSenses.answerable(hurtBy);
        if (attacked) { damageStamp = mob.getLastHurtByMobTimestamp(); provoker = hurtBy; focus = hurtBy; lastKnown = hurtBy.position(); }
        else if (provoker != null && (brain.provoked() == 0 || !WildlifeSenses.answerable(provoker))) provoker = null;
        var attacker = provoker;
        // Struck again after three seconds of running: the attacker keeps up, so the animal turns on it.
        if (attacked && brain.state() == BehaviorState.FLEE && brain.flight() >= 60) corneredTicks = Math.max(corneredTicks, 200);
        if (attacked && mob.species().herd()) {
            for (var member : packWithin(48))
                if (member.species() == mob.species()) {
                    if (mob.species().timid()) member.wildlife().receiveAlarm(attacker.position());
                    else member.wildlife().receiveHerdThreat(attacker);
                }
        }
        double scan = Math.max(48, WildlifeSenses.sightRange(mob));
        var candidates = world.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(scan),
                c -> eligible(c, attacker, peaceful, t))
                .stream().filter(c -> admitted(c, cycle, quietRoutine, attacker, attacked, t))
                .sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(24).toList();
        if (t != null) t.candidates(scan, candidates);
        WildlifeSenses.Detection detection = new WildlifeSenses.Detection(false, 0);
        LivingEntity sensed = null;
        double best = 0;
        if (watched != null && (!watched.isAlive() || world.getGameTime() - watchedSeen > WATCH_MEMORY)) watched = null;
        for (var candidate : candidates) {
            if (!relevant(candidate) && candidate != attacker && candidate != herdThreat) continue;
            if (candidate != attacker && candidate != herdThreat && letBe(world, candidate)) continue;
            var check = WildlifeSenses.detect(mob, candidate, candidate == provoker);
            double score = check.strength() / (1 + mob.distanceTo(candidate) / 32.0) + (candidate == focus && check.strength() > 0 ? 0.15 : 0);
            if (t != null) t.scored(candidate, check, score);
            if (score > best) { best = score; sensed = candidate; detection = check; }
        }
        if (attacked) {
            sensed = attacker; detection = WildlifeSenses.detect(mob, attacker, true);
            if (t != null) t.forced("attacker");
        } else if (herdThreat != null && (herdThreat == provoker || WildlifeSenses.validTarget(herdThreat))) {
            var threatSense = WildlifeSenses.detect(mob, herdThreat, herdThreat == provoker);
            if (threatSense.strength() > 0) {
                sensed = herdThreat; detection = threatSense;
                if (t != null) t.forced("herd_threat");
            }
        }
        // Inside the flight distance, hunting, or coming for this animal: a threat that must be answered.
        // Beyond it the same threat is only watched, and watched long enough it is let be: this pass senses nothing.
        double gap = sensed == null ? Double.MAX_VALUE : WildlifeSenses.bodyDistance(mob, sensed);
        boolean pressing = attacked || herdThreat != null || alarmTicks > 0 && alarmFlight || sensed != null && (sensed == provoker || gap < flightDistance(sensed)
                || sensed instanceof net.minecraft.world.entity.Mob hunter && hunter.getTarget() == mob
                || sensed instanceof CreatureEntity c && c.behavior().combat() && gap < flightDistance(sensed) * 2);
        if (watch(world, sensed, gap, pressing, attacked)) { sensed = null; detection = new WildlifeSenses.Detection(false, 0); }
        if (sensed != null) {
            focus = sensed;
            // Non-visual stimuli carry an approximate point, not a continuously tracked hidden actor.
            lastKnown = detection.visible() ? sensed.position() : sensed.position().add(3, 0, -3);
        }
        if (focus != null && focus != provoker && !WildlifeSenses.validTarget(focus)) { focus = null; mob.setTarget(null); }
        double signal = Math.max(detection.strength(), alarmTicks > 0 ? 0.65 : 0);
        if (t != null) t.sensed(sensed, detection, alarmTicks > 0);
        if (!peaceful && sensed == null) {
            var noise = WildlifeNoise.hear(mob);
            if (noise != null) {
                signal = Math.max(signal, 0.65); lastKnown = noise.position();
                if (t != null) t.noise(noise.position());
            }
        }
        alarmTicks = Math.max(0, alarmTicks - 10);
        boolean visible = sensed != null && detection.visible();
        boolean intruding = visible && sensed instanceof Player && (cycle
                ? WildlifeSenses.bodyDistance(mob, sensed) < Config.WAKE_DISTANCE.get()
                : mob.distanceTo(sensed) < Math.max(6, mob.getBbWidth() + 3));
        if (visible && mob.species().herd() && sensed instanceof CreatureEntity c && c.species().predator
                && mob.distanceTo(sensed) < mob.getBbWidth() + 10) intruding = true;
        boolean guardedPrey = visible && sensed instanceof CreatureEntity prey && prey.species().defensiveHerd()
                && world.getEntitiesOfClass(CreatureEntity.class, prey.getBoundingBox().inflate(32),
                    c -> c.isAlive() && c.packId().equals(prey.packId()) && c.getHealth() >= c.getMaxHealth() * 0.5).size() >= 2;
        boolean intimidating = visible && pressing && sensed instanceof CreatureEntity c && c.species().predator
                && c.getBbWidth() > mob.getBbWidth() * 1.4;
        if (mob.species().herd() && sensed instanceof CreatureEntity) intimidating = false;
        if (visible && dev.nez.arksurvivalreturns.feature.accessory.AccessoryEffects.tyrant(mob, sensed)) intimidating = true;
        // Night herbivores first escape, then evaluate their ability to stand with the herd.
        if (cycle && night && !mob.species().predator) intimidating = false;
        // A hunter keeps clear of a herd that stands together, and backs off from one when it is hurt. One that
        // the herd is already striking answers the blows first.
        if (mob.species().predator && guardedPrey && (mob.getHealth() < mob.getMaxHealth() * 0.45
                || !attacked && brain.provoked() == 0 && mob.distanceTo(sensed) < mob.getBbWidth() + 8)) intimidating = true;
        // Nothing can be done to a player on a peaceful world: an animal one of them strikes runs.
        boolean harmless = peaceful && sensed instanceof Player;
        if (harmless) intimidating = true;
        BehaviorState before = brain.state();
        boolean shelterRequired = TreeShelter.required(mob.species());
        boolean sheltered = !shelterRequired || TreeShelter.sheltered(world, mob.species(), mob.blockPosition());
        boolean shelterWanted = shelterRequired && (schedule(world) == DailySchedule.Phase.SLEEP
                || brain.fatigue() >= 0.7 || before.sleeping());
        boolean seekingShelter = shelterWanted && !sheltered;
        if (shelterWanted && sheltered) adoptShelter();
        // The bank an animal has chosen may lie past its range: it goes, drinks and walks home afterwards.
        boolean watering = before == BehaviorState.DRINK || before == BehaviorState.SEEK_WATER && waterDestination != null;
        boolean far = !seekingShelter && !watering && LandWildlife.distanceSqr(mob.blockPosition(), home()) > territoryRadius() * territoryRadius();
        if (brain.recovery() > 0 && arrived(Vec3.atBottomCenterOf(home()))) brain.arrivedHome();
        // Terrain probes are only useful when the mind can act on them; other states skip the block sweep.
        var activity = cycle ? activity(world) : DailySchedule.Activity.ROAM;
        boolean needsWater = brain.wantsWater(activity) || before == BehaviorState.DRINK;
        boolean needsForage = brain.wantsForage(activity) || !mob.species().predator && before == BehaviorState.FORAGE;
        // The body stops within a step of the bank it chose, and the bank is where the reach was checked:
        // from the neighbouring block the same water can be out of reach or behind the rim.
        boolean water = needsWater && (nearbyWater(world) || snowUnderfoot(world)
                || canNavigate() && waterDestination != null && arrived(waterDestination) && waterAt(world, waterDestination));
        // A grazer that steps onto a bare block has not left its feeding ground: the ground beside it counts.
        boolean forage = needsForage && (LandWildlife.forage(world, mob.species(), mob.blockPosition())
                || before == BehaviorState.FORAGE && forageBeside(world));
        boolean huntable = visible && prey(sensed) && (!guardedPrey || sensed.getHealth() < sensed.getMaxHealth() * 0.35);
        boolean danger = attacked || herdThreat != null || alarmTicks > 0 && alarmFlight
                || cycle && night && !mob.species().predator && intruding
                || visible && sensed instanceof net.minecraft.world.entity.Mob enemy && enemy.getTarget() == mob
                || visible && pressing && sensed instanceof CreatureEntity c && c.species().predator && c.getBbWidth() * 1.3 >= mob.getBbWidth();
        boolean defended = cycle && night && mob.species().defensiveHerd() && danger
                && 1 + packWithin(32).stream().filter(c -> c.getHealth() >= c.getMaxHealth() * 0.5).count() >= 2;
        boolean safeSleep = mob.onGround() && !mob.isInWater() && !mob.isInLava() && !mob.isOnFire()
                && !interrupted && sheltered;
        boolean cornered = !harmless && (corneredTicks > 0
                || attacked && (mob.species() == Species.THERIZINOSAURUS || mob.species() == Species.TITANOSAUR));
        Vec3 regroupDestination = null;
        // Investigation may briefly interrupt fleeing while threat memory fades. Keep the return intent.
        if (!cycle || !night || mob.species().predator) regrouping = false;
        else if (before == BehaviorState.FLEE || before == BehaviorState.REGROUP) regrouping = true;
        if (regrouping) {
            var leader = leader(64);
            Vec3 anchor = leader == null ? Vec3.atBottomCenterOf(home()) : leader.position();
            double near = leader == null ? 6 + mob.getBbWidth() / 2 : mob.species().cohesionDistance();
            if (mob.position().distanceToSqr(anchor) > near * near) regroupDestination = leader == null ? anchor : slot(leader);
            else regrouping = false;
        }
        var routine = cycle ? new WildlifeMind.Routine(true, night, schedule(world) == DailySchedule.Phase.SLEEP, safeSleep,
                danger, defended, cornered,
                Config.SLEEP_CALM.get(), Config.NIGHT_HUNGER.get(), regroupDestination != null, activity) : new WildlifeMind.Routine(false, false, false,
                        sheltered, false, false, cornered, 0, 1);
        var observation = new WildlifeMind.Observation(signal, visible, huntable, intruding, attacked,
                intimidating, far, water, forage, !world.isBrightOutside(), mob.getHealth() / mob.getMaxHealth(), pressing);
        var state = brain.step(observation, 10, routine, null);
        if (t != null) t.step(before, state, brain, observation, routine, guardedPrey, interrupted);
        interrupted = false;
        // The point is kept while the animal still watches it or walks to it.
        if (!brain.remembers() && !brain.wary() && state != BehaviorState.INVESTIGATE) { lastKnown = null; focus = null; }
        mob.setBehavior(state);
        if (state != before) {
            mob.getNavigation().stop(); destination = null; approach = null; nextRoutine = 0; failedPaths = 0; lastPathFailure = null;
            plan = null;
            // Reflexes skip the display: a hit, or a threat already at the body.
            boolean urgent = attacked || sensed != null && WildlifeSenses.bodyDistance(mob, sensed) < 3;
            if (t != null) t.entered(urgent);
            dance.enter(before, state, urgent);
            if (state.alarm() && lastKnown != null && world.getGameTime() >= nextAlarm && (!cycle || visible || attacked)) {
                nextAlarm = world.getGameTime() + 100;
                for (var member : packWithin(32)) member.wildlife().receiveAlarm(lastKnown, state != BehaviorState.ALERT);
            }
        }
        mob.setTarget(state.combat() && visible ? sensed : null);
        boolean trampled = mob.tramplesTrees();
        mob.setTrampling(state.combat() && visible && sensed != null);
        if (trampled != mob.tramplesTrees()) { mob.getNavigation().stop(); destination = null; approach = null; }
        facing = lastKnown != null && state.alarm() ? lastKnown : null;
        if (facing != null) mob.getLookControl().setLookAt(facing.x, facing.y + 1, facing.z, 20, 20);
        boolean holding = dance.holding();
        // A combat state that reaches the switch below has no visible target and stops.
        if (t != null) t.branch(holding ? "hold" : state.combat() ? "no_sight" : state.name().toLowerCase(java.util.Locale.ROOT));
        if (state.combat() && visible && sensed != null) {
            if (mob.isWithinMeleeAttackRange(sensed)) {
                mob.getNavigation().stop(); approach = null;
                // The strike schedules its damage on the clip's hit frame; onStrikeKill feeds the mind.
                mob.strike(sensed);
                brain.closedIn();
                if (t != null) t.branch("strike");
            } else if (holding) { mob.getNavigation().stop(); approach = null; }
            else {
                if (t != null) t.branch("chase");
                move(world, sensed.position(), dance.action() == BehaviorAction.STALK ? mob.wanderModifier() * 0.6 : chaseModifier());
            }
        } else if (holding) { mob.getNavigation().stop(); approach = null; }
        else if (!canNavigate()) routed("airborne_deferred", destination, true);
        else switch (state) {
            case INVESTIGATE -> { if (lastKnown != null) move(world, lastKnown, mob.wanderModifier()); }
            case FLEE -> {
                if (mob.tickCount >= nextRoutine || !cycle && mob.getNavigation().isDone()) {
                    nextRoutine = mob.tickCount + 40 + (cycle ? Math.floorMod(mob.getId(), 10) : 0);
                    Vec3 away = lastKnown == null ? mob.position().subtract(Vec3.atBottomCenterOf(home())) : mob.position().subtract(lastKnown);
                    boolean escaped = escape(world, scatter(away));
                    // A deferred path is not an escape yet: ask again on the next pass instead of standing for two seconds.
                    if ("budget_deferred".equals(lastRoute)) nextRoutine = mob.tickCount + 10;
                    failedEscapes = escaped ? 0 : failedEscapes + 1;
                    if (failedEscapes >= 3) corneredTicks = 100;
                }
            }
            case RETURN_HOME -> {
                if (!seekingShelter || !seekShelter(world)) move(world, Vec3.atBottomCenterOf(home()), mob.wanderModifier());
            }
            case REGROUP -> { if (regroupDestination != null) move(world, regroupDestination, Math.min(1, mob.wanderModifier() * 1.3)); }
            case SEEK_WATER -> seekWater(world);
            case ROAM, SEARCH, FORAGE -> {
                // A hunter with no cover to sleep under stays up: it loafs, it does not stand still until dusk.
                if (!seekingShelter || !seekShelter(world)) calm(world, state == BehaviorState.FORAGE ? CalmRoutine.Mode.GRAZE
                        : state == BehaviorState.SEARCH ? CalmRoutine.Mode.PATROL : CalmRoutine.Mode.LOAF);
            }
            default -> { mob.getNavigation().stop(); approach = null; }
        }
        // Cues and the synced action go out last, so a pause beat chosen above starts its clip this tick.
        dance.settle(travelling());
        var cue = dance.takeCue();
        if (cue != null) mob.playCue(cue);
        else if (state != before && before.sleeping() && !state.sleeping()) mob.playCue(BehaviorAction.Cue.WAKE);
        mob.setAction(dance.action());
    }
    /** A landed strike that killed the target satisfies the predator exactly like the old instant hit did. */
    @Override public void onStrikeKill() {
        var before = mind().state();
        mind().ate(); focus = null; mob.setTarget(null); mob.getNavigation().stop();
        choreo().enter(before, BehaviorState.FEED, false);
    }
    /** Full pursuit and escape speed, varied a little per individual so a pack spreads out. */
    private double chaseModifier() {
        return Math.clamp(Desync.speedFactor(mob.getUUID().getLeastSignificantBits()), 0.92, 1.05);
    }
    /**
     * Escape heading: away from the threat, bent by this animal's own angle for this alarm, and pushed off the
     * nearest herd mate so a fleeing herd fans out instead of stacking on one line.
     */
    private Vec3 scatter(Vec3 away) {
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() < 1.0E-4) flat = Vec3.directionFromRotation(0, mob.getYRot());
        flat = flat.normalize().yRot((float) Math.toRadians(Desync.headingJitter(mob.getUUID().getLeastSignificantBits(), alarmEvents)));
        CreatureEntity nearest = null;
        double closest = Math.pow(mob.getBbWidth() + 4, 2);
        for (var member : packWithin(12)) {
            double d = member.distanceToSqr(mob);
            if (d < closest) { closest = d; nearest = member; }
        }
        if (nearest != null) {
            Vec3 apart = mob.position().subtract(nearest.position());
            apart = new Vec3(apart.x, 0, apart.z);
            if (apart.lengthSqr() > 1.0E-4) flat = flat.add(apart.normalize().scale(0.6)).normalize();
        }
        return flat;
    }
    /** The entity filter of the candidate scan. */
    private boolean eligible(LivingEntity c, LivingEntity attacker, boolean peaceful, DecisionTrace t) {
        if (c == mob) return false;
        boolean valid = c == provoker || WildlifeSenses.validTarget(c), barred = peaceful && c instanceof Player && c != provoker;
        if (t != null && (!valid || barred)) t.excluded(c, valid ? "peaceful" : "mode");
        return valid && !barred
                && (c == attacker || c instanceof Player || c instanceof CreatureEntity || (mob.species().predator && c instanceof Animal));
    }
    /** The day-and-night filter of the candidate scan; without a night cycle every eligible entity passes. */
    private boolean admitted(LivingEntity c, boolean cycle, boolean quietRoutine, LivingEntity attacker, boolean attacked, DecisionTrace t) {
        boolean concerns = !cycle || relevant(c) || c == attacker || c == herdThreat;
        boolean allowed = concerns && (!cycle || routineAllows(c, quietRoutine, attacker));
        if (t != null) t.gate(c, concerns, allowed);
        return allowed;
    }
    private boolean routineAllows(LivingEntity candidate, boolean quietRoutine, LivingEntity recentAttacker) {
        if (!quietRoutine || candidate == recentAttacker || candidate == herdThreat || mind().state().combat()
                || candidate instanceof net.minecraft.world.entity.Mob enemy && enemy.getTarget() == mob) return true;
        if (candidate instanceof Player) return WildlifeSenses.bodyDistance(mob, candidate) <= Config.WAKE_DISTANCE.get();
        return !mob.species().predator || candidate instanceof CreatureEntity c && c.species().predator;
    }
    private boolean relevant(LivingEntity other) {
        // A hunter sizes up a player from as far as it sees; a grazer minds one only inside its watching distance.
        if (other instanceof Player) return mob.species().predator || WildlifeSenses.bodyDistance(mob, other) < flightDistance(other) * 2;
        if (other instanceof CreatureEntity c && c.packId().equals(mob.packId())) return false;
        return prey(other) || (other instanceof CreatureEntity c && c.species().predator && noticeable(c)
                && (mob.species().herd() || c.getBbWidth() > mob.getBbWidth() * 1.4
                    || WildlifeSenses.night(mob) && !mob.species().predator && (c.getBbWidth() * 1.3 >= mob.getBbWidth() || c.getTarget() == mob)));
    }
    /** Ticks a threat that keeps its distance is watched before it is let be (plus up to six seconds of the animal's own), and how long it is remembered unseen. */
    private static final int WATCH_TICKS = 160, WATCH_MEMORY = 600;

    /** Whether this is something a hunter would eat rather than something to keep an eye on. */
    private boolean threat(LivingEntity other) { return !mob.species().predator || !prey(other); }

    /**
     * Habituation. An animal looks at a threat that keeps its distance for a while and then goes back to what
     * it was doing; a herd does not stare at a hunter on the far side of the valley all afternoon. Coming
     * closer, hunting, or stepping inside the flight distance renews the interest at once.
     */
    private boolean letBe(ServerLevel world, LivingEntity candidate) {
        if (candidate != watched) return false;
        watchedSeen = world.getGameTime();
        double gap = WildlifeSenses.bodyDistance(mob, candidate);
        boolean closing = gap < watchedGap - 3;
        boolean urgent = gap < flightDistance(candidate) || candidate instanceof net.minecraft.world.entity.Mob m && m.getTarget() == mob
                || candidate instanceof CreatureEntity c && c.behavior().combat();
        if (closing || urgent) {
            watchedGap = Math.min(watchedGap, gap);
            watchTicks = 0;
            return false;
        }
        return watchTicks >= WATCH_TICKS + (int) (Desync.unit(mob.getUUID().getLeastSignificantBits(), 57) * 120);
    }

    /** Counts how long the sensed threat has been watched. True at the limit: the mind lets go of it, from this pass on. */
    private boolean watch(ServerLevel world, LivingEntity sensed, double gap, boolean pressing, boolean attacked) {
        if (sensed == null || attacked || !threat(sensed)) return false;
        if (sensed != watched) { watched = sensed; watchTicks = 0; watchedGap = gap; }
        watchedSeen = world.getGameTime();
        if (pressing) { watchTicks = 0; watchedGap = Math.min(watchedGap, gap); return false; }
        watchTicks += 10;
        if (!letBe(world, sensed)) return false;
        mind().loseInterest();
        return true;
    }

    /** Cold-adapted animals take their water from the snow they stand on. */
    private boolean snowUnderfoot(ServerLevel world) {
        if (!mob.species().coldAdapted() || !canNavigate()) return false;
        var feet = mob.blockPosition();
        return Boolean.TRUE.equals(LandWildlife.coldHydration(world, feet)) || Boolean.TRUE.equals(LandWildlife.coldHydration(world, feet.below()));
    }

    /**
     * How near a threat may come before this animal must act: farther for a big threat and for a timid animal.
     * Half again as far it is watched; beyond that it is part of the landscape.
     */
    private double flightDistance(LivingEntity threat) {
        return Math.clamp(8 + threat.getBbWidth() * 3 + (mob.species().timid() ? 6 : 0), 12, 40);
    }
    /**
     * Prey graze in sight of a resting hunter. One that sleeps is noticed only at its side, one that roams only
     * inside the watching distance, and one that hunts from much farther.
     */
    private boolean noticeable(CreatureEntity hunter) {
        if (hunter.getTarget() == mob) return true;
        double gap = WildlifeSenses.bodyDistance(mob, hunter);
        if (hunter.behavior().sleeping()) return gap < 4 + mob.getBbWidth();
        return gap < flightDistance(hunter) * (hunter.behavior().combat() ? 2.5 : 1.5);
    }
    private boolean prey(LivingEntity other) {
        if (other == null || !mob.species().predator) return false;
        if (other instanceof Player) return true;
        if (other instanceof CreatureEntity c) return !c.species().predator && c.species() != mob.species() && c.getBbWidth() <= mob.getBbWidth() * 1.3;
        return other instanceof Animal && other.getBbWidth() <= mob.getBbWidth() * 1.3;
    }
    /** Distance-filtered view of the one pack scan taken at the start of this decision. */
    private java.util.List<CreatureEntity> packWithin(double radius) {
        if (packCache == null || packCache.isEmpty()) return java.util.List.of();
        var box = mob.getBoundingBox().inflate(radius);
        return packCache.stream().filter(c -> box.intersects(c.getBoundingBox())).toList();
    }
    private CreatureEntity leader(double radius) {
        return packWithin(radius).stream().filter(c -> c.getId() < mob.getId())
                .min(Comparator.comparingInt(CreatureEntity::getId)).orElse(null);
    }
    /** This member's own place around the leader, so a group spreads out instead of converging on one block. */
    private Vec3 slot(CreatureEntity leader) {
        long seed = mob.getUUID().getLeastSignificantBits();
        double angle = Desync.formationAngle(seed);
        double radius = Desync.formationRadius(seed, mob.species().cohesionDistance(), mob.getBbWidth());
        return leader.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }
    private double territoryRadius() { return LandWildlife.leash(mob.species()); }
    /** Whether grazing ground lies a couple of blocks from the body in any of the four directions. */
    private boolean forageBeside(ServerLevel world) {
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var pos = SpawnRules.surface(world, mob.getBlockX() + direction.getStepX() * 2, mob.getBlockZ() + direction.getStepZ() * 2);
            if (pos != null && Math.abs(pos.getY() - mob.getY()) <= 2 && LandWildlife.forage(world, mob.species(), pos)) return true;
        }
        return false;
    }
    /** Loaded natural ground within reach in height where the body fits. */
    private boolean standable(ServerLevel world, BlockPos pos, double rise) {
        if (pos == null || Math.abs(pos.getY() - mob.getY()) > rise) return false;
        var box = SpawnRules.bounds(mob.species(), pos);
        return SpawnRules.loaded(world, box.inflate(1)) && world.getWorldBorder().isWithinBounds(box) && world.noCollision(mob, box, true);
    }
    /** Legs of an escape in the order they are tried: distance, and the turn away from the straight line in degrees. */
    private static final double[][] ESCAPE_LEGS = {{20, 0}, {20, 0}, {12, 0}, {12, 55}, {12, -55}, {7, 30}, {7, -30}, {7, 100}};
    /**
     * Runs from a threat: straight away first, then shorter legs and wider bearings. On a slope the far point is
     * often much higher or lower than the animal, and a nearer or sideways one still opens the distance. A leg
     * counts only when its path really leads away from where the animal stands.
     */
    private boolean escape(ServerLevel world, Vec3 heading) {
        Vec3 far = mob.position().add(heading.scale(20));
        if (!canNavigate()) return routed("airborne_deferred", far, true);
        int side = mob.getRandom().nextBoolean() ? 1 : -1;
        for (double[] leg : ESCAPE_LEGS) {
            Vec3 center = mob.position().add(heading.yRot((float) Math.toRadians(leg[1] * side)).scale(leg[0]));
            int spread = (int) Math.max(2, leg[0] * 0.4);
            var pos = SpawnRules.surface(world, Mth.floor(center.x) + mob.getRandom().nextInt(spread * 2 + 1) - spread,
                    Mth.floor(center.z) + mob.getRandom().nextInt(spread * 2 + 1) - spread);
            if (!standable(world, pos, 6 + leg[0] * 0.5)) continue;
            if (move(world, Vec3.atBottomCenterOf(pos), chaseModifier(), false, 3)) return true;
        }
        return routed("no_destination", far, false);
    }
    private boolean move(ServerLevel world, Vec3 point, double speed) {
        return move(world, point, speed, false);
    }
    private boolean move(ServerLevel world, Vec3 point, double speed, boolean requireReach) {
        return move(world, point, speed, requireReach, 0);
    }
    private boolean canNavigate() { return mob.onGround() || mob.isInLiquid(); }
    private boolean travelling() { return approach != null || !mob.getNavigation().isDone(); }
    private ServerLevel world() { return (ServerLevel) mob.level(); }
    private boolean arrived(Vec3 point) {
        var delta = point.subtract(mob.position());
        return delta.horizontalDistanceSqr() <= 0.75 * 0.75 && Math.abs(delta.y) < 1;
    }
    private boolean clearApproach(ServerLevel world, Vec3 point) {
        var delta = point.subtract(mob.position());
        if (delta.horizontalDistanceSqr() > Math.pow(mob.getBbWidth() + 3, 2) || Math.abs(delta.y) > 0.5) return false;
        var swept = mob.getBoundingBox().expandTowards(new Vec3(delta.x, 0, delta.z)).deflate(0.01);
        return SpawnRules.loaded(world, swept.inflate(1)) && world.getWorldBorder().isWithinBounds(swept)
                && (mob.tramplesTrees() ? clearButTrees(world, swept) : world.noBlockCollision(mob, swept, true));
    }
    /** As a block collision test, with the trunks and foliage a trampling body will knock down left out. */
    private boolean clearButTrees(ServerLevel world, net.minecraft.world.phys.AABB swept) {
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(swept.minX), Mth.floor(swept.minY), Mth.floor(swept.minZ),
                Mth.floor(swept.maxX), Mth.floor(swept.maxY), Mth.floor(swept.maxZ))) {
            var state = world.getBlockState(pos);
            if (state.isAir() || TreeTrample.untended(state)
                    || state.is(net.minecraft.tags.BlockTags.LOGS) && TreeTrample.naturalTrunk(TreeTrample.loaded(world), pos)) continue;
            var shape = state.getCollisionShape(world, pos);
            if (!shape.isEmpty() && shape.bounds().move(pos).intersects(swept)) return false;
        }
        return true;
    }
    /** A leg must end at least {@code minTravel} blocks from the body to count; zero accepts any path. */
    private boolean move(ServerLevel world, Vec3 point, double speed, boolean requireReach, double minTravel) {
        if (arrived(point)) {
            mob.getNavigation().stop(); destination = null; approach = null; failedPaths = 0; lastPathFailure = null;
            return routed("arrived", point, true);
        }
        // Hops and downhill steps are temporary, not refusals. Keep sensing and reacting while in the air.
        if (!canNavigate()) return routed("airborne_deferred", point, true);
        // Reuse a successful path until the goal moves appreciably. Retry failure at most twice/sec.
        if (destination != null && destination.distanceToSqr(point) < 4 && !mob.getNavigation().isDone()) return routed("reused", point, true);
        if (tier == BehaviorTier.FULL && mob.getNavigation().isDone() && clearApproach(world, point)) {
            approach = point; approachSpeed = speed; destination = point; failedPaths = 0; lastPathFailure = null;
            mob.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed);
            return routed("approach", point, true);
        }
        if (lastPathFailure != null && lastPathFailure.distanceToSqr(point) < 1 && world.getGameTime() < retryPathAt)
            return routed("retry_deferred", point, false);
        if (!SpawnRules.loaded(world, new net.minecraft.world.phys.AABB(mob.position(), point).inflate(mob.getBbWidth() + 1)))
            return routed("route_unloaded", point, false);
        double range = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (!LandWildlife.navigationLoaded(world, mob, range)) return routed("surroundings_unloaded", point, false);
        if (!LandWildlife.allowPath(world, false)) return routed("budget_deferred", point, true); // Deferred work is not a failed route.
        var routePoint = point;
        var delta = point.subtract(mob.position());
        if (delta.horizontalDistance() > 20) {
            var step = mob.position().add(delta.normalize().scale(18));
            var ground = SpawnRules.surface(world, (int)Math.floor(step.x), (int)Math.floor(step.z));
            if (ground == null) return routed("no_ground", point, false);
            routePoint = Vec3.atBottomCenterOf(ground);
        }
        destination = point;
        // A finished path equal to a new one is refused by vanilla. Drop it before creating the next leg.
        if (mob.getNavigation().isDone()) mob.getNavigation().stop();
        var path = mob.getNavigation().createPath(BlockPos.containing(routePoint), 0);
        boolean leads = path != null && (minTravel <= 0 || path.getNodeCount() > 0 && path.getEntityPosAtNode(mob, path.getNodeCount() - 1)
                .subtract(mob.position()).horizontalDistanceSqr() >= minTravel * minTravel);
        boolean found = leads && (!requireReach || path.canReach()) && mob.getNavigation().moveTo(path, speed);
        if (!found && lastFailureTick != world.getGameTime()) { failedPaths++; lastFailureTick = world.getGameTime(); }
        // Report before resetting the counter, so the sixth failed decision is visible.
        routed(found ? "path" : "no_path", point, found);
        if (found) { failedPaths = 0; lastPathFailure = null; }
        else {
            lastPathFailure = point; retryPathAt = world.getGameTime() + 20;
            mob.getNavigation().stop(); destination = null;
            if (failedPaths >= 6) {
                boolean cornered = mind().state() == BehaviorState.FLEE;
                if (cornered) corneredTicks = 100;
                else if (mind().state().combat() || mind().state() == BehaviorState.INVESTIGATE) mind().abandonChase();
                // Ordinary roam, bank and home failures must never re-arm chase recovery.
                failedPaths = 0; destination = null;
                routed(cornered ? "cornered" : mind().state().combat() || mind().state() == BehaviorState.INVESTIGATE
                        ? "abandoned" : "route_rejected", point, false);
            }
        }
        return found;
    }
    /** Tells the session recorder how a path request ended; returns the result unchanged. */
    private boolean routed(String outcome, Vec3 point, boolean result) {
        lastRoute = outcome;
        if (SessionRecorder.on()) SessionRecorder.navigation(mob, trace, outcome, point, failedPaths);
        return result;
    }
    private boolean nearbyWater(ServerLevel world) {
        return canNavigate() && waterAt(world, mob.position());
    }
    /** The same physical drinking reach is used to recognize water and to choose a bank. */
    private boolean waterAt(ServerLevel world, Vec3 point) {
        BlockPos center = BlockPos.containing(point);
        int radius = Math.min(16, 2 + (int) Math.ceil(mob.getBbWidth() / 2));
        Vec3 mouth = point.add(0, 0.8, 0);
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) for (int y = -2; y <= 0; y++) {
            var pos = center.offset(x, y, z);
            if (world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                    || !world.getFluidState(pos).is(FluidTags.WATER)) continue;
            var water = Vec3.atCenterOf(pos).add(0, 0.4, 0);
            var rayBox = new net.minecraft.world.phys.AABB(mouth, water).inflate(1);
            if (SpawnRules.loaded(world, rayBox) && world.clip(new net.minecraft.world.level.ClipContext(mouth, water,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob))
                    .getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
        }
        return false;
    }
    private static java.util.List<BlockPos> waterOffsets() {
        var offsets = new java.util.ArrayList<BlockPos>();
        for (int x = -32; x <= 32; x++) for (int z = -32; z <= 32; z++)
            if (x * x + z * z <= 32 * 32) offsets.add(new BlockPos(x, 0, z));
        offsets.sort(Comparator.comparingInt(p -> p.getX() * p.getX() + p.getZ() * p.getZ()));
        return java.util.List.copyOf(offsets);
    }
    /** Between two sweeps for water a thirsty animal walks on: a new place to look from, not a stand in the old one. */
    private void moveOn(ServerLevel world) {
        if (travelling()) return;
        double leash = LandWildlife.roam(mob.species()) * 0.8;
        boolean homeward = LandWildlife.distanceSqr(mob.blockPosition(), home()) > leash * leash;
        plan = homeward ? CalmRoutine.homeward(mob.behaviorProfile(), routineRandom()) : new CalmRoutine.Plan(CalmRoutine.Step.WALK, 300, 0,
                (12 + routineRandom().nextInt(9)) * CalmRoutine.stride(mob.behaviorProfile()), 1);
        lastStep = plan.step();
        planTicks = plan.ticks();
        if (!stroll(world, plan, homeward)) plan = null;
    }
    private void seekWater(ServerLevel world) {
        if (waterDestination != null) {
            if (waterAt(world, waterDestination) && toBank(world, waterDestination)) return;
            waterDestination = null;
        }
        boolean searching = waterSearchOrigin != null && waterSearchIndex < WATER_OFFSETS.size();
        if (!searching && world.getGameTime() < nextWaterSearch) { moveOn(world); return; }
        if (!searching || LandWildlife.distanceSqr(mob.blockPosition(), waterSearchOrigin) > 64) {
            waterSearchOrigin = mob.blockPosition(); waterSearchIndex = 0; waterAttempts = 0;
            mob.getNavigation().stop(); destination = null; approach = null;
        }
        int attempts = 0;
        // Nearest banks first, with exhaustive coverage and bounded work on each half-second pass: the animal
        // stands while it looks, so the whole sweep takes about three seconds, not twelve.
        for (int i = 0; i < 512 && waterSearchIndex < WATER_OFFSETS.size(); i++) {
            var offset = WATER_OFFSETS.get(waterSearchIndex++);
            var water = dev.nez.arksurvivalreturns.feature.aquatic.Water.surfaceWater(world,
                    waterSearchOrigin.getX() + offset.getX(), waterSearchOrigin.getZ() + offset.getZ());
            if (water == null) continue;
            // A valley floor twenty blocks off is easily ten blocks down; whether the way there exists is the path's call.
            double rise = 6 + 0.5 * Math.hypot(water.getX() + 0.5 - mob.getX(), water.getZ() + 0.5 - mob.getZ());
            if (Math.abs(water.getY() + 1 - mob.getY()) > rise) continue;
            int reach = Math.min(16, 2 + (int) Math.ceil(mob.getBbWidth() / 2));
            banks:
            for (int edge = 1; edge <= reach; edge++) for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var column = water.relative(direction, edge);
                var pos = SpawnRules.surface(world, column.getX(), column.getZ());
                if (pos == null || Math.abs(pos.getY() - mob.getY()) > rise) continue;
                var box = SpawnRules.bounds(mob.species(), pos);
                if (!SpawnRules.loaded(world, box.inflate(reach + 1)) || !world.getWorldBorder().isWithinBounds(box)
                        || !world.noCollision(mob, box, true)) continue;
                Vec3 bank = Vec3.atBottomCenterOf(pos);
                if (!waterAt(world, bank)) continue;
                if (toBank(world, bank)) { waterDestination = bank; drySweeps = 0; return; }
                // A path search is the costly part: one bank per water column, a few per pass, and a sweep that
                // has failed sixteen times is over, so a pond nobody can reach does not take the path budget.
                attempts++;
                if (++waterAttempts >= 16) waterSearchIndex = WATER_OFFSETS.size();
                break banks;
            }
            if (attempts >= 3) break;
        }
        // Nothing usable from here: roam and sweep again from the next place. After three empty sweeps the range is
        // dry: the animal makes do, and looks again when thirst has built up.
        if (waterSearchIndex >= WATER_OFFSETS.size()) {
            nextWaterSearch = world.getGameTime() + 200;
            if (++drySweeps >= 3) { drySweeps = 0; mind().makeDo(); }
            moveOn(world);
        }
    }
    /**
     * Sets off for a bank. A bank close by must be reachable, so walled-in water is given up; a farther one only
     * has to bring the animal at least three blocks nearer, and is judged again from there.
     */
    private boolean toBank(ServerLevel world, Vec3 bank) {
        boolean close = bank.subtract(mob.position()).horizontalDistance() <= 8;
        return move(world, bank, mob.wanderModifier(), close, close ? 0 : 3);
    }
    @Override public void record(Row row) {
        SessionRecorder.mind(row, mind);
        if (home != null) row.block("home", home.getX(), home.getY(), home.getZ());
        if (transientHome != null) row.block("herd_home", transientHome.getX(), transientHome.getY(), transientHome.getZ());
        if (focus != null) row.put("focus", SessionRecorder.sid(focus));
        if (lastKnown != null) row.xyz("known", lastKnown.x, lastKnown.y, lastKnown.z);
        if (destination != null) row.xyz("dest", destination.x, destination.y, destination.z);
        if (waterDestination != null) row.xyz("water_dest", waterDestination.x, waterDestination.y, waterDestination.z);
        if (waterSearchOrigin != null) row.put("water_scan", waterSearchIndex);
        if (approach != null) row.xyz("approach", approach.x, approach.y, approach.z);
        row.flag("trample", mob.tramplesTrees());
        if (herdThreat != null) row.put("herd_threat", SessionRecorder.sid(herdThreat));
        if (failedPaths > 0) row.put("fail", failedPaths);
        if (failedEscapes > 0) row.put("fail_escape", failedEscapes);
        if (corneredTicks > 0) row.put("cornered_t", corneredTicks);
        if (alarmTicks > 0) row.put("alarm_t", alarmTicks);
        if (pendingAlarm != null) row.put("alarm_in", pendingAlarmTicks);
        row.flag("regroup", regrouping);
        if (nextRoutine > mob.tickCount) row.put("routine_in", nextRoutine - mob.tickCount);
        if (choreo != null) {
            row.put("mode", choreo.mode()).flag("hold", choreo.holding());
            if (choreo.remaining() > 0) row.put("beat", choreo.remaining());
            int queued = choreo.queued().size();
            if (queued > 0) row.put("beats", queued);
        }
        // The bout of the calm routine, near or far; the session tools know it by its first name.
        if (plan != null) row.put("amb", plan.step()).put("amb_t", planTicks);
    }
    @Override public void save(ValueOutput out) {
        // The permanent anchor is saved; the prey-herd anchor is transient by design.
        if (home == null) home = mob.blockPosition();
        var p = home;
        out.putInt("WildHomeX", p.getX()); out.putInt("WildHomeY", p.getY()); out.putInt("WildHomeZ", p.getZ());
        if (preyHerd != null) out.putString("WildPreyHerd", preyHerd.toString());
        out.putDouble("WildHunger", mind().hunger()); out.putDouble("WildThirst", mind().thirst()); out.putDouble("WildFatigue", mind().fatigue());
        out.putInt("WildSleepCalm", mind().calmTicksRemaining());
    }
    @Override public void load(ValueInput in) {
        home = new BlockPos(in.getIntOr("WildHomeX", mob.blockPosition().getX()), in.getIntOr("WildHomeY", mob.blockPosition().getY()), in.getIntOr("WildHomeZ", mob.blockPosition().getZ()));
        transientHome = null;
        mind = null; // Reset transient pursuit and sleep state on reload.
        choreo = null;
        mind().restoreNeeds(in.getDoubleOr("WildHunger", 0.55), in.getDoubleOr("WildThirst", 0.35), in.getDoubleOr("WildFatigue", 0.15));
        mind().restoreCalm(in.getIntOr("WildSleepCalm", 0));
        focus = null; lastKnown = null; destination = null; regrouping = false; packCache = null; pendingAlarm = null; facing = null;
        approach = null; lastPathFailure = null; waterDestination = null; waterSearchOrigin = null; waterSearchIndex = 0;
        retryPathAt = 0; nextWaterSearch = 0; failedPaths = 0; lastFailureTick = Long.MIN_VALUE; waterAttempts = 0; drySweeps = 0;
        herdThreat = null; herdThreatTicks = 0; provoker = null; watched = null; watchTicks = 0;
        plan = null; lastStep = null; tier = BehaviorTier.FULL;
        try { preyHerd = java.util.UUID.fromString(in.getStringOr("WildPreyHerd", "")); }
        catch (IllegalArgumentException ignored) { preyHerd = null; }
    }
}
