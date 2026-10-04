package dev.nez.arksurvivalreturns.feature.spawn;

import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * Sleeping-site rule of the land hunters. Trees in a biome are necessary but not sufficient. It no longer
 * gates spawning: hunters are placed by day as well and walk to cover, or stay awake where there is none.
 */
public final class TreeShelter {
    public static boolean required(Species species) {
        return species.predator && species.landHabitat() && species.sleeps();
    }

    public static boolean treeBiome(ServerLevelAccessor world, BlockPos pos) {
        // BiomeManager can sample across a quart boundary; check its neighboring columns as well.
        return SpawnRules.loaded(world, new AABB(pos).inflate(4))
                && SurfaceBiomes.profile(world.getBiome(pos)).treeBiome();
    }

    public static boolean sheltered(ServerLevelAccessor world, Species species, BlockPos feet) {
        if (!required(species)) return true;
        int offset = Math.max(1, (int) Math.ceil(species.width * 0.45));
        if (!SpawnRules.loaded(world, new AABB(feet).inflate(offset + 4)) || !treeBiome(world, feet)) return false;
        // The lying body must fit below foliage: never accept a bush at its feet or a stone roof.
        int bottom = feet.getY() + Math.max(2, (int) Math.ceil(species.height * 0.5));
        int top = Math.min(world.getMaxY(), feet.getY() + Math.max(16, (int) Math.ceil(species.height) + 20));
        if (!leavesAbove(world, feet.getX(), feet.getZ(), bottom, top)) return false;
        int covered = 1;
        for (int dx : new int[]{-offset, offset}) for (int dz : new int[]{-offset, offset})
            if (leavesAbove(world, feet.getX() + dx, feet.getZ() + dz, bottom, top)) covered++;
        return covered >= 3;
    }

    private static boolean leavesAbove(ServerLevelAccessor world, int x, int z, int bottom, int top) {
        var cursor = new BlockPos.MutableBlockPos();
        for (int y = bottom; y <= top; y++) {
            cursor.set(x, y, z);
            if (world.getBlockState(cursor).is(BlockTags.LEAVES)) return true;
        }
        return false;
    }

    private TreeShelter() {}
}
