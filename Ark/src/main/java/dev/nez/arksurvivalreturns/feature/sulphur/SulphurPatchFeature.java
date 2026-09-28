package dev.nez.arksurvivalreturns.feature.sulphur;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A small sulphur patch on a cave floor: the placement chain (count + in-square + a full-height range +
 * an environment scan down through air onto solid ground, the same combination vanilla uses for
 * lush-cave moss and clay) hands this feature an air position sitting right above cave stone. From
 * there it only ever reads and writes blocks in that same chunk column (an explicit
 * {@link SectionPos} check on every candidate position), so it can never force a neighbour chunk to
 * load. The centre always becomes budding sulphur topped with a random bud stage; up to four cardinal
 * neighbours may join as plain sulphur block, and at most one of those also buds.
 */
public final class SulphurPatchFeature extends Feature<NoneFeatureConfiguration> {
    private static final Direction[] SPREAD = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    /** Chance each cardinal neighbour joins the patch as plain sulphur block. */
    private static final float NEIGHBOUR_CHANCE = 0.55f;
    /** Chance a neighbour that joined also becomes a second budding block (at most one ever does). */
    private static final float SECOND_BUD_CHANCE = 0.35f;

    public SulphurPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!level.getBlockState(origin).isAir()) return false;

        BlockPos center = origin.below();
        if (!isPatchGround(level, center)) return false;

        int chunkX = SectionPos.blockToSectionCoord(center.getX());
        int chunkZ = SectionPos.blockToSectionCoord(center.getZ());
        boolean grewBud = false;
        int placed = 0;
        for (int i = -1; i < SPREAD.length; i++) {
            BlockPos floor = i < 0 ? center : center.relative(SPREAD[i]);
            if (i >= 0 && random.nextFloat() > NEIGHBOUR_CHANCE) continue;
            // Never touch a position outside the chunk column the feature was asked to decorate.
            if (SectionPos.blockToSectionCoord(floor.getX()) != chunkX || SectionPos.blockToSectionCoord(floor.getZ()) != chunkZ) continue;
            if (!isPatchGround(level, floor)) continue;
            BlockPos air = floor.above();
            if (!level.getBlockState(air).isAir()) continue;

            boolean budding = i < 0 || (!grewBud && random.nextFloat() < SECOND_BUD_CHANCE);
            level.setBlock(floor, (budding ? SulphurContent.BUDDING_SULPHUR.get() : SulphurContent.SULPHUR_BLOCK.get())
                    .defaultBlockState(), Block.UPDATE_CLIENTS);
            placed++;
            if (budding) {
                grewBud = true;
                Block stage = randomBudStage(random);
                BlockState budState = stage.defaultBlockState()
                        .setValue(SulphurClusterBlock.FACING, Direction.UP)
                        .setValue(SulphurClusterBlock.WATERLOGGED, false);
                level.setBlock(air, budState, Block.UPDATE_CLIENTS);
            }
        }
        return placed > 0;
    }

    private static boolean isPatchGround(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.BASE_STONE_OVERWORLD) && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /** Weighted like a young geode pocket: mostly small growth, a cluster only rarely. */
    private static Block randomBudStage(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.45f) return SulphurContent.SMALL_SULPHUR_BUD.get();
        if (roll < 0.70f) return SulphurContent.MEDIUM_SULPHUR_BUD.get();
        if (roll < 0.90f) return SulphurContent.LARGE_SULPHUR_BUD.get();
        return SulphurContent.SULPHUR_CLUSTER.get();
    }
}
