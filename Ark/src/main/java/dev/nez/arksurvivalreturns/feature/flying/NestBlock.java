package dev.nez.arksurvivalreturns.feature.flying;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/**
 * Inert, non-ticking nest. The egg is part of the nest's look and has no item (BUG-16): breaking a nest that holds
 * one angers the bird that owns it.
 */
public final class NestBlock extends Block {
    public static final MapCodec<NestBlock> CODEC = simpleCodec(NestBlock::new);
    public static final BooleanProperty EGG = BooleanProperty.create("egg");
    public NestBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(EGG, true)); }
    @Override public MapCodec<NestBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(EGG); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(1, 0, 1, 15, 5, 15); }
    public Species species() {
        for (var entry : ModContent.NESTS.entrySet()) if (entry.getValue().isBound() && entry.getValue().get() == this) return entry.getKey();
        return Species.PTERANODON;
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }
    /** The nest has no item form. */
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }
    public static void disturb(ServerLevel world, BlockPos pos, Player thief) {
        for (var bird : world.getEntitiesOfClass(FlyingCreatureEntity.class, new AABB(pos).inflate(48),
                b -> b.isAlive() && pos.equals(b.nestPosition()))) bird.defendEgg(thief);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getValue(EGG) && level instanceof ServerLevel world) disturb(world, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }
}
