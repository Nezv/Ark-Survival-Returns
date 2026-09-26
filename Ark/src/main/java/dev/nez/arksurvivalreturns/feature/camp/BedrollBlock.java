package dev.nez.arksurvivalreturns.feature.camp;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.item.context.BlockPlaceContext;
import java.util.Map;
import java.util.Optional;
import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Primitive Bedroll: a grass mat as long as a bed, flat on the ground, that sets the personal respawn point.
 *
 * <p>Two halves like a vanilla bed: the foot where it was placed, the head one block further in the facing
 * direction; only the head drops the item. The respawn position lives in the player's own saved data and
 * points at the head, so destroying the bedroll never strands the owner: the fallback remains the world
 * spawn. Sneak-use rolls it back up.
 */
public final class BedrollBlock extends Block {
    public static final MapCodec<BedrollBlock> CODEC = simpleCodec(BedrollBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    private static final Map<Direction, VoxelShape> SHAPES = CampShapes.horizontal(Block.box(1, 0, 0, 15, 3.75, 16));

    public BedrollBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The head half: where the respawn point is saved. */
    public static BlockPos head(BlockState state, BlockPos pos) {
        return state.getValue(PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(FACING));
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
        if (!level.isClientSide()) level.setBlock(head(state, pos), state.setValue(PART, BedPart.HEAD), UPDATE_ALL);
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

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        if (direction == towardPartner(state)) {
            return neighborState.is(this) && neighborState.getValue(PART) != state.getValue(PART) ? state : Blocks.AIR.defaultBlockState();
        }
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Creative breaking of the foot also clears the head, which would otherwise drop the item. */
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(PART) == BedPart.FOOT) {
            BlockPos headPos = head(state, pos);
            BlockState headState = level.getBlockState(headPos);
            if (headState.is(this) && headState.getValue(PART) == BedPart.HEAD) {
                level.setBlock(headPos, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, headPos, getId(headState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel world) || !(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() && Config.CAMP_BEDROLL_PICKUP.get()) return rollUp(world, pos, state, player);
        if (!Config.CAMP_BEDROLL_SETS_SPAWN.get()) {
            server.sendSystemMessage(Component.translatable("camp.arksurvivalreturns.bedroll_disabled"), true);
            return InteractionResult.SUCCESS;
        }
        server.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(level.dimension(), head(state, pos), server.getYRot(), server.getXRot()), false), true);
        server.sendSystemMessage(Component.translatable("camp.arksurvivalreturns.bedroll_set"), false);
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    /** Respawning needs this hook: NeoForge only knows vanilla beds, so without it the point is ignored. */
    @Override public Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition(BlockState state, EntityType<?> type,
            LevelReader level, BlockPos pos, float orientation) {
        return BedBlock.findStandUpPosition(type, level, pos, state.getValue(FACING), orientation)
                .map(standUp -> ServerPlayer.RespawnPosAngle.of(standUp, pos, 0.0f));
    }

    private InteractionResult rollUp(net.minecraft.server.level.ServerLevel world, BlockPos pos, BlockState state, Player player) {
        if (player.isSpectator()) return InteractionResult.SUCCESS;
        // Removing the head first: the foot then breaks through its shape update and drops nothing.
        BlockPos headPos = head(state, pos);
        BlockState headState = world.getBlockState(headPos);
        boolean removed = headState.is(this) && headState.getValue(PART) == BedPart.HEAD
                ? world.setBlock(headPos, Blocks.AIR.defaultBlockState(), UPDATE_ALL) : world.removeBlock(pos, false);
        if (!removed) return InteractionResult.SUCCESS;
        if (world.getBlockState(pos).is(this)) world.removeBlock(pos, false);
        var roll = new ItemStack(ModContent.BEDROLL.get());
        if (!player.getInventory().add(roll)) player.drop(roll, false);
        Component message = Component.translatable("camp.arksurvivalreturns.bedroll_picked");
        if (player instanceof ServerPlayer server) server.sendSystemMessage(message, true);
        else player.sendSystemMessage(message);
        return InteractionResult.SUCCESS;
    }
}
