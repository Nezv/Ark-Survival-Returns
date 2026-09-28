package dev.nez.arksurvivalreturns.feature.camp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Bedroll: a reusable field bed built from the {@code camp/field_bedroll_*} art, occupying a 2x2 area
 * (four block instances) instead of the old single-column Primitive Bedroll. Right-clicking sleeps in it
 * and keeps the personal respawn point at the head-left cell; unlike the disposable {@link MattressBlock}
 * it is never removed.
 *
 * <p>Layout: {@link #PART} (HEAD/FOOT) runs along {@code FACING}, exactly like a vanilla bed. {@link
 * #SIDE} (LEFT/RIGHT) is the extra width, defined relative to the placer's right hand ({@code
 * facing.getClockWise()}), not to compass directions — that keeps the role-to-model mapping fixed
 * (head-left is always the {@code field_bedroll_head_west} art, head-right always {@code
 * field_bedroll_head_east}, etc.) no matter which way the bed is rotated: rotating the four cells
 * together by the same angle as {@code FACING} reproduces every other facing without swapping which
 * asset goes where, only the per-cell {@code y} rotation in the datagen'd blockstate changes.
 *
 * <p>The block clicked when placing becomes foot-left; the other three cells are derived from it and
 * placed by {@link #setPlacedBy}. Breaking, creative destruction and the loot table all key off the
 * head-left cell, which is also the only position {@link #getRespawnPosition} answers for.
 */
public final class BedrollBlock extends AbstractSleepingBlock {
    public static final MapCodec<BedrollBlock> CODEC = simpleCodec(BedrollBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final EnumProperty<BedrollBlock.Side> SIDE = EnumProperty.create("side", BedrollBlock.Side.class);
    private static final Map<Direction, VoxelShape> SHAPES = CampShapes.horizontal(Block.box(0, 0, 0, 16, 11.5, 16));

    public enum Side implements StringRepresentable {
        LEFT("left"), RIGHT("right");
        private final String name;
        Side(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public BedrollBlock(Properties properties) {
        super(properties, false, true);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT).setValue(SIDE, Side.LEFT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, SIDE);
    }

    /** The foot-left anchor cell that every other position of this instance is derived from. */
    private static BlockPos footLeft(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        BlockPos result = pos;
        if (state.getValue(PART) == BedPart.HEAD) result = result.relative(facing.getOpposite());
        if (state.getValue(SIDE) == Side.RIGHT) result = result.relative(right.getOpposite());
        return result;
    }

    @Override protected BlockPos headPos(BlockState state, BlockPos pos) {
        return footLeft(state, pos).relative(state.getValue(FACING));
    }

    @Override protected List<BlockPos> otherParts(BlockState state, BlockPos pos) {
        BlockPos footLeft = footLeft(state, pos);
        Direction facing = state.getValue(FACING), right = facing.getClockWise();
        BlockPos footRight = footLeft.relative(right);
        BlockPos headLeft = footLeft.relative(facing);
        BlockPos headRight = headLeft.relative(right);
        List<BlockPos> all = new ArrayList<>(List.of(footLeft, footRight, headLeft, headRight));
        all.remove(pos);
        return all;
    }

    @Override protected ItemStack rolledItem() {
        return new ItemStack(ModContent.BEDROLL.get());
    }

    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        Direction right = facing.getClockWise();
        BlockPos footLeft = context.getClickedPos();
        BlockPos footRight = footLeft.relative(right), headLeft = footLeft.relative(facing), headRight = headLeft.relative(right);
        List<BlockPos> cells = List.of(footLeft, footRight, headLeft, headRight);
        Level level = context.getLevel();
        for (BlockPos cell : cells) {
            if (!level.getBlockState(cell).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(cell)) return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, facing);
        for (BlockPos cell : cells) if (!canSurvive(state, level, cell)) return null;
        return state;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) return;
        Direction facing = state.getValue(FACING), right = facing.getClockWise();
        level.setBlock(pos.relative(right), state.setValue(SIDE, Side.RIGHT), UPDATE_ALL);
        level.setBlock(pos.relative(facing), state.setValue(PART, BedPart.HEAD), UPDATE_ALL);
        level.setBlock(pos.relative(facing).relative(right), state.setValue(PART, BedPart.HEAD).setValue(SIDE, Side.RIGHT), UPDATE_ALL);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override public MapCodec<BedrollBlock> codec() {
        return CODEC;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private boolean isPartner(BlockState neighbor, BlockState self) {
        return neighbor.is(this) && neighbor.getValue(FACING) == self.getValue(FACING);
    }

    /**
     * Only the head/foot pairing along {@code FACING} is destructive here, matching the original
     * Primitive Bedroll exactly: a world saved before this rewrite has that pairing but no "side" cell
     * at all, and must keep functioning as a (now narrower-looking) Bedroll rather than have its next
     * incidental neighbour update erase it for lacking a partner it never had. {@link #playerWillDestroy}
     * is what actually guarantees a deliberate break takes every cell of a properly placed instance.
     */
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        Direction towardLong = state.getValue(PART) == BedPart.FOOT ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
        if (direction == towardLong) {
            boolean valid = isPartner(neighborState, state) && neighborState.getValue(PART) != state.getValue(PART)
                    && neighborState.getValue(SIDE) == state.getValue(SIDE);
            return valid ? state : Blocks.AIR.defaultBlockState();
        }
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }
}
