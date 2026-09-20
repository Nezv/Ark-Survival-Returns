package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Independent wildlife budget: keeps a configurable population of mod creatures around players
 * regardless of the vanilla mob cap, using the same danger, biome and placement rules as the
 * biome spawn tables. It only counts, places and culls natural wildlife; tames, spawn eggs and
 * command summons are never touched, and no chunk is ever force-loaded.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class NaturalPopulations {
    /**
     * Regional large species the budget wants at least one of near a player, mirroring the deleted
     * director's missing-large priority. They are the only species allowed to cross their biome tag,
     * at a reduced weight, so a level-4/5 area always has a chance at an apex encounter.
     */
    private static final java.util.EnumSet<Species> REGIONAL_LARGE = java.util.EnumSet.of(
            Species.GIGANOTOSAURUS, Species.TITANOSAUR, Species.TYRANNOSAURUS, Species.BRONTOSAURUS,
            Species.THERIZINOSAURUS, Species.SPINOSAURUS, Species.ACROCANTHOSAURUS);

    public static boolean isRegionalLarge(Species species) { return REGIONAL_LARGE.contains(species); }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % Config.POPULATION_INTERVAL.get() != 0) return;
        var level = server.overworld();
        if (!Config.NATURAL_SPAWNS.get() || !Config.POPULATION_BUDGET.get()
                || !level.getGameRules().get(GameRules.SPAWN_MOBS) || level.players().isEmpty()) return;
        enforce(level, level.players());
    }

    /** One budget pass; also the deterministic entry point for the headless tests. */
    public static void enforce(ServerLevel level, List<? extends Player> players) {
        var wilds = loadedWildlife(level);
        int globalCap = Config.POPULATION_GLOBAL_CAP.get();
        if (wilds.size() > globalCap) {
            cull(level, wilds, wilds.size() - globalCap);
            wilds = loadedWildlife(level);
        }
        double radius = Config.POPULATION_RADIUS.get();
        double minDistance = Config.POPULATION_MIN_DISTANCE.get();
        for (var player : players) {
            int nearby = 0;
            for (var creature : wilds) if (creature.distanceToSqr(player) <= radius * radius) nearby++;
            int target = Config.POPULATION_TARGET.get();
            if (nearby > target + Config.POPULATION_CULL_MARGIN.get()) {
                var local = new ArrayList<CreatureEntity>();
                for (var creature : wilds) if (creature.distanceToSqr(player) <= radius * radius) local.add(creature);
                cull(level, local, nearby - target);
                continue;
            }
            if (nearby >= target) continue;
            var wanted = missingRegionalLarge(level, player, wilds, radius);
            for (int attempt = 0; attempt < Config.POPULATION_ATTEMPTS.get(); attempt++) {
                // The first attempt chases one missing regional large; later attempts fall back to the
                // normal roll so an unreachable apex cannot stall the budget.
                var only = attempt == 0 && wanted != null ? java.util.Collections.singleton(wanted) : null;
                if (tryGroup(level, player, radius, minDistance, only)) break;
            }
        }
    }

    /** One regional large species absent near the player and legal at the player's danger, or null. */
    private static @Nullable Species missingRegionalLarge(ServerLevel level, Player player,
            List<CreatureEntity> wilds, double radius) {
        var present = java.util.EnumSet.noneOf(Species.class);
        for (var creature : wilds)
            if (creature.distanceToSqr(player) <= radius * radius) present.add(creature.species());
        var missing = new ArrayList<Species>();
        int danger = ProgressionData.dangerAt(level, player.blockPosition());
        for (var species : REGIONAL_LARGE)
            if (!present.contains(species) && danger >= species.minimumDanger()) missing.add(species);
        return missing.isEmpty() ? null : missing.get(level.getRandom().nextInt(missing.size()));
    }

    private static ArrayList<CreatureEntity> loadedWildlife(ServerLevel level) {
        var wilds = new ArrayList<CreatureEntity>();
        for (var entity : level.getAllEntities())
            if (entity instanceof CreatureEntity creature && creature.isAlive() && creature.isNaturalWildlife())
                wilds.add(creature);
        return wilds;
    }

    /** Discards the farthest cullable wilds first; tames, riders and leashed creatures are immune. */
    private static void cull(ServerLevel level, List<CreatureEntity> candidates, int excess) {
        if (excess <= 0) return;
        var cullable = candidates.stream()
                .filter(c -> !c.isPersistenceRequired() && !c.isPassenger() && !c.isVehicle() && !c.isLeashed())
                .sorted(Comparator.comparingDouble(c -> -nearestPlayerDistanceSqr(level, c)))
                .limit(excess).toList();
        for (var creature : cullable) creature.discard();
    }

    private static double nearestPlayerDistanceSqr(ServerLevel level, CreatureEntity creature) {
        double nearest = Double.MAX_VALUE;
        for (var player : level.players()) nearest = Math.min(nearest, player.distanceToSqr(creature));
        return nearest;
    }

    /** Finds one biome/danger-legal group site around the player and places the group. */
    private static boolean tryGroup(ServerLevel level, Player player, double radius, double minDistance,
            @Nullable Set<Species> only) {
        int reach = (int) radius;
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = player.getBlockX() + level.getRandom().nextInt(reach * 2 + 1) - reach;
            int z = player.getBlockZ() + level.getRandom().nextInt(reach * 2 + 1) - reach;
            if (player.distanceToSqr(x + 0.5, player.getY(), z + 0.5) < minDistance * minDistance) continue;
            var anchor = new BlockPos(x, player.getBlockY(), z);
            if (!level.hasChunkAt(anchor) || !level.canSpawnEntitiesInChunk(new ChunkPos(anchor.getX() >> 4, anchor.getZ() >> 4))) continue;
            var species = pickSpecies(level, anchor, only);
            if (species == null) continue;
            if (species.aquatic()) {
                var surface = dev.nez.arksurvivalreturns.feature.aquatic.Water.surfaceWater(level, x, z);
                if (surface == null || !dev.nez.arksurvivalreturns.feature.aquatic.Water.siteAllowed(level, species, surface))
                    continue;
                return spawnGroup(level, species, surface, true);
            }
            var surface = SpawnRules.surface(level, x, z);
            if (surface == null) continue;
            var type = ModContent.CREATURES.get(species).get();
            if (!SpawnRules.canSpawn(type, level, EntitySpawnReason.NATURAL, surface, level.getRandom())) continue;
            return spawnGroup(level, species, surface, false);
        }
        return false;
    }

    /**
     * Weighted pick over species whose minimum danger admits this position. Ordinary species stay inside
     * their biome tag; regional large species are only biased by it (x3 in habitat, x1 outside), so an
     * apex can rarely cross into a neighbouring level-4/5 habitat instead of being impossible there.
     */
    private static Species pickSpecies(ServerLevel level, BlockPos pos, @Nullable Set<Species> only) {
        int danger = ProgressionData.dangerAt(level, pos);
        if (danger < 1) return null;
        var biome = level.getBiome(pos);
        var eligible = new ArrayList<Species>();
        var weights = new ArrayList<Integer>();
        int total = 0;
        for (var species : Species.values()) {
            if (species.weight <= 0 || danger < species.minimumDanger()) continue;
            if (only != null && !only.contains(species)) continue;
            boolean habitat = biome.is(species.biomes);
            if (!habitat && !REGIONAL_LARGE.contains(species)) continue;
            int weight = selectionWeight(species, habitat);
            eligible.add(species);
            weights.add(weight);
            total += weight;
        }
        if (eligible.isEmpty()) return null;
        int roll = level.getRandom().nextInt(total);
        for (int i = 0; i < eligible.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return eligible.get(i);
        }
        return eligible.getFirst();
    }

    /** Biome tag bonus: the natural habitat triples a species' chance, off-habitat apex strays keep the base. */
    private static int selectionWeight(Species species, boolean habitat) {
        int base = Math.max(1, species.weight);
        return habitat ? base * 3 : base;
    }

    private static boolean spawnGroup(ServerLevel level, Species species, BlockPos anchor, boolean water) {
        var type = ModContent.CREATURES.get(species).get();
        int count = species.minGroup + level.getRandom().nextInt(1 + species.maxGroup - species.minGroup);
        int spread = species.solitary() ? 2 : 6;
        SpawnGroupData data = null;
        int placed = 0;
        for (int i = 0; i < count; i++) {
            int x = anchor.getX() + level.getRandom().nextInt(spread * 2 + 1) - spread;
            int z = anchor.getZ() + level.getRandom().nextInt(spread * 2 + 1) - spread;
            BlockPos pos;
            if (water) {
                var surface = dev.nez.arksurvivalreturns.feature.aquatic.Water.surfaceWater(level, x, z);
                if (surface == null || !dev.nez.arksurvivalreturns.feature.aquatic.Water.siteAllowed(level, species, surface))
                    continue;
                pos = surface;
            } else {
                var surface = SpawnRules.surface(level, x, z);
                if (surface == null || !SpawnRules.canSpawn(type, level, EntitySpawnReason.NATURAL, surface, level.getRandom()))
                    continue;
                pos = surface;
            }
            var creature = type.create(level, EntitySpawnReason.NATURAL);
            if (creature == null) continue;
            creature.snapTo(x + 0.5, pos.getY(), z + 0.5, level.getRandom().nextFloat() * 360f, 0f);
            if (!EventHooks.checkSpawnPosition(creature, level, EntitySpawnReason.NATURAL)) {
                creature.discard();
                continue;
            }
            data = creature.finalizeSpawn(level, level.getCurrentDifficultyAt(creature.blockPosition()),
                    EntitySpawnReason.NATURAL, data);
            level.addFreshEntityWithPassengers(creature);
            placed++;
        }
        return placed > 0;
    }

    private NaturalPopulations() {}
}
