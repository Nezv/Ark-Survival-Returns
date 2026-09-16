package dev.nez.arksurvivalreturns.feature.creature;

import java.util.UUID;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.spawn.BiomeTier;
import dev.nez.arksurvivalreturns.feature.behavior.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class CreatureEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEHAVIOR = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> NIGHT_ACTIVE = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Species species;
    private boolean levelInitialized;
    private UUID packId = UUID.randomUUID();
    private boolean naturalWildlife;
    private WildlifeController wildlife;
    private double animationBlocksPerSecond;
    private float eyeGlow, previousEyeGlow;

    public CreatureEntity(EntityType<? extends CreatureEntity> type, Level level, Species species) {
        super(type, level);
        this.species = species;
    }

    public Species species() { return species; }
    public int creatureLevel() { return entityData.get(LEVEL); }
    public UUID packId() { return packId; }
    public void assignHabitatPack(UUID id) { if (species.flyer()) packId = id; }
    public void assignLandHabitat(UUID id) { if (species.landHabitat()) packId = id; }
    public void assignAquaticHabitat(UUID id) { if (species.aquatic()) packId = id; }
    @Override public void remove(Entity.RemovalReason reason) {
        if (!isRemoved() && species != null) dev.nez.arksurvivalreturns.feature.land.LandHabitats.removed(this, reason);
        super.remove(reason);
    }
    public boolean isNaturalWildlife() { return naturalWildlife && !isPersistenceRequired(); }
    public WildlifeController wildlife() { return wildlife; }
    public BehaviorState behavior() { return BehaviorState.values()[Math.clamp(entityData.get(BEHAVIOR), 0, BehaviorState.values().length - 1)]; }
    public void setBehavior(BehaviorState state) { entityData.set(BEHAVIOR, state.ordinal()); }
    public void setNightActive(boolean value) { if (!species.flyer()) entityData.set(NIGHT_ACTIVE, value); }
    public boolean nightActive() { return entityData.get(NIGHT_ACTIVE); }
    public float nightEyeGlow(float partialTick) { return previousEyeGlow + (eyeGlow - previousEyeGlow) * partialTick; }
    public void behaviorCue(BehaviorState state) {
        if (state == BehaviorState.THREATEN || state == BehaviorState.FLEE) triggerAnim("reaction", "warn");
        var sound = state == BehaviorState.ALERT || state == BehaviorState.INVESTIGATE ? net.minecraft.sounds.SoundEvents.SNIFFER_SNIFFING
                : species.predator ? net.minecraft.sounds.SoundEvents.RAVAGER_ROAR : net.minecraft.sounds.SoundEvents.POLAR_BEAR_WARNING;
        playSound(sound, state.combat() ? 1.0f : 0.7f, species.solitary() ? 0.7f : 1.2f);
    }

    public static AttributeSupplier.Builder attributes(Species s) {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, s.health)
                .add(Attributes.ATTACK_DAMAGE, s.damage).add(Attributes.MOVEMENT_SPEED, s.speed)
                .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.STEP_HEIGHT, Math.clamp(s.height * 0.08, 1.0, 3.0))
                .add(Attributes.KNOCKBACK_RESISTANCE, s.solitary() ? 0.7 : 0.1);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LEVEL, 1);
        builder.define(BEHAVIOR, BehaviorState.ROAM.ordinal());
        builder.define(NIGHT_ACTIVE, false);
    }
    @Override protected void registerGoals() {
        // registerGoals runs from the superclass constructor, before this.species is assigned, so the
        // realm is detected from the concrete type. Water-bound and flying species never float.
        if (!(this instanceof FlyingCreatureEntity) && !(this instanceof dev.nez.arksurvivalreturns.feature.aquatic.AquaticCreatureEntity))
            goalSelector.addGoal(0, new FloatGoal(this));
        wildlife = createController();
        goalSelector.addGoal(1, wildlife);
    }
    /** Realm hook: water-bound species replace the land adapter with their own steering goal. */
    protected WildlifeController createController() { return new WildlifeGoal(this); }
    public void initializeLevel(int level) {
        if (levelInitialized) return;
        entityData.set(LEVEL, LevelScaling.clamp(level));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(LevelScaling.health(species.health, creatureLevel(), Config.HEALTH_GROWTH.get()));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(LevelScaling.damage(species.damage, creatureLevel(), Config.DAMAGE_GROWTH.get()));
        setHealth(getMaxHealth());
        levelInitialized = true;
    }
    private void rollLevel() {
        var tier = level() instanceof ServerLevel world ? BiomeTier.at(world, blockPosition()) : BiomeTier.EASY;
        int a = Config.MIN_LEVEL.get(tier).get(), b = Config.MAX_LEVEL.get(tier).get();
        initializeLevel(Math.min(a, b) + random.nextInt(Math.abs(a - b) + 1));
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        super.finalizeSpawn(level, difficulty, reason, data);
        naturalWildlife = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
        rollLevel();
        if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND) setPersistenceRequired();
        var group = !species.solitary() && data instanceof PackData pack ? pack : new PackData(UUID.randomUUID());
        packId = group.id();
        return group;
    }
    @Override public void tick() {
        if (!level().isClientSide() && !levelInitialized) rollLevel(); // Covers /summon and external spawners.
        if (!level().isClientSide() && tickCount % 20 == 0) applyMovementTuning();
        super.tick();
        if (!species.flyer()) {
            if (level().isClientSide()) {
                previousEyeGlow = eyeGlow;
                float target = nightActive() && isAlive() && !behavior().sleeping() ? 1 : 0;
                eyeGlow += Math.clamp(target - eyeGlow, -0.05f, 0.05f);
            } else if (WildlifeSenses.hasNightCycle(this) && species.sleeps() && behavior().sleeping()
                    && (isInWater() || isInLava() || isOnFire() || !onGround())) wildlife.interruptSleep();
        }
        double distance = Math.hypot(getX() - xo, getZ() - zo);
        animationBlocksPerSecond += (Math.min(30, distance * 20) - animationBlocksPerSecond) * 0.35;
    }
    private void applyMovementTuning() {
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(MovementTuning.attribute(Config.SPRINT_RATIO.get(species).get(), Config.PLAYER_SPRINT_REFERENCE.get()));
        getAttribute(Attributes.STEP_HEIGHT).setBaseValue(Math.clamp(species.height * 0.08, 1.0, 3.0));
    }
    @Override protected void travelInWater(net.minecraft.world.phys.Vec3 input, double gravity, boolean falling, double oldY) {
        // Preserve fluid gravity, collision and buoyancy, but calibrate horizontal propulsion to land speed.
        var horizontal = new net.minecraft.world.phys.Vec3(input.x, 0, input.z);
        if (horizontal.lengthSqr() > 1.0E-7) {
            double speed = MovementTuning.blocksPerSecond(getSpeed()) * Config.WATER_RETENTION.get(species).get()
                    * getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED);
            moveRelative((float)(speed / 20 * 0.2), horizontal.normalize());
        }
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getFluidFallingAdjustedMovement(gravity, falling, getDeltaMovement().multiply(0.8, 0.8, 0.8)));
        var velocity = getDeltaMovement();
        if (horizontalCollision && isFree(velocity.x, velocity.y + 0.6 - getY() + oldY, velocity.z))
            setDeltaMovement(velocity.x, 0.3, velocity.z);
    }
    @Override public int getMaxSpawnClusterSize() { return species.maxGroup; }
    @Override public boolean removeWhenFarAway(double distance) { return true; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("CreatureLevel", creatureLevel());
        output.putBoolean("LevelInitialized", levelInitialized);
        output.putString("PackId", packId.toString());
        output.putBoolean("NaturalWildlife", naturalWildlife);
        wildlife.save(output);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(LEVEL, LevelScaling.clamp(input.getIntOr("CreatureLevel", 1)));
        levelInitialized = input.getBooleanOr("LevelInitialized", false);
        naturalWildlife = input.getBooleanOr("NaturalWildlife", !isPersistenceRequired());
        // Adopt the new movement baseline for old saves without stacking a multiplier on each load.
        applyMovementTuning();
        wildlife.load(input);
        try { packId = UUID.fromString(input.getStringOr("PackId", packId.toString())); }
        catch (IllegalArgumentException ignored) { packId = UUID.randomUUID(); }
        // Vanilla persists attributes and current HP. Never reroll or heal saved creatures.
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) triggerAnim("attack", "strike");
        return hit;
    }
    @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float damage) {
        boolean hit = super.hurtServer(level, source, damage);
        if (hit && isAlive() && WildlifeSenses.hasNightCycle(this)) {
            boolean sleeping = behavior().sleeping();
            wildlife.interruptSleep();
            if (sleeping) behaviorCue(BehaviorState.ALERT);
        }
        return hit;
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<CreatureEntity>("movement", 5, state -> {
            var behavior = behavior();
            boolean running = behavior.combat() || behavior == BehaviorState.FLEE;
            state.setControllerSpeed(state.isMoving() ? (float)MovementTuning.animationRate(animationBlocksPerSecond,
                    species.height, species.strideCycleSeconds(running), running, Config.STRIDE_SCALE.get(species).get()) : 1);
            String clip = state.isMoving() ? movingClip(running)
                    : behavior == BehaviorState.SLEEP ? species.sleepClip()
                    : behavior == BehaviorState.REST ? restingClip()
                    : (behavior == BehaviorState.FORAGE || behavior == BehaviorState.FEED || behavior == BehaviorState.DRINK)
                            && !species.additiveFood() ? species.foodClip() : standingClip();
            return state.setAndContinue(RawAnimation.begin().thenLoop(clip));
        }));
        registrar.add(new AnimationController<CreatureEntity>("feeding", 5, state ->
                species.additiveFood() && behavior() == BehaviorState.FEED
                        ? state.setAndContinue(RawAnimation.begin().thenLoop(species.foodClip())) : com.geckolib.animation.object.PlayState.STOP).additiveAnimations());
        registrar.add(new AnimationController<CreatureEntity>("reaction", 4, state -> com.geckolib.animation.object.PlayState.STOP)
                .triggerableAnim("warn", RawAnimation.begin().thenPlay(species.warningClip())));
        registrar.add(new AnimationController<CreatureEntity>("attack", 3, state -> com.geckolib.animation.object.PlayState.STOP)
                .triggerableAnim("strike", RawAnimation.begin().thenPlay(species.attack)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    /** Realm hook: a swimming creature selects its water clip set. */
    protected boolean swimming() { return false; }
    protected String movingClip(boolean running) {
        return swimming() ? (running ? species.swimRun() : species.swimWalk()) : running ? species.runClip() : species.walk;
    }
    protected String standingClip() { return swimming() ? species.swimIdle() : species.idle; }
    protected String restingClip() { return swimming() ? species.swimIdle() : species.restClip(); }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return net.minecraft.sounds.SoundEvents.SNIFFER_IDLE; }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) { return net.minecraft.sounds.SoundEvents.SNIFFER_HURT; }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return net.minecraft.sounds.SoundEvents.SNIFFER_DEATH; }
    @Override protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState block) {
        playSound(net.minecraft.sounds.SoundEvents.SNIFFER_STEP, species.solitary() ? 0.7f : 0.2f, species.solitary() ? 0.65f : 1.25f);
    }
    private record PackData(UUID id) implements SpawnGroupData {}
}
