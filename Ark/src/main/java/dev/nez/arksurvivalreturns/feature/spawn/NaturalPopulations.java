package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.aquatic.Water;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.recorder.SessionRecorder;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Independent wildlife budget: keeps a configurable population of mod creatures around players
 * regardless of the vanilla mob cap, using the same danger, habitat and placement rules as the
 * biome spawn tables. It only counts, places and culls natural wildlife; tames, spawn eggs and
 * command summons are never touched, and no chunk is ever force-loaded.
 *
 * <p>Distances are horizontal: wildlife lives on the surface, so a player mining far below still has
 * the animals above counted, and species come from the biome where the group will stand. The LEDGER
 * model keeps a density per chunk scaled by the {@link RegionalLedger}; BUDGET is the previous fixed
 * target per player.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class NaturalPopulations {
    /** LEDGER: density near players scaled by the regional predator-prey ledger. BUDGET: fixed target per player. */
    public enum Model { LEDGER, BUDGET }

    /**
     * Regional large species the budget wants at least one of near a player, mirroring the deleted
     * director's missing-large priority, so a level-4/5 area always has a chance at an apex encounter.
     */
    private static final java.util.EnumSet<Species> REGIONAL_LARGE = java.util.EnumSet.of(
            Species.GIGANOTOSAURUS, Species.TITANOSAUR, Species.TYRANNOSAURUS, Species.BRONTOSAURUS,
            Species.THERIZINOSAURUS, Species.SPINOSAURUS, Species.ACROCANTHOSAURUS);
    /** Each player's position at the previous check, for the direction of travel. */
    private static final Map<UUID, Vec3> LAST_SEEN = new HashMap<>();

    public static boolean isRegionalLarge(Species species) { return REGIONAL_LARGE.contains(species); }

    public static boolean ledger() { return Config.POPULATION_MODEL.get() == Model.LEDGER; }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % Config.POPULATION_INTERVAL.get() != 0) return;
        var level = server.overworld();
        if (!Config.NATURAL_SPAWNS.get() || !Config.POPULATION_BUDGET.get()
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)) return;
        var players = level.players().stream().filter(player -> !player.isSpectator()).toList();
        if (!players.isEmpty()) enforce(level, players);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LAST_SEEN.clear(); }

    /** One budget pass; also the deterministic entry point for the headless tests. */
    public static void enforce(ServerLevel level, List<? extends Player> players) {
        boolean ledger = ledger();
        var wilds = loadedWildlife(level);
        int globalCap = globalCap(players.size());
        if (wilds.size() > globalCap) {
            cull(level, wilds, wilds.size() - globalCap);
            wilds = loadedWildlife(level);
        }
        double radius = Config.POPULATION_RADIUS.get();
        double minDistance = Config.POPULATION_MIN_DISTANCE.get();
        for (var player : players) {
            var heading = travel(player);
            var local = new ArrayList<CreatureEntity>();
            for (var creature : wilds) if (!creature.isRemoved() && horizontalSqr(creature, player.getX(), player.getZ()) <= radius * radius) local.add(creature);
            int nearby = local.size();
            int target = targetFor(level, player);
            int ceiling = ledger ? (int) Math.ceil(target * (1 + Config.POPULATION_CULL_FRACTION.get()))
                    : target + Config.POPULATION_CULL_MARGIN.get();
            if (nearby > ceiling) {
                // The ledger target drifts with the cycle; trimming only the excess keeps a falling
                // target from deleting a quarter of the animals in sight at once.
                cull(level, local, nearby - (ledger ? ceiling : target));
                continue;
            }
            if (nearby >= target) continue;
            var wanted = missingRegionalLarge(level, player, local);
            int groups = ledger ? Config.POPULATION_GROUPS_PER_PASS.get() : 1;
            int attempts = Config.POPULATION_ATTEMPTS.get() * groups, loaded = wilds.size(), placed = 0;
            for (int attempt = 0; attempt < attempts && placed < groups && nearby < target && loaded < globalCap; attempt++) {
                // The first attempt chases one missing regional large; later attempts fall back to the
                // normal roll so an unreachable apex cannot stall the budget. Under the ledger model a
                // travelling player gets every other attempt ahead of them.
                var only = attempt == 0 && wanted != null ? java.util.Collections.singleton(wanted) : null;
                int added = tryGroup(level, player, radius, minDistance, only, ledger, ledger && attempt % 2 == 0 ? heading : null);
                if (added > 0) { placed++; nearby += added; loaded += added; }
            }
            if (placed > 0) wilds = loadedWildlife(level);
        }
    }

    /** Natural animals the budget keeps within the population radius of this player. */
    public static int targetFor(ServerLevel level, Player player) {
        if (!ledger()) return Config.POPULATION_TARGET.get();
        double radius = Config.POPULATION_RADIUS.get();
        return (int) Math.round(Config.POPULATION_DENSITY.get() * Math.PI * radius * radius / 256.0 * abundanceAround(level, player, radius));
    }

    /** The ledger's abundance averaged over the player's column and four points half a radius away. */
    public static double abundanceAround(ServerLevel level, Player player, double radius) {
        var ledger = RegionalLedger.get(level);
        double sum = 0;
        int half = (int) (radius / 2);
        for (int[] offset : new int[][]{{0, 0}, {half, 0}, {-half, 0}, {0, half}, {0, -half}})
            sum += ledger.abundance(level, player.getBlockX() + offset[0], player.getBlockZ() + offset[1]);
        return Math.clamp(sum / 5, 0.25, 2.0);
    }

    /** Loaded natural wildlife allowed in the dimension before the farthest is removed. */
    public static int globalCap(int players) {
        if (!ledger()) return Config.POPULATION_GLOBAL_CAP.get();
        return Math.min(Config.POPULATION_HARD_CAP.get(), Math.max(1, players) * Config.POPULATION_CAP_PER_PLAYER.get());
    }

    /**
     * Horizontal direction of travel since the previous check, when the player covered more than six
     * blocks a second (faster than running), else null.
     */
    private static @Nullable Vec3 travel(Player player) {
        var now = player.position();
        var before = LAST_SEEN.put(player.getUUID(), now);
        if (before == null) return null;
        var moved = new Vec3(now.x - before.x, 0, now.z - before.z);
        double threshold = 0.3 * Config.POPULATION_INTERVAL.get();
        return moved.lengthSqr() > threshold * threshold ? moved.normalize() : null;
    }

    /** One regional large species absent near the player and legal at the surface under the player, or null. */
    private static @Nullable Species missingRegionalLarge(ServerLevel level, Player player, List<CreatureEntity> local) {
        var present = java.util.EnumSet.noneOf(Species.class);
        for (var creature : local) present.add(creature.species());
        var ground = SpawnRules.surface(level, player.getBlockX(), player.getBlockZ());
        var at = ground != null ? ground : player.blockPosition();
        var missing = new ArrayList<Species>();
        int danger = ProgressionData.dangerAt(level, at);
        var biome = level.getBiome(at);
        for (var species : REGIONAL_LARGE)
            if (!present.contains(species) && danger >= species.minimumDanger() && biome.is(species.biomes))
                missing.add(species);
        return missing.isEmpty() ? null : missing.get(level.getRandom().nextInt(missing.size()));
    }

    private static ArrayList<CreatureEntity> loadedWildlife(ServerLevel level) {
        var wilds = new ArrayList<CreatureEntity>();
        for (var entity : level.getAllEntities())
            if (entity instanceof CreatureEntity creature && creature.isAlive() && creature.isNaturalWildlife())
                wilds.add(creature);
        return wilds;
    }

    /** Discards the farthest cullable wilds first; tames, riders, leashed and mid-tame creatures are immune. */
    private static void cull(ServerLevel level, List<CreatureEntity> candidates, int excess) {
        if (excess <= 0) return;
        var cullable = candidates.stream()
                .filter(NaturalPopulations::cullable)
                .sorted(Comparator.comparingDouble(c -> -nearestPlayerDistanceSqr(level, c)))
                .limit(excess).toList();
        for (var creature : cullable) {
            SessionRecorder.note(creature, "budget_cull");
            creature.discard();
        }
    }

    /**
     * A creature someone is taming is not spare wildlife: knocked out, claimed, fed or holding deposited
     * food, it would vanish with the tamer's food and progress. The list may be stale after an earlier cull.
     */
    public static boolean cullable(CreatureEntity c) {
        if (c.isRemoved() || c.isPersistenceRequired() || c.isPassenger() || c.isVehicle() || c.isLeashed()) return false;
        if (TorporService.restricted(c) || !c.tamingInventory().isEmpty()) return false;
        if (!TamingService.tracked(c)) return true;
        var taming = TamingService.of(c);
        return taming.claimant() == null && taming.progress() <= 0f;
    }

    private static double nearestPlayerDistanceSqr(ServerLevel level, CreatureEntity creature) {
        double nearest = Double.MAX_VALUE;
        for (var player : level.players()) nearest = Math.min(nearest, horizontalSqr(creature, player.getX(), player.getZ()));
        return nearest;
    }

    private static double horizontalSqr(Entity entity, double x, double z) {
        double dx = entity.getX() - x, dz = entity.getZ() - z;
        return dx * dx + dz * dz;
    }

    /** Finds one habitat/danger-legal group site around the player, places the group and returns its size. */
    private static int tryGroup(ServerLevel level, Player player, double radius, double minDistance,
            @Nullable Set<Species> only, boolean ledger, @Nullable Vec3 heading) {
        int reach = (int) radius;
        var random = level.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            int x, z;
            if (heading != null) {
                // Ahead of a travelling player: within 60 degrees of the heading, in the outer half of the radius.
                double near = Math.max(minDistance, radius / 2);
                double angle = Math.atan2(heading.z, heading.x) + (random.nextDouble() - 0.5) * Math.toRadians(120);
                double distance = near + random.nextDouble() * (radius - near);
                x = Mth.floor(player.getX() + Math.cos(angle) * distance);
                z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            } else {
                x = player.getBlockX() + random.nextInt(reach * 2 + 1) - reach;
                z = player.getBlockZ() + random.nextInt(reach * 2 + 1) - reach;
                // The ledger model places inside the circle it counts, so every placement counts.
                if (ledger && horizontalSqr(player, x + 0.5, z + 0.5) > radius * radius) continue;
            }
            if (horizontalSqr(player, x + 0.5, z + 0.5) < minDistance * minDistance) continue;
            // Chunk columns only: the player's own height (deep underground, high in flight) is irrelevant.
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null
                    || !level.canSpawnEntitiesInChunk(new ChunkPos(x >> 4, z >> 4))) continue;
            // The species comes from the biome where the group will stand, never from the player's depth.
            BlockPos site = SpawnRules.surface(level, x, z);
            boolean water = site == null;
            if (water) site = Water.surfaceWater(level, x, z);
            if (site == null) continue;
            var species = pickSpecies(level, site, only, water, ledger);
            if (species == null) continue;
            if (water) {
                if (!Water.siteAllowed(level, species, site)) continue;
                return spawnGroup(level, species, site, true);
            }
            var type = ModContent.CREATURES.get(species).get();
            if (!SpawnRules.canSpawn(type, level, EntitySpawnReason.NATURAL, site, random)) continue;
            return spawnGroup(level, species, site, false);
        }
        return 0;
    }

    /**
     * Weighted pick over species whose minimum danger admits this position, whose habitat tag holds its
     * biome and whose realm fits the site (water-bound on water, the rest on land). The area decides how
     * dangerous the pick may be; the biome only decides between the temperate, wetland, cold, sea and sky
     * communities; the ledger, when used, tilts the pick toward whichever side is abundant in the region.
     */
    private static @Nullable Species pickSpecies(ServerLevel level, BlockPos pos, @Nullable Set<Species> only,
            boolean water, boolean ledger) {
        int danger = ProgressionData.dangerAt(level, pos);
        if (danger < 1) return null;
        var biome = level.getBiome(pos);
        var region = ledger ? RegionalLedger.get(level).at(level, pos.getX(), pos.getZ()) : null;
        var eligible = new ArrayList<Species>();
        var weights = new ArrayList<Double>();
        double total = 0;
        boolean openDuringSleep = TreeShelter.sleepWindow(level) && !TreeShelter.treeBiome(level, pos);
        for (var species : Species.values()) {
            if (species.weight <= 0 || danger < species.minimumDanger() || species.aquatic() != water) continue;
            if (only != null && !only.contains(species)) continue;
            if (!biome.is(species.biomes)) continue;
            if (openDuringSleep && TreeShelter.required(species)) continue;
            double weight = species.weight * (region == null ? 1 : species.predator ? region.predators() : region.prey());
            eligible.add(species);
            weights.add(weight);
            total += weight;
        }
        if (eligible.isEmpty() || !(total > 0)) return null;
        double roll = level.getRandom().nextDouble() * total;
        for (int i = 0; i < eligible.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return eligible.get(i);
        }
        return eligible.getLast();
    }

    private static int spawnGroup(ServerLevel level, Species species, BlockPos anchor, boolean water) {
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
                var surface = Water.surfaceWater(level, x, z);
                if (surface == null || !Water.siteAllowed(level, species, surface)) continue;
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
            // Through FinalizeSpawnEvent, so other mods can adjust or cancel these spawns.
            data = EventHooks.finalizeMobSpawn(creature, level, level.getCurrentDifficultyAt(creature.blockPosition()),
                    EntitySpawnReason.NATURAL, data);
            if (creature.isSpawnCancelled()) {
                creature.discard();
                continue;
            }
            SessionRecorder.note(creature, "budget_spawn");
            level.addFreshEntityWithPassengers(creature);
            placed++;
        }
        return placed;
    }

    private NaturalPopulations() {}
}
