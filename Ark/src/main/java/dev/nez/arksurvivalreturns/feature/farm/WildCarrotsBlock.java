package dev.nez.arksurvivalreturns.feature.farm;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wild Carrots: carrot plants that grew on their own, always ripe, in clumps on the grass of open plains and meadows
 * (worldgen wild_carrots). They stand on soil, never on tilled land, and do not grow or spread; broken, they give carrots
 * (loot table blocks/wild_carrots). The only source of carrots in a world without villages.
 */
public final class WildCarrotsBlock extends VegetationBlock {
    public static final MapCodec<WildCarrotsBlock> CODEC = simpleCodec(WildCarrotsBlock::new);
    /** A ripe carrot plant's height. */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 9.0, 16.0);

    public WildCarrotsBlock(Properties properties) { super(properties); }

    @Override public MapCodec<WildCarrotsBlock> codec() { return CODEC; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Soil the bushes grow on, but not farmland: these are wild. */
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(FarmTags.PLANTABLE_ON) && !state.is(Blocks.FARMLAND);
    }
}
