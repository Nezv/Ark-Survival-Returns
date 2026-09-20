package dev.nez.arksurvivalreturns.feature.flying;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.FlyingCreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Per-bird nest placement. No colony records are saved; every flyer owns the nest it claims. */
public final class Nests {
    public static boolean siteAllowed(ServerLevel world, Species species, BlockPos pos) {
        var profile = species.flyerProfile();
        if (!species.flyer() || profile == null || !SpawnRules.loaded(world, new AABB(pos).inflate(2)) || !world.canSeeSky(pos)) return false;
        var floor = world.getBlockState(pos.below());
        if (!floor.isFaceSturdy(world, pos.below(), Direction.UP) || !floor.is(SpawnRules.SURFACES)) return false;
        if (!profile.shoreSand()) {
            int floorY = species.flyerNestFloorY();
            return floorY <= 0 || pos.getY() - 1 >= floorY;
        }
        if (!floor.is(BlockTags.SAND)) return false;
        int radius = species.flyerShoreWaterRadius();
        // Sample a bounded shoreline grid. A missed site is retried later; never request terrain.
        for (int x = -radius; x <= radius; x += 2) for (int z = -radius; z <= radius; z += 2) {
            if (x*x + z*z > radius*radius) continue;
            int wx = pos.getX()+x, wz = pos.getZ()+z;
            var chunk = world.getChunkSource().getChunkNow(wx >> 4, wz >> 4); if (chunk == null) continue;
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx & 15, wz & 15);
            if (Math.abs(y - (pos.getY()-1)) > 4) continue;
            var water = new BlockPos(wx, y, wz);
            if (!SpawnRules.loaded(world, new AABB(water).inflate(1)) || !world.getFluidState(water).is(FluidTags.WATER)
                    || !world.getBlockState(water).is(Blocks.WATER) || !world.canSeeSky(water.above())) continue;
            // At least a 2x2 patch of actual water, not a single waterlogged block/puddle.
            if (world.getBlockState(water.east()).is(Blocks.WATER) && world.getBlockState(water.south()).is(Blocks.WATER)
                    && world.getBlockState(water.east().south()).is(Blocks.WATER)) return true;
        }
        return false;
    }
    /** Bounded search around the bird for a free nest spot. Returns the claimed position or null. */
    public static BlockPos placeNear(ServerLevel world, FlyingCreatureEntity bird) {
        var species = bird.species();
        var origin = bird.blockPosition();
        int radius = species.flyerProfile() == null ? 16 : species.flyerProfile().nestRadius();
        for (int attempt = 0; attempt < 24; attempt++) {
            var pos = attempt == 0 ? origin : SpawnRules.surface(world,
                    origin.getX() + world.getRandom().nextInt(radius*2+1) - radius,
                    origin.getZ() + world.getRandom().nextInt(radius*2+1) - radius);
            if (pos == null || Math.abs(pos.getY() - origin.getY()) > 12 || !world.isPositionEntityTicking(pos)
                    || !siteAllowed(world, species, pos) || !world.getBlockState(pos).isAir() || !world.getFluidState(pos).isEmpty()) continue;
            var box = SpawnRules.bounds(species, pos.above(4));
            if (!SpawnRules.loaded(world, box.inflate(1)) || !world.getWorldBorder().isWithinBounds(box)
                    || !world.noCollision(null, box, true) || box.maxY >= world.getMaxY()) continue;
            if (!world.setBlock(pos, ModContent.NESTS.get(species).get().defaultBlockState(), Block.UPDATE_ALL)) continue;
            return pos;
        }
        return null;
    }
    private Nests() {}
}
