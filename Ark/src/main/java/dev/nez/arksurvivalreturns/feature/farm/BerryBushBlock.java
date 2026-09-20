package dev.nez.arksurvivalreturns.feature.farm;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Supplier;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plantable Ark berry bush. Ages 0-3 grow by random tick (loaded chunks only, exactly like a vanilla
 * crop), a ripe bush yields two to three berries on use and resets to growing. Planting is handled by
 * {@link FarmEvents}; the bush itself never has an item form, it drops its berries when broken ripe.
 */
public final class BerryBushBlock extends VegetationBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final MapCodec<BerryBushBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("berry").forGetter(block -> block.berry.get()),
            propertiesCodec()
    ).apply(instance, (berry, properties) -> new BerryBushBlock(() -> berry, properties)));

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
    private final Supplier<Item> berry;

    public BerryBushBlock(Supplier<Item> berry, Properties properties) {
        super(properties);
        this.berry = berry;
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override public MapCodec<BerryBushBlock> codec() { return CODEC; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(FarmTags.PLANTABLE_ON);
    }

    @Override protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 3;
    }

    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < 9) return;
        if (random.nextDouble() >= Config.FARM_BERRY_GROWTH.get()) return;
        level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (state.getValue(AGE) < 3) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        harvest((ServerLevel) level, pos, state, player);
        return InteractionResult.SUCCESS;
    }

    /** Public so the headless suite can drive a harvest without interaction plumbing. */
    public static void harvest(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        ItemStack yield = new ItemStack(((BerryBushBlock) state.getBlock()).berry.get(), 2 + level.getRandom().nextInt(2));
        if (!player.getInventory().add(yield)) player.drop(yield, false);
        level.setBlock(pos, state.setValue(AGE, 1), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS,
                1.0f, 0.8f + level.getRandom().nextFloat() * 0.4f);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
