package dev.nez.arksurvivalreturns.feature.station;

import java.util.Map;
import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.feature.camp.TallBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Saddlery: a saddler's bench under a saddle horse and a tack rack, two blocks tall. Both halves open the
 * same workstation graph, bound to the lower one; neither stands without the other ({@link TallBlocks}).
 */
public final class SaddleryBlock extends StationBlock {
    public static final MapCodec<SaddleryBlock> CODEC = simpleCodec(SaddleryBlock::new);
    /** Facing north: the bench, then the saddle on its horse and the tack rack along the back. */
    private static final Map<Direction, VoxelShape> LOWER = Shapes.rotateHorizontal(Shapes.or(Block.box(0, 0, 0, 16, 14.5, 16),
            Block.box(1, 14.5, 5, 15, 16, 11.5), Block.box(0, 14.5, 12.5, 16, 16, 16)));
    private static final Map<Direction, VoxelShape> UPPER = Shapes.rotateHorizontal(Shapes.or(Block.box(1, 0, 5, 15, 11.5, 11.5),
            Block.box(0, 0, 12.5, 16, 16, 16)));

    public SaddleryBlock(Properties properties) {
        super(Kind.SADDLERY, properties);
        registerDefaultState(defaultBlockState().setValue(TallBlocks.HALF, DoubleBlockHalf.LOWER));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TallBlocks.HALF);
    }

    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return TallBlocks.roomAbove(context) ? super.getStateForPlacement(context) : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        TallBlocks.placeUpper(level, pos, state);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        BlockState partner = TallBlocks.partnerUpdate(state, direction, neighborState);
        return partner != null ? partner : super.updateShape(state, level, ticks, pos, direction, neighbor, neighborState, random);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        TallBlocks.preventLowerDrop(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (TallBlocks.isUpper(state) ? UPPER : LOWER).get(state.getValue(FACING));
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override public BlockPos menuPos(BlockState state, BlockPos pos) { return TallBlocks.base(state, pos); }
}
