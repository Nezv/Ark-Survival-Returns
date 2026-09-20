package dev.nez.arksurvivalreturns.feature.spawn;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Danger-gated spawn predicates. The vanilla spawner owns placement, caps and despawn. */
public final class SpawnRules {
    public static final TagKey<Block> SURFACES = TagKey.create(Registries.BLOCK, ArkSurvivalReturns.id("spawn_surfaces"));
    public static void placements(RegisterSpawnPlacementsEvent event) {
        for (var species : Species.values()) {
            var type = ModContent.CREATURES.get(species).get();
            if (species.aquatic()) {
                event.register(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        SpawnRules::canSpawnWater, RegisterSpawnPlacementsEvent.Operation.REPLACE);
            } else {
                event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        SpawnRules::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
            }
        }
    }
    public static boolean speciesAllowed(Species species, Holder<Biome> biome, int danger) {
        // Displayed local danger is authoritative; a distant plains region can also be level 5.
        return danger >= species.minimumDanger();
    }
    /**
     * Loaded-chunk check that also works inside the world-generation region. On the server an
     * unloaded chunk is refused without loading it; during chunk generation the region's own cache
     * answers, so generation never requests a chunk outside the region.
     */
    public static boolean loaded(ServerLevelAccessor world, AABB bounds) {
        int minX = ((int) Math.floor(bounds.minX)) >> 4, maxX = ((int) Math.floor(bounds.maxX)) >> 4;
        int minZ = ((int) Math.floor(bounds.minZ)) >> 4, maxZ = ((int) Math.floor(bounds.maxZ)) >> 4;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (world instanceof ServerLevel level) {
                    if (level.getChunkSource().getChunkNow(x, z) == null) return false;
                } else if (!world.hasChunkAt(new BlockPos(x << 4, world.getMinY(), z << 4))) {
                    return false;
                }
            }
        return true;
    }
    public static AABB bounds(Species species, BlockPos pos) {
        double half = species.width / 2.0;
        return new AABB(pos.getX() + 0.5 - half, pos.getY(), pos.getZ() + 0.5 - half,
                pos.getX() + 0.5 + half, pos.getY() + species.height, pos.getZ() + 0.5 + half);
    }
    /** Ground predicate shared by the vanilla spawner, the population budget and the headless tests. */
    public static boolean canSpawn(EntityType<CreatureEntity> type, ServerLevelAccessor world, EntitySpawnReason reason,
            BlockPos pos, RandomSource random) {
        return placement(type, world, pos) == Placement.OK;
    }
    /** Why a land placement was accepted or refused; also the diagnostic vocabulary of {@code /arkwildlife}. */
    public enum Placement { OK, CONFIG, LOADED, BOUNDS, FLUID, DANGER, FOOTPRINT, CLEARANCE }
    /**
     * Ordered funnel for land species. The vanilla spawner, the population budget and the operator
     * diagnostic all read the same reasons, so a report of "stuck on clearance" is the real predicate.
     */
    public static Placement placement(EntityType<CreatureEntity> type, ServerLevelAccessor world, BlockPos pos) {
        var level = world.getLevel();
        if (!Config.NATURAL_SPAWNS.get() || level.dimension() != Level.OVERWORLD
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)) return Placement.CONFIG;
        var species = ModContent.species(type);
        if (species.aquatic()) return Placement.CONFIG;
        var box = bounds(species, pos);
        if (!loaded(world, box.inflate(1))) return Placement.LOADED;
        if (!level.getWorldBorder().isWithinBounds(box)
                || pos.getY() < level.getMinY() + 1 || box.maxY >= level.getMaxY()) return Placement.BOUNDS;
        if (!world.getFluidState(pos).isEmpty()) return Placement.FLUID;
        if (!speciesAllowed(species, world.getBiome(pos), ProgressionData.dangerAt(level, pos))) return Placement.DANGER;
        if (!groundFits(world, species, pos, box)) return Placement.FOOTPRINT;
        return clearForBody(world, species, box) ? Placement.OK : Placement.CLEARANCE;
    }
    /**
     * Body clearance with foliage tolerance: leaves and other foliage may cross a large body, but solid
     * blocks and logs may not. Only the feet slab must be collision-free, so an apex can stand under a
     * forest canopy instead of every tree vetoing its spawn.
     */
    private static boolean clearForBody(ServerLevelAccessor world, Species species, AABB box) {
        double slabTop = Math.min(box.maxY, box.minY + 2.5);
        if (!world.noCollision(null, new AABB(box.minX, box.minY, box.minZ, box.maxX, slabTop, box.maxZ), true))
            return false;
        int stride = species.width >= 12 ? 3 : species.width >= 8 ? 2 : 1;
        for (int x = (int)Math.floor(box.minX + 0.001); x <= (int)Math.floor(box.maxX - 0.001); x += stride)
            for (int z = (int)Math.floor(box.minZ + 0.001); z <= (int)Math.floor(box.maxZ - 0.001); z += stride)
                for (int y = (int)Math.floor(slabTop); y < (int)Math.floor(box.maxY); y += stride) {
                    var pos = new BlockPos(x, y, z);
                    var state = world.getBlockState(pos);
                    if (state.isAir() || state.is(BlockTags.LEAVES)) continue;
                    if (!state.getCollisionShape(world, pos).isEmpty()) return false;
                }
        return true;
    }
    /** Water predicate for water-bound species: a deep, clear, loaded column. */
    public static boolean canSpawnWater(EntityType<CreatureEntity> type, ServerLevelAccessor world, EntitySpawnReason reason,
            BlockPos pos, RandomSource random) {
        var level = world.getLevel();
        if (!Config.NATURAL_SPAWNS.get() || level.dimension() != Level.OVERWORLD
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)) return false;
        var species = ModContent.species(type);
        if (!species.aquatic() || !world.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) return false;
        if (!speciesAllowed(species, world.getBiome(pos), ProgressionData.dangerAt(level, pos))) return false;
        var surface = dev.nez.arksurvivalreturns.feature.aquatic.Water.surfaceWater(level, pos.getX(), pos.getZ());
        if (surface == null) return false;
        int depth = dev.nez.arksurvivalreturns.feature.aquatic.Water.depth(level, surface);
        if (depth < Config.WILDLIFE_WATER_DEPTH.get() || depth < Math.ceil(species.height)) return false;
        double y = surface.getY() - depth + 1.0;
        var box = new AABB(pos.getX() + 0.5 - species.width / 2.0, y, pos.getZ() + 0.5 - species.width / 2.0,
                pos.getX() + 0.5 + species.width / 2.0, y + species.height, pos.getZ() + 0.5 + species.width / 2.0);
        return loaded(world, box) && level.getWorldBorder().isWithinBounds(box) && world.noCollision(null, box, false);
    }
    /**
     * Ground acceptance: the body stands on a mostly solid natural surface with only a small
     * height spread under its footprint. Ordinary slopes are accepted; cliffs and terrain poking
     * into the body are not. The previous exact-surface equality rejected most natural terrain.
     */
    private static boolean groundFits(ServerLevelAccessor world, Species species, BlockPos pos, AABB box) {
        int maxStep = species.width >= 8 ? 3 : species.width >= 3 ? 2 : 1;
        int stride = species.width >= 8 ? 2 : 1;
        int supported = 0, footprint = 0, low = pos.getY(), high = pos.getY();
        var center = surface(world, pos.getX(), pos.getZ());
        if (center == null) return false;
        footprint++;
        low = Math.min(low, center.getY());
        high = Math.max(high, center.getY());
        if (supportedAt(world, pos)) supported++;
        for (int x = (int) Math.floor(box.minX + 0.001); x <= (int) Math.floor(box.maxX - 0.001); x += stride)
            for (int z = (int) Math.floor(box.minZ + 0.001); z <= (int) Math.floor(box.maxZ - 0.001); z += stride) {
                if (x == pos.getX() && z == pos.getZ()) continue;
                var ground = surface(world, x, z);
                if (ground == null) return false;
                footprint++;
                low = Math.min(low, ground.getY());
                high = Math.max(high, ground.getY());
                if (supportedAt(world, new BlockPos(x, pos.getY(), z))) supported++;
            }
        if (footprint == 0 || supported < Math.ceil(footprint * 0.25)) return false;
        return high <= pos.getY() && pos.getY() - low <= maxStep;
    }
    private static boolean supportedAt(ServerLevelAccessor world, BlockPos pos) {
        var floor = world.getBlockState(pos.below());
        return (floor.isSolidRender() || floor.is(Blocks.SNOW)) && floor.is(SURFACES);
    }
    /** Sample the loaded surface, permitting forest canopies but never searching down through ground into caves. */
    public static BlockPos surface(ServerLevelAccessor world, int x, int z) {
        int y;
        if (world instanceof ServerLevel level) {
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) return null;
            y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        } else {
            if (!world.hasChunkAt(new BlockPos(x, world.getMinY(), z))) return null;
            y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        }
        for (int drop = 0; drop <= 16 && y - drop > world.getMinY(); drop++) {
            var pos = new BlockPos(x, y - drop, z);
            var floor = world.getBlockState(pos.below());
            if (!floor.getFluidState().isEmpty()) return null;
            if (floor.is(SURFACES)) return pos;
            // Skip leaves, air and tree wood; other solid surfaces are not natural habitat.
            if (floor.isSolidRender() && !floor.is(net.minecraft.tags.BlockTags.LOGS)) return null;
        }
        return null;
    }
    private SpawnRules() {}
}
