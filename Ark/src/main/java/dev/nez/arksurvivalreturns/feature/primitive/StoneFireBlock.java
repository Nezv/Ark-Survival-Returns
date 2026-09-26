package dev.nez.arksurvivalreturns.feature.primitive;

import java.util.Locale;
import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlock;
import dev.nez.arksurvivalreturns.feature.tech.TechEvent;
import dev.nez.arksurvivalreturns.feature.tech.TechEventKind;
import dev.nez.arksurvivalreturns.feature.tech.TechService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The stone fire ("A little warmth"): the Prehistoric hearth that replaces the furnace for food.
 *
 * <p>Right-click with fuel to stack wood, with a fire starter or flint and steel to light it, with raw
 * food to put it on the spit, and with a stick to light a torch. An empty hand takes the cooked food.
 * A cooking pot placed on top sits on its trivet over the flames and uses the fire as heat.
 */
public final class StoneFireBlock extends BaseEntityBlock {
    public static final MapCodec<StoneFireBlock> CODEC = simpleCodec(StoneFireBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty FUELED = BooleanProperty.create("fueled");
    public static final BooleanProperty POT = BooleanProperty.create("pot");
    public static final EnumProperty<Spit> SPIT = EnumProperty.create("spit", Spit.class);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 7, 15);

    public enum Spit implements StringRepresentable {
        NONE, RAW, SEARED, COOKED;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public StoneFireBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false)
                .setValue(FUELED, false).setValue(POT, false).setValue(SPIT, Spit.NONE));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, FUELED, POT, SPIT);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POT, context.getLevel().getBlockState(context.getClickedPos().above()).getBlock() instanceof CookingPotBlock);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        return direction == Direction.UP ? state.setValue(POT, neighborState.getBlock() instanceof CookingPotBlock) : state;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(POT) ? Shapes.or(SHAPE, CookingPotBlock.seatedOutline(level.getBlockState(pos.above()))) : SHAPE;
    }

    /**
     * A pot seated on the stones sits inside this block's space, so looking at it hits the fire. Anything
     * aimed above the top course belongs to the pot (its block, and its inventory, are the block above).
     */
    private static boolean aimsAtPot(BlockState state, BlockPos pos, Vec3 hit) {
        return state.getValue(POT) && (hit.y - pos.getY()) * 16 >= CookingPotBlock.STONE_SEAT - 1;
    }

    /** Mining the seated pot takes the pot and leaves the fire burning. */
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack tool,
            boolean willHarvest, FluidState fluid) {
        if (player.pick(player.blockInteractionRange(), 1.0f, false) instanceof BlockHitResult aim
                && aim.getBlockPos().equals(pos) && aimsAtPot(state, pos, aim.getLocation())) {
            if (!level.isClientSide()) level.destroyBlock(pos.above(), !player.preventsBlockDrops(), player);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, tool, willHarvest, fluid);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StoneFireBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, PrimitiveContent.STONE_FIRE_BLOCK_ENTITY.get(), StoneFireBlockEntity::tick);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (aimsAtPot(state, pos, hit.getLocation())) {
            return level.getBlockState(pos.above()).useItemOn(stack, level, player, hand, hit.withPosition(pos.above()));
        }
        if (!(level.getBlockEntity(pos) instanceof StoneFireBlockEntity fire)) return InteractionResult.PASS;
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        boolean igniter = stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(PrimitiveContent.FIRE_STARTER.get());
        if (igniter) {
            if (state.getValue(LIT) || !fire.hasFuel()) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            boolean caught;
            if (stack.is(PrimitiveContent.FIRE_STARTER.get())) {
                caught = FireStarterItem.attempt(level, pos, stack, player, hand);
            } else {
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
                if (!player.getAbilities().instabuild) {
                    if (stack.is(Items.FIRE_CHARGE)) stack.shrink(1);
                    else stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                }
                caught = true;
            }
            if (caught && fire.light() && player instanceof ServerPlayer server) {
                TechService.notify(server, TechEvent.place(server, level.getBlockState(pos)));
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.STICK) && state.getValue(LIT)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            ItemStack torch = new ItemStack(Items.TORCH);
            if (!player.getInventory().add(torch)) player.drop(torch, false);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5f, 1.4f);
            if (player instanceof ServerPlayer server) TechService.notify(server, TechEvent.simple(TechEventKind.LIGHT_TORCH, server));
            return InteractionResult.SUCCESS;
        }
        if (fire.isCookable(stack)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            return fire.insertFood(stack) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (fire.isFuel(stack)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            return fire.addFuel(stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.PASS; // Anything else, such as a cooking pot, is placed normally.
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (aimsAtPot(state, pos, hit.getLocation())) {
            return level.getBlockState(pos.above()).useWithoutItem(level, player, hit.withPosition(pos.above()));
        }
        if (!(level.getBlockEntity(pos) instanceof StoneFireBlockEntity fire)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack taken = fire.extract();
        if (!taken.isEmpty() && !player.getInventory().add(taken)) player.drop(taken, false);
        return InteractionResult.SUCCESS;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextInt(8) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.6f + random.nextFloat(), random.nextFloat() * 0.7f + 0.6f, false);
        }
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.04, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 0.25,
                    pos.getZ() + 0.4 + random.nextDouble() * 0.2, 0.0, 0.01, 0.0);
        }
    }
}
