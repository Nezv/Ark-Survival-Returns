package dev.nez.arksurvivalreturns.feature.creature;

import java.util.UUID;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;
import com.geckolib.util.GeckoLibUtil;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.cargo.CargoProfiles;
import dev.nez.arksurvivalreturns.feature.companion.CompanionGoal;
import dev.nez.arksurvivalreturns.feature.companion.CompanionService;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import dev.nez.arksurvivalreturns.feature.spawn.BiomeTier;
import dev.nez.arksurvivalreturns.feature.behavior.*;
import dev.nez.arksurvivalreturns.feature.taming.*;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.feature.work.WorkGoal;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CreatureEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEHAVIOR = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> NIGHT_ACTIVE = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Species species;
    private final CreatureInventory tamingInventory = new CreatureInventory(this);
    /** One rig slot: the harness tier decides whether the species cargo capacity or the bare allowance applies. */
    private final SimpleContainer harnessSlot = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return CargoProfiles.tier(stack) != CargoProfiles.Harness.NONE;
        }
        @Override public void setChanged() {
            if (level() != null && !level().isClientSide()) MassService.markDirty(CreatureEntity.this);
        }
    };
    private boolean levelInitialized;
    private int originDanger = -1;
    private UUID packId = UUID.randomUUID();
    private boolean naturalWildlife;
    private WildlifeController wildlife;
    private final LocomotionSignal locomotion = new LocomotionSignal();
    private double animationBlocksPerSecond;
    private @Nullable Vec3 companionDestination;
    private boolean companionTraveling;
    private int petCooldown;
    private float eyeGlow, previousEyeGlow;
    private transient int riddenTicks;
    private @Nullable LivingEntity pendingStrike;
    private int strikeWindup;
    private int strikeCooldown;
    private boolean applyingStrike;

    public CreatureEntity(EntityType<? extends CreatureEntity> type, Level level, Species species) {
        super(type, level);
        this.species = species;
    }

    public Species species() { return species; }

    // ------------------------------------------------------------------ taming and sedation

    /** Taming balance of this creature; never null, the registry enforces completeness. */
    public CreatureTamingProfile profile() { return CreatureProfileRegistry.of(species); }

    /** Seat transform measured from this creature's own model. */
    public CreatureRideProfile rideProfile() { return CreatureProfileRegistry.ride(species); }

    public TorporState torpor() { return TorporService.of(this); }

    public TamingState taming() { return TamingService.of(this); }

    public CreatureInventory tamingInventory() { return tamingInventory; }

    /** One-slot rig container; cargo capacity derives from the harness tier it holds. */
    public SimpleContainer harnessSlot() { return harnessSlot; }

    public CargoProfiles.Harness harnessTier() { return CargoProfiles.tier(harnessSlot.getItem(0)); }

    /** The saddle lives in the real equipment slot, so vanilla persists, syncs and drops it. */
    public boolean isSaddled() { return getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE); }

    public boolean isTamed() { return TamingService.isTamed(this); }

    public boolean isOwnedBy(@Nullable Player player) { return TamingService.ownedBy(this, player); }

    /** Fires when the creature enters or leaves the restricted phases, on both sides. */
    public void onUnconsciousnessChanged(boolean unconscious) {
        if (unconscious) {
            stopTriggeredAnim("attack", null);
            pendingStrike = null;
            strikeWindup = 0;
            setBehavior(BehaviorState.REST);
            setSprinting(false);
        } else {
            setBehavior(BehaviorState.ROAM);
        }
    }

    /** Ownership, persistence and leaving the wildlife systems behind. */
    public void onTamed(UUID owner) {
        setPersistenceRequired();
        setTarget(null);
        if (wildlife != null) wildlife.interruptSleep();
        applyTameState();
    }

    /** Removing a tamed creature from wild population accounting; called on tame and on reload. */
    public void applyTameState() {
        setPersistenceRequired();
        naturalWildlife = false;
    }

    /** Aerial feeding truce: the feeder is ignored by this creature for a bounded window. */
    public void grantFeedingTruce(Player feeder) {
        taming().grantTruce(feeder.getUUID(), Config.FEEDING_TRUCE_TICKS.get());
        if (getTarget() == feeder) setTarget(null);
    }

    public boolean feedingTruce(Player player) {
        return player != null && taming().truceActive(player.getUUID());
    }

    /** Attacking the truce holder ends the truce immediately. */
    public void cancelFeedingTruce(@Nullable Entity source) {
        taming().cancelTruce(source == null ? null : source.getUUID());
    }

    // ------------------------------------------------------------------------------ riding

    /** Seat position in world space, used for the rider placement and the mount room check. */
    public Vec3 seatPosition() {
        var seat = rideProfile();
        return position().add(CreatureRideController.localToWorld(this, seat.seatX(), seat.seatY(), seat.seatZ()));
    }

    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        var seat = rideProfile();
        return new Vec3(seat.seatX(), seat.seatY(), seat.seatZ())
                .yRot(-getYRot() * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    @Override protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger instanceof LivingEntity rider) rider.yBodyRot = getYRot() + (float) rideProfile().yawOffset();
    }

    /** Only a conscious, tamed, saddled creature may be controlled. */
    @Override public @Nullable LivingEntity getControllingPassenger() {
        if (TorporService.restricted(this) || !isTamed() || !isSaddled()) return null;
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        var input = CreatureRideController.input(controller, getXRot());
        // Flight and swimming both steer in three dimensions; a ground mount stays level.
        return CreatureRideController.toLocal(input, rideProfile().mode() != CreatureRideProfile.MovementMode.GROUND);
    }

    @Override protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        setYRot(controller.getYRot());
        yRotO = yBodyRot = yHeadRot = getYRot();
        setXRot(net.minecraft.util.Mth.clamp(controller.getXRot() * 0.5f, -45f, 45f));
        setSpeed(CreatureRideController.riddenSpeed(this, rideProfile()));
        riddenTicks = 40;
    }

    @Override protected float getRiddenSpeed(Player controller) {
        return CreatureRideController.riddenSpeed(this, rideProfile());
    }

    /** True while a player is driving this creature, so the animation bridge and the AI can stand aside. */
    public boolean isRidden() {
        return getControllingPassenger() != null;
    }

    /** Ticks since the last ridden input, used to keep the locomotion clip alive while mounted. */
    public int riddenTicks() {
        return riddenTicks;
    }

    // ------------------------------------------------------------------------- interaction

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        var state = TamingService.of(this);
        if (TorporService.restricted(this)) return interactWhileUnconscious(player);
        if (state.tamed()) return interactWhileTamed(player, hand);
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (level().isClientSide()) {
            // Client-side prediction only: the server decides the real outcome and consumes the item.
            return profile().accepts(player.getItemInHand(hand)) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        var result = TamingService.feedByHand(this, player, hand);
        if (result == TamingFeedback.Result.ACCEPTED) return InteractionResult.SUCCESS;
        TamingFeedback.denied(player, this, result);
        return InteractionResult.SUCCESS;
    }

    /**
     * A wild creature is reachable only while unconscious, and only by the player holding the attempt; an
     * absent or expired lease is open to anyone, and opening the inventory takes that lease over.
     */
    private InteractionResult interactWhileUnconscious(Player player) {
        if (profile().method() != TamingMethod.KNOCKOUT) {
            if (!level().isClientSide()) TamingFeedback.denied(player, this, TamingFeedback.Result.WRONG_STATE);
            return InteractionResult.SUCCESS;
        }
        if (TorporService.feedable(this) && TamingService.canAccess(this, player)) {
            if (level().isClientSide()) return InteractionResult.SUCCESS;
            TamingService.claimIfFree(this, player);
            openInventory(player);
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide()) TamingFeedback.notFeedable(player, this);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult interactWhileTamed(Player player, InteractionHand hand) {
        boolean client = level().isClientSide();
        boolean owner = isOwnedBy(player);
        // Non-owners are decided server-side; the client only predicts for the owner.
        if (!owner && client) return InteractionResult.PASS;
        // The whistle owns orders and petting; everything else keeps the mount and inventory contract.
        if (player.getItemInHand(hand).is(ModContent.COMPANION_WHISTLE.get())) {
            if (!owner && !TribeService.canCommand(this, player)) {
                denied(player, "taming.arksurvivalreturns.denied.command");
                return InteractionResult.PASS;
            }
            if (client) return InteractionResult.SUCCESS;
            if (player.isSecondaryUseActive()) pet(player);
            else CompanionService.orderCommand(this, player);
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            if (!owner && !TribeService.canAccessCargo(this, player)) {
                denied(player, "taming.arksurvivalreturns.denied.cargo");
                return InteractionResult.PASS;
            }
            if (client) return InteractionResult.SUCCESS;
            openInventory(player);
            return InteractionResult.SUCCESS;
        }
        if (!CreatureRideController.canMount(this, player)) {
            if (client) return InteractionResult.PASS;
            if (player instanceof ServerPlayer server) {
                server.sendSystemMessage(CreatureRideController.mountFailure(this, player), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (!CreatureRideController.hasRoomForRider(this, player)) {
            if (client) return InteractionResult.PASS;
            if (player instanceof ServerPlayer server) {
                server.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "taming.arksurvivalreturns.mount.noroom"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (client) return InteractionResult.SUCCESS;
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    /** Tells a tribe member why an action is not permitted; strangers stay silent. */
    private void denied(Player player, String key) {
        if (level().isClientSide() || !TribeService.isTribeMember(this, player)) return;
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key, getDisplayName()), true);
        }
    }

    private void openInventory(Player player) {
        if (player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CreatureMountMenu(id, inventory, this),
                    getDisplayName()), buffer -> buffer.writeVarInt(getId()));
        }
    }

    /** A short affectionate response: hearts, a call and attention, on a cooldown to avoid spam. */
    private void pet(Player player) {
        if (petCooldown > 0) return;
        petCooldown = Config.COMPANION_PET_COOLDOWN_TICKS.get();
        TamingFeedback.hearts(this);
        playSound(getAmbientSound(), 0.8f, 1.1f);
        getLookControl().setLookAt(player, 30, 30);
    }

    /** Hearts on success, smoke on a failed attempt: the vanilla horse presentation. */
    @Override public void handleEntityEvent(byte id) {
        if (id == 7) TamingFeedback.particles(this, net.minecraft.core.particles.ParticleTypes.HEART);
        else if (id == 6) TamingFeedback.particles(this, net.minecraft.core.particles.ParticleTypes.SMOKE);
        else super.handleEntityEvent(id);
    }

    /** Contents are dropped exactly once, so a failed attempt never silently eats a player's supplies. */
    @Override protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
            boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        tamingInventory.dropAll(level, position());
        if (!harnessSlot.isEmpty()) {
            ItemStack rig = harnessSlot.removeItemNoUpdate(0);
            var drop = new net.minecraft.world.entity.item.ItemEntity(level, getX(), getY(), getZ(), rig);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            harnessSlot.setChanged();
        }
    }
    public int creatureLevel() { return entityData.get(LEVEL); }
    public UUID packId() { return packId; }
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
        // Torpor outranks every routine, including FloatGoal: goal arbitration only lets a strictly lower
        // priority replace a running goal, so an equal-priority FloatGoal would otherwise lock JUMP and
        // keep an unconscious swimming creature fully active.
        goalSelector.addGoal(-1, new UnconsciousBehavior(this));
        if (!(this instanceof FlyingCreatureEntity) && !(this instanceof dev.nez.arksurvivalreturns.feature.aquatic.AquaticCreatureEntity))
            goalSelector.addGoal(0, new FloatGoal(this));
        wildlife = createController();
        goalSelector.addGoal(1, wildlife);
        // Same priority as the wild routine; taming makes the two mutually exclusive through canUse.
        goalSelector.addGoal(1, new CompanionGoal(this));
        // Work orders hand the creature to the harvest routine instead of the companion routine.
        goalSelector.addGoal(1, new WorkGoal(this));
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
        // Reads the thread-safe danger view: chunk-generation workers must not touch saved data.
        int danger = level() instanceof ServerLevel world
                ? dev.nez.arksurvivalreturns.feature.spawn.ProgressionData.dangerAt(world, blockPosition()) : -1;
        recordOrigin(danger);
        var tier = danger >= 1 && danger <= BiomeTier.values().length
                ? BiomeTier.values()[danger - 1] : BiomeTier.EASY;
        int a = Config.MIN_LEVEL.get(tier).get(), b = Config.MAX_LEVEL.get(tier).get();
        initializeLevel(Math.min(a, b) + random.nextInt(Math.abs(a - b) + 1));
    }
    /** Records the danger band where this creature first appeared; -1 when unknown. */
    public void recordOrigin(int danger) {
        if (originDanger < 0) originDanger = danger;
    }
    /** Origin band used by the map entitlement: a rank-5 tame opens the map once. */
    public int originDanger() { return originDanger; }
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
        if (riddenTicks > 0) riddenTicks--;
        if (petCooldown > 0) petCooldown--;
        if (strikeWindup > 0) strikeWindup--;
        if (strikeCooldown > 0) strikeCooldown--;
        super.tick();
        if (!level().isClientSide()) resolveStrike();
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
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        locomotion.update(Math.sqrt(dx * dx + dy * dy + dz * dz) * 20);
    }
    /** Real travel with hysteresis; never GeckoLib's smoothed render-state movement flag. */
    public boolean isLocomoting() { return locomotion.moving(); }
    /** Companion steering entry point; realm classes override the hooks, never this contract. */
    public final void companionTravel(Vec3 point, double speed) {
        companionTraveling = true;
        steerCompanion(point, speed);
    }
    /** Stops companion steering without touching any other navigation owner. */
    public final void companionHold() {
        companionTraveling = false;
        stopCompanion();
    }
    /** True while the companion goal holds a movement intent, whatever the realm. */
    public boolean isCompanionTraveling() { return companionTraveling; }
    /** Realm hook: walkers and amphibious species path toward the point. */
    protected void steerCompanion(Vec3 point, double speed) {
        if (companionDestination != null && companionDestination.distanceToSqr(point) < 4 && !getNavigation().isDone()) return;
        companionDestination = point;
        getNavigation().moveTo(point.x, point.y, point.z, speed);
    }
    protected void stopCompanion() {
        companionDestination = null;
        getNavigation().stop();
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
    // Natural wildlife persists like vanilla animals; the population budget owns culling.
    @Override public boolean removeWhenFarAway(double distance) { return !isNaturalWildlife(); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("CreatureLevel", creatureLevel());
        output.putBoolean("LevelInitialized", levelInitialized);
        output.putInt("OriginDanger", originDanger);
        output.putString("PackId", packId.toString());
        output.putBoolean("NaturalWildlife", naturalWildlife);
        tamingInventory.serialize(output.child("TamingInventory"));
        ContainerHelper.saveAllItems(output.child("Harness"), harnessSlot.getItems());
        wildlife.save(output);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(LEVEL, LevelScaling.clamp(input.getIntOr("CreatureLevel", 1)));
        levelInitialized = input.getBooleanOr("LevelInitialized", false);
        originDanger = input.getIntOr("OriginDanger", -1);
        naturalWildlife = input.getBooleanOr("NaturalWildlife", !isPersistenceRequired());
        input.child("TamingInventory").ifPresent(tamingInventory::deserialize);
        input.child("Harness").ifPresent(harness -> {
            harnessSlot.clearContent();
            ContainerHelper.loadAllItems(harness, harnessSlot.getItems());
        });
        // Adopt the new movement baseline for old saves without stacking a multiplier on each load.
        applyMovementTuning();
        wildlife.load(input);
        try { packId = UUID.fromString(input.getStringOr("PackId", packId.toString())); }
        catch (IllegalArgumentException ignored) { packId = UUID.randomUUID(); }
        // Vanilla persists attributes and current HP. Never reroll or heal saved creatures.
    }
    /** True when this creature may begin a new melee attack. */
    public boolean canStrike() {
        return pendingStrike == null && strikeCooldown <= 0 && !TorporService.restricted(this);
    }

    /** True while a scheduled strike is still in its wind-up. */
    public boolean isStriking() { return pendingStrike != null; }

    /**
     * Begins a melee attack: the attack clip starts now and its damage lands on the authored hit frame,
     * so the bite connects with the animation instead of the instant the AI decided to attack. The AI
     * must never call {@code doHurtTarget} directly, or the two desynchronize again.
     */
    public boolean strike(Entity target) {
        if (!(level() instanceof ServerLevel) || !(target instanceof LivingEntity living)
                || !living.isAlive() || living.level() != level() || !canStrike() || !hasLineOfSight(living)) return false;
        var clips = CreatureAttackClips.of(species);
        if (clips == null) return false;
        triggerAnim("attack", "strike");
        pendingStrike = living;
        strikeWindup = hitDelayTicks(clips);
        strikeCooldown = cooldownTicks(clips);
        return true;
    }

    private static int hitDelayTicks(CreatureAttackClips.Clips clips) {
        int delay = (int)Math.round(clips.attackTicks() * Config.COMBAT_HIT_FRACTION.get());
        return Math.clamp(delay, 1, Math.max(1, clips.attackTicks() - 1));
    }

    private static int cooldownTicks(CreatureAttackClips.Clips clips) {
        return Math.max(clips.attackTicks(),
                clips.attackTicks() + (int)Math.round(clips.attackTicks() * Config.COMBAT_RECOVERY_FRACTION.get()));
    }

    /** Resolves a scheduled strike once its wind-up has elapsed; an evasive target makes it whiff. */
    private void resolveStrike() {
        if (pendingStrike == null || strikeWindup > 0) return;
        LivingEntity target = pendingStrike;
        pendingStrike = null;
        if (!(level() instanceof ServerLevel world) || !isAlive() || !target.isAlive()
                || target.level() != level() || TorporService.restricted(this)
                || !isWithinMeleeAttackRange(target) || !hasLineOfSight(target)) return;
        applyingStrike = true;
        try {
            if (applyStrikeDamage(world, target)) world.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    target.getX(), target.getY(0.5), target.getZ(), 6, 0.2, 0.2, 0.2, 0.08);
        } finally {
            applyingStrike = false;
        }
    }
    /** Applies a resolved strike; realm classes with their own damage gates may replace the rule. */
    protected boolean applyStrikeDamage(ServerLevel level, Entity target) {
        return doHurtTarget(level, target);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (!hit) return false;
        // A scheduled strike already played its clip; a direct call still needs the swing animation.
        if (!applyingStrike) triggerAnim("attack", "strike");
        // Any lethal hit feeds the mind, whether it landed via the wind-up or a direct call.
        if (target instanceof LivingEntity living && !living.isAlive() && species.predator
                && !(living instanceof Player) && wildlife != null) wildlife.onStrikeKill();
        return true;
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
    /** Triggered clips must play once even when the imported source montage is flagged to loop. */
    protected static RawAnimation oneShot(String clip) {
        return RawAnimation.begin().then(clip, LoopType.PLAY_ONCE);
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
            if (isRidden() || riddenTicks > 0) {
                boolean moving = isLocomoting();
                state.setControllerSpeed(moving
                        ? (float) MovementTuning.animationRate(animationBlocksPerSecond, species.height,
                                species.strideCycleSeconds(true), true, Config.STRIDE_SCALE.get(species).get())
                        : 1f);
                return state.setAndContinue(RawAnimation.begin().thenLoop(riddenClip(moving)));
            }
            var behavior = behavior();
            boolean running = behavior.combat() || behavior == BehaviorState.FLEE;
            boolean moving = isLocomoting();
            state.setControllerSpeed(moving ? (float)MovementTuning.animationRate(animationBlocksPerSecond,
                    species.height, species.strideCycleSeconds(running), running, Config.STRIDE_SCALE.get(species).get()) : 1);
            String clip = moving ? movingClip(running)
                    : behavior == BehaviorState.SLEEP ? species.sleepClip()
                    : behavior == BehaviorState.REST ? restingClip()
                    : (behavior == BehaviorState.FORAGE || behavior == BehaviorState.FEED || behavior == BehaviorState.DRINK)
                            && !species.additiveFood() ? species.foodClip() : standingClip();
            return state.setAndContinue(RawAnimation.begin().thenLoop(clip));
        }));
        registrar.add(new AnimationController<CreatureEntity>("feeding", 5, state ->
                species.additiveFood() && behavior() == BehaviorState.FEED && !TorporService.restricted(this)
                        ? state.setAndContinue(RawAnimation.begin().thenLoop(species.foodClip())) : com.geckolib.animation.object.PlayState.STOP).additiveAnimations());
        registrar.add(new AnimationController<CreatureEntity>("reaction", 4, state -> com.geckolib.animation.object.PlayState.STOP)
                .triggerableAnim("warn", oneShot(species.warningClip())));
        registrar.add(new AnimationController<CreatureEntity>("attack", 3, state -> com.geckolib.animation.object.PlayState.STOP)
                .triggerableAnim("strike", oneShot(species.attack)));
    }

    /** Mounted locomotion uses real movement, not the AI's navigation state. */
    protected String riddenClip(boolean moving) {
        return moving ? movingClip(true) : standingClip();
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
        boolean heavy = species.height >= 2.5f;
        playSound(heavy ? net.minecraft.sounds.SoundEvents.RAVAGER_STEP : net.minecraft.sounds.SoundEvents.SNIFFER_STEP,
                species.solitary() ? 0.7f : 0.25f,
                heavy ? Math.clamp(1.3f - species.height * 0.05f, 0.55f, 0.9f)
                        : species.solitary() ? 0.65f : 1.25f);
        // A heavy body kicks dust where the foot lands; client-only, so the server never fakes particles.
        if (heavy && level().isClientSide() && onGround())
            level().addParticle(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 0.05, getZ(), 0.0, 0.02, 0.0);
    }
    private record PackData(UUID id) implements SpawnGroupData {}
}
