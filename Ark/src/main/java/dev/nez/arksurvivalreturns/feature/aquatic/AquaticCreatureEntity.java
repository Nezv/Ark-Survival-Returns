package dev.nez.arksurvivalreturns.feature.aquatic;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeController;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.mass.MassRules;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import dev.nez.arksurvivalreturns.feature.taming.CreatureAnimationBridge;
import dev.nez.arksurvivalreturns.feature.taming.CreatureRideController;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Water-bound wildlife: gravity-free steering inside one saved pool, its own swim clip set and a
 * beaching rule so a stranded animal sinks back instead of floating.
 *
 * Ground needs are not used. Hunger, sight, hunting, alarms and saved state come from the shared
 * {@link dev.nez.arksurvivalreturns.feature.behavior.WildlifeMind}.
 */
public final class AquaticCreatureEntity extends CreatureEntity {
    private int outOfWater;
    public AquaticCreatureEntity(EntityType<? extends CreatureEntity> type, Level level, Species species) {
        super(type, level, species);
        setNoGravity(true);
        // Water steering owns body orientation; the ground look control would reset pitch each tick.
        lookControl = new LookControl(this) { @Override public void tick() {} };
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); }
    @Override protected WildlifeController createController() { return new AquaticGoal(this); }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean isPushedByFluid() { return false; }
    /** A ridden swimmer follows the rider; an unconscious one simply sinks and drifts. */
    @Override public void travel(Vec3 input) {
        if (isRidden()) {
            travelFlying(overloadAdjusted(input),
                    Math.max(0.03f, CreatureRideController.riddenSpeed(this, rideProfile()) * 0.25f));
            return;
        }
        if (TorporService.restricted(this)) {
            setNoGravity(false);
            super.travel(input);
            return;
        }
        travelFlying(Vec3.ZERO, 0);
        resetFallDistance();
    }
    @Override protected boolean swimming() { return true; }

    /**
     * Overload swim rules: an overloaded mount cannot dive and becomes slightly buoyant; when the
     * rider's air runs low it surfaces outright, so overburdening never causes an unavoidable drowning.
     */
    private Vec3 overloadAdjusted(Vec3 input) {
        if (!MassService.overloaded(this)) return input;
        if (getFirstPassenger() instanceof Player rider
                && MassRules.forcedSurface(rider.getAirSupply(), rider.getMaxAirSupply())) {
            MassService.warn(this, "hud.arksurvivalreturns.overload.surface");
            return new Vec3(input.x, 1.0, input.z);
        }
        if (input.y < 0) MassService.warn(this, "hud.arksurvivalreturns.overload.dive");
        return new Vec3(input.x, Math.max(0.12, input.y), input.z);
    }
    /** Companion steering: a tamed swimmer heads to the point without leaving its water column. */
    @Override protected void steerCompanion(Vec3 point, double speed) {
        if (!(level() instanceof ServerLevel world)) return;
        var to = point.subtract(position());
        if (to.length() < 1.2) { setDeltaMovement(getDeltaMovement().scale(0.7)); return; }
        var desired = to.normalize().scale(0.26 * speed);
        var next = getDeltaMovement().lerp(desired, 0.18);
        var column = Water.column(world, blockPosition());
        if (column != null) {
            if (getY() > column[0] - 1) next = next.add(0, -0.05, 0);
            else if (getY() < column[1]) next = next.add(0, 0.05, 0);
        } else if (!isInWater()) {
            next = next.add(0, -0.05, 0);
        }
        setDeltaMovement(next);
        if (next.horizontalDistanceSqr() > 0.0001) {
            float yaw = (float)(Math.atan2(next.z, next.x) * 180 / Math.PI) - 90;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 8));
            yBodyRot = getYRot(); yHeadRot = getYRot();
        }
    }
    @Override protected void stopCompanion() { setDeltaMovement(getDeltaMovement().scale(0.8)); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide() || !(level() instanceof ServerLevel world)) return;
        if (TorporService.restricted(this)) return;
        if (isInWater()) { outOfWater = 0; return; }
        outOfWater++;
        // No gravity means a beached animal would hover; sink it back toward the pool instead.
        if (!onGround()) setDeltaMovement(getDeltaMovement().add(0, -0.05, 0));
        if (outOfWater > 200 && outOfWater % 20 == 0 && isAlive())
            hurtServer(world, damageSources().generic(), 1f);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<CreatureEntity>("movement", 5, state -> {
            String sedation = CreatureAnimationBridge.sedationClip(this, 0f);
            if (sedation != null) {
                state.setControllerSpeed(1f);
                return state.setAndContinue(CreatureAnimationBridge.isOneShot(this, sedation)
                        ? oneShot(sedation)
                        : RawAnimation.begin().thenLoop(sedation));
            }
            var behavior = behavior();
            boolean running = behavior.combat() || behavior == BehaviorState.FLEE;
            state.setControllerSpeed(1);
            String clip = isLocomoting() ? (running ? species().swimRun() : species().swimWalk()) : species().swimIdle();
            return state.setAndContinue(RawAnimation.begin().thenLoop(clip));
        }));
        registrar.add(new AnimationController<CreatureEntity>("reaction", 4, state -> PlayState.STOP)
                .triggerableAnim("warn", oneShot(species().warningClip())));
        registrar.add(new AnimationController<CreatureEntity>("attack", 3, state -> PlayState.STOP)
                .triggerableAnim("strike", oneShot(species().attack)));
    }
}
