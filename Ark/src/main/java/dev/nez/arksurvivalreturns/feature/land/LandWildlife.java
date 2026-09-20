package dev.nez.arksurvivalreturns.feature.land;

import java.util.Map;
import java.util.WeakHashMap;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Bounded terrain helpers for roaming land and amphibious wildlife. No saved habitat records. */
public final class LandWildlife {
    private static final class Budget { long tick = Long.MIN_VALUE; int paths, plans; }
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();
    public static int roam(Species s) { return Config.LAND_ROAM.get(s.family()).get(); }
    public static int leash(Species s) { return Math.max(roam(s), Config.LAND_LEASH.get(s.family()).get()); }
    public static double waterWeight(Species species, BlockPos center, BlockPos water) {
        var f = species.family();
        return LandFamily.waterWeight(Math.sqrt(distanceSqr(center, water)),
                Config.LAND_WATER_PREFERRED.get(f).get(), Config.LAND_WATER_MAX.get(f).get());
    }
    public static double distanceSqr(BlockPos a, BlockPos b) {
        double x = (double) a.getX() - b.getX(), z = (double) a.getZ() - b.getZ(); return x * x + z * z;
    }
    public static boolean allowPath(ServerLevel world, boolean planning) {
        var budget = BUDGETS.computeIfAbsent(world, w -> new Budget());
        if (budget.tick != world.getGameTime()) { budget.tick = world.getGameTime(); budget.paths = 0; budget.plans = 0; }
        if (budget.paths >= 8 || planning && budget.plans >= 2) return false;
        budget.paths++; if (planning) budget.plans++; return true;
    }
    public static boolean navigationLoaded(ServerLevel world, CreatureEntity mob, double range) {
        return SpawnRules.loaded(world, new AABB(mob.blockPosition()).inflate(range + 9));
    }
    public static void clear() { BUDGETS.clear(); }
    /**
     * Cold hydration: snow cover, powder snow, or ice that a bounded check confirms sits over water.
     * Arbitrary packed-ice structures are never treated as a drink source. Null means unknown terrain.
     */
    public static Boolean coldHydration(ServerLevel world, BlockPos pos) {
        if (world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
        var state = world.getBlockState(pos);
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return true;
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE))
            return world.getFluidState(pos.below()).is(FluidTags.WATER);
        return world.getFluidState(pos).is(FluidTags.WATER) && world.canSeeSky(pos.above());
    }
    public static boolean forage(ServerLevel world, Species species, BlockPos pos) {
        if (world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
        var ground = world.getBlockState(pos.below());
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM)
                || ground.is(Blocks.MOSS_BLOCK) || ground.is(Blocks.PALE_MOSS_BLOCK)) return true;
        // Snow cover over browse ground is used as a browsing abstraction, never as terrain damage.
        if (species != null && species.coldAdapted())
            return ground.is(Blocks.SNOW) || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.POWDER_SNOW);
        return false;
    }
    private LandWildlife() {}
}
