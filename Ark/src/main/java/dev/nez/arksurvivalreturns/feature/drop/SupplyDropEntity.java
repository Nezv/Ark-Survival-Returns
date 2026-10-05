package dev.nez.arksurvivalreturns.feature.drop;

import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A loot crate on its way down under a parachute. It sinks at a steady pace through air, plants and leaves,
 * on both sides so the descent is smooth, and where it meets the ground or water the parachute goes and a
 * {@link LootCrateBlock} is left. It only moves in chunks that tick, so it never loads one.
 */
public final class SupplyDropEntity extends Entity {
    /** Blocks a tick: three a second. */
    public static final double FALL_SPEED = 0.15;
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(SupplyDropEntity.class, EntityDataSerializers.INT);

    public SupplyDropEntity(EntityType<? extends SupplyDropEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(0, -FALL_SPEED, 0);
    }

    public SupplyDropEntity(Level level, double x, double y, double z, SupplyTier tier) {
        this(DropContent.SUPPLY_DROP.get(), level);
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
        setTier(tier);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(TIER, 0);
    }

    public SupplyTier tier() { return SupplyTier.values()[Math.floorMod(entityData.get(TIER), SupplyTier.values().length)]; }

    public void setTier(SupplyTier tier) { entityData.set(TIER, tier.ordinal()); }

    @Override public void tick() {
        super.tick();
        setPos(getX(), getY() - FALL_SPEED, getZ());
        if (!(level() instanceof ServerLevel world)) return;
        BlockPos at = blockPosition();
        if (getY() < world.getMinY() - 8) discard();
        else if (!world.getFluidState(at).isEmpty()) land(world, at);
        else if (ground(world, at)) land(world, at.above());
    }

    /** Something to stand on: leaves and anything without a collision shape let the crate through. */
    public static boolean ground(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.is(BlockTags.LEAVES) && !state.getCollisionShape(world, pos).isEmpty();
    }

    /** The parachute goes; the crate stands where the drop came to rest, with its loot and its hour. */
    private void land(ServerLevel world, BlockPos pos) {
        discard();
        if (!world.isInWorldBounds(pos)) return;
        SupplyTier tier = tier();
        // Whatever stood in the way (a flower, a torch, leaves) drops as if broken; a fluid is simply displaced.
        if (!world.getBlockState(pos).isAir() && world.getFluidState(pos).isEmpty()) world.destroyBlock(pos, true);
        world.setBlockAndUpdate(pos, DropContent.LOOT_CRATE.get().defaultBlockState().setValue(LootCrateBlock.TIER, tier));
        if (world.getBlockEntity(pos) instanceof LootCrateBlockEntity crate)
            crate.arm(world.getGameTime() + Config.DROP_LIFETIME.get(), world.getRandom().nextLong());
        world.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + 1.5, pos.getZ() + .5, 24, .9, .5, .9, .02);
        world.playSound(null, pos, SoundEvents.WOOL_FALL, SoundSource.BLOCKS, 1.2f, .8f);
        world.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1f);
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("tier", tier().id);
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        setTier(SupplyTier.byId(input.getStringOr("tier", SupplyTier.WHITE.id)));
        setDeltaMovement(0, -FALL_SPEED, 0);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }

    @Override public boolean isAttackable() { return false; }

    @Override public boolean isIgnoringBlockTriggers() { return true; }

    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }

    /** The beam shows as far as the drop is tracked. */
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 320 * 320; }
}
