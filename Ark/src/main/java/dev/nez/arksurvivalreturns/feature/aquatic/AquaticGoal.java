package dev.nez.arksurvivalreturns.feature.aquatic;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeController;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeMind;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.land.LandHabitatData;
import dev.nez.arksurvivalreturns.feature.land.LandHabitats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Water adapter over the shared decision model.
 *
 * Perception, warnings, hunting, fleeing, satiation and the saved home are the same contract the
 * ground adapter uses, so a shark reports status and routines like every other creature. Only the
 * movement and the destination rules differ: the body steers inside one saved pool and never
 * leaves the water column on its own.
 */
public final class AquaticGoal extends WildlifeController {
    private static final double SWIM_SPEED = 0.26;
    private WildlifeMind mind;
    private LandHabitatData.Habitat habitat;
    private BlockPos home;
    private Vec3 lastKnown, destination;
    private LivingEntity focus, herdThreat;
    private UUID preyHerd;
    private int herdThreatTicks, alarmTicks, corneredTicks, failedPaths, damageStamp = -1;
    private long nextRoutine, nextAttack, nextAlarm;

    public AquaticGoal(CreatureEntity mob) { super(mob); setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

    @Override public WildlifeMind mind() {
        if (mind == null) {
            mind = new WildlifeMind(mob.species().predator, mob.species().timid(), true);
            long variation = mob.getUUID().getLeastSignificantBits();
            mind.restoreNeeds(0.4 + (variation & 255) / 1024.0, 0.15 + ((variation >>> 8) & 255) / 512.0,
                    0.05 + ((variation >>> 16) & 255) / 1024.0);
        }
        return mind;
    }
    @Override public BlockPos home() {
        if (habitat != null) return habitat.center;
        if (home == null) home = mob.blockPosition();
        return home;
    }
    @Override public boolean canUse() { return mob.isAlive() && mob.species().aquatic(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        if (Math.floorMod(mob.tickCount + mob.getId(), 10) == 0) think();
        if (mob.level() instanceof ServerLevel world) keepSubmerged(world);
    }
    @Override public void stop() { mob.setDeltaMovement(Vec3.ZERO); mob.setTarget(null); }
    @Override public void receiveAlarm(Vec3 position) { lastKnown = position; alarmTicks = 40; }
    @Override public void interruptSleep() { mind().interruptSleep(Config.SLEEP_CALM.get()); }
    @Override public void followPreyHerd(UUID herd) { preyHerd = herd; }
    @Override public UUID preyHerd() { return preyHerd; }
    @Override public void receiveHerdThreat(LivingEntity threat) {
        if (!WildlifeSenses.validTarget(threat) || mob.species().maxGroup < 2) return;
        herdThreat = threat; herdThreatTicks = 100; focus = threat; lastKnown = threat.position(); mind().defendHerd();
    }

    @Override public void think() {
        if (!(mob.level() instanceof ServerLevel world) || !mob.species().aquatic()) return;
        var brain = mind();
        habitat = AquaticHabitats.think(mob);
        if (habitat != null) home = habitat.center;
        boolean cycle = WildlifeSenses.hasNightCycle(mob);
        boolean night = cycle && (habitat == null ? WildlifeSenses.night(mob) : LandHabitats.night(world, habitat));
        corneredTicks = Math.max(0, corneredTicks - 10);
        mob.setNightActive(night && mob.species().predator);
        home();
        if (preyHerd != null && habitat == null) {
            var herd = world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(96),
                    c -> c.isAlive() && c.packId().equals(preyHerd));
            if (!herd.isEmpty()) home = herd.getFirst().blockPosition();
        }
        if ((herdThreatTicks -= 10) <= 0) herdThreat = null;
        boolean peaceful = world.getDifficulty() == Difficulty.PEACEFUL;
        var attacker = mob.getLastHurtByMob();
        boolean attacked = attacker != null && mob.getLastHurtByMobTimestamp() != damageStamp
                && WildlifeSenses.validTarget(attacker) && !(peaceful && attacker instanceof Player);
        if (attacked) {
            damageStamp = mob.getLastHurtByMobTimestamp(); focus = attacker; lastKnown = attacker.position();
            if (mob.species().maxGroup > 1)
                for (var member : world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(48),
                        c -> c != mob && c.isAlive() && c.species() == mob.species() && c.packId().equals(mob.packId())))
                    member.wildlife().receiveAlarm(attacker.position());
        }
        var candidates = world.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(Math.max(32, WildlifeSenses.sightRange(mob))),
                c -> c != mob && WildlifeSenses.validTarget(c) && !(peaceful && c instanceof Player)
                        && (c == attacker || c instanceof Player || c instanceof CreatureEntity
                            || (mob.species().predator && (c instanceof Animal || c instanceof WaterAnimal))))
                .stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(24).toList();
        WildlifeSenses.Detection detection = new WildlifeSenses.Detection(false, 0);
        LivingEntity sensed = null;
        double best = 0;
        for (var candidate : candidates) {
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
            lastKnown = detection.visible() ? sensed.position() : sensed.position().add(2, 0, -2);
        }
        if (focus != null && !WildlifeSenses.validTarget(focus)) { focus = null; mob.setTarget(null); }
        double signal = Math.max(detection.strength(), alarmTicks > 0 ? 0.65 : 0);
        alarmTicks = Math.max(0, alarmTicks - 10);
        boolean visible = sensed != null && detection.visible();
        boolean intruding = visible && sensed instanceof Player && WildlifeSenses.bodyDistance(mob, sensed) < Config.WAKE_DISTANCE.get();
        boolean danger = attacked || herdThreat != null || alarmTicks > 0
                || visible && sensed instanceof net.minecraft.world.entity.Mob enemy && enemy.getTarget() == mob;
        boolean far = Math.hypot(mob.getX() - home().getX(), mob.getZ() - home().getZ())
                > AquaticHabitats.leash(mob.species());
        BehaviorState before = brain.state();
        var routine = new WildlifeMind.Routine(true, night, false, true, danger, false,
                corneredTicks > 0, Config.SLEEP_CALM.get(), Config.NIGHT_HUNGER.get(), false);
        var state = brain.step(new WildlifeMind.Observation(signal, visible, visible && prey(sensed), intruding, attacked,
                false, far, false, false, night, mob.getHealth() / mob.getMaxHealth()), 10, routine,
                habitat == null ? null : new WildlifeMind.GroupRoutine(habitat.needs.hunger(), habitat.routine));
        if (!brain.remembers()) { lastKnown = null; focus = null; }
        mob.setBehavior(state);
        if (state != before) {
            destination = null; nextRoutine = 0;
            if (state.alarm()) {
                mob.behaviorCue(state);
                if (lastKnown != null && world.getGameTime() >= nextAlarm && visible) {
                    nextAlarm = world.getGameTime() + 100;
                    if (mob.species().maxGroup > 1)
                        for (var member : world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(32),
                                c -> c != mob && c.isAlive() && c.packId().equals(mob.packId())))
                            member.wildlife().receiveAlarm(lastKnown);
                }
            }
        }
        mob.setTarget(state.combat() && visible ? sensed : null);
        if (lastKnown != null && state.alarm()) mob.getLookControl().setLookAt(lastKnown.x, lastKnown.y + 1, lastKnown.z, 20, 20);
        if (state.combat() && visible && sensed != null) {
            if (mob.isWithinMeleeAttackRange(sensed)) {
                mob.setDeltaMovement(mob.getDeltaMovement().scale(0.6));
                if (world.getGameTime() >= nextAttack && mob.hasLineOfSight(sensed)) {
                    nextAttack = world.getGameTime() + 20;
                    mob.doHurtTarget(world, sensed);
                    if (!sensed.isAlive() && mob.species().predator && !(sensed instanceof Player)) {
                        if (habitat != null) LandHabitats.feed(mob, sensed);
                        brain.ate(); focus = null; mob.setTarget(null);
                    }
                }
            } else swimTo(world, sensed.position(), 1.0);
        } else switch (state) {
            case INVESTIGATE -> { if (lastKnown != null) swimTo(world, lastKnown, 0.7); }
            case FLEE -> {
                if (mob.tickCount >= nextRoutine) {
                    nextRoutine = mob.tickCount + 40 + Math.floorMod(mob.getId(), 10);
                    Vec3 away = lastKnown == null ? mob.position().subtract(Vec3.atBottomCenterOf(home())) : mob.position().subtract(lastKnown);
                    var point = mob.position().add(away.normalize().scale(16));
                    if (AquaticHabitats.column(world, BlockPos.containing(point)) == null) {
                        failedPaths++;
                        if (failedPaths >= 3) corneredTicks = 100;
                        point = habitat != null ? Vec3.atCenterOf(habitat.water) : point;
                    } else failedPaths = 0;
                    swimTo(world, point, 1.0);
                }
            }
            case RETURN_HOME -> swimTo(world, Vec3.atBottomCenterOf(home()).add(0, -1, 0), 0.8);
            case REST -> mob.setDeltaMovement(mob.getDeltaMovement().scale(0.6));
            default -> roam(world);
        }
    }
    private boolean prey(LivingEntity other) {
        if (other == null || !mob.species().predator) return false;
        if (other instanceof Player) return true;
        if (other instanceof CreatureEntity creature)
            return !creature.species().predator && creature.species() != mob.species()
                    && creature.getBbWidth() <= mob.getBbWidth() * 1.3;
        return (other instanceof Animal || other instanceof WaterAnimal) && other.getBbWidth() <= mob.getBbWidth() * 1.3;
    }
    private void roam(ServerLevel world) {
        if (habitat != null) {
            if (world.getGameTime() < nextRoutine) return;
            nextRoutine = world.getGameTime() + 40 + Math.floorMod(mob.getUUID().hashCode(), 10);
            var point = habitat.destination != null ? habitat.destination : AquaticHabitats.destination(world, habitat, mob);
            if (point != null) swimTo(world, point, mob.species().predator ? 0.6 : 0.5);
            return;
        }
        if (destination == null || mob.position().distanceToSqr(destination) < 4 || mob.tickCount >= nextRoutine) {
            nextRoutine = mob.tickCount + 100 + mob.getRandom().nextInt(100);
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double radius = 6 + mob.getRandom().nextDouble() * 10;
            var point = mob.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            // A loose resident without a saved pool still refuses to plan onto dry land.
            if (AquaticHabitats.column(world, BlockPos.containing(point)) == null) return;
            destination = point;
        }
        swimTo(world, destination, 0.5);
    }
    /** Steer toward a water point, keeping the body inside the local water column. */
    private void swimTo(ServerLevel world, Vec3 point, double speed) {
        var to = point.subtract(mob.position());
        if (to.length() < 1.2) { mob.setDeltaMovement(mob.getDeltaMovement().scale(0.7)); return; }
        var desired = to.normalize().scale(SWIM_SPEED * speed);
        var next = mob.getDeltaMovement().lerp(desired, 0.18);
        next = keepInsideColumn(world, next);
        mob.setDeltaMovement(next);
        if (next.horizontalDistanceSqr() > 0.0001) {
            float yaw = (float) (Math.atan2(next.z, next.x) * 180 / Math.PI) - 90;
            mob.setYRot(Mth.approachDegrees(mob.getYRot(), yaw, 8));
            mob.yBodyRot = mob.getYRot(); mob.yHeadRot = mob.getYRot();
        }
        float pitch = (float) (-Math.atan2(next.y, Math.max(0.001, next.horizontalDistance())) * 180 / Math.PI);
        mob.setXRot(Mth.approachDegrees(mob.getXRot(), Mth.clamp(pitch, -60, 60), 6));
    }
    /** Never swim above the surface or through the floor of the local column. */
    private Vec3 keepInsideColumn(ServerLevel world, Vec3 velocity) {
        var column = AquaticHabitats.column(world, mob.blockPosition());
        if (column == null) return mob.isInWater() ? velocity : velocity.add(0, -0.05, 0);
        if (mob.getY() > column[0] - 1) return velocity.add(0, -0.05, 0);
        if (mob.getY() < column[1]) return velocity.add(0, 0.05, 0);
        return velocity;
    }
    /** Per-tick correction so a resident never drifts out of its column between decisions. */
    private void keepSubmerged(ServerLevel world) {
        if (!mob.isInWater()) {
            if (!mob.onGround()) mob.setDeltaMovement(mob.getDeltaMovement().add(0, -0.05, 0));
            return;
        }
        mob.setDeltaMovement(keepInsideColumn(world, mob.getDeltaMovement()));
    }
    @Override public void save(ValueOutput out) {
        var p = home();
        out.putInt("WildHomeX", p.getX()); out.putInt("WildHomeY", p.getY()); out.putInt("WildHomeZ", p.getZ());
        if (preyHerd != null) out.putString("WildPreyHerd", preyHerd.toString());
        out.putDouble("WildHunger", mind().hunger()); out.putDouble("WildThirst", mind().thirst()); out.putDouble("WildFatigue", mind().fatigue());
        out.putInt("WildSleepCalm", mind().calmTicksRemaining());
    }
    @Override public void load(ValueInput in) {
        home = new BlockPos(in.getIntOr("WildHomeX", mob.blockPosition().getX()), in.getIntOr("WildHomeY", mob.blockPosition().getY()), in.getIntOr("WildHomeZ", mob.blockPosition().getZ()));
        mind = null; // Reset transient pursuit state on reload; needs are restored below.
        mind().restoreNeeds(in.getDoubleOr("WildHunger", 0.55), in.getDoubleOr("WildThirst", 0.35), in.getDoubleOr("WildFatigue", 0.15));
        mind().restoreCalm(in.getIntOr("WildSleepCalm", 0));
        focus = null; lastKnown = null; destination = null;
        herdThreat = null; herdThreatTicks = 0;
        try { preyHerd = UUID.fromString(in.getStringOr("WildPreyHerd", "")); }
        catch (IllegalArgumentException ignored) { preyHerd = null; }
    }
}
