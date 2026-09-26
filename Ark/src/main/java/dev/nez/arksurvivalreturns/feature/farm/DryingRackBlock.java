package dev.nez.arksurvivalreturns.feature.farm;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import dev.nez.arksurvivalreturns.feature.camp.CampShapes;
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

/** Drying rack block: raw food in, portable rations out, one interaction at a time. */
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
    private static final Map<Direction, VoxelShape> SHAPES = CampShapes.horizontal(
            net.minecraft.world.phys.shapes.Shapes.or(Block.box(1, 0, 3, 15, 2.5, 13), Block.box(1, 2.5, 3, 2.75, 16, 4.75), Block.box(13.25, 2.5, 3, 15, 16, 4.75), Block.box(1, 2.5, 11.25, 2.75, 16, 13), Block.box(13.25, 2.5, 11.25, 15, 16, 13), Block.box(1, 14, 3, 15, 16, 13)));

    public DryingRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HANGING, 0).setValue(READY, false).setValue(FOOD, Food.MEAT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HANGING, READY, FOOD);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
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

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DryingRackBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof DryingRackBlockEntity rack)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return rack.insert(stack) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DryingRackBlockEntity rack)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack taken = rack.extract();
        if (taken.isEmpty()) return InteractionResult.SUCCESS;
        if (!player.getInventory().add(taken)) player.drop(taken, false);
        return InteractionResult.SUCCESS;
    }
}
