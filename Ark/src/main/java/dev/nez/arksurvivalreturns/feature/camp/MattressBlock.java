package dev.nez.arksurvivalreturns.feature.camp;

import java.util.List;
import java.util.Map;
import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Prehistoric Mattress: a grass mat as long as a bed, flat on the ground — the exact look, footprint and
 * recipe of the original Primitive Bedroll. Unlike that block it never sets a respawn point; instead a
 * player who sleeps in it (see {@link AbstractSleepingBlock}) is removed along with its partner half,
 * with no drop, once they wake. Breaking it by hand before it is ever slept in still drops the item.
 *
 * <p>Two halves like a bed: the foot where it was placed, the head one block further in the facing
 * direction; only the head drops the item on a hand break. Sneak-use rolls it back up into an item.
 */
public final class MattressBlock extends AbstractSleepingBlock {
    public static final MapCodec<MattressBlock> CODEC = simpleCodec(MattressBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    private static final Map<Direction, VoxelShape> SHAPES = CampShapes.horizontal(Block.box(1, 0, 0, 15, 3.75, 16));

    public MattressBlock(Properties properties) {
        super(properties, true, false);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override protected BlockPos headPos(BlockState state, BlockPos pos) {
        return state.getValue(PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(FACING));
    }

    @Override protected List<BlockPos> otherParts(BlockState state, BlockPos pos) {
        return List.of(pos.relative(towardPartner(state)));
    }

    @Override protected ItemStack rolledItem() {
        return new ItemStack(ModContent.MATTRESS.get());
    }

    private static Direction towardPartner(BlockState state) {
        return state.getValue(PART) == BedPart.FOOT ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
    }

    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos head = context.getClickedPos().relative(facing);
        Level level = context.getLevel();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return level.getBlockState(head).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(head)
                && canSurvive(state, level, head) ? state : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide()) level.setBlock(headPos(state, pos), state.setValue(PART, BedPart.HEAD), UPDATE_ALL);
    }

    @Override protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override public MapCodec<MattressBlock> codec() {
        return CODEC;
    }

    @Override protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        if (direction == towardPartner(state)) {
            return neighborState.is(this) && neighborState.getValue(PART) != state.getValue(PART) ? state : Blocks.AIR.defaultBlockState();
        }
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }
}
