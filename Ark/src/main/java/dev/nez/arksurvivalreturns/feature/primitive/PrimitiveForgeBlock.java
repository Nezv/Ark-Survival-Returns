package dev.nez.arksurvivalreturns.feature.primitive;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.feature.camp.TallBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Primitive forge: hand-fed like the drying rack. Right-click with something smeltable to load it, with
 * fuel to stoke it (sneak to force an item that is both, such as logs, into the fuel slot), and with an
 * empty hand to take the result. A two-block clay bloomery: the lower half holds the block entity and the
 * mouth (the facing side), the upper half is the stack and chimney and mirrors LIT.
 */
public final class PrimitiveForgeBlock extends BaseEntityBlock {
    public static final MapCodec<PrimitiveForgeBlock> CODEC = simpleCodec(PrimitiveForgeBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape LOWER = Shapes.or(Block.box(0.5, 0, 0.5, 15.5, 3.5, 15.5), Block.box(1.5, 3.5, 1.5, 14.5, 16, 14.5));
    private static final VoxelShape UPPER = Shapes.or(Block.box(2.5, 0, 2.5, 13.5, 10, 13.5), Block.box(3.8, 10, 3.8, 12.2, 13, 12.2),
            Block.box(5, 13, 5, 11, 16, 11));

    public PrimitiveForgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false)
                .setValue(TallBlocks.HALF, DoubleBlockHalf.LOWER));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, TallBlocks.HALF);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!TallBlocks.roomAbove(context)) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return TallBlocks.isUpper(state) ? UPPER : LOWER;
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return TallBlocks.isUpper(state) ? null : new PrimitiveForgeBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, PrimitiveContent.PRIMITIVE_FORGE_BLOCK_ENTITY.get(), PrimitiveForgeBlockEntity::tick);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(TallBlocks.base(state, pos)) instanceof PrimitiveForgeBlockEntity forge)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        boolean toFuel = player.isSecondaryUseActive() || !forge.isSmeltable(stack);
        int moved = forge.insert(stack, toFuel ? PrimitiveForgeBlockEntity.FUEL : PrimitiveForgeBlockEntity.INPUT);
        return moved > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(TallBlocks.base(state, pos)) instanceof PrimitiveForgeBlockEntity forge)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack taken = forge.extract();
        if (!taken.isEmpty() && !player.getInventory().add(taken)) player.drop(taken, false);
        return InteractionResult.SUCCESS;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (TallBlocks.isUpper(state)) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0.0, 0.05, 0.0);
            return;
        }
        if (random.nextInt(10) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 1.0f, 0.8f, false);
        }
        if (random.nextInt(4) == 0) {
            Direction mouth = state.getValue(FACING);
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + mouth.getStepX() * 0.45, pos.getY() + 0.35,
                    pos.getZ() + 0.5 + mouth.getStepZ() * 0.45, 0.0, 0.0, 0.0);
        }
    }
}
