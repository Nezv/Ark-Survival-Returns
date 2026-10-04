package dev.nez.arksurvivalreturns.feature.primitive;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Places one loose rock on the ground under the position the placed feature picked, the way No Tree
 * Punching does: the ground must be in {@code #arksurvivalreturns:primitive/loose_rock_ground} and the
 * rock's look follows that ground. The feature finds the ground itself, down through air, canopy and
 * water, so a forest floor, a sea bed and a cave floor get their rocks too; a rock takes the place of
 * low plants and lies waterlogged under water.
 */
public final class LooseRockFeature extends Feature<NoneFeatureConfiguration> {
    /** How far below the picked position the ground may be: the tallest canopy, or a cave's height. */
    private static final int REACH = 48;

    public LooseRockFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos.MutableBlockPos pos = context.origin().mutable();
        for (int step = 0; step < REACH && pos.getY() > level.getMinY(); step++, pos.move(Direction.DOWN)) {
            BlockState here = level.getBlockState(pos);
            boolean water = isWater(here);
            BlockPos below = pos.below();
            BlockState ground = level.getBlockState(below);
            if (ground.is(PrimitiveContent.LOOSE_ROCK_GROUND) && ground.isFaceSturdy(level, below, Direction.UP)) {
                if (!water && !isClear(here)) return false;
                level.setBlock(pos, PrimitiveContent.LOOSE_ROCK.get().defaultBlockState()
                        .setValue(LooseRockBlock.VARIANT, LooseRockBlock.Variant.of(ground))
                        .setValue(LooseRockBlock.WATERLOGGED, water), Block.UPDATE_CLIENTS);
                return true;
            }
            if (!water && !isClear(here) && !here.is(BlockTags.LEAVES) && !here.is(BlockTags.LOGS)) return false;
        }
        return false;
    }

    private static boolean isWater(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /** Air, a snow layer or a low plant: what a rock may lie in the place of. A tall plant would lose its foot. */
    private static boolean isClear(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof DoublePlantBlock);
    }
}
