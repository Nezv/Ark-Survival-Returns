package dev.nez.arksurvivalreturns.feature.aquatic;

import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Bounded local water reads for water-bound residents. Nothing here requests a chunk. */
public final class Water {
    private static final int MAX_SCAN = 40;
    /** Water surface of a loaded column, or null when the column is dry or unloaded. */
    public static BlockPos surfaceWater(ServerLevel world, int x, int z) {
        var chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return null;
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        if (top <= world.getMinY() || top >= world.getMaxY()) return null;
        var pos = new BlockPos(x, top, z);
        return world.getFluidState(pos).is(FluidTags.WATER) ? pos : null;
    }
    /** Connected water depth below a surface block, capped so a scan is always bounded. */
    public static int depth(ServerLevel world, BlockPos surface) {
        int depth = 0;
        for (int y = surface.getY(); y > surface.getY() - MAX_SCAN && y > world.getMinY(); y--) {
            if (!world.getFluidState(new BlockPos(surface.getX(), y, surface.getZ())).is(FluidTags.WATER)) break;
            depth++;
        }
        return depth;
    }
    /** Water column bounds at a position, used by the steering goal to stay submerged. */
    public static int[] column(ServerLevel world, BlockPos pos) {
        var surface = surfaceWater(world, pos.getX(), pos.getZ());
        if (surface == null) return null;
        int depth = depth(world, surface);
        return new int[]{surface.getY(), Math.max(surface.getY() - depth + 1, world.getMinY() + 1)};
    }
    /** True when the column can hold this species' body. */
    public static boolean siteAllowed(ServerLevel world, Species species, BlockPos surface) {
        if (surface == null) return false;
        var pos = surfaceWater(world, surface.getX(), surface.getZ());
        if (pos == null || pos.getY() != surface.getY()) return false;
        int depth = depth(world, pos);
        if (depth < dev.nez.arksurvivalreturns.Config.WILDLIFE_WATER_DEPTH.get() || depth < Math.ceil(species.height)) return false;
        double y = pos.getY() - depth + 1.0;
        var box = new AABB(pos.getX() + 0.5 - species.width / 2.0, y, pos.getZ() + 0.5 - species.width / 2.0,
                pos.getX() + 0.5 + species.width / 2.0, y + species.height, pos.getZ() + 0.5 + species.width / 2.0);
        return loaded(world, box) && world.noCollision(null, box, false);
    }
    private static boolean loaded(ServerLevel world, AABB bounds) {
        for (int cx = ((int) Math.floor(bounds.minX)) >> 4; cx <= ((int) Math.floor(bounds.maxX)) >> 4; cx++)
            for (int cz = ((int) Math.floor(bounds.minZ)) >> 4; cz <= ((int) Math.floor(bounds.maxZ)) >> 4; cz++)
                if (world.getChunkSource().getChunkNow(cx, cz) == null) return false;
        return true;
    }
    private Water() {}
}
