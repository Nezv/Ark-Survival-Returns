package dev.nez.arksurvivalreturns.feature.spawn;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

public final class SpawnRules {
    public static final TagKey<Block> SURFACES = TagKey.create(Registries.BLOCK, ArkSurvivalReturns.id("spawn_surfaces"));
    public static void placements(RegisterSpawnPlacementsEvent event) {
        ModContent.CREATURES.values().forEach(type -> event.register(type.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, world, reason, pos, random) -> false, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        // PopulationDirector exclusively owns natural spawns. This also disables old biome-modifier
        // entries from installed data packs, which could bypass the group's atomic placement.
    }
    public static boolean speciesAllowed(Species species, Holder<Biome> biome, int danger) {
        // Displayed local danger is authoritative; a distant plains region can also be level 5.
        return danger >= species.minimumDanger();
    }
    public static boolean loaded(ServerLevel world, AABB bounds) {
        for (int x = ((int) Math.floor(bounds.minX)) >> 4; x <= ((int) Math.floor(bounds.maxX)) >> 4; x++)
            for (int z = ((int) Math.floor(bounds.minZ)) >> 4; z <= ((int) Math.floor(bounds.maxZ)) >> 4; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    public static AABB bounds(Species species, BlockPos pos) {
        double half = species.width / 2.0;
        return new AABB(pos.getX() + 0.5 - half, pos.getY(), pos.getZ() + 0.5 - half,
                pos.getX() + 0.5 + half, pos.getY() + species.height, pos.getZ() + 0.5 + half);
    }
    public static boolean canSpawn(EntityType<CreatureEntity> type, ServerLevelAccessor world, EntitySpawnReason reason,
            BlockPos pos, RandomSource random) {
        if (!(world instanceof ServerLevel level) || !Config.NATURAL_SPAWNS.get() || level.dimension() != Level.OVERWORLD
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)) return false;
        var species = ModContent.species(type);
        var box = bounds(species, pos);
        if (!loaded(level, box.inflate(1)) || !level.getWorldBorder().isWithinBounds(box)
                || pos.getY() < level.getMinY() + 1 || box.maxY >= level.getMaxY()) return false;
        if (!pos.equals(placementSurface(level, species, pos.getX(), pos.getZ()))) return false;
        if (!speciesAllowed(species, level.getBiome(pos), BiomeTier.at(level, pos).dangerLevel())) return false;
        if (!level.getFluidState(pos).isEmpty()) return false;
        if (species.flyer() && !dev.nez.arksurvivalreturns.feature.flying.FlyerHabitats.siteAllowed(level, species, pos)) return false;
        int supported = 0, footprint = 0;
        // Use the actual foot footprint, not a rounded 3x3 square even for a tiny raptor.
        for (int x = (int) Math.floor(box.minX + 0.001); x <= (int) Math.floor(box.maxX - 0.001); x++)
            for (int z = (int) Math.floor(box.minZ + 0.001); z <= (int) Math.floor(box.maxZ - 0.001); z++) {
                BlockPos floor = new BlockPos(x, pos.getY() - 1, z);
                var state = level.getBlockState(floor);
                footprint++;
                if ((state.isSolidRender() || state.is(Blocks.SNOW)) && state.is(SURFACES)) supported++;
            }
        if (supported < Math.ceil(footprint * 0.25)) return false;
        if (!level.noCollision(null, box, true)) return false;
        Vec3 feet = Vec3.atBottomCenterOf(pos);
        if (level.players().stream().anyMatch(p -> !p.isSpectator() && p.distanceToSqr(feet) < 24 * 24)) return false;
        var nearby = level.getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(96), Entity::isAlive);
        if (nearby.size() >= Config.LOCAL_CAP.get()) return false;
        double spacing = Config.SOLITARY_SPACING.get();
        return !species.solitary() || nearby.stream().noneMatch(c -> c.species() == species && c.distanceToSqr(feet) < spacing * spacing);
    }
    /** Stand above the highest part of a gently uneven footprint, without embedding the body in terrain. */
    public static BlockPos placementSurface(ServerLevel world, Species species, int x, int z) {
        var center = surface(world, x, z);
        if (center == null) return null;
        var box = bounds(species, center);
        if (!loaded(world, box.inflate(1))) return null;
        int low = center.getY(), high = low;
        for (int bx = (int)Math.floor(box.minX + 0.001); bx <= (int)Math.floor(box.maxX - 0.001); bx++)
            for (int bz = (int)Math.floor(box.minZ + 0.001); bz <= (int)Math.floor(box.maxZ - 0.001); bz++) {
                var ground = surface(world, bx, bz);
                if (ground == null) return null;
                low = Math.min(low, ground.getY()); high = Math.max(high, ground.getY());
                if (high - low > (species.width >= 5 ? 3 : 1)) return null;
            }
        return new BlockPos(x, high, z);
    }
    /** Sample the loaded surface, permitting forest canopies but never searching down through ground into caves. */
    public static BlockPos surface(ServerLevel world, int x, int z) {
        var chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return null;
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
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
