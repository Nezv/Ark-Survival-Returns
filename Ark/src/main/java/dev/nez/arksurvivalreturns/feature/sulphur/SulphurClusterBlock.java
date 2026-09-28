package dev.nez.arksurvivalreturns.feature.sulphur;

import java.util.Map;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Mirrors vanilla {@code AmethystClusterBlock}: a directional, waterloggable crystal growth. One class
 * serves all four growth stages (small/medium/large bud, cluster); only the height and width differ,
 * exactly like vanilla registers {@code AMETHYST_CLUSTER}/{@code LARGE_AMETHYST_BUD}/etc from one class.
 */
public final class SulphurClusterBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<SulphurClusterBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.FLOAT.fieldOf("height").forGetter(b -> b.height),
                    Codec.FLOAT.fieldOf("width").forGetter(b -> b.width),
                    propertiesCodec())
            .apply(i, SulphurClusterBlock::new));
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private final float height;
    private final float width;
    private final Map<Direction, VoxelShape> shapes;

    public SulphurClusterBlock(float height, float width, BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false).setValue(FACING, Direction.UP));
        this.shapes = Shapes.rotateAll(Block.boxZ(width, 16.0f - height, 16.0));
        this.height = height;
        this.width = width;
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockPos adjacent = pos.relative(direction.getOpposite());
        return level.getBlockState(adjacent).isFaceSturdy(level, adjacent, direction);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return directionToNeighbour == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return defaultBlockState().setValue(WATERLOGGED, level.getFluidState(pos).is(Fluids.WATER)).setValue(FACING, context.getClickedFace());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, FACING);
    }
}
