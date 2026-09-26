package dev.nez.arksurvivalreturns.feature.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * Rules shared by camp stations that stand two blocks tall (forge, drying rack), following vanilla doors:
 * the lower half owns the block entity and the drop, the upper half mirrors its state, and neither half
 * survives without the other.
 */
public final class TallBlocks {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public static boolean isUpper(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER;
    }

    /** Where the block entity lives: the lower half. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return isUpper(state) ? pos.below() : pos;
    }

    /** Placement needs a free block above, inside the world. */
    public static boolean roomAbove(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(context);
    }

    public static void placeUpper(Level level, BlockPos pos, BlockState lower) {
        if (!level.isClientSide()) level.setBlock(pos.above(), lower.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * The shape update toward the other half: air when the partner is gone, the upper half copying the
     * lower one otherwise. Null for every other direction, so the block applies its own rules.
     */
    public static @Nullable BlockState partnerUpdate(BlockState state, Direction direction, BlockState neighbor) {
        boolean upper = isUpper(state);
        if (direction != (upper ? Direction.DOWN : Direction.UP)) return null;
        if (!neighbor.is(state.getBlock()) || neighbor.getValue(HALF) == state.getValue(HALF)) return Blocks.AIR.defaultBlockState();
        return upper ? neighbor.setValue(HALF, DoubleBlockHalf.UPPER) : state;
    }

    /** Breaking the upper half without a drop (creative, wrong tool) also removes the lower half silently. */
    public static void preventLowerDrop(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide() || !isUpper(state) || !(player.isCreative() || !player.hasCorrectToolForDrops(state, level, pos))) return;
        BlockPos below = pos.below();
        BlockState lower = level.getBlockState(below);
        if (lower.is(state.getBlock()) && !isUpper(lower)) {
            level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            level.levelEvent(player, 2001, below, Block.getId(lower));
        }
    }

    private TallBlocks() {}
}
