package dev.nez.arksurvivalreturns.feature.sulphur;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Mirrors vanilla {@code BuddingAmethystBlock}: on a random tick it picks one of its six faces and, if
 * that neighbour is air (or a full water source), grows the next stage there (small -> medium -> large
 * -> cluster). It has no loot table entry, so it drops nothing at all, even with silk touch, exactly
 * like budding amethyst.
 */
public final class BuddingSulphurBlock extends Block {
    public static final MapCodec<BuddingSulphurBlock> CODEC = simpleCodec(BuddingSulphurBlock::new);
    public static final int GROWTH_CHANCE = 5;
    private static final Direction[] DIRECTIONS = Direction.values();

    public BuddingSulphurBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROWTH_CHANCE) != 0) return;
        Direction growDirection = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos growPos = pos.relative(growDirection);
        BlockState relative = level.getBlockState(growPos);
        Block nextStage = null;
        if (canClusterGrowAtState(relative)) {
            nextStage = SulphurContent.SMALL_SULPHUR_BUD.get();
        } else if (relative.is(SulphurContent.SMALL_SULPHUR_BUD.get()) && relative.getValue(SulphurClusterBlock.FACING) == growDirection) {
            nextStage = SulphurContent.MEDIUM_SULPHUR_BUD.get();
        } else if (relative.is(SulphurContent.MEDIUM_SULPHUR_BUD.get()) && relative.getValue(SulphurClusterBlock.FACING) == growDirection) {
            nextStage = SulphurContent.LARGE_SULPHUR_BUD.get();
        } else if (relative.is(SulphurContent.LARGE_SULPHUR_BUD.get()) && relative.getValue(SulphurClusterBlock.FACING) == growDirection) {
            nextStage = SulphurContent.SULPHUR_CLUSTER.get();
        }
        if (nextStage == null) return;
        BlockState target = nextStage.defaultBlockState()
                .setValue(SulphurClusterBlock.FACING, growDirection)
                .setValue(SulphurClusterBlock.WATERLOGGED, relative.getFluidState().is(Fluids.WATER));
        level.setBlockAndUpdate(growPos, target);
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(net.minecraft.world.level.block.Blocks.WATER) && state.getFluidState().isFull();
    }
}
