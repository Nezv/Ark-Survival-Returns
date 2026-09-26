package dev.nez.arksurvivalreturns.feature.kitchen;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Direction;
import dev.nez.arksurvivalreturns.feature.camp.CampShapes;
import java.util.Map;
import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Cooking pot block: opens the four-slot kitchen and drops its contents when removed. */
public final class CookingPotBlock extends BaseEntityBlock {
    public static final MapCodec<CookingPotBlock> CODEC = simpleCodec(CookingPotBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON_CAMPFIRE = BooleanProperty.create("on_campfire");
    private static final Map<Direction, VoxelShape> SHAPES = CampShapes.horizontal(
            net.minecraft.world.phys.shapes.Shapes.or(Block.box(3, 0, 3, 13, 8.7, 13), Block.box(1, 6, 5.5, 15, 7, 10.5)));
    private static final Map<Direction, VoxelShape> MOUNTED = CampShapes.horizontal(net.minecraft.world.phys.shapes.Shapes.or(SHAPES.get(Direction.NORTH),
            Block.box(4, -9, 5, 5, 0, 6), Block.box(11, -9, 5, 12, 0, 6), Block.box(7.5, -9, 11, 8.5, 0, 12)));

    public CookingPotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON_CAMPFIRE, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON_CAMPFIRE);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ON_CAMPFIRE, isHearth(context.getLevel().getBlockState(context.getClickedPos().below())));
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        return direction == Direction.DOWN ? state.setValue(ON_CAMPFIRE, isHearth(neighborState)) : state;
    }

    /** A campfire or a stone fire: the pot stands on its trivet over either. */
    public static boolean isHearth(BlockState below) {
        return below.getBlock() instanceof CampfireBlock
                || below.getBlock() instanceof dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlock;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(ON_CAMPFIRE) ? MOUNTED : SHAPES).get(state.getValue(FACING));
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CookingPotBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModContent.COOKING_POT_BLOCK_ENTITY.get(), CookingPotBlockEntity::tick);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider(
                            (id, inventory, actor) -> new CookingPotMenu(id, inventory, pos),
                            Component.translatable("block.arksurvivalreturns.cooking_pot")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
