package dev.nez.arksurvivalreturns.feature.farm;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import dev.nez.arksurvivalreturns.feature.camp.CampShapes;
import dev.nez.arksurvivalreturns.feature.camp.TallBlocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.Direction;
import java.util.Map;
import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Drying rack: raw food in, portable rations out, one interaction at a time. Two blocks tall: food hangs from
 * the top lines, rations cure on the bottom shelf. The lower half holds the block entity; racks still stack.
 */
public final class DryingRackBlock extends BaseEntityBlock {
    public static final MapCodec<DryingRackBlock> CODEC = simpleCodec(DryingRackBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty HANGING = IntegerProperty.create("hanging", 0, 3);
    public static final BooleanProperty READY = BooleanProperty.create("ready");
    public static final EnumProperty<Food> FOOD = EnumProperty.create("food", Food.class);
    public enum Food implements StringRepresentable {
        MEAT, FISH, BERRIES;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = CampShapes.horizontal(
            net.minecraft.world.phys.shapes.Shapes.or(Block.box(1, 0, 3, 15, 2.5, 13), uprights(2.5, 16)));
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = CampShapes.horizontal(
            net.minecraft.world.phys.shapes.Shapes.or(uprights(0, 15.6), Block.box(1, 12, 3, 15, 13.5, 13)));

    private static VoxelShape uprights(double y, double top) {
        return net.minecraft.world.phys.shapes.Shapes.or(Block.box(1, y, 3, 2.75, top, 4.75), Block.box(13.25, y, 3, 15, top, 4.75),
                Block.box(1, y, 11.25, 2.75, top, 13), Block.box(13.25, y, 11.25, 15, top, 13));
    }

    public DryingRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HANGING, 0).setValue(READY, false).setValue(FOOD, Food.MEAT)
                .setValue(TallBlocks.HALF, DoubleBlockHalf.LOWER));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HANGING, READY, FOOD, TallBlocks.HALF);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!TallBlocks.roomAbove(context)) return null;
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        return defaultBlockState().setValue(FACING, below.getBlock() instanceof DryingRackBlock
                ? below.getValue(FACING) : context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
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
        return (TallBlocks.isUpper(state) ? UPPER_SHAPES : LOWER_SHAPES).get(state.getValue(FACING));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return TallBlocks.isUpper(state) ? null : new DryingRackBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModContent.DRYING_RACK_BLOCK_ENTITY.get(), DryingRackBlockEntity::tick);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // An empty hand must fall through to useWithoutItem, or taking contents out never runs.
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(asItem())) return InteractionResult.PASS; // Let the block item place the next tier.
        if (!(level.getBlockEntity(TallBlocks.base(state, pos)) instanceof DryingRackBlockEntity rack)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return rack.insert(stack) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(TallBlocks.base(state, pos)) instanceof DryingRackBlockEntity rack)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack taken = rack.extract();
        if (taken.isEmpty()) return InteractionResult.SUCCESS;
        if (!player.getInventory().add(taken)) player.drop(taken, false);
        return InteractionResult.SUCCESS;
    }
}
