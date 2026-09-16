package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayList;
import java.util.List;
import dev.nez.arksurvivalreturns.feature.land.*;
import dev.nez.arksurvivalreturns.feature.aquatic.AquaticHabitats;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Bounded replenishment of complete packs around active players; never requests a chunk. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class PopulationDirector {
    public static final int RADIUS = 96;
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel world) || world.dimension() != Level.OVERWORLD
                || !Config.NATURAL_SPAWNS.get() || !world.getGameRules().get(GameRules.SPAWN_MOBS)
                || world.getGameTime() % Config.SPAWN_INTERVAL.get() != 0) return;
        var players = world.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
        if (players.isEmpty()) return;
        int start = (int) Math.floorMod(world.getGameTime() / Config.SPAWN_INTERVAL.get(), players.size());
        int remaining = Config.GROUPS_PER_PASS.get();
        // Bound failed searches too: crowded multiplayer worlds cannot multiply the work without limit.
        for (int i = 0; i < Math.min(8, players.size()) && remaining > 0; i++) {
            var player = players.get((start + i) % players.size());
            remaining -= replenish(world, player.blockPosition(), remaining, world.getRandom());
        }
    }
    public static long groupCount(List<CreatureEntity> creatures) {
        return creatures.stream().filter(CreatureEntity::isNaturalWildlife).map(CreatureEntity::packId).distinct().count();
    }
    public static int replenish(ServerLevel world, BlockPos viewer, int budget, RandomSource random) {
        if (world.dimension() != Level.OVERWORLD || !Config.NATURAL_SPAWNS.get()
                || !world.getGameRules().get(GameRules.SPAWN_MOBS)) return 0;
        var nearby = world.getEntitiesOfClass(CreatureEntity.class, new AABB(viewer).inflate(RADIUS), Entity::isAlive);
        // Land, semi-aquatic and water-bound residents share one saved occupancy store.
        var stored = LandHabitats.enabled(world) || AquaticHabitats.enabled(world)
                ? LandHabitatData.get(world).near(viewer, RADIUS) : List.<LandHabitatData.Habitat>of();
        var habitats = stored.stream().filter(h -> !h.species.aquatic() && LandHabitats.enabled(world)).toList();
        var pools = stored.stream().filter(h -> h.species.aquatic() && AquaticHabitats.enabled(world)).toList();
        var knownGroups = new java.util.HashSet<java.util.UUID>();
        nearby.stream().filter(CreatureEntity::isNaturalWildlife).forEach(c -> knownGroups.add(c.packId()));
        habitats.forEach(h -> knownGroups.add(h.id));
        pools.forEach(h -> knownGroups.add(h.id));
        int missing = Math.max(0, Config.MIN_GROUPS.get() - knownGroups.size());
        int added = 0;
        // Repair one partially populated colony even when it already counts toward the group target.
        for (var habitat : dev.nez.arksurvivalreturns.feature.flying.HabitatData.get(world).near(viewer, RADIUS)) {
            if (Config.WEIGHTS.get(habitat.species()).get() == 0) continue;
            long living = nearby.stream().filter(c -> c instanceof dev.nez.arksurvivalreturns.feature.creature.FlyingCreatureEntity b && habitat.id().equals(b.habitatId())).count();
            if (living == 0 || living >= habitat.nests().size() || added >= budget) continue;
            var repaired = dev.nez.arksurvivalreturns.feature.flying.FlyerHabitats.spawn(world, habitat.center(), habitat.species(), Config.LOCAL_CAP.get()-nearby.size(), random);
            if (!repaired.isEmpty()) { nearby.addAll(repaired); added++; break; }
        }
        for (var h : habitats) {
            if (added >= budget) break;
            var repaired = tryReplenishHabitat(world, h, Config.LOCAL_CAP.get()-nearby.size(), random);
            if (!repaired.isEmpty()) { nearby.addAll(repaired); added++; }
        }
        // A saved pool recovers the same way a herd does; the residents are replaced inside it.
        for (var h : pools) {
            if (added >= budget) break;
            var repaired = AquaticHabitats.replenish(world, h, Config.LOCAL_CAP.get()-nearby.size(), random);
            if (!repaired.isEmpty()) { nearby.addAll(repaired); added++; }
        }
        int viewerDanger = BiomeTier.at(world, viewer).dangerLevel();
        for (int attempt = 0; attempt < 96 && added < budget; attempt++) {
            var regionalLarge = prioritySpecies(viewerDanger);
            var missingLarge = nearby.stream().anyMatch(c -> regionalLarge.contains(c.species())) || habitats.stream().anyMatch(h -> regionalLarge.contains(h.species)) ? List.<Species>of()
                    : regionalLarge.stream().filter(s -> Config.WEIGHTS.get(s).get() > 0).toList();
            if (added >= missing && missingLarge.isEmpty()) break;
            if (nearby.size() >= Config.LOCAL_CAP.get()) break;
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(32 * 32 + random.nextDouble() * (80 * 80 - 32 * 32));
            int sampleX = viewer.getX() + (int) (Math.cos(angle) * radius), sampleZ = viewer.getZ() + (int) (Math.sin(angle) * radius);
            var pos = SpawnRules.surface(world, sampleX, sampleZ);
            var waterPos = AquaticHabitats.surfaceWater(world, sampleX, sampleZ);
            if (pos != null && (!world.isPositionEntityTicking(pos) || Math.abs(pos.getY() - viewer.getY()) > 48)) pos = null;
            if (waterPos != null && (!world.isPositionEntityTicking(waterPos) || Math.abs(waterPos.getY() - viewer.getY()) > 48
                    || AquaticHabitats.depth(world, waterPos) < Config.AQUATIC_DEPTH.get())) waterPos = null;
            if (pos == null && waterPos == null) continue;
            var anchor = pos != null ? pos : waterPos;
            var biome = world.getBiome(anchor);
            int danger = BiomeTier.at(world, anchor).dangerLevel();
            var choices = new ArrayList<Species>();
            var weights = new ArrayList<Integer>();
            int total = 0;
            for (var species : Species.values()) {
                // Each realm is only offered the sample it can actually use.
                if (species.aquatic() ? waterPos == null : pos == null) continue;
                if (!SpawnRules.speciesAllowed(species, biome, danger)) continue;
                int weight = selectionWeight(species, Config.WEIGHTS.get(species).get(), biome.is(species.biomes), anchor.getY(), world.getSeaLevel());
                if (!missingLarge.isEmpty() && !missingLarge.contains(species)) continue;
                if (species == Species.ARGENTAVIS && nearby.stream().filter(c -> c.species() == species).map(CreatureEntity::packId).distinct().count() >= 2) continue;
                if (weight == 0) continue;
                choices.add(species); weights.add(weight); total += weight;
            }
            if (total == 0) continue;
            int pick = random.nextInt(total), index = 0;
            while (pick >= weights.get(index)) pick -= weights.get(index++);
            var selected = choices.get(index);
            if (!SpawnRules.speciesAllowed(selected, biome, danger)) continue;
            var origin = selected.aquatic() ? waterPos : pos;
            if (origin == null) continue;
            if (selected == Species.BRONTOSAURUS && budget - added < 2) continue;
            var group = selected == Species.BRONTOSAURUS
                    ? trySpawnBrontoEncounter(world, origin, Config.LOCAL_CAP.get() - nearby.size(), random)
                    : trySpawnGroup(world, origin, selected, Config.LOCAL_CAP.get() - nearby.size(), random);
            if (!group.isEmpty()) { nearby.addAll(group); added += (int)groupCount(group); }
        }
        return added;
    }
    public static List<CreatureEntity> trySpawnGroup(ServerLevel world, BlockPos origin, Species species, int capacity, RandomSource random) {
        if (species.flyer()) return dev.nez.arksurvivalreturns.feature.flying.FlyerHabitats.spawn(world, origin, species, capacity, random);
        if (species.aquatic()) return AquaticHabitats.spawn(world, origin, species, capacity, random);
        return spawnLand(world, origin, species, capacity, random, null);
    }
    public static List<CreatureEntity> tryReplenishHabitat(ServerLevel world, LandHabitatData.Habitat h, int capacity, RandomSource random) {
        if (!LandHabitats.enabled(world)) return List.of();
        LandHabitats.revalidate(world,h);
        if (!h.valid || world.getGameTime() < h.replacementAt || h.members.size() >= h.capacity
                || Config.WEIGHTS.get(h.species).get() == 0) return List.of();
        return spawnLand(world, h.center, h.species, capacity, random, h);
    }
    private static List<CreatureEntity> spawnLand(ServerLevel world, BlockPos origin, Species species, int capacity, RandomSource random, LandHabitatData.Habitat existing) {
        int minimum = existing == null ? species.minGroup : existing.capacity-existing.members.size();
        if (minimum <= 0 || capacity < minimum) return List.of();
        origin = SpawnRules.placementSurface(world, species, origin.getX(), origin.getZ());
        if (origin == null) return List.of();
        var type = ModContent.CREATURES.get(species).get();
        if (existing == null && !SpawnRules.canSpawn(type, world, EntitySpawnReason.NATURAL, origin, random)) return List.of();
        if (existing == null && !world.getEntitiesOfClass(CreatureEntity.class, new AABB(origin).inflate(18),
                c -> c.isAlive() && c.isNaturalWildlife()).isEmpty()) return List.of();
        LandHabitats.Site site = null;
        if (LandHabitats.enabled(world)) {
            if (existing == null && LandHabitatData.get(world).near(origin, 40).stream().anyMatch(h -> h.species.family() == species.family())) return List.of();
            site = LandHabitats.plan(world, species, origin);
            if (site == null || existing == null && random.nextDouble() >= site.weight()) return List.of();
        }
        int size = existing == null ? species.minGroup + random.nextInt(Math.min(species.maxGroup, capacity) - species.minGroup + 1) : minimum;
        var pending = new ArrayList<CreatureEntity>();
        int originDanger = BiomeTier.at(world, origin).dangerLevel();
        SpawnGroupData pack = null;
        int radius = species.groupRadius();
        for (int attempt = 0; attempt < size * 24 && pending.size() < size; attempt++) {
            var pos = pending.isEmpty() && existing == null ? origin : SpawnRules.placementSurface(world, species,
                    origin.getX() + random.nextInt(radius * 2 + 1) - radius, origin.getZ() + random.nextInt(radius * 2 + 1) - radius);
            if (pos == null || Math.abs(pos.getY() - origin.getY()) > species.groupHeightRange() || BiomeTier.at(world, pos).dangerLevel() != originDanger
                    || !SpawnRules.canSpawn(type, world, EntitySpawnReason.NATURAL, pos, random)) continue;
            var box = SpawnRules.bounds(species, pos);
            double gap = species == Species.ARGENTAVIS ? 5 : species.herd() ? 2 : 0.5;
            if (pending.stream().anyMatch(c -> c.getBoundingBox().inflate(gap).intersects(box))) continue;
            int occupied = world.getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(RADIUS), Entity::isAlive).size();
            if (occupied + size > Config.LOCAL_CAP.get()) continue;
            var creature = type.create(world, EntitySpawnReason.NATURAL);
            if (creature == null) continue;
            creature.setPos(Vec3.atBottomCenterOf(pos));
            creature.setYRot(random.nextFloat() * 360);
            creature.yBodyRot = creature.getYRot(); creature.yHeadRot = creature.getYRot();
            pack = net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(creature, world,
                    world.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, pack);
            if (creature.isSpawnCancelled()) continue;
            if (existing != null) creature.assignLandHabitat(existing.id);
            pending.add(creature);
        }
        // All or nothing: failed terrain searches cannot create a lone pack animal.
        if (pending.size() != size) return List.of();
        for (var creature : pending) {
            if (!world.addFreshEntity(creature)) {
                pending.forEach(Entity::discard);
                return List.of();
            }
        }
        if (site != null) {
            if (existing == null) LandHabitats.register(world, site, pending, size);
            else { LandHabitatData.get(world).relocate(existing,site.center(),site.water()); pending.forEach(LandHabitats::group); }
        }
        return pending;
    }
    public static int selectionWeight(Species species, int configured, boolean habitat, int y, int seaLevel) {
        if (configured == 0) return 0;
        return Math.max(1, (int)Math.round(configured * 100 * (habitat ? 3 : 1)
                * dev.nez.arksurvivalreturns.feature.creature.FlyingCreatureEntity.altitudeWeight(species, y, seaLevel)));
    }
    public static List<Species> prioritySpecies(int danger) {
        return java.util.stream.Stream.of(Species.GIGANOTOSAURUS, Species.TITANOSAUR, Species.TYRANNOSAURUS,
                Species.BRONTOSAURUS, Species.THERIZINOSAURUS, Species.SPINOSAURUS, Species.ACROCANTHOSAURUS).filter(s -> danger >= s.minimumDanger()).toList();
    }
    /** A Bronto encounter is atomic: no new herd is left without its nearby Rex. */
    public static List<CreatureEntity> trySpawnBrontoEncounter(ServerLevel world, BlockPos origin, int capacity, RandomSource random) {
        if (capacity < Species.BRONTOSAURUS.minGroup + 1 || Config.WEIGHTS.get(Species.TYRANNOSAURUS).get() == 0) return List.of();
        var herd = trySpawnGroup(world, origin, Species.BRONTOSAURUS, capacity - 1, random);
        if (herd.isEmpty()) return List.of();
        for (int i = 0; i < 32; i++) {
            double angle = random.nextDouble() * Math.PI * 2, radius = 32 + random.nextInt(17);
            var point = SpawnRules.surface(world, origin.getX() + (int)(Math.cos(angle) * radius), origin.getZ() + (int)(Math.sin(angle) * radius));
            if (point == null) continue;
            var predator = trySpawnGroup(world, point, Species.TYRANNOSAURUS, capacity - herd.size(), random);
            if (!predator.isEmpty()) {
                predator.getFirst().wildlife().followPreyHerd(herd.getFirst().packId());
                var encounter = new ArrayList<>(herd); encounter.addAll(predator); return encounter;
            }
        }
        var failedId = herd.getFirst().packId();
        herd.forEach(Entity::discard);
        if (LandHabitats.enabled(world)) LandHabitatData.get(world).remove(failedId);
        return List.of();
    }
    private PopulationDirector() {}
}
