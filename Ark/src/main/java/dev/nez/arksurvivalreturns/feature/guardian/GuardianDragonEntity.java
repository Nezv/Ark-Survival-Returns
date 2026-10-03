package dev.nez.arksurvivalreturns.feature.guardian;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.guardian.TribeProgressData;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** A persistent territorial dragon, generated once with its beacon. No key or activation step. */
public final class GuardianDragonEntity extends CreatureEntity {
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(GuardianDragonEntity.class, EntityDataSerializers.INT);
    private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.arksurvivalreturns.guardian_dragon"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private BlockPos beaconHome;
    private boolean initialized, rewarded;
    private UUID creditedPlayer;
    private int breathCooldown, breathWindup;
    private Player breathTarget;
    public static final int DEFENSE_RADIUS = 40;

    public GuardianDragonEntity(EntityType<? extends CreatureEntity> type, Level level) {
        super(type, level, Species.DRAGON);
        setNoGravity(true);
        lookControl = new net.minecraft.world.entity.ai.control.LookControl(this) {
            @Override public void tick() {}
        };
    }

    public static AttributeSupplier.Builder attributes() {
        return CreatureEntity.attributes(Species.DRAGON).add(Attributes.ARMOR, 8)
                .add(Attributes.MAX_HEALTH, 400).add(Attributes.FOLLOW_RANGE, 48);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b); b.define(VARIANT, 0);
    }
    @Override protected void registerGoals() {}
    public int beaconVariant() { return entityData.get(VARIANT); }
    public void setBeaconVariant(int variant) { entityData.set(VARIANT, Math.floorMod(variant, 3)); }
    public BlockPos beaconHome() { return beaconHome == null ? blockPosition() : beaconHome; }
    public void anchorAt(BlockPos home) {
        beaconHome = home.immutable(); setPersistenceRequired(); setNoGravity(true);
    }
    @Override public boolean isNaturalWildlife() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
    @Override public void onTamed(UUID owner) {}
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) { return InteractionResult.PASS; }

    /** Public for real-entity tests; applies vertical distance as well as horizontal distance. */
    public boolean defendsAgainst(Player player) {
        return Config.GUARDIAN_ENABLED.get() && player != null && player.isAlive() && player.level() == level()
                && !player.isCreative() && !player.isSpectator() && level().getDifficulty() != Difficulty.PEACEFUL
                && !dev.nez.arksurvivalreturns.feature.taming.TorporService.restricted(player)
                && !player.getData(dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments.DOWNED).downed()
                && player.position().distanceToSqr(Vec3.atCenterOf(beaconHome())) <= DEFENSE_RADIUS * DEFENSE_RADIUS;
    }

    @Override protected void customServerAiStep(ServerLevel world) {
        super.customServerAiStep(world);
        if (!initialized) {
            anchorAt(blockPosition());
            initializeLevel(1);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(Config.GUARDIAN_BASE_HEALTH.get());
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Species.DRAGON.damage * Config.GUARDIAN_DAMAGE_MULTIPLIER.get());
            getAttribute(Attributes.ARMOR).setBaseValue(Config.GUARDIAN_ARMOR.get());
            setHealth(getMaxHealth()); initialized = true;
        }
        setNoGravity(true); resetFallDistance();
        Player target = getTarget() instanceof Player p && defendsAgainst(p) && hasLineOfSight(p) ? p : null;
        if (tickCount % 20 == 0 || target == null) {
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : world.players()) if (defendsAgainst(p) && hasLineOfSight(p)) {
                double d = distanceToSqr(p);
                if (d < best) { target = p; best = d; }
            }
        }
        setTarget(target);
        setBehavior(target == null ? BehaviorState.ROAM : BehaviorState.DEFEND);
        Vec3 home = Vec3.atBottomCenterOf(beaconHome());
        double angle = tickCount * 0.022 + (getUUID().hashCode() & 255) * 0.024;
        // Orbit through the open centre and in front of/behind the monolith; never seek a ground nest.
        Vec3 destination = home.add(Math.cos(angle) * 2, Math.sin(angle * 2) * 3, Math.sin(angle) * 16);
        if (target != null) {
            Vec3 approach = target.position().add(0, 4, 0);
            destination = distanceToSqr(target) < 100 ? position().add(position().subtract(approach).normalize().scale(3)) : approach;
            if (breathCooldown <= 0 && distanceToSqr(target) <= 24 * 24) {
                breathTarget = target; breathWindup = 14; breathCooldown = 70;
                triggerAnim("attack", "strike");
            }
        }
        if (breathCooldown > 0) breathCooldown--;
        if (breathWindup > 0 && --breathWindup == 0 && breathTarget != null) {
            Player victim = breathTarget; breathTarget = null;
            if (defendsAgainst(victim) && distanceToSqr(victim) <= 24 * 24 && hasLineOfSight(victim)) {
                Vec3 mouth = position().add(0, getBbHeight() * 0.7, 0);
                Vec3 end = victim.getEyePosition();
                for (int i = 0; i < 12; i++) {
                    Vec3 p = mouth.lerp(end, i / 12.0);
                    world.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, .15, .15, .15, .01);
                }
                victim.hurtServer(world, damageSources().mobAttack(this), (float)getAttributeValue(Attributes.ATTACK_DAMAGE));
            }
        }
        if (position().distanceToSqr(home) > 48 * 48) destination = home;
        steer(world, destination, target == null ? .45 : .65);
        if (tickCount % 20 == 0) {
            bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
            bar.setColor(beaconVariant() == 0 ? BossEvent.BossBarColor.RED :
                    beaconVariant() == 1 ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.PURPLE);
            Set<ServerPlayer> viewers = new HashSet<>();
            for (ServerPlayer p : world.players())
                if (p.distanceToSqr(this) <= Config.GUARDIAN_BAR_RANGE.get() * Config.GUARDIAN_BAR_RANGE.get()) viewers.add(p);
            for (ServerPlayer p : List.copyOf(bar.getPlayers())) if (!viewers.contains(p)) bar.removePlayer(p);
            viewers.forEach(bar::addPlayer);
        }
    }

    private void steer(ServerLevel world, Vec3 destination, double speed) {
        Vec3 desired = destination.subtract(position());
        if (desired.lengthSqr() > 1) desired = desired.normalize().scale(speed);
        Vec3 velocity = getDeltaMovement().scale(.85).add(desired.scale(.15));
        // Test the complete moved body and only already-loaded chunks, including diagonal edge crossings.
        if (!GuardianStructure.arenaLoaded(world, BlockPos.containing(position().add(velocity)), 4)
                || !world.getWorldBorder().isWithinBounds(getBoundingBox().move(velocity))) {
            setDeltaMovement(Vec3.ZERO); return;
        }
        if (!world.noCollision(this, getBoundingBox().move(velocity).inflate(.25))) {
            Vec3 climb = new Vec3(0, .3, 0);
            setDeltaMovement(world.noCollision(this, getBoundingBox().move(climb).inflate(.25)) ? climb : Vec3.ZERO);
            return;
        }
        setDeltaMovement(velocity);
        Vec3 facing = getTarget() != null ? getTarget().position().subtract(position()) : velocity;
        if (facing.horizontalDistanceSqr() > .0001) {
            setYRot(Mth.approachDegrees(getYRot(), (float)(Math.atan2(facing.z, facing.x) * 180 / Math.PI - 90), 5));
            yBodyRot = yHeadRot = getYRot();
        }
        setXRot(Mth.approachDegrees(getXRot(), (float)(-Math.atan2(velocity.y, Math.max(.01, velocity.horizontalDistance())) * 180 / Math.PI), 3));
    }

    @Override public void travel(Vec3 input) { travelFlying(Vec3.ZERO, 0); resetFallDistance(); }
    @Override public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        boolean hit = super.hurtServer(world, source, amount);
        if (hit) {
            UUID credit = credit(source.getEntity());
            if (credit != null) creditedPlayer = credit;
            bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        }
        return hit;
    }
    private static UUID credit(Entity entity) {
        if (entity instanceof ServerPlayer p) return p.getUUID();
        if (entity instanceof CreatureEntity tame && tame.isTamed()) return tame.taming().owner();
        return null;
    }
    @Override public void die(DamageSource source) {
        // Resolve the lethal attacker before super dispatches death events.
        UUID credit = credit(source.getEntity());
        if (credit != null) creditedPlayer = credit;
        super.die(source);
        if (!isAlive() && level() instanceof ServerLevel world) reward(world);
        bar.removeAllPlayers();
    }
    private void reward(ServerLevel world) {
        if (rewarded || creditedPlayer == null) return;
        String key = "sky_beacon|" + world.dimension().identifier() + "|" + beaconHome().asLong();
        GuardianData data = GuardianData.get(world);
        if (data.find(key).map(GuardianEncounter::rewardsIssued).orElse(false)) return;
        rewarded = true;
        UUID tribe = TribeService.team(creditedPlayer).map(t -> t.getId()).orElse(creditedPlayer);
        Set<UUID> participants = new HashSet<>(); participants.add(creditedPlayer);
        for (ServerPlayer p : world.players())
            if (GuardianService.inTribe(p.getUUID(), tribe) && p.blockPosition().distSqr(beaconHome()) <= 80 * 80)
                participants.add(p.getUUID());
        data.put(new GuardianEncounter(key, world.dimension(), beaconHome(), SkyBeaconStructure.ID, tribe,
                GuardianState.DEFEATED, Optional.empty(), participants, Set.of(), getMaxHealth(), true, 0, 0));
        // The terrace is 18 blocks below the guardian's initial position; aerial kills retain reachable rewards.
        Vec3 landing = Vec3.atBottomCenterOf(beaconHome().below(16));
        drop(world, landing, ModContent.GUARDIAN_TROPHY.get().getDefaultInstance());
        if (!TribeProgressData.get(world).has(tribe, TribeProgressData.WORKSHOP_SCHEMATIC)) {
            TribeProgressData.get(world).grant(tribe, TribeProgressData.WORKSHOP_SCHEMATIC);
            drop(world, landing, ModContent.WORKSHOP_SCHEMATIC.get().getDefaultInstance());
        }
        for (UUID id : participants) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(id);
            if (p != null) TamingService.discovery(p, GuardianService.ADVANCEMENT);
        }
    }
    private static void drop(ServerLevel world, Vec3 p, net.minecraft.world.item.ItemStack stack) {
        ItemEntity item = new ItemEntity(world, p.x, p.y, p.z, stack);
        item.setDefaultPickUpDelay(); world.addFreshEntity(item);
    }
    @Override public void remove(RemovalReason reason) { bar.removeAllPlayers(); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("BeaconVariant", beaconVariant());
        out.putLong("BeaconHome", beaconHome().asLong());
        out.putBoolean("BeaconInitialized", initialized); out.putBoolean("BeaconRewarded", rewarded);
        if (creditedPlayer != null) out.putString("BeaconCredit", creditedPlayer.toString());
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setBeaconVariant(in.getIntOr("BeaconVariant", 0));
        beaconHome = BlockPos.of(in.getLongOr("BeaconHome", blockPosition().asLong()));
        initialized = in.getBooleanOr("BeaconInitialized", false);
        rewarded = in.getBooleanOr("BeaconRewarded", false);
        try { creditedPlayer = UUID.fromString(in.getStringOr("BeaconCredit", "")); }
        catch (IllegalArgumentException ignored) { creditedPlayer = null; }
        setNoGravity(true); setPersistenceRequired();
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar r) {
        r.add(new AnimationController<GuardianDragonEntity>("movement", 5, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop("animation.wyvern.fly"))));
        r.add(new AnimationController<GuardianDragonEntity>("attack", 3, state -> PlayState.STOP)
                .triggerableAnim("strike", oneShot("animation.wyvern.fly_fire")));
    }
}
