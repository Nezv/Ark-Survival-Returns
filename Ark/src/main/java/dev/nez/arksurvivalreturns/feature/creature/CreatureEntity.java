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
import dev.nez.arksurvivalreturns.feature.spawn.DangerTier;
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
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
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
    private BehaviorTier behaviorTier = BehaviorTier.FULL;
    private int engagedTicks;
    private @Nullable BehaviorProfile behaviorProfile;
    private float turnBase = -1;
    /** Client: smoothed body yaw change in degrees per tick, positive when turning right. */
    private float bodyTurn;

    public CreatureEntity(EntityType<? extends CreatureEntity> type, Level level, Species species) {
        super(type, level);
        this.species = species;
        moveControl = new CreatureMoveControl(this);
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
        dev.nez.arksurvivalreturns.feature.tech.TechEvents.onTamed(this, owner);
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
    /** The animation-timed step inside the current behaviour state; synchronized for the clip choice. */
    public BehaviorAction action() {
        return BehaviorAction.values()[Math.clamp(entityData.get(ACTION), 0, BehaviorAction.values().length - 1)];
    }
    public void setAction(BehaviorAction action) { entityData.set(ACTION, action.ordinal()); }

    /** This species' runtime clips, lengths, authored ground speeds and behaviour roles. */
    public ClipBook clips() { return BehaviorClips.of(species.id); }

    /** What the choreography needs to know about this species. */
    public BehaviorProfile behaviorProfile() {
        if (behaviorProfile == null) {
            var family = species.flyer() ? null : species.family();
            boolean stalker = family == dev.nez.arksurvivalreturns.feature.land.LandFamily.COLD_STALKER
                    || family == dev.nez.arksurvivalreturns.feature.land.LandFamily.COLD_PREDATOR
                    || family == dev.nez.arksurvivalreturns.feature.land.LandFamily.AMPHIBIOUS
                    || family == dev.nez.arksurvivalreturns.feature.land.LandFamily.SWAMP_PACK;
            String warning = species.warningClip();
            behaviorProfile = new BehaviorProfile(species.id, species.predator, species.timid(), species.herd(), stalker,
                    !species.predator && !species.aquatic(), species.height,
                    warning.equals(species.idle) ? null : warning, clips());
        }
        return behaviorProfile;
    }

    /**
     * Degrees per tick the body may turn. Rigs with a turn clip turn about 75 degrees per clip cycle;
     * the others turn slower the taller they stand. Running and walking turns are wider arcs.
     */
    public float turnRate(boolean running, boolean moving) {
        if (turnBase < 0) {
            var turn = clips().role(ClipRole.TURN_LEFT);
            float byClip = turn == null ? 0 : 75f / turn.ticks();
            float bySize = (float) (18 / Math.sqrt(Math.max(1, species.height)));
            turnBase = Math.clamp(turn == null ? bySize : byClip, 1.5f, 12f);
        }
        return turnBase * (running ? 2.5f : 1f) * (moving ? 1.5f : 1f);
    }

    /**
     * Navigation speed modifier for wandering: near the pace this species' walk clip was authored for,
     * so its feet do not slide, varied per individual so a herd does not march in step.
     */
    public double wanderModifier() {
        double attribute = getAttributeValue(Attributes.MOVEMENT_SPEED);
        double target = MovementTuning.wanderBlocksPerSecond(clips().groundSpeed(species.walk),
                MovementTuning.blocksPerSecond(attribute), Desync.speedFactor(getUUID().getLeastSignificantBits()));
        return Math.clamp(MovementTuning.modifierFor(target, attribute), 0.3, 0.9);
    }

    /** Level of detail of this creature's behaviour; always FULL for tames, riders and alarmed animals. */
    public BehaviorTier behaviorTier() { return behaviorTier; }

    /** Keeps full behaviour for a while, e.g. after a hit from beyond the full-detail radius. */
    public void engage(int ticks) {
        engagedTicks = Math.max(engagedTicks, ticks);
        behaviorTier = BehaviorTier.FULL;
    }

    /** Recomputes the tier now; called on a staggered one-second timer and by tests. */
    public void refreshBehaviorTier() {
        if (!(level() instanceof ServerLevel world)) return;
        var state = behavior();
        if (!BehaviorLod.enabled() || isTamed() || isRidden() || isVehicle() || TorporService.restricted(this)
                || engagedTicks > 0 || state.alarm() || state == BehaviorState.INVESTIGATE || getTarget() != null) {
            behaviorTier = BehaviorTier.FULL;
            return;
        }
        behaviorTier = BehaviorTier.classify(BehaviorLod.nearestObserverSq(world, this), behaviorTier, BehaviorLod.radii());
    }

    /** Whether a dormant creature is still close enough for its pose to follow the sleep schedule. */
    public boolean posedWhenDormant() {
        return level() instanceof ServerLevel world
                && BehaviorTier.posed(BehaviorLod.nearestObserverSq(world, this), BehaviorLod.radii());
    }

    /** Plays a one-shot reaction clip (when the rig has one) and its call. */
    public void playCue(BehaviorAction.Cue cue) {
        boolean clip = cue == BehaviorAction.Cue.WARN ? !species.warningClip().equals(species.idle) : clips().has(cue.role());
        if (clip) triggerAnim("reaction", cue == BehaviorAction.Cue.WARN ? "warn" : cue.key());
        switch (cue) {
            case WARN -> playSound(species.predator ? net.minecraft.sounds.SoundEvents.RAVAGER_ROAR
                    : net.minecraft.sounds.SoundEvents.POLAR_BEAR_WARNING, 1.0f, species.solitary() ? 0.7f : 1.2f);
            case STARTLE, WAKE -> playSound(net.minecraft.sounds.SoundEvents.SNIFFER_SNIFFING, 0.7f, species.solitary() ? 0.7f : 1.2f);
            case LOOK, SNIFF -> playSound(net.minecraft.sounds.SoundEvents.SNIFFER_SNIFFING, 0.35f, species.solitary() ? 0.7f : 1.2f);
            default -> {}
        }
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
        builder.define(ACTION, BehaviorAction.IDLE.ordinal());
        builder.define(NIGHT_ACTIVE, false);
    }
    /**
     * The body follows the facing at the creature's own turn rate, moving or not, on both sides. Vanilla
     * lets a standing mob's body trail its head and then snap when it walks off, which reads as a glitch
     * on a large animal turning in place.
     */
    @Override protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
        return new net.minecraft.world.entity.ai.control.BodyRotationControl(this) {
            @Override public void clientTick() {
                float limit = isRidden() || riddenTicks > 0 ? 180f : turnRate(true, true) * 1.5f;
                yBodyRot = net.minecraft.util.Mth.approachDegrees(yBodyRot, getYRot(), limit);
                float head = net.minecraft.util.Mth.wrapDegrees(yHeadRot - yBodyRot);
                if (Math.abs(head) > 50f) yHeadRot = yBodyRot + Math.signum(head) * 50f;
            }
        };
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
        var tier = danger >= 1 && danger <= DangerTier.values().length
                ? DangerTier.values()[danger - 1] : DangerTier.EASY;
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
        if (!level().isClientSide()) {
            if (engagedTicks > 0) engagedTicks--;
            if (Math.floorMod(tickCount + getId(), 20) == 0) refreshBehaviorTier();
        } else {
            bodyTurn += (net.minecraft.util.Mth.wrapDegrees(yBodyRot - yBodyRotO) - bodyTurn) * 0.4f;
        }
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
        if (target instanceof LivingEntity living && !living.isAlive()) {
            dev.nez.arksurvivalreturns.feature.tech.TechEvents.onTameKill(this);
        }
        return true;
    }
    @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float damage) {
        boolean hit = super.hurtServer(level, source, damage);
        if (hit && isAlive()) {
            // A hit from any distance brings the full behaviour back for the fight or the escape.
            engage(200);
            if (clips().has(ClipRole.HURT) && !isStriking() && !TorporService.restricted(this)) triggerAnim("reaction", "hurt");
        }
        if (hit && isAlive() && WildlifeSenses.hasNightCycle(this)) {
            boolean sleeping = behavior().sleeping();
            wildlife.interruptSleep();
            if (sleeping) playCue(BehaviorAction.Cue.WAKE);
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
                String clip = riddenClip(moving);
                state.setControllerSpeed(moving ? gaitRate(clip, true) : 1f);
                return state.setAndContinue(RawAnimation.begin().thenLoop(clip));
            }
            var behavior = behavior();
            var action = action();
            if (isLocomoting()) {
                boolean running = running(action, behavior);
                String clip = movingClip(running);
                state.setControllerSpeed(gaitRate(clip, running));
                return state.setAndContinue(RawAnimation.begin().thenLoop(clip));
            }
            // Turning in place steps with the rig's own turn clip instead of sliding round on the spot.
            String turn = !swimming() && Math.abs(bodyTurn) > 0.8f ? turnClip(bodyTurn > 0) : null;
            if (turn != null) {
                var authored = clips().clip(turn);
                float perTick = authored == null ? 3f : 75f / authored.ticks();
                state.setControllerSpeed(Math.clamp(Math.abs(bodyTurn) / perTick, 0.5f, 2f));
                return state.setAndContinue(RawAnimation.begin().thenLoop(turn));
            }
            state.setControllerSpeed(individualRate());
            return state.setAndContinue(RawAnimation.begin().thenLoop(idleClip(action, behavior)));
        }));
        registrar.add(new AnimationController<CreatureEntity>("feeding", 5, state ->
                species.additiveFood() && eating(action(), behavior()) && !TorporService.restricted(this)
                        ? state.setAndContinue(RawAnimation.begin().thenLoop(species.foodClip())) : com.geckolib.animation.object.PlayState.STOP).additiveAnimations());
        var reaction = new AnimationController<CreatureEntity>("reaction", 4, state -> com.geckolib.animation.object.PlayState.STOP)
                .triggerableAnim("warn", oneShot(species.warningClip()));
        var book = clips();
        for (var cue : BehaviorAction.Cue.values())
            if (cue.role() != null && book.has(cue.role())) reaction.triggerableAnim(cue.key(), oneShot(book.name(cue.role())));
        if (book.has(ClipRole.HURT)) reaction.triggerableAnim("hurt", oneShot(book.name(ClipRole.HURT)));
        registrar.add(reaction);
        registrar.add(new AnimationController<CreatureEntity>("attack", 3, state -> com.geckolib.animation.object.PlayState.STOP)
                .triggerableAnim("strike", oneShot(species.attack)));
    }

    private static boolean running(BehaviorAction action, BehaviorState behavior) {
        return switch (action) {
            case RUN, CHASE, BOLT -> true;
            case WALK, STALK -> false;
            default -> behavior.combat() || behavior == BehaviorState.FLEE;
        };
    }

    private static boolean eating(BehaviorAction action, BehaviorState behavior) {
        return action == BehaviorAction.GRAZE || action == BehaviorAction.DRINK || action == BehaviorAction.FEED
                || behavior == BehaviorState.FEED || behavior == BehaviorState.FORAGE || behavior == BehaviorState.DRINK;
    }

    /** The clip a creature holds while it stands: sleeping, resting, eating, threatening or idle. */
    protected String idleClip(BehaviorAction action, BehaviorState behavior) {
        if (behavior == BehaviorState.SLEEP || action == BehaviorAction.SLEEP) return species.sleepClip();
        if (behavior == BehaviorState.REST || action == BehaviorAction.REST) {
            String bask = clips().name(ClipRole.REST);
            return bask != null && !swimming() ? bask : restingClip();
        }
        if (eating(action, behavior) && !species.additiveFood()) return species.foodClip();
        if (action == BehaviorAction.THREAT && !swimming()) {
            String threat = clips().name(ClipRole.THREAT);
            if (threat != null) return threat;
        }
        return standingClip();
    }

    /** Playback rate that makes a gait clip's feet match the ground speed, varied per individual. */
    protected float gaitRate(String clip, boolean running) {
        double natural = clips().groundSpeed(clip);
        double stride = Config.STRIDE_SCALE.get(species).get();
        double rate = Double.isNaN(natural)
                ? MovementTuning.animationRate(animationBlocksPerSecond, species.height, species.strideCycleSeconds(running), running, stride)
                : MovementTuning.matchedRate(animationBlocksPerSecond, natural, stride);
        return (float) rate * individualRate();
    }

    /** Per-individual animation rate so herd mates drift out of step. */
    protected float individualRate() { return Desync.animationRate(getUUID().getLeastSignificantBits()); }

    /** The rig's turn clip for a direction, or null; the client option swaps sides for mirrored rigs. */
    protected @Nullable String turnClip(boolean right) {
        boolean mirror = level().isClientSide() && dev.nez.arksurvivalreturns.NighttimeClientConfig.MIRROR_TURN_CLIPS.get();
        return clips().name(right != mirror ? ClipRole.TURN_RIGHT : ClipRole.TURN_LEFT);
    }

    /** Client: smoothed body yaw change in degrees per tick, positive when turning right. */
    public float bodyTurn() { return bodyTurn; }

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
