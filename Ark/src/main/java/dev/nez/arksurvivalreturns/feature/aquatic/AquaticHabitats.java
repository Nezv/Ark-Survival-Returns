package dev.nez.arksurvivalreturns.feature.aquatic;

import java.util.*;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.land.LandHabitatData;
import dev.nez.arksurvivalreturns.feature.land.LandHabitats;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Saved home pools for water-bound wildlife.
 *
 * A pool is a bounded sample of connected deep water: shallow water, single-block puddles and
 * unloaded terrain are rejected. Occupancy, replacement cooldown, discovery and map markers are
 * shared with the land habitat store so a resident keeps one stable identity across saves.
 * Nothing here ever requests a chunk.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class AquaticHabitats {
    /** Surface center, a deep sample inside the pool, the placement weight and the measured radius. */
    public record Pool(BlockPos center, BlockPos deep, double weight, int radius) {}
    private static final int MAX_SCAN = 40;
    private static final class Budget { long tick = Long.MIN_VALUE; int probes; }
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();

    public static boolean enabled(ServerLevel world) {
        return Config.AQUATIC_HABITATS.get() && world.dimension() == Level.OVERWORLD;
    }
    public static int roam(Species species) { return LandHabitats.roam(species); }
    public static int leash(Species species) { return LandHabitats.leash(species); }

    private static boolean reserve(ServerLevel world, int count) {
        var budget = BUDGETS.computeIfAbsent(world, unused -> new Budget());
        if (budget.tick != world.getGameTime()) { budget.tick = world.getGameTime(); budget.probes = 0; }
        if (budget.probes + count > Config.AQUATIC_PROBES.get()) return false;
        budget.probes += count; return true;
    }
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
    /** True when the column can host a resident: deep enough and clear of solid blocks. */
    public static boolean siteAllowed(ServerLevel world, Species species, BlockPos surface) {
        if (surface == null || !reserve(world, 2)) return false;
        var pos = surfaceWater(world, surface.getX(), surface.getZ());
        if (pos == null || pos.getY() != surface.getY()) return false;
        int depth = depth(world, pos);
        if (depth < Config.AQUATIC_DEPTH.get()) return false;
        // A resident needs body clearance at its own depth, not only at the surface.
        if (depth < Math.ceil(species.height)) return false;
        var box = bounds(species, pos.getX() + 0.5, pos.getY() - depth + 1.0, pos.getZ() + 0.5);
        // Water is not a collision block for a resident.
        return loaded(world, box) && world.noCollision(null, box, false);
    }
    /** Bounded pool survey. Returns null when the sampled water cannot host the species. */
    public static Pool plan(ServerLevel world, Species species, BlockPos origin) {
        int radius = Config.AQUATIC_RADIUS.get();
        int step = Math.max(4, radius / 6);
        int minimum = Config.AQUATIC_DEPTH.get();
        int deepColumns = 0, maxDepth = 0;
        long sumX = 0, sumZ = 0;
        int surface = Integer.MAX_VALUE;
        BlockPos deepest = null;
        for (int dx = -radius; dx <= radius; dx += step)
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz > radius * radius) continue;
                if (!reserve(world, 2)) break;
                var column = surfaceWater(world, origin.getX() + dx, origin.getZ() + dz);
                if (column == null) continue;
                int depth = depth(world, column);
                if (depth < minimum) continue;
                deepColumns++; sumX += column.getX(); sumZ += column.getZ();
                surface = Math.min(surface, column.getY());
                if (depth > maxDepth) {
                    maxDepth = depth;
                    deepest = new BlockPos(column.getX(), column.getY() - Math.min(depth - 1, Math.max(1, minimum / 2)), column.getZ());
                }
            }
        if (deepest == null || deepColumns < Config.AQUATIC_COLUMNS.get()) return null;
        var center = new BlockPos((int) (sumX / deepColumns), surface, (int) (sumZ / deepColumns));
        // The averaged center of a curved shoreline can fall on dry land; fall back to the deep sample.
        var centerColumn = surfaceWater(world, center.getX(), center.getZ());
        if (centerColumn == null || depth(world, centerColumn) < minimum)
            center = new BlockPos(deepest.getX(), deepest.getY() + 1, deepest.getZ());
        double weight = Math.min(1.0, deepColumns / (double) (Config.AQUATIC_COLUMNS.get() * 3));
        double reach = Math.max(Math.hypot(center.getX() - deepest.getX(), center.getZ() - deepest.getZ()), radius * 0.5);
        int measured = (int) Math.max(6, Math.min(radius, Math.round(reach)));
        return new Pool(center.immutable(), deepest.immutable(), Math.max(0.25, weight), measured);
    }
    /** Also called by population passes so a vacant pool can recover with no living member. */
    public static void revalidate(ServerLevel world, LandHabitatData.Habitat habitat) {
        if (world.getGameTime() < habitat.nextCheck) return;
        var column = surfaceWater(world, habitat.water.getX(), habitat.water.getZ());
        if (column == null) {
            // Unloaded or dry right now: keep the record and retry, never invalidate on unknown terrain.
            habitat.nextCheck = world.getGameTime() + 100;
            return;
        }
        habitat.nextCheck = world.getGameTime() + Config.AQUATIC_RECHECK.get();
        boolean valid = depth(world, column) >= Config.AQUATIC_DEPTH.get();
        if (valid == habitat.valid) return;
        habitat.valid = valid; LandHabitatData.get(world).setDirty();
        if (valid) return;
        // The pool dried up or was filled in: try to move the residents to another loaded pool nearby.
        var pool = plan(world, habitat.species, habitat.center);
        if (pool != null) {
            LandHabitatData.get(world).relocate(habitat, pool.center(), pool.deep());
            habitat.radius = Math.max(6, Math.min(LandHabitats.roam(habitat.species), pool.radius()));
        }
    }
    public static LandHabitatData.Habitat group(CreatureEntity mob) {
        if (!mob.species().aquatic() || !(mob.level() instanceof ServerLevel world) || !enabled(world)) return null;
        var habitat = LandHabitatData.get(world).byId(mob.packId());
        if (habitat != null && habitat.species == mob.species()) { attach(world, habitat, mob); return habitat; }
        return null;
    }
    private static void attach(ServerLevel world, LandHabitatData.Habitat habitat, CreatureEntity mob) {
        if (habitat.species != mob.species()) return;
        if (habitat.members.add(mob.getUUID())) LandHabitatData.get(world).setDirty();
        habitat.loaded.put(mob.getUUID(), mob);
        mob.assignAquaticHabitat(habitat.id);
    }
    public static LandHabitatData.Habitat register(ServerLevel world, Pool pool, List<CreatureEntity> group, int capacity) {
        if (group.isEmpty()) throw new IllegalArgumentException("An empty spawn cannot register a pool");
        var first = group.getFirst();
        var habitat = new LandHabitatData.Habitat(first.packId(), first.species(), pool.center(), pool.deep(), capacity,
                group.stream().map(Entity::getUUID).toList(), List.of(),
                group.stream().mapToDouble(c -> c.wildlife().mind().hunger()).average().orElse(0.55), 0, true);
        habitat.radius = Math.max(6, Math.min(LandHabitats.roam(first.species()), pool.radius()));
        LandHabitatData.get(world).add(habitat);
        group.forEach(c -> attach(world, habitat, c));
        habitat.nextCheck = world.getGameTime() + Config.AQUATIC_RECHECK.get() + Math.floorMod(habitat.id.hashCode(), 200);
        return habitat;
    }
    /** Shared group clock, routine and pool destination; the mind owns the actual state choice. */
    public static LandHabitatData.Habitat think(CreatureEntity mob) {
        if (!(mob.level() instanceof ServerLevel world) || !mob.species().aquatic() || !enabled(world)) return null;
        var habitat = group(mob);
        if (habitat == null && mob.isNaturalWildlife() && Math.floorMod(mob.tickCount + mob.getId(), 100) < 10
                && Config.NATURAL_SPAWNS.get() && world.getGameRules().get(GameRules.SPAWN_MOBS)) {
            var peers = world.getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(64),
                    c -> c.isAlive() && c.isNaturalWildlife() && c.species() == mob.species() && c.packId().equals(mob.packId()));
            if (!peers.isEmpty() && peers.stream().noneMatch(c -> c.getId() < mob.getId())) {
                var surface = surfaceWater(world, mob.blockPosition().getX(), mob.blockPosition().getZ());
                if (surface != null) {
                    var pool = plan(world, mob.species(), surface);
                    if (pool != null) habitat = register(world, pool, peers, Math.max(peers.size(), mob.species().minGroup));
                }
            }
        }
        if (habitat == null) return null;
        boolean night = LandHabitats.night(world, habitat);
        boolean hunting = habitat.species.predator && habitat.needs.hunger() >= 0.4;
        if (habitat.needs.advance(world.getGameTime(),
                habitat.species.predator && night && Config.NIGHTTIME.get() ? Config.NIGHT_HUNGER.get() : 1, 0, habitat.members.size())) {
            LandHabitatData.get(world).setDirty();
            var routine = hunting ? BehaviorState.SEARCH : BehaviorState.ROAM;
            if (routine != habitat.routine) { habitat.routine = routine; habitat.nextPlan = 0; }
            if (world.getGameTime() >= habitat.nextPlan) {
                habitat.nextPlan = world.getGameTime() + 100 + world.getRandom().nextInt(100);
                habitat.destination = destination(world, habitat, mob);
                habitat.heading = world.getRandom().nextDouble() * Math.PI * 2;
            }
            revalidate(world, habitat);
        }
        return habitat;
    }
    /** A member-slot point inside the pool, always below the surface and above the floor. */
    public static Vec3 destination(ServerLevel world, LandHabitatData.Habitat habitat, CreatureEntity mob) {
        int radius = Math.max(4, Math.min(roam(mob.species()), habitat.radius));
        var members = habitat.members.stream().sorted().toList();
        int slot = Math.max(0, members.indexOf(mob.getUUID()));
        double spacing = mob.getBbWidth() + 2;
        double side = members.size() == 1 ? 0 : (slot % 3 - 1) * spacing;
        double behind = members.size() == 1 ? 0 : (slot / 3) * spacing;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double r = Math.sqrt(world.getRandom().nextDouble()) * radius;
            int x = habitat.center.getX() + (int) (Math.cos(angle) * r - Math.sin(angle) * side);
            int z = habitat.center.getZ() + (int) (Math.sin(angle) * r + Math.cos(angle) * side - behind);
            var column = surfaceWater(world, x, z);
            if (column == null) continue;
            int depth = depth(world, column);
            if (depth < Math.max(3, Config.AQUATIC_DEPTH.get() / 2)) continue;
            int y = column.getY() - 1 - world.getRandom().nextInt(depth - 1);
            y = Math.max(world.getMinY() + 1, Math.min(y, column.getY() - 1));
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        return null;
    }
    /** Water column bounds at a position, used by the steering goal to stay submerged. */
    public static int[] column(ServerLevel world, BlockPos pos) {
        var surface = surfaceWater(world, pos.getX(), pos.getZ());
        if (surface == null) return null;
        int depth = depth(world, surface);
        return new int[]{surface.getY(), Math.max(surface.getY() - depth + 1, world.getMinY() + 1)};
    }
    public static List<CreatureEntity> spawn(ServerLevel world, BlockPos origin, Species species, int capacity, RandomSource random) {
        if (!enabled(world) || !species.aquatic()) return List.of();
        var surface = surfaceWater(world, origin.getX(), origin.getZ());
        if (surface == null || !siteAllowed(world, species, surface)) return List.of();
        var data = LandHabitatData.get(world);
        if (data.near(surface, 48).stream().anyMatch(h -> h.species == species)) return List.of();
        int size = species.minGroup + random.nextInt(Math.min(species.maxGroup, capacity) - species.minGroup + 1);
        if (capacity < size) return List.of();
        var pool = plan(world, species, surface);
        if (pool == null || random.nextDouble() >= pool.weight()) return List.of();
        var pending = place(world, species, pool, size, null, random);
        if (pending.size() != size) { pending.forEach(Entity::discard); return List.of(); }
        register(world, pool, pending, size);
        return pending;
    }
    /** Add members to an existing saved pool without re-planning or relocating it. */
    public static List<CreatureEntity> replenish(ServerLevel world, LandHabitatData.Habitat habitat, int capacity, RandomSource random) {
        if (!enabled(world) || !habitat.species.aquatic() || !habitat.valid) return List.of();
        if (world.getGameTime() < habitat.replacementAt || habitat.members.size() >= habitat.capacity) return List.of();
        if (Config.WEIGHTS.get(habitat.species).get() == 0) return List.of();
        int missing = habitat.capacity - habitat.members.size();
        if (capacity < missing) return List.of();
        var pool = new Pool(habitat.center, habitat.water, 1, habitat.radius);
        var placed = place(world, habitat.species, pool, missing, habitat, random);
        if (placed.size() != missing) { placed.forEach(Entity::discard); return List.of(); }
        return placed;
    }
    /** All-or-nothing member placement inside a pool; a partial group is discarded. */
    private static List<CreatureEntity> place(ServerLevel world, Species species, Pool pool, int size,
            LandHabitatData.Habitat existing, RandomSource random) {
        var type = ModContent.CREATURES.get(species).get();
        var pending = new ArrayList<CreatureEntity>();
        var occupied = world.getEntitiesOfClass(CreatureEntity.class, new AABB(pool.center()).inflate(96), Entity::isAlive);
        if (existing == null && occupied.size() + size > Config.LOCAL_CAP.get()) return List.of();
        net.minecraft.world.entity.SpawnGroupData pack = null;
        for (int attempt = 0; attempt < size * 16 && pending.size() < size; attempt++) {
            int radius = Math.max(3, Math.min((int) (roam(species) * 0.6), pool.radius()));
            int x = pool.center.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = pool.center.getZ() + random.nextInt(radius * 2 + 1) - radius;
            var column = surfaceWater(world, x, z);
            if (column == null) continue;
            int depth = depth(world, column);
            if (depth < Math.max(3, Config.AQUATIC_DEPTH.get() - 2)) continue;
            int y = Math.max(world.getMinY() + 1, column.getY() - 1 - random.nextInt(depth - 1));
            var box = bounds(species, x + 0.5, y, z + 0.5);
            if (!loaded(world, box) || !world.getWorldBorder().isWithinBounds(box) || !world.noCollision(null, box, false)) continue;
            if (pending.stream().anyMatch(c -> c.getBoundingBox().inflate(1).intersects(box))) continue;
            var creature = type.create(world, EntitySpawnReason.NATURAL);
            if (creature == null) continue;
            creature.setPos(x + 0.5, y, z + 0.5);
            creature.setYRot(random.nextFloat() * 360);
            creature.yBodyRot = creature.getYRot(); creature.yHeadRot = creature.getYRot();
            pack = net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(creature, world,
                    world.getCurrentDifficultyAt(creature.blockPosition()), EntitySpawnReason.NATURAL, pack);
            if (creature.isSpawnCancelled()) continue;
            pending.add(creature);
        }
        if (pending.size() != size) return List.of();
        if (existing != null) pending.forEach(c -> c.assignAquaticHabitat(existing.id));
        for (var creature : pending)
            if (!world.addFreshEntity(creature)) { pending.forEach(Entity::discard); return List.of(); }
        if (existing != null) pending.forEach(c -> attach(world, existing, c));
        return pending;
    }
    static AABB bounds(Species species, double x, double y, double z) {
        double half = species.width / 2.0;
        return new AABB(x - half, y, z - half, x + half, y + species.height, z + half);
    }
    static boolean loaded(ServerLevel world, AABB bounds) {
        for (int cx = ((int) Math.floor(bounds.minX)) >> 4; cx <= ((int) Math.floor(bounds.maxX)) >> 4; cx++)
            for (int cz = ((int) Math.floor(bounds.minZ)) >> 4; cz <= ((int) Math.floor(bounds.maxZ)) >> 4; cz++)
                if (world.getChunkSource().getChunkNow(cx, cz) == null) return false;
        return true;
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { BUDGETS.clear(); }
    private AquaticHabitats() {}
}
