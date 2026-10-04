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
 * model keeps a number of groups around each player, scaled by the {@link RegionalLedger}; BUDGET is the
 * previous fixed number of animals per player.
 *
 * <p>Wildlife is laid out the way livestock is met in the vanilla game: a herd, a pack or a lone animal every
 * few dozen blocks, each of a species whose range holds the biome ({@link SpeciesRange}) and at its full
 * size, apart from one another, with hunters the minority by day and by night and no species repeated
 * beside itself. A walking player has the land ahead filled first.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class NaturalPopulations {
    /** LEDGER: density near players scaled by the regional predator-prey ledger. BUDGET: fixed target per player. */
    public enum Model { LEDGER, BUDGET }

    /**
     * Regional large species the budget wants at least one of near a player, mirroring the deleted
     * director's missing-large priority, so a zone-3 area always has a chance at an apex encounter.
     */
    private static final java.util.EnumSet<Species> REGIONAL_LARGE = java.util.EnumSet.of(
            Species.GIGANOTOSAURUS, Species.TITANOSAUR, Species.TYRANNOSAURUS, Species.BRONTOSAURUS,
            Species.THERIZINOSAURUS, Species.SPINOSAURUS, Species.ACROCANTHOSAURUS);
    /** Blocks between any two groups when one is placed, and around a hunter, so nothing is born into a chase. */
    public static final int GROUP_SPACING = 40, PREDATOR_SPACING = 48;
    /** Blocks between two groups of one species: a second herd of the same animal is a different place. */
    public static final int SPECIES_SPACING = 64;
    /** Share of the groups around a player that may be hunters, and the groups that may be flyers. */
    public static final double PREDATOR_GROUPS = 0.25;
    public static final int FLYER_GROUPS = 2;
    /** Mean group size of the species table: turns the group target into animals for the ledger. */
    public static final double MEAN_GROUP = 2.3;
    /** The regional ledger swings the group target a quarter either way; a bust never empties the land. */
    public static final double LEAN = 0.75, RICH = 1.25;
    /** Tries for each member of a group around its anchor, so trees and slopes do not thin a herd to one animal. */
    private static final int MEMBER_TRIES = 8;
    /** Probes and reach of the search for open ground when the picked species does not fit where the site fell. */
    private static final int ROOM_TRIES = 12, ROOM_REACH = 12;
    /** Blocks a tick (2.4 a second, a slow walk) from which a player counts as travelling. */
    private static final double TRAVEL_PACE = 0.12;
    /** A group is only removed this far from every player, a couple per check, so nothing vanishes in view. */
    private static final int CULL_DISTANCE = 56, CULLED_PER_PASS = 2;
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
            var groups = groups(wilds);
            var near = groups.stream().filter(g -> g.distanceSqr(player.getX(), player.getZ()) <= radius * radius).toList();
            // LEDGER counts groups, BUDGET animals.
            int nearby = ledger ? near.size() : local.size();
            int target = targetFor(level, player);
            int ceiling = ledger ? (int) Math.ceil(target * (1 + Config.POPULATION_CULL_FRACTION.get()))
                    : target + Config.POPULATION_CULL_MARGIN.get();
            if (nearby > ceiling) {
                // The ledger target drifts with the cycle: only the excess goes, whole groups at a time and
                // out of sight, so a falling target neither thins a herd nor empties the view.
                if (ledger) cullGroups(level, near, nearby - ceiling);
                else cull(level, local, nearby - target);
                continue;
            }
            if (nearby >= target) continue;
            var wanted = missingRegionalLarge(level, player, local);
            int perPass = ledger ? Config.POPULATION_GROUPS_PER_PASS.get() : 1;
            int attempts = Config.POPULATION_ATTEMPTS.get() * perPass, loaded = wilds.size(), placed = 0;
            for (int attempt = 0; attempt < attempts && placed < perPass && nearby < target && loaded < globalCap; attempt++) {
                // The first attempt chases one missing regional large; later attempts fall back to the
                // normal roll so an unreachable apex cannot stall the budget. Under the ledger model a
                // travelling player gets every other attempt ahead of them.
                var only = attempt == 0 && wanted != null ? java.util.Collections.singleton(wanted) : null;
                int added = tryGroup(level, player, radius, minDistance, only, ledger, ledger && attempt % 2 == 0 ? heading : null,
                        groups, ledger ? target : 0);
                if (added > 0) {
                    placed++; nearby += ledger ? 1 : added; loaded += added;
                    // The next group of this pass must keep its distance from the one just placed.
                    wilds = loadedWildlife(level);
                    groups = groups(wilds);
                }
            }
        }
    }

    /** What the budget keeps within the population radius of this player: groups under LEDGER, animals under BUDGET. */
    public static int targetFor(ServerLevel level, Player player) {
        if (!ledger()) return Config.POPULATION_TARGET.get();
        return Math.max(1, (int) Math.round(Config.POPULATION_GROUPS.get() * abundanceAround(level, player, Config.POPULATION_RADIUS.get())));
    }

    /** Natural animals per chunk the group target amounts to; the ledger weighs one kill against it. */
    public static double animalsPerChunk() {
        double radius = Config.POPULATION_RADIUS.get();
        return Config.POPULATION_GROUPS.get() * MEAN_GROUP / (Math.PI * radius * radius / 256.0);
    }

    /** One loaded group of natural wildlife: a herd, a pack, a pair or a lone animal. */
    public record Group(Species species, List<CreatureEntity> members, double x, double z) {
        double distanceSqr(double px, double pz) { return (x - px) * (x - px) + (z - pz) * (z - pz); }
    }

    /** The loaded natural wildlife by pack, each at the middle of its members. */
    public static List<Group> groups(List<CreatureEntity> wilds) {
        var packs = new java.util.LinkedHashMap<UUID, List<CreatureEntity>>();
        for (var creature : wilds) if (!creature.isRemoved()) packs.computeIfAbsent(creature.packId(), id -> new ArrayList<>()).add(creature);
        var groups = new ArrayList<Group>();
        for (var members : packs.values()) {
            double x = 0, z = 0;
            for (var member : members) { x += member.getX(); z += member.getZ(); }
            groups.add(new Group(members.getFirst().species(), members, x / members.size(), z / members.size()));
        }
        return groups;
    }

    /** The ledger's abundance averaged over the player's column and four points half a radius away. */
    public static double abundanceAround(ServerLevel level, Player player, double radius) {
        var ledger = RegionalLedger.get(level);
        double sum = 0;
        int half = (int) (radius / 2);
        for (int[] offset : new int[][]{{0, 0}, {half, 0}, {-half, 0}, {0, half}, {0, -half}})
            sum += ledger.abundance(level, player.getBlockX() + offset[0], player.getBlockZ() + offset[1]);
        return Math.clamp(sum / 5, LEAN, RICH);
    }

    /** Loaded natural wildlife allowed in the dimension before the farthest is removed. */
    public static int globalCap(int players) {
        if (!ledger()) return Config.POPULATION_GLOBAL_CAP.get();
        return Math.min(Config.POPULATION_HARD_CAP.get(), Math.max(1, players) * Config.POPULATION_CAP_PER_PLAYER.get());
    }

    /**
     * Horizontal direction of travel since the previous check, when the player kept at least a walking
     * pace, else null.
     */
    private static @Nullable Vec3 travel(Player player) {
        var now = player.position();
        var before = LAST_SEEN.put(player.getUUID(), now);
        if (before == null) return null;
        var moved = new Vec3(now.x - before.x, 0, now.z - before.z);
        double threshold = TRAVEL_PACE * Config.POPULATION_INTERVAL.get();
        return moved.lengthSqr() > threshold * threshold ? moved.normalize() : null;
    }

    /**
     * One regional large species legal at the surface under the player while none is near, or null. One giant
     * in the landscape is the event; the budget never collects the whole list around a player.
     */
    private static @Nullable Species missingRegionalLarge(ServerLevel level, Player player, List<CreatureEntity> local) {
        for (var creature : local) if (REGIONAL_LARGE.contains(creature.species())) return null;
        var ground = SpawnRules.surface(level, player.getBlockX(), player.getBlockZ());
        var at = ground != null ? ground : player.blockPosition();
        var missing = new ArrayList<Species>();
        int danger = ProgressionData.dangerAt(level, at);
        var biome = level.getBiome(at);
        for (var species : REGIONAL_LARGE)
            if (danger >= species.minimumDanger() && SpeciesRange.lives(species, biome))
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

    /** Removes whole spare groups, farthest first: never one a player is near, never one with a member someone needs. */
    private static void cullGroups(ServerLevel level, List<Group> candidates, int excess) {
        var spare = candidates.stream()
                .filter(group -> group.members().stream().allMatch(NaturalPopulations::cullable))
                .filter(group -> group.members().stream().allMatch(c -> nearestPlayerDistanceSqr(level, c) >= CULL_DISTANCE * CULL_DISTANCE))
                .sorted(Comparator.comparingDouble((Group group) -> -nearestPlayerDistanceSqr(level, group.members().getFirst())))
                .limit(Math.min(excess, CULLED_PER_PASS)).toList();
        for (var group : spare) for (var creature : group.members()) {
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
            @Nullable Set<Species> only, boolean ledger, @Nullable Vec3 heading, List<Group> groups, int target) {
        int reach = (int) radius;
        var random = level.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            int x, z;
            if (heading == null && ledger) {
                // Around a player at rest: anywhere in the counted circle past its inner third, evenly by area,
                // so a group appears at a distance instead of popping up beside the camp.
                double near = Math.max(minDistance, radius * 0.3);
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = Math.sqrt(near * near + random.nextDouble() * (radius * radius - near * near));
                x = Mth.floor(player.getX() + Math.cos(angle) * distance);
                z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            } else if (heading != null) {
                // Ahead of a travelling player: within 60 degrees of the heading, in the outer half of the radius.
                double near = Math.max(minDistance, radius / 2);
                double angle = Math.atan2(heading.z, heading.x) + (random.nextDouble() - 0.5) * Math.toRadians(120);
                double distance = near + random.nextDouble() * (radius - near);
                x = Mth.floor(player.getX() + Math.cos(angle) * distance);
                z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            } else {
                x = player.getBlockX() + random.nextInt(reach * 2 + 1) - reach;
                z = player.getBlockZ() + random.nextInt(reach * 2 + 1) - reach;
            }
            if (horizontalSqr(player, x + 0.5, z + 0.5) < minDistance * minDistance) continue;
            if (nearestGroupSqr(groups, x + 0.5, z + 0.5, null) < GROUP_SPACING * GROUP_SPACING) continue;
            // Chunk columns only: the player's own height (deep underground, high in flight) is irrelevant.
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null
                    || !level.canSpawnEntitiesInChunk(new ChunkPos(x >> 4, z >> 4))) continue;
            // The species comes from the biome where the group will stand, never from the player's depth.
            BlockPos site = SpawnRules.surface(level, x, z);
            boolean water = site == null;
            if (water) site = Water.surfaceWater(level, x, z);
            if (site == null) continue;
            var species = pickSpecies(level, site, only, water, ledger, groups, player, radius, target);
            if (species == null) continue;
            if (water) {
                if (!Water.siteAllowed(level, species, site)) continue;
                return spawnGroup(level, species, site, true);
            }
            var type = ModContent.CREATURES.get(species).get();
            if (!SpawnRules.canSpawn(type, level, EntitySpawnReason.NATURAL, site, random)) {
                site = roomNear(level, species, site);
                if (site == null) continue;
            }
            return spawnGroup(level, species, site, false);
        }
        return 0;
    }

    /**
     * Open ground for this species near a site where it did not fit, or null. Without it the large animals
     * lose every wooded site to the small ones, and a forest holds nothing but its smallest resident.
     */
    private static @Nullable BlockPos roomNear(ServerLevel level, Species species, BlockPos site) {
        var type = ModContent.CREATURES.get(species).get();
        var random = level.getRandom();
        for (int probe = 0; probe < ROOM_TRIES; probe++) {
            var spot = SpawnRules.surface(level, site.getX() + random.nextInt(ROOM_REACH * 2 + 1) - ROOM_REACH,
                    site.getZ() + random.nextInt(ROOM_REACH * 2 + 1) - ROOM_REACH);
            if (spot != null && SpeciesRange.lives(species, level.getBiome(spot))
                    && SpawnRules.canSpawn(type, level, EntitySpawnReason.NATURAL, spot, random)) return spot;
        }
        return null;
    }

    /** Squared distance from a point to the nearest loaded group, of one species or of any (null). */
    private static double nearestGroupSqr(List<Group> groups, double x, double z, @Nullable Species species) {
        double nearest = Double.MAX_VALUE;
        for (var group : groups)
            if (species == null || group.species() == species) nearest = Math.min(nearest, group.distanceSqr(x, z));
        return nearest;
    }

    /**
     * Weighted pick over species whose minimum danger admits this position, whose range holds its biome and
     * whose realm fits the site (water-bound on water, the rest on land). The area decides how dangerous the
     * pick may be and the biome which animals live here; the ledger, when used, tilts the pick toward
     * whichever side is abundant in the region.
     *
     * <p>The groups already there decide the rest: no second group of a species beside the first, no hunter
     * beside another group, and under the ledger model flyers and apex animals stay the minority of what a
     * player has around. Land hunters hold a share: about every fourth group is theirs wherever a hunter of
     * the zone lives, and the others go to the plant eaters. Left to the weights alone, the first groups
     * were plant eaters and no site was far enough from them for a hunter any more.
     */
    private static @Nullable Species pickSpecies(ServerLevel level, BlockPos pos, @Nullable Set<Species> only,
            boolean water, boolean ledger, List<Group> groups, Player player, double radius, int target) {
        int danger = ProgressionData.dangerAt(level, pos);
        if (danger < 1) return null;
        var biome = level.getBiome(pos);
        var profile = SurfaceBiomes.profile(biome);
        var region = ledger ? RegionalLedger.get(level).at(level, pos.getX(), pos.getZ()) : null;
        var eligible = new ArrayList<Species>();
        var weights = new ArrayList<Double>();
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
        boolean crowded = nearestGroupSqr(groups, x, z, null) < PREDATOR_SPACING * PREDATOR_SPACING;
        int counted = 0, predators = 0, flyers = 0, apex = 0;
        if (ledger) for (var group : groups) {
            if (group.distanceSqr(player.getX(), player.getZ()) > radius * radius) continue;
            counted++;
            if (group.species().predator) predators++;
            if (group.species().flyer()) flyers++;
            if (group.species().apex()) apex++;
        }
        int predatorCap = Math.max(1, (int) Math.round(target * PREDATOR_GROUPS));
        // The second group around a player is a hunter's, then the sixth and the tenth.
        boolean hunterTurn = predators < Math.min(predatorCap, Math.round((counted + 1) * PREDATOR_GROUPS));
        for (var species : Species.values()) {
            if (species.weight <= 0 || danger < species.minimumDanger() || species.aquatic() != water) continue;
            if (only != null && !only.contains(species)) continue;
            if (!biome.is(species.biomes) && !SpeciesRange.lives(species, profile)) continue;
            if (nearestGroupSqr(groups, x, z, species) < SPECIES_SPACING * SPECIES_SPACING) continue;
            if (species.predator && crowded) continue;
            if (ledger && (species.predator && predators >= predatorCap || species.flyer() && flyers >= FLYER_GROUPS
                    || species.apex() && apex >= 1)) continue;
            eligible.add(species);
            weights.add(species.weight * (region == null ? 1 : species.predator ? region.predators() : region.prey()));
        }
        // On land under the ledger model the pick is taken from one side: the hunters on their turn, the plant
        // eaters otherwise, and whichever side lives here when the other does not. The sea, a requested giant
        // and the BUDGET model keep the plain weighted pick.
        if (ledger && !water && only == null) {
            boolean hunters = hunterTurn && eligible.stream().anyMatch(species -> species.predator)
                    || eligible.stream().allMatch(species -> species.predator);
            for (int i = eligible.size() - 1; i >= 0; i--)
                if (eligible.get(i).predator != hunters) { eligible.remove(i); weights.remove(i); }
        }
        double total = 0;
        for (double weight : weights) total += weight;
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
            // The first member stands on the anchor, which the caller has checked; each of the others gets
            // several tries around it, so the group reaches the size it rolled.
            for (int attempt = 0; attempt < MEMBER_TRIES; attempt++) {
                boolean onAnchor = i == 0 && attempt == 0;
                // Each failed try looks a little farther out, up to the reach of the herd's cohesion.
                int reach = spread + attempt * 2;
                int x = anchor.getX() + (onAnchor ? 0 : level.getRandom().nextInt(reach * 2 + 1) - reach);
                int z = anchor.getZ() + (onAnchor ? 0 : level.getRandom().nextInt(reach * 2 + 1) - reach);
                BlockPos pos = water ? Water.surfaceWater(level, x, z) : SpawnRules.surface(level, x, z);
                if (pos == null) continue;
                if (water ? !Water.siteAllowed(level, species, pos)
                        : !SpawnRules.canSpawn(type, level, EntitySpawnReason.NATURAL, pos, level.getRandom())) continue;
                var creature = type.create(level, EntitySpawnReason.NATURAL);
                if (creature == null) break;
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
                    break;
                }
                SessionRecorder.note(creature, "budget_spawn");
                level.addFreshEntityWithPassengers(creature);
                placed++;
                break;
            }
        }
        return placed;
    }

    private NaturalPopulations() {}
}
