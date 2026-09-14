package dev.nez.arksurvivalreturns.feature.flying;

import java.util.*;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.spawn.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/** Bounded habitat planning, reuse and migration in already loaded terrain. */
public final class FlyerHabitats {
    public static boolean siteAllowed(ServerLevel world, Species species, BlockPos pos) {
        if (!species.flyer() || !SpawnRules.loaded(world, new AABB(pos).inflate(2)) || !world.canSeeSky(pos)) return false;
        var floor = world.getBlockState(pos.below());
        if (!floor.isFaceSturdy(world, pos.below(), Direction.UP) || !floor.is(SpawnRules.SURFACES)) return false;
        if (species == Species.ARGENTAVIS) return pos.getY() - 1 >= Config.ARGENT_NEST_Y.get();
        if (!floor.is(BlockTags.SAND)) return false;
        int radius = Config.NEST_WATER_RADIUS.get();
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
    private static boolean enabled(ServerLevel world) { return world.dimension() == Level.OVERWORLD && Config.NATURAL_SPAWNS.get() && world.getGameRules().get(GameRules.SPAWN_MOBS); }
    private static List<BlockPos> plan(ServerLevel world, BlockPos origin, Species species, int size, RandomSource random) {
        if (!siteAllowed(world, species, origin)) return List.of();
        var result = new ArrayList<BlockPos>();
        int radius = species == Species.ARGENTAVIS ? 24 : 16;
        for (int attempt = 0; attempt < size*24 && result.size() < size; attempt++) {
            var pos = attempt == 0 ? origin : SpawnRules.surface(world, origin.getX()+random.nextInt(radius*2+1)-radius, origin.getZ()+random.nextInt(radius*2+1)-radius);
            if (pos == null || Math.abs(pos.getY()-origin.getY()) > 12 || HabitatData.horizontalDistanceSqr(origin, pos) > radius*radius
                    || !world.isPositionEntityTicking(pos) || !siteAllowed(world, species, pos)
                    || !world.getBlockState(pos).isAir() || !world.getFluidState(pos).isEmpty()) continue;
            if (result.stream().anyMatch(p -> HabitatData.horizontalDistanceSqr(p, pos) < 36)) continue;
            if (!SpawnRules.canSpawn(ModContent.CREATURES.get(species).get(), world, EntitySpawnReason.NATURAL, pos, random)) continue;
            var flightBox = SpawnRules.bounds(species, pos.above(4));
            if (!SpawnRules.loaded(world, flightBox.inflate(1)) || !world.getWorldBorder().isWithinBounds(flightBox)
                    || !world.noCollision(null, flightBox, true) || flightBox.maxY >= world.getMaxY()) continue;
            result.add(pos.immutable());
        }
        return result.size() == size ? result : List.of();
    }
    public static List<CreatureEntity> spawn(ServerLevel world, BlockPos origin, Species species, int capacity, RandomSource random) {
        if (!enabled(world) || !species.flyer()) return List.of();
        var data = HabitatData.get(world);
        var nearby = data.near(origin, 64);
        var h = nearby.stream().filter(c -> c.species() == species && c.nests().size() >= species.minGroup).findFirst().orElse(null);
        boolean fresh = h == null;
        if (fresh && (!nearby.isEmpty() || capacity < species.minGroup)) return List.of();
        if (fresh) {
            origin = SpawnRules.surface(world, origin.getX(), origin.getZ()); if (origin == null) return List.of();
            if (!world.getEntitiesOfClass(CreatureEntity.class, new AABB(origin).inflate(18), c -> c.isAlive() && c.isNaturalWildlife()).isEmpty()) return List.of();
            int size = species.minGroup + random.nextInt(Math.min(species.maxGroup, capacity)-species.minGroup+1);
            var sites = plan(world, origin, species, size, random); if (sites.isEmpty()) return List.of();
            h = new HabitatData.Habitat(UUID.randomUUID(), species, origin, sites, Set.of());
        }
        final var habitat = h;
        // Every possible member inside the leash must be visible before replenishing a saved colony.
        if (!fresh && !SpawnRules.loaded(world, new AABB(h.center()).inflate(Config.FLIGHT_LEASH.get()+8))) return List.of();
        var members = world.getEntitiesOfClass(FlyingCreatureEntity.class, new AABB(h.center()).inflate(96), b -> b.isAlive() && habitat.id().equals(b.habitatId()));
        int missing = h.nests().size() - members.size();
        if (missing <= 0 || capacity < missing) return List.of();
        var sites = h.nests().stream().filter(p -> members.stream().noneMatch(b -> p.equals(b.nestPosition()))).limit(missing).toList();
        if (sites.size() != missing) return List.of();
        var pending = new ArrayList<CreatureEntity>();
        for (var pos : sites) {
            if (!SpawnRules.loaded(world, new AABB(pos).inflate(3)) || !world.isPositionEntityTicking(pos)
                    || !siteAllowed(world, species, pos) || !SpawnRules.speciesAllowed(species, world.getBiome(pos), BiomeTier.at(world,pos).dangerLevel())) return List.of();
            if (!fresh && !world.getBlockState(pos).is(ModContent.NESTS.get(species).get())) return List.of();
            var box = SpawnRules.bounds(species, pos.above(4));
            if (!SpawnRules.loaded(world, box.inflate(1)) || !world.noCollision(null, box, true) || !world.getWorldBorder().isWithinBounds(box)
                    || box.maxY >= world.getMaxY() || world.players().stream().anyMatch(p -> !p.isSpectator() && p.distanceToSqr(Vec3.atBottomCenterOf(pos)) < 24*24)
                    || world.getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(96), Entity::isAlive).size()+missing > Config.LOCAL_CAP.get()) return List.of();
            var bird = (FlyingCreatureEntity)ModContent.CREATURES.get(species).get().create(world, EntitySpawnReason.NATURAL);
            if (bird == null) return List.of();
            bird.setPos(Vec3.atBottomCenterOf(pos.above(4)));
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(bird, world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            if (bird.isSpawnCancelled()) return List.of();
            bird.assignHabitat(h, pos); pending.add(bird);
        }
        var placed = new ArrayList<BlockPos>();
        if (fresh) for (var pos : h.nests()) {
            if (!world.getBlockState(pos).isAir() || !world.setBlock(pos, ModContent.NESTS.get(species).get().defaultBlockState(), Block.UPDATE_ALL)) {
                placed.forEach(p -> world.removeBlock(p, false)); return List.of();
            }
            placed.add(pos);
        }
        for (var bird : pending) if (!world.addFreshEntity(bird)) {
            pending.forEach(Entity::discard); placed.forEach(p -> world.removeBlock(p, false)); return List.of();
        }
        if (fresh) data.add(h);
        return pending;
    }
    public static void adopt(ServerLevel world, FlyingCreatureEntity bird) {
        if (!enabled(world)) return;
        var group = world.getEntitiesOfClass(FlyingCreatureEntity.class, bird.getBoundingBox().inflate(64),
                b -> b.isAlive() && b.isNaturalWildlife() && b.species() == bird.species() && b.packId().equals(bird.packId()));
        if (group.isEmpty() || group.stream().anyMatch(b -> b.getId() < bird.getId()) || group.size() > 4) return;
        var data = HabitatData.get(world);
        var nearby = data.near(bird.blockPosition(), 64);
        for (var h : nearby) {
            if (h.species() != bird.species() || h.nests().size() < group.size()
                    || !SpawnRules.loaded(world, new AABB(h.center()).inflate(Config.FLIGHT_LEASH.get()+8))) continue;
            if (!world.getEntitiesOfClass(FlyingCreatureEntity.class, new AABB(h.center()).inflate(96), b -> b.isAlive() && h.id().equals(b.habitatId())).isEmpty()) continue;
            for (int i=0;i<group.size();i++) group.get(i).assignHabitat(h,h.nests().get(i));
            return;
        }
        if (!nearby.isEmpty()) return;
        // Legacy wild packs get at most eight local attempts per ten seconds, led by one member.
        for (int i=0;i<8;i++) {
            var origin = SpawnRules.surface(world,bird.blockPosition().getX()+world.getRandom().nextInt(33)-16,bird.blockPosition().getZ()+world.getRandom().nextInt(33)-16);
            if (origin == null || !siteAllowed(world,bird.species(),origin)) continue;
            var sites = plan(world,origin,bird.species(),Math.max(3,group.size()),world.getRandom());
            if (sites.isEmpty()) continue;
            var h = new HabitatData.Habitat(bird.packId(),bird.species(),origin,sites,Set.of());
            var placed = new ArrayList<BlockPos>();
            for (var pos : sites) {
                if (!world.setBlock(pos,ModContent.NESTS.get(bird.species()).get().defaultBlockState(),Block.UPDATE_ALL)) break;
                placed.add(pos);
            }
            if (placed.size()!=sites.size()) { placed.forEach(p -> world.removeBlock(p,false)); return; }
            data.add(h); for (int n=0;n<group.size();n++) group.get(n).assignHabitat(h,sites.get(n)); return;
        }
    }
    private FlyerHabitats() {}
}
