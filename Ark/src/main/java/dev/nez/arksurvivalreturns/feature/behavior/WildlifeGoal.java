package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.Comparator;
import java.util.EnumSet;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Server adapter: bounded perception, local routines, pack signals and collision-aware navigation. */
public final class WildlifeGoal extends Goal {
    private final CreatureEntity mob;
    private WildlifeMind mind;
    private BlockPos home;
    private Vec3 lastKnown, destination, waterDestination;
    private LivingEntity focus;
    private LivingEntity herdThreat;
    private int herdThreatTicks;
    private java.util.UUID preyHerd;
    private int damageStamp = -1, failedPaths, nextRoutine, alarmTicks;
    private long nextAttack, nextAlarm, nextWaterSearch;
    private int corneredTicks;
    private int failedEscapes;
    private boolean interrupted, regrouping;
    public WildlifeGoal(CreatureEntity mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    public WildlifeMind mind() {
        if (mind == null) {
            mind = new WildlifeMind(mob.species().predator, mob.species().timid(), mob.species() == Species.VELOCIRAPTOR);
            long variation = mob.getUUID().getLeastSignificantBits();
            mind.restoreNeeds(0.4 + (variation & 255) / 1024.0, 0.15 + ((variation >>> 8) & 255) / 512.0,
                    0.05 + ((variation >>> 16) & 255) / 1024.0);
        }
        return mind;
    }
    public BlockPos home() { if (home == null) home = mob.blockPosition(); return home; }
    @Override public boolean canUse() { return mob.isAlive() && !mob.species().flyer(); }
    @Override public boolean canContinueToUse() { return mob.isAlive() && !mob.species().flyer(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() { if (Math.floorMod(mob.tickCount + mob.getId(), 10) == 0) think(); }
    @Override public void stop() { mob.getNavigation().stop(); mob.setTarget(null); }
    public void receiveAlarm(Vec3 position) { lastKnown = position; alarmTicks = 40; }
    public void interruptSleep() {
        if (mob.species().flyer()) return;
        interrupted = true;
        mind().interruptSleep(Config.SLEEP_CALM.get());
        mob.setBehavior(mind().state());
    }
    public void followPreyHerd(java.util.UUID herd) { preyHerd = herd; }
    public java.util.UUID preyHerd() { return preyHerd; }
    public void receiveHerdThreat(LivingEntity threat) {
        if (!mob.species().defensiveHerd() || !WildlifeSenses.validTarget(threat)) return;
        herdThreat = threat; herdThreatTicks = 100; focus = threat; lastKnown = threat.position(); mind().defendHerd();
    }
    public void think() {
        if (!(mob.level() instanceof ServerLevel world) || mob.species().flyer()) return;
        var brain = mind();
        boolean cycle = WildlifeSenses.hasNightCycle(mob), night = cycle && WildlifeSenses.night(mob);
        boolean quietRoutine = cycle && (mob.species().predator ? !night : night);
        corneredTicks = Math.max(0, corneredTicks - 10);
        mob.setNightActive(night && mob.species().predator);
        home();
        if (preyHerd != null) {
            var herd = world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(96),
                    c -> c.isAlive() && c.species() == Species.BRONTOSAURUS && c.packId().equals(preyHerd));
            if (!herd.isEmpty()) home = herd.getFirst().blockPosition();
        }
        if ((herdThreatTicks -= 10) <= 0) herdThreat = null;
        boolean peaceful = world.getDifficulty() == Difficulty.PEACEFUL;
        var attacker = mob.getLastHurtByMob();
        boolean attacked = attacker != null && mob.getLastHurtByMobTimestamp() != damageStamp
                && WildlifeSenses.validTarget(attacker) && !(peaceful && attacker instanceof Player);
        if (attacked) { damageStamp = mob.getLastHurtByMobTimestamp(); focus = attacker; lastKnown = attacker.position(); }
        if (attacked && mob.species().herd()) {
            for (var member : world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(48),
                    c -> c != mob && c.isAlive() && c.species() == mob.species() && c.packId().equals(mob.packId())))
                if (mob.species().timid()) member.wildlife().receiveAlarm(attacker.position());
                else member.wildlife().receiveHerdThreat(attacker);
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
        // Night herbivores first escape, then evaluate their ability to stand with the herd.
        if (cycle && night && !mob.species().predator) intimidating = false;
        if (mob.species().predator && guardedPrey && (mob.distanceTo(sensed) < mob.getBbWidth() + 8
                || mob.getHealth() < mob.getMaxHealth() * 0.45)) intimidating = true;
        boolean far = mob.blockPosition().distSqr(home) > territoryRadius() * territoryRadius();
        boolean water = nearbyWater(world);
        var ground = world.getBlockState(mob.blockPosition().below());
        boolean forage = ground.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || ground.is(net.minecraft.world.level.block.Blocks.PODZOL)
                || ground.is(net.minecraft.world.level.block.Blocks.MYCELIUM) || ground.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)
                || ground.is(net.minecraft.world.level.block.Blocks.PALE_MOSS_BLOCK);
        BehaviorState before = brain.state();
        boolean huntable = visible && prey(sensed) && (!guardedPrey || sensed.getHealth() < sensed.getMaxHealth() * 0.35);
        boolean danger = attacked || herdThreat != null || alarmTicks > 0
                || cycle && night && !mob.species().predator && intruding
                || visible && sensed instanceof net.minecraft.world.entity.Mob enemy && enemy.getTarget() == mob
                || visible && sensed instanceof CreatureEntity c && c.species().predator && c.getBbWidth() * 1.3 >= mob.getBbWidth();
        boolean defended = cycle && night && mob.species().defensiveHerd() && danger
                && world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(32),
                    c -> c.isAlive() && c.packId().equals(mob.packId()) && c.getHealth() >= c.getMaxHealth() * 0.5).size() >= 2;
        boolean safeSleep = mob.onGround() && !mob.isInWater() && !mob.isInLava() && !mob.isOnFire() && !interrupted;
        Vec3 regroupDestination = null;
        // Investigation may briefly interrupt fleeing while threat memory fades. Keep the return intent.
        if (!cycle || !night || mob.species().predator) regrouping = false;
        else if (before == BehaviorState.FLEE || before == BehaviorState.REGROUP) regrouping = true;
        if (regrouping) {
            var leader = world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(64),
                    c -> c != mob && c.isAlive() && c.packId().equals(mob.packId()) && c.getId() < mob.getId())
                    .stream().min(Comparator.comparingInt(CreatureEntity::getId)).orElse(null);
            Vec3 anchor = leader == null ? Vec3.atBottomCenterOf(home) : leader.position();
            double near = leader == null ? 6 + mob.getBbWidth() / 2 : mob.species().cohesionDistance();
            if (mob.position().distanceToSqr(anchor) > near * near) regroupDestination = anchor;
            else regrouping = false;
        }
        var routine = cycle ? new WildlifeMind.Routine(true, night,
                NighttimeCycle.sleepWanted(world.getDefaultClockTime(), mob.getUUID().getLeastSignificantBits(),
                        mob.species().predator, night, Config.DAY_SLEEP.get()), safeSleep, danger, defended,
                corneredTicks > 0 || attacked && (mob.species() == Species.THERIZINOSAURUS || mob.species() == Species.TITANOSAUR),
                Config.SLEEP_CALM.get(), Config.NIGHT_HUNGER.get(), regroupDestination != null) : WildlifeMind.Routine.LEGACY;
        var state = brain.step(new WildlifeMind.Observation(signal, visible, huntable, intruding, attacked,
                intimidating, far, water, forage, !world.isBrightOutside(), mob.getHealth() / mob.getMaxHealth()), 10, routine);
        interrupted = false;
        if (!brain.remembers()) { lastKnown = null; focus = null; }
        mob.setBehavior(state);
        if (state != before) {
            mob.getNavigation().stop(); destination = null; nextRoutine = 0;
            if (state.alarm()) {
                mob.behaviorCue(state);
                if (lastKnown != null && world.getGameTime() >= nextAlarm && (!cycle || visible || attacked)) {
                    nextAlarm = world.getGameTime() + 100;
                    for (var member : world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(32),
                            c -> c != mob && c.isAlive() && c.packId().equals(mob.packId()))) member.wildlife().receiveAlarm(lastKnown);
                }
            }
            else if (before == BehaviorState.SLEEP && !state.sleeping()) mob.behaviorCue(BehaviorState.ALERT);
        }
        mob.setTarget(state.combat() && visible ? sensed : null);
        if (lastKnown != null && state.alarm()) mob.getLookControl().setLookAt(lastKnown.x, lastKnown.y + 1, lastKnown.z, 20, 20);
        if (state.combat() && visible && sensed != null) {
            if (mob.isWithinMeleeAttackRange(sensed)) {
                mob.getNavigation().stop();
                if (world.getGameTime() >= nextAttack && mob.hasLineOfSight(sensed)) {
                    nextAttack = world.getGameTime() + 20; // Keep the previous vanilla melee cadence and damage.
                    mob.doHurtTarget(world, sensed);
                    if (!sensed.isAlive() && mob.species().predator && !(sensed instanceof Player)) {
                        brain.ate(); focus = null; mob.setTarget(null); mob.getNavigation().stop();
                    }
                }
            } else move(world, sensed.position(), 1.0);
        } else switch (state) {
            case INVESTIGATE -> { if (lastKnown != null) move(world, lastKnown, 0.7); }
            case FLEE -> {
                if (mob.tickCount >= nextRoutine || !cycle && mob.getNavigation().isDone()) {
                    nextRoutine = mob.tickCount + 40 + (cycle ? Math.floorMod(mob.getId(), 10) : 0);
                    Vec3 away = lastKnown == null ? mob.position().subtract(Vec3.atBottomCenterOf(home)) : mob.position().subtract(lastKnown);
                    boolean escaped = chooseDestination(world, mob.position().add(away.normalize().scale(20)), 8, 1.0);
                    if (cycle) {
                        failedEscapes = escaped ? 0 : failedEscapes + 1;
                        if (failedEscapes >= 3) corneredTicks = 100;
                    }
                }
            }
            case RETURN_HOME -> move(world, Vec3.atBottomCenterOf(home), 0.8);
            case REGROUP -> { if (regroupDestination != null) move(world, regroupDestination, 0.8); }
            case SEEK_WATER -> seekWater(world);
            case ROAM -> roam(world);
            case SEARCH -> roam(world);
            default -> mob.getNavigation().stop();
        }
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
    private double territoryRadius() { return mob.species().flyer() ? 96 : mob.species().herd() || mob.species().solitary() ? 80 : 48; }
    private void roam(ServerLevel world) {
        if (!mob.species().solitary()) {
            var leader = world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(64),
                    c -> c.isAlive() && c.packId().equals(mob.packId()) && c.getId() < mob.getId())
                    .stream().min(Comparator.comparingInt(CreatureEntity::getId)).orElse(null);
            if (leader != null && mob.distanceTo(leader) > mob.species().cohesionDistance()) { move(world, leader.position(), 0.85); return; }
        }
        if (mob.tickCount < nextRoutine) return;
        nextRoutine = mob.tickCount + (mind().state() == BehaviorState.SEARCH ? 60 : 100) + mob.getRandom().nextInt(100);
        chooseDestination(world, Vec3.atBottomCenterOf(home), mob.species().solitary() ? 36 : 24, mob.species().predator ? 0.6 : 0.55);
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
        destination = point;
        boolean found = mob.getNavigation().moveTo(point.x, point.y, point.z, speed);
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
        int radius = Math.min(12, 2 + (int) (mob.getBbWidth() / 2));
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            var pos = center.offset(x, -1, z);
            if (world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null && world.getFluidState(pos).is(FluidTags.WATER)) return true;
        }
        return false;
    }
    private void seekWater(ServerLevel world) {
        if (waterDestination != null && move(world, waterDestination, 0.75)) return;
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
                if (move(world, waterDestination, 0.75)) return;
            }
        }
        roam(world);
    }
    public void save(ValueOutput out) {
        var p = home(); out.putInt("WildHomeX", p.getX()); out.putInt("WildHomeY", p.getY()); out.putInt("WildHomeZ", p.getZ());
        if (preyHerd != null) out.putString("WildPreyHerd", preyHerd.toString());
        out.putDouble("WildHunger", mind().hunger()); out.putDouble("WildThirst", mind().thirst()); out.putDouble("WildFatigue", mind().fatigue());
        if (!mob.species().flyer()) out.putInt("WildSleepCalm", mind().calmTicksRemaining());
    }
    public void load(ValueInput in) {
        home = new BlockPos(in.getIntOr("WildHomeX", mob.blockPosition().getX()), in.getIntOr("WildHomeY", mob.blockPosition().getY()), in.getIntOr("WildHomeZ", mob.blockPosition().getZ()));
        if (!mob.species().flyer()) mind = null; // Reset land transient pursuit and sleep state on reload.
        mind().restoreNeeds(in.getDoubleOr("WildHunger", 0.55), in.getDoubleOr("WildThirst", 0.35), in.getDoubleOr("WildFatigue", 0.15));
        if (!mob.species().flyer()) mind().restoreCalm(in.getIntOr("WildSleepCalm", 0));
        focus = null; lastKnown = null; destination = null; regrouping = false;
        herdThreat = null; herdThreatTicks = 0;
        try { preyHerd = java.util.UUID.fromString(in.getStringOr("WildPreyHerd", "")); }
        catch (IllegalArgumentException ignored) { preyHerd = null; }
    }
}
