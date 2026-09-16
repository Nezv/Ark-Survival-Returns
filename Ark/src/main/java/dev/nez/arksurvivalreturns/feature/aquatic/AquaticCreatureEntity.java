package dev.nez.arksurvivalreturns.feature.aquatic;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeController;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.LookControl;
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
    @Override public void travel(Vec3 input) { travelFlying(Vec3.ZERO, 0); resetFallDistance(); }
    @Override protected boolean swimming() { return true; }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide() || !(level() instanceof ServerLevel world)) return;
        if (isInWater()) { outOfWater = 0; return; }
        outOfWater++;
        // No gravity means a beached animal would hover; sink it back toward the pool instead.
        if (!onGround()) setDeltaMovement(getDeltaMovement().add(0, -0.05, 0));
        if (outOfWater > 200 && outOfWater % 20 == 0 && isAlive())
            hurtServer(world, damageSources().generic(), 1f);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<CreatureEntity>("movement", 5, state -> {
            var behavior = behavior();
            boolean running = behavior.combat() || behavior == BehaviorState.FLEE;
            state.setControllerSpeed(state.isMoving() ? 1 : 1);
            String clip = state.isMoving() ? (running ? species().swimRun() : species().swimWalk()) : species().swimIdle();
            return state.setAndContinue(RawAnimation.begin().thenLoop(clip));
        }));
        registrar.add(new AnimationController<CreatureEntity>("reaction", 4, state -> PlayState.STOP)
                .triggerableAnim("warn", RawAnimation.begin().thenPlay(species().warningClip())));
        registrar.add(new AnimationController<CreatureEntity>("attack", 3, state -> PlayState.STOP)
                .triggerableAnim("strike", RawAnimation.begin().thenPlay(species().attack)));
    }
}
