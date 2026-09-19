package dev.nez.arksurvivalreturns.feature.creature;

import java.util.UUID;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.*;
import com.geckolib.animation.object.PlayState;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.flying.*;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.feature.taming.CreatureAnimationBridge;
import dev.nez.arksurvivalreturns.feature.taming.CreatureRideController;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Habitat-anchored circle/swoop steering. No ground needs, target acquisition or pack-follow goal. */
public final class FlyingCreatureEntity extends CreatureEntity {
    public enum Phase { ROAM, DEFENSE_CIRCLE, SWOOP, RETURN_HOME, LAND, PERCH, TAKEOFF }
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(FlyingCreatureEntity.class, EntityDataSerializers.INT);
    private UUID habitatId, thiefId;
    private BlockPos center, nest;
    private Vec3 destination;
    private long defenseUntil, nextSwoop, lastSeen;
    private int phaseTicks, nextPerch, nextDestination, nextAdoption, swoopTicks, blockedTicks;
    private double angle, orbitRadius, orbitHeight;
    private boolean orbitInitialized, struck;

    public FlyingCreatureEntity(EntityType<? extends CreatureEntity> type, Level level, Species species) {
        super(type, level, species); setNoGravity(true);
        // Ground LookControl resets pitch every tick; flight steering owns the whole body orientation.
        lookControl = new net.minecraft.world.entity.ai.control.LookControl(this) { @Override public void tick() {} };
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(PHASE, 0); }
    public Phase flightPhase() { return Phase.values()[Math.clamp(entityData.get(PHASE), 0, Phase.values().length - 1)]; }
    public UUID habitatId() { return habitatId; }
    public UUID eggThief() { return thiefId; }
    public BlockPos habitatCenter() { return center == null ? wildlife().home() : center; }
    public BlockPos nestPosition() { return nest; }
    public void assignHabitat(HabitatData.Habitat h, BlockPos perch) {
        habitatId = h.id(); center = h.center(); nest = perch; assignHabitatPack(h.id());
        destination = null; orbitInitialized = false;
    }
    private void phase(Phase next) {
        if (flightPhase() == next) return;
        var before = flightPhase(); entityData.set(PHASE, next.ordinal()); phaseTicks = 0; destination = null;
        setBehavior(switch (next) {
            case DEFENSE_CIRCLE, SWOOP -> BehaviorState.DEFEND;
            case RETURN_HOME -> BehaviorState.RETURN_HOME;
            case PERCH -> BehaviorState.REST;
            default -> BehaviorState.ROAM;
        });
        if (next == Phase.TAKEOFF || before == Phase.PERCH && next == Phase.DEFENSE_CIRCLE) triggerAnim("transition", "takeoff");
        if (next == Phase.PERCH) triggerAnim("transition", "land");
        if (next == Phase.SWOOP) stopTriggeredAnim("transition", null);
        if (before == Phase.SWOOP && next == Phase.DEFENSE_CIRCLE) triggerAnim("transition", "pullout");
    }
    public boolean validThief(Player player) {
        // A feeder inside the feeding truce is not a target, so the bird cannot instantly re-engage it.
        if (feedingTruce(player)) return false;
        return player != null && player.isAlive() && player.level() == level() && !player.isCreative() && !player.isSpectator()
                && level().getDifficulty() != Difficulty.PEACEFUL
                && HabitatData.horizontalDistanceSqr(habitatCenter(), player.blockPosition()) <= (double)Config.FLIGHT_LEASH.get() * Config.FLIGHT_LEASH.get()
                && Math.abs(player.getY() - habitatCenter().getY()) < 64;
    }
    public void defendEgg(Player thief) {
        if (habitatId == null || !validThief(thief)) return;
        engage(thief);
        playSound(net.minecraft.sounds.SoundEvents.PARROT_AMBIENT, 0.8f, species() == Species.ARGENTAVIS ? 0.7f : 1.2f);
    }
    /** Circle-and-swoop engagement shared by egg defense and territorial apex flyers. */
    private void engage(Player target) {
        thiefId = target.getUUID(); defenseUntil = level().getGameTime() + Config.EGG_DEFENSE_TICKS.get();
        lastSeen = level().getGameTime(); nextSwoop = level().getGameTime() + 20 + Math.floorMod(getId() * 17, 41);
        struck = false; phase(Phase.DEFENSE_CIRCLE); setTarget(target);
    }
    private void endDefense() { thiefId = null; setTarget(null); phase(Phase.RETURN_HOME); }
    /**
     * Apex flyers guard the airspace around their own roost. Peaceful, creative and spectator
     * players are excluded, the engagement ends at the habitat leash and nothing is pursued
     * outside the saved home area.
     */
    private void considerTerritorialDefense(ServerLevel world) {
        if (thiefId != null || !species().predator || !species().apex()) return;
        if (Math.floorMod(tickCount + getId(), 40) != 0) return;
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (var player : world.players()) {
            if (!validThief(player)) continue;
            double distance = HabitatData.horizontalDistanceSqr(habitatCenter(), player.blockPosition());
            if (distance < best) { best = distance; nearest = player; }
        }
        if (nearest != null && seesPlayer(world, nearest)) engage(nearest);
    }
    private int roamRadius() { return species().flyerRoamRadius(); }
    @Override protected void customServerAiStep(ServerLevel world) {
        super.customServerAiStep(world);
        tickFlight(world);
    }
    /** Also exercised by headless tests with controlled world time. */
    public void tickFlight(ServerLevel world) {
        // An unconscious bird loses powered flight and falls under ordinary movement physics.
        if (TorporService.restricted(this)) {
            setNoGravity(false);
            destination = null;
            return;
        }
        // A rider drives the flight; the autonomous steering stands aside so it cannot fight the input.
        if (isRidden()) {
            setNoGravity(true);
            resetFallDistance();
            destination = null;
            if (flightPhase() == Phase.PERCH || flightPhase() == Phase.LAND) phase(Phase.TAKEOFF);
            return;
        }
        setNoGravity(true); resetFallDistance(); phaseTicks++;
        if (center == null) center = wildlife().home();
        if (!orbitInitialized) {
            angle = random.nextDouble() * Math.PI * 2;
            orbitRadius = 8 + random.nextDouble() * (roamRadius() - 10);
            orbitHeight = (species() == Species.ARGENTAVIS ? 12 : 6) + random.nextDouble() * 12;
            nextPerch = tickCount + 240 + random.nextInt(401); orbitInitialized = true;
        }
        if (tickCount >= nextAdoption) {
            nextAdoption = tickCount + 200 + Math.floorMod(getId(), 40);
            var h = habitatId == null ? null : HabitatData.get(world).byId(habitatId);
            if (h == null) { habitatId = null; nest = null; if (thiefId != null) endDefense(); }
            else if (!h.nests().contains(nest)) nest = h.nests().getFirst();
            if (habitatId == null && isNaturalWildlife()) FlyerHabitats.adopt(world, this);
        }
        considerTerritorialDefense(world);
        Player thief = thiefId != null && world.getEntity(thiefId) instanceof Player player ? player : null;
        if (thiefId != null && (!validThief(thief) || world.getGameTime() >= defenseUntil
                || HabitatData.horizontalDistanceSqr(center, blockPosition()) > (double)Config.FLIGHT_LEASH.get() * Config.FLIGHT_LEASH.get())) endDefense();
        if (thiefId != null) {
            setTarget(thief);
            if (Math.floorMod(tickCount + getId(), 5) == 0) {
                if (seesPlayer(world, thief)) lastSeen = world.getGameTime();
                if (world.getGameTime() - lastSeen > 60) endDefense();
                else if (flightPhase() == Phase.DEFENSE_CIRCLE && world.getGameTime() >= nextSwoop && seesPlayer(world, thief)) {
                    var approach = new Vec3(thief.getX(), thief.getY(0.5), thief.getZ());
                    if (clearRoute(world, approach)) { phase(Phase.SWOOP); struck = false; swoopTicks = 0; destination = approach; }
                    else nextSwoop = world.getGameTime() + 20;
                } else if (flightPhase() == Phase.SWOOP && seesPlayer(world, thief)) destination = new Vec3(thief.getX(), thief.getY(0.5), thief.getZ());
            }
            if (flightPhase() == Phase.SWOOP) {
                swoopTicks++;
                if (doContact(world, thief) || struck || swoopTicks >= 100 || horizontalCollision || verticalCollision && !onGround()) recover(world);
            }
        } else setTarget(null); // External target assignments never grant attack authority.
        if (flightPhase() == Phase.PERCH) {
            if (!Config.PERCHING.get() || !safePerch(world) || isInWater() || hurtTime > 0 || phaseTicks > 100 + Math.floorMod(getId() * 31, 201)) {
                phase(Phase.TAKEOFF); nextPerch = tickCount + 400 + random.nextInt(401);
            } else { setDeltaMovement(Vec3.ZERO); setXRot(Mth.approachDegrees(getXRot(), 0, 5)); return; }
        }
        if (flightPhase() == Phase.LAND) {
            if (isInWater() || isInLava() || !safePerch(world) || phaseTicks > 200) { phase(Phase.TAKEOFF); nextPerch = tickCount + 400; }
            else {
                destination = Vec3.atBottomCenterOf(nest);
                if (position().distanceToSqr(destination) < 0.0064) { setPos(destination); setOnGround(true); setDeltaMovement(Vec3.ZERO); phase(Phase.PERCH); return; }
            }
        }
        if (flightPhase() == Phase.TAKEOFF) {
            destination = position().add(0, 4, 0);
            if (phaseTicks > (species() == Species.ARGENTAVIS ? 28 : 48)) phase(Phase.ROAM);
        }
        if (flightPhase() == Phase.ROAM && HabitatData.horizontalDistanceSqr(center, blockPosition()) > (double)roamRadius() * roamRadius()) phase(Phase.RETURN_HOME);
        if (flightPhase() == Phase.RETURN_HOME) {
            destination = Vec3.atBottomCenterOf(center).add(0, orbitHeight, 0);
            if (HabitatData.horizontalDistanceSqr(center, blockPosition()) < 100) phase(Phase.ROAM);
        }
        if (flightPhase() == Phase.ROAM && tickCount >= nextPerch && Config.PERCHING.get()) {
            nextPerch = tickCount + 200;
            beginPerching(world);
        }
        if (flightPhase() == Phase.ROAM || flightPhase() == Phase.DEFENSE_CIRCLE) {
            if (destination == null || tickCount >= nextDestination || position().distanceToSqr(destination) < 9) {
                nextDestination = tickCount + 40 + random.nextInt(21);
                double direction = (getUUID().getLeastSignificantBits() & 1) == 0 ? 1 : -1;
                angle += direction * (0.35 + random.nextDouble() * 0.3);
                double r = Math.min(orbitRadius + Math.sin(tickCount / 180.0) * 3, roamRadius() - 3);
                destination = Vec3.atBottomCenterOf(center).add(Math.cos(angle)*r, orbitHeight + Math.sin(angle*2)*2, Math.sin(angle)*r);
            }
        }
        steer(world);
    }
    private boolean seesPlayer(ServerLevel world, Player player) {
        return SpawnRules.loaded(world, new AABB(position(), player.position()).inflate(1)) && hasLineOfSight(player);
    }
    private boolean doContact(ServerLevel world, Player thief) {
        return !struck && isWithinMeleeAttackRange(thief) && seesPlayer(world, thief) && doHurtTarget(world, thief);
    }
    @Override public boolean doHurtTarget(ServerLevel world, Entity target) {
        if (!(target instanceof Player p) || thiefId == null || !thiefId.equals(p.getUUID()) || !validThief(p)
                || world.getGameTime() >= defenseUntil || flightPhase() != Phase.SWOOP || struck
                || !isWithinMeleeAttackRange(p) || !seesPlayer(world, p)) return false;
        struck = true; return super.doHurtTarget(world, target);
    }
    private void recover(ServerLevel world) { phase(Phase.DEFENSE_CIRCLE); nextSwoop = world.getGameTime() + 80 + random.nextInt(81); }
    public boolean beginPerching(ServerLevel world) {
        if (thiefId != null || isInWater() || isInLava() || !Config.PERCHING.get() || !safePerch(world) || !clearRoute(world, Vec3.atBottomCenterOf(nest))) return false;
        phase(Phase.LAND); return true;
    }
    public boolean safePerch(ServerLevel world) {
        if (nest == null || !SpawnRules.loaded(world, new AABB(nest).inflate(getBbWidth() + 1))) return false;
        var state = world.getBlockState(nest);
        if (!(state.getBlock() instanceof NestBlock) || !state.canSurvive(world, nest)) return false;
        var box = SpawnRules.bounds(species(), nest);
        return world.noCollision(this, box, true) && world.getEntitiesOfClass(FlyingCreatureEntity.class, box.inflate(0.5), b -> b != this && b.isAlive()).isEmpty();
    }
    private boolean clearRoute(ServerLevel world, Vec3 point) {
        Vec3 delta = point.subtract(position());
        var sweep = getBoundingBox().expandTowards(delta).deflate(0.01);
        if (!SpawnRules.loaded(world, sweep.inflate(1)) || !world.getWorldBorder().isWithinBounds(sweep)
                || sweep.minY <= world.getMinY() || sweep.maxY >= world.getMaxY()) return false;
        // Keep collision recovery inside the colony's counted vertical extent, allowing displaced birds back down.
        if (point.y > habitatCenter().getY()+56 && point.y >= getY()) return false;
        int steps = Math.max(1, (int)Math.ceil(delta.length()/2));
        if (steps > 64) return false;
        Vec3 step = delta.scale(1.0/steps);
        for (int i=0;i<steps;i++) {
            var segment = getBoundingBox().move(step.scale(i)).expandTowards(step).deflate(0.01);
            // Avoid entering fluids, but let a bird already knocked into water rise out.
            if (!world.noBlockCollision(this, segment, !isInWater() && !isInLava())) return false;
        }
        return true;
    }
    private void steer(ServerLevel world) {
        if (isInWater() || isInLava()) destination = position().add(0, 4, 0);
        if (destination == null) { setDeltaMovement(getDeltaMovement().scale(0.8)); return; }
        Vec3 to = destination.subtract(position());
        double speed = flightPhase() == Phase.SWOOP ? 0.48 : flightPhase() == Phase.LAND ? Math.min(0.18, to.length()*0.2) : 0.27;
        Vec3 desired = to.normalize().scale(speed);
        Vec3 velocity = getDeltaMovement().lerp(desired, 0.18);
        var ahead = position().add(velocity.scale(4));
        if (!clearRoute(world, ahead)) {
            blockedTicks++;
            if (flightPhase() == Phase.SWOOP) recover(world);
            // A blocked dive or orbit may climb/turn locally. Never plan through unloaded chunks.
            Vec3 up = position().add(0, 2, 0);
            if (clearRoute(world, up)) velocity = new Vec3(0, 0.16, 0);
            else {
                Vec3 side = position().add(-Math.sin(angle)*2, 0, Math.cos(angle)*2);
                velocity = clearRoute(world, side) ? side.subtract(position()).normalize().scale(0.14) : Vec3.ZERO;
            }
            if (blockedTicks >= 40) { angle += Math.PI/2; destination = null; blockedTicks = 0; if (flightPhase() == Phase.LAND) phase(Phase.ROAM); }
        } else blockedTicks = 0;
        setDeltaMovement(velocity);
        if (velocity.horizontalDistanceSqr() > 0.0001) {
            float yaw = (float)(Math.atan2(velocity.z, velocity.x)*180/Math.PI) - 90;
            setYRot(Mth.approachDegrees(getYRot(), yaw, flightPhase() == Phase.SWOOP ? 10 : 5));
            yBodyRot = getYRot(); yHeadRot = getYRot();
        }
        float pitch = (float)(-Math.atan2(velocity.y, Math.max(0.001, velocity.horizontalDistance()))*180/Math.PI);
        setXRot(Mth.approachDegrees(getXRot(), Math.clamp(pitch, -45, 55), 4));
    }
    @Override public void travel(Vec3 input) {
        if (isRidden()) {
            travelFlying(input, Math.max(0.05f, CreatureRideController.riddenSpeed(this, rideProfile())));
            return;
        }
        if (TorporService.restricted(this)) {
            // Plain physics while unconscious: gravity applies, no powered flight, no fall-distance reset.
            setNoGravity(false);
            super.travel(input);
            return;
        }
        travelFlying(Vec3.ZERO, 0);
        resetFallDistance();
    }
    @Override public boolean hurtServer(ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float damage) {
        boolean hit = super.hurtServer(world, source, damage);
        if (hit && isAlive() && thiefId == null) { phase(Phase.TAKEOFF); nextPerch = tickCount + 400; }
        return hit;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        BlockPos home = habitatCenter(); out.putLong("FlightHome", home.asLong());
        if (habitatId != null) out.putString("FlightHabitat", habitatId.toString());
        if (nest != null) out.putLong("FlightNest", nest.asLong());
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        center = BlockPos.of(in.getLongOr("FlightHome", wildlife().home().asLong()));
        try { habitatId = UUID.fromString(in.getStringOr("FlightHabitat", "")); } catch (IllegalArgumentException e) { habitatId = null; }
        nest = habitatId == null ? null : BlockPos.of(in.getLongOr("FlightNest", center.asLong()));
        thiefId = null; setTarget(null); entityData.set(PHASE, Phase.ROAM.ordinal()); setBehavior(BehaviorState.ROAM); setNoGravity(true);
        orbitInitialized = false; destination = null;
    }
    public String flightPrefix() { return species() == Species.ARGENTAVIS ? "Argentavis-" : "Ptero-"; }
    private Species.FlyerProfile flightProfile() { return species().flyerProfile(); }
    /** Clip used while airborne and travelling. */
    private String flyMoveClip() { var p = flightProfile(); return p == null ? flightPrefix() + "Fly-Fwd" : p.flyMove(); }
    /** Clip used while hovering, landing or taking off. */
    private String hoverClip() { var p = flightProfile(); return p == null ? flightPrefix() + "Fly-Hover" : p.hover(); }
    private String landClip() { var p = flightProfile(); return p == null ? flightPrefix() + "Land" : p.land(); }
    private String takeOffClip() { var p = flightProfile(); return p == null ? flightPrefix() + "Take-Off" : p.takeOff(); }
    private String swoopLoopClip() {
        var p = flightProfile();
        String clip = p == null ? (species() == Species.PTERANODON ? flightPrefix() + "Fly-Attack-Swoop-Loop" : null) : p.swoopLoop();
        return clip == null ? flyMoveClip() : clip;
    }
    private String swoopOutClip() {
        var p = flightProfile();
        String clip = p == null ? flightPrefix() + "Fly-Attack-Swoop-Out" : p.swoopOut();
        return clip == null ? species().attack : clip;
    }
    private String attackClip() {
        var p = flightProfile();
        return p == null ? flightPrefix() + (species() == Species.ARGENTAVIS ? "Fly-Attack-Claw" : "Fly-Attack-Bite") : p.attack();
    }
    private String perchClip() {
        var p = flightProfile();
        return p == null || p.perchIdle() == null ? species().idle : p.perchIdle();
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<FlyingCreatureEntity>("movement", 5, state -> {
            String sedation = CreatureAnimationBridge.sedationClip(this, 0f);
            if (sedation != null) {
                state.setControllerSpeed(1f);
                return state.setAndContinue(CreatureAnimationBridge.isOneShot(this, sedation)
                        ? oneShot(sedation)
                        : RawAnimation.begin().thenLoop(sedation));
            }
            if (isRidden()) return state.setAndContinue(RawAnimation.begin()
                    .thenLoop(state.isMoving() ? flyMoveClip() : hoverClip()));
            String clip = flightPhase() == Phase.PERCH ? perchClip()
                    : flightPhase() == Phase.SWOOP ? swoopLoopClip()
                    : flightPhase() == Phase.LAND || flightPhase() == Phase.TAKEOFF ? hoverClip() : flyMoveClip();
            return state.setAndContinue(RawAnimation.begin().thenLoop(clip));
        }));
        registrar.add(new AnimationController<FlyingCreatureEntity>("transition", 4, state -> PlayState.STOP)
                .triggerableAnim("takeoff", oneShot(takeOffClip()))
                .triggerableAnim("land", oneShot(landClip()))
                .triggerableAnim("pullout", oneShot(swoopOutClip())));
        registrar.add(new AnimationController<FlyingCreatureEntity>("attack", 2, state -> PlayState.STOP)
                .triggerableAnim("strike", oneShot(attackClip())));
    }
    public static double altitudeWeight(Species species, int y, int seaLevel) {
        if (species == Species.PTERANODON) return 1;
        if (species == Species.ARGENTAVIS) {
            int relative = y - seaLevel;
            return relative < 16 ? 0.08 : relative <= 80 ? 1 : relative <= 112 ? 0.4 : 0.1;
        }
        var profile = species.flyerProfile();
        if (profile == null || profile.nestFloorY() <= 0) return 1;
        // High-ground nesters are weighted toward their own altitude band, not the surface.
        return y - seaLevel < profile.nestFloorY() - seaLevel - 24 ? 0.15 : 1;
    }
}
