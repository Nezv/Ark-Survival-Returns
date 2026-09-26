package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.SplittableRandom;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.land.*;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
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
 * AMBIENT: {@link AmbientRoutine}, a cheap visible routine with no senses and frozen needs. DORMANT: only the
 * sleep pose follows the schedule, on a slow timer.
 */
public final class WildlifeGoal extends WildlifeController {
    private WildlifeMind mind;
    private Choreographer choreo;
    private BlockPos home, transientHome;
    private Vec3 lastKnown, destination, waterDestination, pendingAlarm, facing;
    private LivingEntity focus;
    private LivingEntity herdThreat;
    private int herdThreatTicks, pendingAlarmTicks, alarmEvents;
    private java.util.UUID preyHerd;
    private int damageStamp = -1, failedPaths, nextRoutine, alarmTicks;
    private long nextAlarm, nextWaterSearch;
    private int corneredTicks;
    private int failedEscapes;
    private boolean interrupted, regrouping;
    private java.util.List<CreatureEntity> packCache;
    private BehaviorTier tier = BehaviorTier.FULL;
    private AmbientRoutine.Plan ambient;
    private int ambientTicks;
    private float ambientYaw;
    private SplittableRandom ambientRandom;
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
                // Facing a stimulus turns the whole body in place, at the creature's own rate.
                if (facing != null && choreo().action().motion() == BehaviorAction.Motion.FACE && mob.getNavigation().isDone())
                    turnToward(yawTo(facing), false);
            }
            case AMBIENT -> ambientTick();
            case DORMANT -> dormantTick();
        }
    }
    @Override public void stop() { mob.getNavigation().stop(); mob.setTarget(null); packCache = null; }
    /** An alarm from a pack mate reaches this animal after its own delay, rippling outward from the caller. */
    @Override public void receiveAlarm(Vec3 position) {
        int delay = Desync.reactionDelay(mob.getUUID().getLeastSignificantBits(), ++alarmEvents,
                mob.position().distanceTo(position), mob.species().timid());
        if (pendingAlarm == null || delay < pendingAlarmTicks) { pendingAlarm = position; pendingAlarmTicks = delay; }
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
        if (!mob.species().defensiveHerd() || !WildlifeSenses.validTarget(threat)) return;
        herdThreat = threat; herdThreatTicks = 100; focus = threat; lastKnown = threat.position(); mind().defendHerd();
    }

    // ----------------------------------------------------------------------------------- tiers

    private void changeTier(BehaviorTier next) {
        if (next == BehaviorTier.FULL) {
            // Resume the full routine from what the cheaper routine was showing, without replaying a bridge.
            var shown = mob.behavior();
            if (shown == BehaviorState.SLEEP || shown == BehaviorState.ROAM || shown == BehaviorState.FORAGE) mind().resumeAs(shown);
            choreo().reset(mind().state());
            mob.setAction(choreo().action());
        } else {
            // Leaving full detail drops pursuit and bridges; needs stay frozen until the creature is near again.
            mob.setTarget(null); focus = null; lastKnown = null; pendingAlarm = null; destination = null; facing = null;
            mob.getNavigation().stop();
            choreo().reset(mind().state().sleeping() ? mind().state() : BehaviorState.ROAM);
        }
        ambient = null;
        ambientTicks = 0;
        tier = next;
    }

    private DailySchedule.Phase schedule(ServerLevel world) {
        if (!WildlifeSenses.hasNightCycle(mob)) return DailySchedule.Phase.ROAM;
        return DailySchedule.phase(world.getDefaultClockTime(), mob.getUUID().getLeastSignificantBits(), mob.species().predator,
                Config.NIGHT_START.get(), Config.NIGHT_END.get(), Config.NIGHT_TRANSITION.get(), Config.DAY_SLEEP.get());
    }

    /** Tier 2: walk, turn, stop, look, sniff, graze, poop and sleep on schedule; one decision every few seconds. */
    private void ambientTick() {
        if (!(mob.level() instanceof ServerLevel world)) return;
        if (ambientRandom == null) ambientRandom = new SplittableRandom(mob.getUUID().getMostSignificantBits());
        if (ambient == null || --ambientTicks <= 0) { nextAmbient(world); return; }
        switch (ambient.step()) {
            case TURN -> { if (turnToward(ambientYaw, false)) ambientTicks = Math.min(ambientTicks, 10); }
            case WALK -> { if (mob.getNavigation().isDone()) ambientTicks = Math.min(ambientTicks, 1); }
            default -> {}
        }
    }

    private void nextAmbient(ServerLevel world) {
        var phase = schedule(world);
        double leash = LandWildlife.roam(mob.species()) * 0.8;
        boolean homeward = LandWildlife.distanceSqr(mob.blockPosition(), home()) > leash * leash;
        ambient = AmbientRoutine.next(phase, mob.behaviorProfile(), ambientRandom, homeward);
        ambientTicks = Math.max(1, ambient.ticks());
        mob.getNavigation().stop();
        switch (ambient.step()) {
            case WALK -> {
                Vec3 target;
                if (homeward) target = Vec3.atBottomCenterOf(home());
                else {
                    float heading = mob.getYRot() + (float) (ambientRandom.nextDouble() * 120 - 60);
                    target = mob.position().add(Vec3.directionFromRotation(0, heading).scale(ambient.distance()));
                }
                var ground = SpawnRules.surface(world, Mth.floor(target.x), Mth.floor(target.z));
                if (ground == null || Math.abs(ground.getY() - mob.getY()) > 4
                        || !move(world, Vec3.atBottomCenterOf(ground), mob.wanderModifier() * 0.85)) {
                    ambient = new AmbientRoutine.Plan(AmbientRoutine.Step.STAND, 60, 0, 0);
                    ambientTicks = 60;
                }
            }
            case TURN -> { ambientYaw = mob.getYRot() + (float) ambient.turn(); ambientTicks = 200; }
            case LOOK -> mob.playCue(BehaviorAction.Cue.LOOK);
            case SNIFF -> mob.playCue(BehaviorAction.Cue.SNIFF);
            case POOP -> mob.playCue(BehaviorAction.Cue.POOP);
            default -> {}
        }
        mob.setBehavior(AmbientRoutine.state(ambient.step()));
        mob.setAction(AmbientRoutine.action(ambient.step()));
        mob.setNightActive(phase == DailySchedule.Phase.HUNT);
    }

    /** Tier 3: no routine; within the outer radius the pose still follows the sleep schedule. */
    private void dormantTick() {
        if (Math.floorMod(mob.tickCount + mob.getId(), 100) != 0) return;
        if (!mob.getNavigation().isDone()) mob.getNavigation().stop();
        if (!(mob.level() instanceof ServerLevel world) || !mob.posedWhenDormant()) return;
        boolean asleep = schedule(world) == DailySchedule.Phase.SLEEP;
        mob.setBehavior(asleep ? BehaviorState.SLEEP : BehaviorState.ROAM);
        mob.setAction(asleep ? BehaviorAction.SLEEP : BehaviorAction.IDLE);
    }

    /** Rotates the facing toward a yaw at the creature's turn rate; true once aligned. */
    private boolean turnToward(float yaw, boolean running) {
        float turned = Mth.approachDegrees(mob.getYRot(), yaw, mob.turnRate(running, false));
        mob.setYRot(turned);
        return Math.abs(Mth.wrapDegrees(yaw - turned)) < 2f;
    }

    private float yawTo(Vec3 point) {
        return (float) (Mth.atan2(point.z - mob.getZ(), point.x - mob.getX()) * (180.0 / Math.PI)) - 90.0f;
    }

    // ------------------------------------------------------------------------------ full tier

    @Override public void think() {
        if (!(mob.level() instanceof ServerLevel world) || !mob.species().landHabitat()) return;
        var brain = mind();
        var dance = choreo();
        dance.advance(10, !mob.getNavigation().isDone());
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
            lastKnown = pendingAlarm; alarmTicks = 40; pendingAlarm = null;
        }
        boolean peaceful = world.getDifficulty() == Difficulty.PEACEFUL;
        var attacker = mob.getLastHurtByMob();
        boolean attacked = attacker != null && mob.getLastHurtByMobTimestamp() != damageStamp
                && WildlifeSenses.validTarget(attacker) && !(peaceful && attacker instanceof Player);
        if (attacked) { damageStamp = mob.getLastHurtByMobTimestamp(); focus = attacker; lastKnown = attacker.position(); }
        if (attacked && mob.species().herd()) {
            for (var member : packWithin(48))
                if (member.species() == mob.species()) {
                    if (mob.species().timid()) member.wildlife().receiveAlarm(attacker.position());
                    else member.wildlife().receiveHerdThreat(attacker);
                }
        }
        var candidates = world.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(Math.max(48, WildlifeSenses.sightRange(mob))),
                c -> c != mob && WildlifeSenses.validTarget(c) && !(peaceful && c instanceof Player)
                        && (c == attacker || c instanceof Player || c instanceof CreatureEntity || (mob.species().predator && c instanceof Animal)))
                .stream().filter(c -> !cycle || (relevant(c) || c == attacker || c == herdThreat)
                        && routineAllows(c, quietRoutine, attacked ? attacker : null))
                .sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(24).toList();
        WildlifeSenses.Detection detection = new WildlifeSenses.Detection(false, 0);
        LivingEntity sensed = null;
        double best = 0;
        for (var candidate : candidates) {
            if (!relevant(candidate) && candidate != attacker && candidate != herdThreat) continue;
            var check = WildlifeSenses.detect(mob, candidate);
            double score = check.strength() / (1 + mob.distanceTo(candidate) / 32.0) + (candidate == focus && check.strength() > 0 ? 0.15 : 0);
            if (score > best) { best = score; sensed = candidate; detection = check; }
        }
        if (attacked) { sensed = attacker; detection = WildlifeSenses.detect(mob, attacker); }
        else if (herdThreat != null && WildlifeSenses.validTarget(herdThreat)) {
            var threatSense = WildlifeSenses.detect(mob, herdThreat);
            if (threatSense.strength() > 0) { sensed = herdThreat; detection = threatSense; }
        }
        if (sensed != null) {
            focus = sensed;
            // Non-visual stimuli carry an approximate point, not a continuously tracked hidden actor.
            lastKnown = detection.visible() ? sensed.position() : sensed.position().add(3, 0, -3);
        }
        if (focus != null && !WildlifeSenses.validTarget(focus)) { focus = null; mob.setTarget(null); }
        double signal = Math.max(detection.strength(), alarmTicks > 0 ? 0.65 : 0);
        if (!peaceful && sensed == null) {
            var noise = WildlifeNoise.hear(mob);
            if (noise != null) { signal = Math.max(signal, 0.65); lastKnown = noise.position(); }
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
        boolean intimidating = visible && sensed instanceof CreatureEntity c && c.species().predator
                && c.getBbWidth() > mob.getBbWidth() * 1.4;
        if (mob.species().herd() && sensed instanceof CreatureEntity) intimidating = false;
        if (visible && dev.nez.arksurvivalreturns.feature.accessory.AccessoryEffects.tyrant(mob, sensed)) intimidating = true;
        // Night herbivores first escape, then evaluate their ability to stand with the herd.
        if (cycle && night && !mob.species().predator) intimidating = false;
        if (mob.species().predator && guardedPrey && (mob.distanceTo(sensed) < mob.getBbWidth() + 8
                || mob.getHealth() < mob.getMaxHealth() * 0.45)) intimidating = true;
        boolean far = LandWildlife.distanceSqr(mob.blockPosition(), home()) > territoryRadius() * territoryRadius();
        BehaviorState before = brain.state();
        // Terrain probes are only useful when the mind can act on them; other states skip the block sweep.
        boolean needsWater = brain.thirst() >= 0.6 || before == BehaviorState.DRINK;
        boolean needsForage = !mob.species().predator
                && (brain.hunger() >= 0.4 || before == BehaviorState.FORAGE);
        boolean water = needsWater && nearbyWater(world);
        boolean forage = needsForage && LandWildlife.forage(world, mob.species(), mob.blockPosition());
        boolean huntable = visible && prey(sensed) && (!guardedPrey || sensed.getHealth() < sensed.getMaxHealth() * 0.35);
        boolean danger = attacked || herdThreat != null || alarmTicks > 0
                || cycle && night && !mob.species().predator && intruding
                || visible && sensed instanceof net.minecraft.world.entity.Mob enemy && enemy.getTarget() == mob
                || visible && sensed instanceof CreatureEntity c && c.species().predator && c.getBbWidth() * 1.3 >= mob.getBbWidth();
        boolean defended = cycle && night && mob.species().defensiveHerd() && danger
                && 1 + packWithin(32).stream().filter(c -> c.getHealth() >= c.getMaxHealth() * 0.5).count() >= 2;
        boolean safeSleep = mob.onGround() && !mob.isInWater() && !mob.isInLava() && !mob.isOnFire() && !interrupted;
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
                danger, defended,
                corneredTicks > 0 || attacked && (mob.species() == Species.THERIZINOSAURUS || mob.species() == Species.TITANOSAUR),
                Config.SLEEP_CALM.get(), Config.NIGHT_HUNGER.get(), regroupDestination != null) : WildlifeMind.Routine.LEGACY;
        var state = brain.step(new WildlifeMind.Observation(signal, visible, huntable, intruding, attacked,
                intimidating, far, water, forage, !world.isBrightOutside(), mob.getHealth() / mob.getMaxHealth()), 10, routine, null);
        interrupted = false;
        if (!brain.remembers()) { lastKnown = null; focus = null; }
        mob.setBehavior(state);
        if (state != before) {
            mob.getNavigation().stop(); destination = null; nextRoutine = 0;
            // Reflexes skip the display: a hit, or a threat already at the body.
            boolean urgent = attacked || sensed != null && WildlifeSenses.bodyDistance(mob, sensed) < 3;
            dance.enter(before, state, urgent);
            if (state.alarm() && lastKnown != null && world.getGameTime() >= nextAlarm && (!cycle || visible || attacked)) {
                nextAlarm = world.getGameTime() + 100;
                for (var member : packWithin(32)) member.wildlife().receiveAlarm(lastKnown);
            }
        }
        mob.setTarget(state.combat() && visible ? sensed : null);
        facing = lastKnown != null && state.alarm() ? lastKnown : null;
        if (facing != null) mob.getLookControl().setLookAt(facing.x, facing.y + 1, facing.z, 20, 20);
        boolean holding = dance.holding();
        if (state.combat() && visible && sensed != null) {
            if (mob.isWithinMeleeAttackRange(sensed)) {
                mob.getNavigation().stop();
                // The strike schedules its damage on the clip's hit frame; onStrikeKill feeds the mind.
                mob.strike(sensed);
            } else if (holding) mob.getNavigation().stop();
            else move(world, sensed.position(), dance.action() == BehaviorAction.STALK ? mob.wanderModifier() * 0.6 : chaseModifier());
        } else if (holding) mob.getNavigation().stop();
        else switch (state) {
            case INVESTIGATE -> { if (lastKnown != null) move(world, lastKnown, mob.wanderModifier()); }
            case FLEE -> {
                if (mob.tickCount >= nextRoutine || !cycle && mob.getNavigation().isDone()) {
                    nextRoutine = mob.tickCount + 40 + (cycle ? Math.floorMod(mob.getId(), 10) : 0);
                    Vec3 away = lastKnown == null ? mob.position().subtract(Vec3.atBottomCenterOf(home())) : mob.position().subtract(lastKnown);
                    boolean escaped = chooseDestination(world, mob.position().add(scatter(away).scale(20)), 8, chaseModifier());
                    if (cycle) {
                        failedEscapes = escaped ? 0 : failedEscapes + 1;
                        if (failedEscapes >= 3) corneredTicks = 100;
                    }
                }
            }
            case RETURN_HOME -> move(world, Vec3.atBottomCenterOf(home()), mob.wanderModifier());
            case REGROUP -> { if (regroupDestination != null) move(world, regroupDestination, Math.min(1, mob.wanderModifier() * 1.3)); }
            case SEEK_WATER -> seekWater(world);
            case ROAM, SEARCH -> roam(world);
            default -> mob.getNavigation().stop();
        }
        // Cues and the synced action go out last, so a pause beat chosen above starts its clip this tick.
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
    private boolean routineAllows(LivingEntity candidate, boolean quietRoutine, LivingEntity recentAttacker) {
        if (!quietRoutine || candidate == recentAttacker || candidate == herdThreat || mind().state().combat()
                || candidate instanceof net.minecraft.world.entity.Mob enemy && enemy.getTarget() == mob) return true;
        if (candidate instanceof Player) return WildlifeSenses.bodyDistance(mob, candidate) <= Config.WAKE_DISTANCE.get();
        return !mob.species().predator || candidate instanceof CreatureEntity c && c.species().predator;
    }
    private boolean relevant(LivingEntity other) {
        if (other instanceof Player) return true;
        if (other instanceof CreatureEntity c && c.packId().equals(mob.packId())) return false;
        return prey(other) || (other instanceof CreatureEntity c && c.species().predator
                && (mob.species().herd() || c.getBbWidth() > mob.getBbWidth() * 1.4
                    || WildlifeSenses.night(mob) && !mob.species().predator && (c.getBbWidth() * 1.3 >= mob.getBbWidth() || c.getTarget() == mob)));
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
    private void roam(ServerLevel world) {
        if (!mob.species().solitary()) {
            var leader = leader(64);
            if (leader != null && mob.distanceTo(leader) > mob.species().cohesionDistance()) {
                move(world, slot(leader), Math.min(1, mob.wanderModifier() * 1.15));
                return;
            }
        }
        if (mob.tickCount < nextRoutine) {
            // A roaming pause is filled with the idle beats the rig can play.
            if (mob.getNavigation().isDone()) choreo().pause();
            return;
        }
        nextRoutine = mob.tickCount + (mind().state() == BehaviorState.SEARCH ? 60 : 100) + mob.getRandom().nextInt(100);
        chooseDestination(world, Vec3.atBottomCenterOf(home()), mob.species().solitary() ? 36 : 24, mob.wanderModifier());
    }
    private boolean chooseDestination(ServerLevel world, Vec3 center, int radius, double speed) {
        for (int i = 0; i < 8; i++) {
            var pos = SpawnRules.surface(world, (int) Math.floor(center.x) + mob.getRandom().nextInt(radius * 2 + 1) - radius,
                    (int) Math.floor(center.z) + mob.getRandom().nextInt(radius * 2 + 1) - radius);
            if (pos == null || Math.abs(pos.getY() - mob.getY()) > 6) continue;
            var box = SpawnRules.bounds(mob.species(), pos);
            if (!SpawnRules.loaded(world, box.inflate(1)) || !world.getWorldBorder().isWithinBounds(box) || !world.noCollision(mob, box, true)) continue;
            if (move(world, Vec3.atBottomCenterOf(pos), speed)) return true;
        }
        return false;
    }
    private boolean move(ServerLevel world, Vec3 point, double speed) {
        // Reuse a successful path until the goal moves appreciably. Retry failure at most twice/sec.
        if (destination != null && destination.distanceToSqr(point) < 4 && !mob.getNavigation().isDone()) return true;
        if (!SpawnRules.loaded(world, new net.minecraft.world.phys.AABB(mob.position(), point).inflate(mob.getBbWidth() + 1))) return false;
        double range = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (!LandWildlife.navigationLoaded(world, mob, range)) return false;
        if (!LandWildlife.allowPath(world, false)) return true; // Deferred work is not a failed route.
        var routePoint = point;
        var delta = point.subtract(mob.position());
        if (delta.horizontalDistance() > 20) {
            var step = mob.position().add(delta.normalize().scale(18));
            var ground = SpawnRules.surface(world, (int)Math.floor(step.x), (int)Math.floor(step.z));
            if (ground == null) return false;
            routePoint = Vec3.atBottomCenterOf(ground);
        }
        destination = point;
        boolean found = mob.getNavigation().moveTo(routePoint.x, routePoint.y, routePoint.z, speed);
        if (found) failedPaths = 0;
        else if (++failedPaths >= 6) {
            if (WildlifeSenses.hasNightCycle(mob) && mind().state() == BehaviorState.FLEE) corneredTicks = 100;
            else mind().abandonChase();
            failedPaths = 0; destination = null;
        }
        return found;
    }
    private boolean nearbyWater(ServerLevel world) {
        // Local water use only; does not invent or load remote need zones.
        BlockPos center = mob.blockPosition();
        int radius = Math.min(16, 2 + (int) Math.ceil(mob.getBbWidth() / 2));
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            var pos = center.offset(x, -1, z);
            if (world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null && world.getFluidState(pos).is(FluidTags.WATER)) return true;
        }
        return false;
    }
    private void seekWater(ServerLevel world) {
        if (waterDestination != null && move(world, waterDestination, mob.wanderModifier())) return;
        if (world.getGameTime() < nextWaterSearch) { roam(world); return; }
        nextWaterSearch = world.getGameTime() + 200;
        waterDestination = null;
        for (int i = 0; i < 32; i++) {
            int x = mob.getBlockX() + mob.getRandom().nextInt(65) - 32, z = mob.getBlockZ() + mob.getRandom().nextInt(65) - 32;
            var pos = SpawnRules.surface(world, x, z);
            if (pos == null || Math.abs(pos.getY() - mob.getY()) > 6) continue;
            var box = SpawnRules.bounds(mob.species(), pos);
            if (!SpawnRules.loaded(world, box.inflate(2)) || !world.getWorldBorder().isWithinBounds(box) || !world.noCollision(mob, box, true)) continue;
            int edge = 1 + (int) Math.ceil(mob.getBbWidth() / 2);
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var water = pos.relative(direction, edge).below();
                if (!world.getFluidState(water).is(FluidTags.WATER)) continue;
                waterDestination = Vec3.atBottomCenterOf(pos);
                if (move(world, waterDestination, mob.wanderModifier())) return;
            }
        }
        roam(world);
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
        herdThreat = null; herdThreatTicks = 0;
        ambient = null; tier = BehaviorTier.FULL;
        try { preyHerd = java.util.UUID.fromString(in.getStringOr("WildPreyHerd", "")); }
        catch (IllegalArgumentException ignored) { preyHerd = null; }
    }
}
