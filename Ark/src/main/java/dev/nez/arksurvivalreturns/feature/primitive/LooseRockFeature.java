package dev.nez.arksurvivalreturns.feature.primitive;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Places one loose rock at the surface position the placed feature picked, the way No Tree Punching
 * does: the ground must be in {@code #arksurvivalreturns:primitive/loose_rock_ground} and the rock's
 * look follows that ground. Water, leaves and anything else leave the spot empty.
 */
public final class LooseRockFeature extends Feature<NoneFeatureConfiguration> {
    public LooseRockFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos pos = context.origin();
        BlockState here = level.getBlockState(pos);
        if (!here.isAir() && !here.is(Blocks.SNOW)) return false;
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.is(PrimitiveContent.LOOSE_ROCK_GROUND) || !ground.isFaceSturdy(level, below, Direction.UP)) return false;
        BlockState rock = PrimitiveContent.LOOSE_ROCK.get().defaultBlockState()
                .setValue(LooseRockBlock.VARIANT, LooseRockBlock.Variant.of(ground));
        level.setBlock(pos, rock, Block.UPDATE_CLIENTS);
        return true;
    }
}
