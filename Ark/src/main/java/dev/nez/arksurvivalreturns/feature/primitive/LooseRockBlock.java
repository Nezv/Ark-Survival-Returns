package dev.nez.arksurvivalreturns.feature.primitive;

import java.util.Locale;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A loose rock lying on the ground: the first resource of the Prehistoric age. It needs no tool,
 * right-click picks it up and breaking it drops the same rock. The variant only changes its look and
 * follows the ground it was generated on.
 */
public final class LooseRockBlock extends Block {
    public static final MapCodec<LooseRockBlock> CODEC = simpleCodec(LooseRockBlock::new);
    public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 3, 13);

    public enum Variant implements StringRepresentable {
        STONE, GRANITE, DIORITE, ANDESITE, SANDSTONE, RED_SANDSTONE;

        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

        /** The look that matches the ground; unknown ground reads as plain stone. */
        public static Variant of(BlockState ground) {
            if (ground.is(Blocks.GRANITE)) return GRANITE;
            if (ground.is(Blocks.DIORITE) || ground.is(Blocks.CALCITE)) return DIORITE;
            if (ground.is(Blocks.ANDESITE) || ground.is(Blocks.TUFF)) return ANDESITE;
            if (ground.is(Blocks.SAND) || ground.is(Blocks.SANDSTONE) || ground.is(Blocks.SUSPICIOUS_SAND)) return SANDSTONE;
            if (ground.is(Blocks.RED_SAND) || ground.is(Blocks.RED_SANDSTONE)
                    || ground.is(net.minecraft.tags.BlockTags.TERRACOTTA)) return RED_SANDSTONE;
            return STONE;
        }
    }

    public LooseRockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VARIANT, Variant.STONE));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(VARIANT,
                Variant.of(context.getLevel().getBlockState(context.getClickedPos().below())));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Picking a rock up is the same as breaking it, without the swing. */
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.removeBlock(pos, false);
        ItemStack rock = new ItemStack(PrimitiveContent.ROCK.get());
        if (!player.getInventory().add(rock)) player.drop(rock, false);
        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.6f, 1.2f);
        return InteractionResult.SUCCESS;
    }
}
