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
 * beside itself. A walking player has the land ahead filled first, where it is hidden from them.
 *
 * <p>Nothing appears or vanishes while somebody watches: an animal is placed, and a spare group removed, only
 * where no player has it in plain sight ({@link PlainSight}): behind them, behind a hill or a wood, or so far
 * off that it is a speck. And an animal the server has sent to a client is never removed at all: it is in the
 * {@link WildlifeRegister} and lives until it dies. Above its targets the budget only stops placing. Beyond the
 * loaded land the animals live on as records ({@link SilentLife}), which the BIOME model's pass also moves on.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class NaturalPopulations {
    /**
     * BIOME: the land's biome regions are settled once as their chunks load and kept at their quotas by arrivals that
     * come with the days ({@link LandRegister}). LEDGER: density near players scaled by the regional predator-prey
     * ledger. BUDGET: fixed target per player.
     */
    public enum Model { BIOME, LEDGER, BUDGET }

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
    /** A group is only removed this far from every player and out of their sight, a couple per check, so nothing vanishes in view. */
    private static final int CULL_DISTANCE = 56, CULLED_PER_PASS = 2;
    /** The chunk each player stood in at the previous check of the BIOME model: one who was elsewhere has just come to this land. */
    private static final Map<UUID, ChunkPos> LAST_CHUNK = new HashMap<>();
    /** Each player's position at the previous check, for the direction of travel. */
    private static final Map<UUID, Vec3> LAST_SEEN = new HashMap<>();
    /** What the last pass counted for each player; the debug screen of a single-player client reads it from another thread. */
    private static final Map<UUID, Census> CENSUS = new java.util.concurrent.ConcurrentHashMap<>();

    /** One pass's count for a player: natural wildlife loaded against its cap, and groups (LEDGER) or animals (BUDGET) nearby against the target. */
    public record Census(int loaded, int cap, int nearby, int target, boolean groups) {}

    /** The last pass's count for this player, or null when the budget has not run for them. */
    public static @Nullable Census census(UUID player) { return CENSUS.get(player); }

    public static boolean isRegionalLarge(Species species) { return REGIONAL_LARGE.contains(species); }

    public static boolean ledger() { return Config.POPULATION_MODEL.get() == Model.LEDGER; }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % Config.POPULATION_INTERVAL.get() != 0) return;
        var level = server.overworld();
        if (!Config.NATURAL_SPAWNS.get() || !Config.POPULATION_BUDGET.get()
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            CENSUS.clear();
            return;
        }
        var players = level.players().stream().filter(player -> !player.isSpectator()).toList();
        if (!players.isEmpty()) enforce(level, players);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LAST_SEEN.clear(); LAST_CHUNK.clear(); CENSUS.clear(); }

    /** One budget pass; also the deterministic entry point for the headless tests. */
    public static void enforce(ServerLevel level, List<? extends Player> players) {
        boolean ledger = ledger();
        var wilds = loadedWildlife(level);
        WildlifeRegister.get(level).refresh(level, wilds);
        if (Config.POPULATION_MODEL.get() == Model.BIOME) {
            settle(level, players, wilds);
            return;
        }
        int globalCap = globalCap(players.size());
        if (wilds.size() > globalCap) {
            cull(level, players, wilds, wilds.size() - globalCap);
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
            CENSUS.put(player.getUUID(), new Census(wilds.size(), globalCap, nearby, target, ledger));
            int ceiling = ledger ? (int) Math.ceil(target * (1 + Config.POPULATION_CULL_FRACTION.get()))
                    : target + Config.POPULATION_CULL_MARGIN.get();
            if (nearby > ceiling) {
                // The ledger target drifts with the cycle: only the excess goes, whole groups at a time and
                // out of sight, so a falling target neither thins a herd nor empties the view.
                if (ledger) cullGroups(level, players, near, nearby - ceiling);
                else cull(level, players, local, nearby - target);
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
                int added = tryGroup(level, player, players, radius, minDistance, only, ledger, ledger && attempt % 2 == 0 ? heading : null,
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

    /**
     * The BIOME model. Every loaded chunk in reach of a player is looked at once: it gets its share of its region's
     * groups, drawn from the world's seed, while the region is below its quota. After that a region below a quota
     * takes in what the days have allowed it, in a chunk nobody watches. Nothing is placed around a player for being
     * there, and nothing is removed: what lives is in the {@link WildlifeRegister} until it dies.
     */
    private static void settle(ServerLevel level, List<? extends Player> players, List<CreatureEntity> wilds) {
        SilentLife.pass(level, players);
        var land = LandRegister.get(level);
        var counts = land.count(level, WildlifeRegister.get(level));
        var groups = new ArrayList<>(groups(wilds));
        int cap = globalCap(players.size()), loaded = wilds.size(), kinds = WildClass.values().length;
        double today = level.getServer().overworld().getGameTime() / 24000.0;
        int view = level.getServer().getPlayerList().getViewDistance(), reach = view > 0 ? Math.min(view, 12) : 8;
        Map<LandRegister.Region, List<ChunkPos>> inReach = new java.util.IdentityHashMap<>();
        // Land a player has only just come to, by joining or by a leap, they have not looked at yet: its first animals
        // are there before they do. Only the players who were already here count as watching.
        var watchers = new ArrayList<Player>();
        for (var player : players) {
            var here = new ChunkPos(player.getBlockX() >> 4, player.getBlockZ() >> 4);
            var last = LAST_CHUNK.put(player.getUUID(), here);
            if (last != null && Math.abs(last.getMinBlockX() - here.getMinBlockX()) <= 64 && Math.abs(last.getMinBlockZ() - here.getMinBlockZ()) <= 64)
                watchers.add(player);
        }
        for (var player : players) {
            int centreX = player.getBlockX() >> 4, centreZ = player.getBlockZ() >> 4;
            for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
                int chunkX = centreX + dx, chunkZ = centreZ + dz;
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                var tile = land.tile(level, chunkX, chunkZ);
                land.survey(level, tile, chunk);
                var region = land.region(tile, chunkX, chunkZ);
                inReach.computeIfAbsent(region, ignored -> new ArrayList<>()).add(new ChunkPos(chunkX, chunkZ));
                if (land.settled(tile, chunkX, chunkZ)) continue;
                land.settle(tile, chunkX, chunkZ);
                int[] count = counts.computeIfAbsent(region, ignored -> new int[kinds]);
                for (WildClass kind : WildClass.values()) {
                    int quota = land.quota(level, region, kind);
                    if (quota == 0 || count[kind.ordinal()] >= quota || loaded >= cap || !land.suits(tile, chunkX, chunkZ, kind)
                            || share(level.getSeed(), chunkX, chunkZ, kind) >= (double) quota / region.cells) continue;
                    int placed = place(level, players, watchers, kind, chunkX, chunkZ, groups);
                    if (placed > 0) {
                        count[kind.ordinal()]++;
                        loaded += placed;
                    }
                }
            }
        }
        var random = level.getRandom();
        for (var entry : inReach.entrySet()) {
            var region = entry.getKey();
            var chunks = entry.getValue();
            int[] count = counts.computeIfAbsent(region, ignored -> new int[kinds]);
            land.grow(level, region, today, count);
            for (WildClass kind : WildClass.values()) {
                if (region.arrivals(kind) < 1.0 || count[kind.ordinal()] >= land.quota(level, region, kind) || loaded >= cap) continue;
                int placed = 0;
                for (int attempt = 0; attempt < 4 && placed == 0; attempt++) {
                    var pos = chunks.get(random.nextInt(chunks.size()));
                    int chunkX = pos.getMinBlockX() >> 4, chunkZ = pos.getMinBlockZ() >> 4;
                    if (land.suits(land.tile(level, chunkX, chunkZ), chunkX, chunkZ, kind)) placed = place(level, players, players, kind, chunkX, chunkZ, groups);
                }
                if (placed == 0) {
                    land.full(region, kind);
                    continue;
                }
                land.arrived(region, kind);
                count[kind.ordinal()]++;
                loaded += placed;
            }
        }
        for (var player : players) {
            var region = land.regionAt(level, player.getBlockX(), player.getBlockZ());
            int[] count = counts.getOrDefault(region, new int[kinds]);
            int have = 0, room = 0;
            for (WildClass kind : WildClass.values()) {
                have += count[kind.ordinal()];
                room += land.quota(level, region, kind);
            }
            CENSUS.put(player.getUUID(), new Census(wilds.size(), cap, have, room, true));
        }
    }

    /** A number from 0 to 1 that belongs to this chunk and class in this world: the same whenever it is asked. */
    private static double share(long seed, int chunkX, int chunkZ, WildClass kind) {
        long mix = seed ^ chunkX * 0x9E3779B97F4A7C15L ^ chunkZ * 0xC2B2AE3D27D4EB4FL ^ (kind.ordinal() + 1) * 0x165667B19E3779F9L;
        mix = (mix ^ mix >>> 33) * 0xFF51AFD7ED558CCDL;
        mix = (mix ^ mix >>> 33) * 0xC4CEB9FE1A85EC53L;
        return ((mix ^ mix >>> 33) >>> 11) / (double) (1L << 53);
    }

    /**
     * Puts one group of the class into the chunk where the placement rules allow, away from every player and out of
     * the sight of those watching; the animals placed.
     */
    private static int place(ServerLevel level, List<? extends Player> players, List<? extends Player> watchers, WildClass kind,
                             int chunkX, int chunkZ, List<Group> groups) {
        var random = level.getRandom();
        double minDistance = Config.POPULATION_MIN_DISTANCE.get();
        boolean water = kind == WildClass.SEA;
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = (chunkX << 4) + random.nextInt(16), z = (chunkZ << 4) + random.nextInt(16);
            boolean close = nearestGroupSqr(groups, x + 0.5, z + 0.5, null) < GROUP_SPACING * GROUP_SPACING;
            for (var player : players) if (horizontalSqr(player, x + 0.5, z + 0.5) < minDistance * minDistance) close = true;
            if (close) continue;
            BlockPos site = water ? Water.surfaceWater(level, x, z) : SpawnRules.surface(level, x, z);
            if (site == null) continue;
            var species = pickOfClass(level, site, kind, groups);
            if (species == null) continue;
            if (water) {
                if (!Water.siteAllowed(level, species, site)) continue;
            } else if (!SpawnRules.canSpawn(ModContent.CREATURES.get(species).get(), level, EntitySpawnReason.NATURAL, site, random)) {
                site = roomNear(level, species, site);
                if (site == null) continue;
            }
            if (seen(level, watchers, site.getX() + 0.5, site.getY(), site.getZ() + 0.5, species.width, species.height)) continue;
            int placed = spawnGroup(level, watchers, species, site, water);
            if (placed == 0) continue;
            groups.add(new Group(species, List.of(), site.getX() + 0.5, site.getZ() + 0.5));
            return placed;
        }
        return 0;
    }

    /** A species of the class that may live at the site: its danger zone and range, apart from its own kind and, a hunter, from any group. */
    private static @Nullable Species pickOfClass(ServerLevel level, BlockPos pos, WildClass kind, List<Group> groups) {
        int danger = ProgressionData.dangerAt(level, pos);
        if (danger < 1) return null;
        var biome = level.getBiome(pos);
        var profile = SurfaceBiomes.profile(biome);
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5, total = 0;
        boolean crowded = nearestGroupSqr(groups, x, z, null) < PREDATOR_SPACING * PREDATOR_SPACING;
        var eligible = new ArrayList<Species>();
        for (var species : Species.values()) {
            if (species.weight <= 0 || WildClass.of(species) != kind || danger < species.minimumDanger()) continue;
            if (!biome.is(species.biomes) && !SpeciesRange.lives(species, profile)) continue;
            if (nearestGroupSqr(groups, x, z, species) < SPECIES_SPACING * SPECIES_SPACING || species.predator && crowded) continue;
            eligible.add(species);
            total += species.weight;
        }
        if (eligible.isEmpty()) return null;
        double roll = level.getRandom().nextDouble() * total;
        for (var species : eligible) {
            roll -= species.weight;
            if (roll < 0) return species;
        }
        return eligible.getLast();
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

    /** Discards the farthest cullable wilds nobody watches first; tames, riders, leashed and mid-tame creatures are immune. */
    private static void cull(ServerLevel level, List<? extends Player> players, List<CreatureEntity> candidates, int excess) {
        if (excess <= 0) return;
        var cullable = candidates.stream()
                .filter(NaturalPopulations::cullable)
                .filter(creature -> !seen(level, players, creature))
                .sorted(Comparator.comparingDouble(c -> -nearestPlayerDistanceSqr(level, c)))
                .limit(excess).toList();
        for (var creature : cullable) {
            SessionRecorder.note(creature, "budget_cull");
            creature.discard();
        }
    }

    /** Removes whole spare groups, farthest first: never one a player is near or watches, never one with a member someone needs. */
    private static void cullGroups(ServerLevel level, List<? extends Player> players, List<Group> candidates, int excess) {
        var spare = candidates.stream()
                .filter(group -> group.members().stream().allMatch(NaturalPopulations::cullable))
                .filter(group -> group.members().stream().allMatch(c -> nearestPlayerDistanceSqr(level, c) >= CULL_DISTANCE * CULL_DISTANCE))
                .filter(group -> group.members().stream().noneMatch(c -> seen(level, players, c)))
                .sorted(Comparator.comparingDouble((Group group) -> -nearestPlayerDistanceSqr(level, group.members().getFirst())))
                .limit(Math.min(excess, CULLED_PER_PASS)).toList();
        for (var group : spare) for (var creature : group.members()) {
            SessionRecorder.note(creature, "budget_cull");
            creature.discard();
        }
    }

    /**
     * A creature someone is taming is not spare wildlife: knocked out, claimed, fed or holding deposited
     * food, it would vanish with the tamer's food and progress. Nor is one a client was ever sent: a player may
     * know it, so it stays until it dies. The list may be stale after an earlier cull.
     */
    public static boolean cullable(CreatureEntity c) {
        return !c.isRemoved() && !c.isPersistenceRequired() && !c.shown() && !inUse(c);
    }

    /** Somebody rides, leads or is taming this animal; the rules beyond the loaded land leave it alone as well ({@link SilentLife}). */
    public static boolean inUse(CreatureEntity c) {
        if (c.isPassenger() || c.isVehicle() || c.isLeashed()) return true;
        if (TorporService.restricted(c) || !c.tamingInventory().isEmpty()) return true;
        if (!TamingService.tracked(c)) return false;
        var taming = TamingService.of(c);
        return taming.claimant() != null || taming.progress() > 0f;
    }

    static boolean seen(ServerLevel level, List<? extends Player> players, CreatureEntity creature) {
        return seen(level, players, creature.getX(), creature.getY(), creature.getZ(), creature.getBbWidth(), creature.getBbHeight());
    }

    /** Whether a player would watch an animal of this size appear or vanish with its feet here (spawning.populationOutOfSight). */
    static boolean seen(ServerLevel level, List<? extends Player> players, double x, double y, double z, float width, float height) {
        return Config.POPULATION_OUT_OF_SIGHT.get() && PlainSight.seen(level, players, x, y, z, width, height);
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
    private static int tryGroup(ServerLevel level, Player player, List<? extends Player> players, double radius, double minDistance,
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
                if (seen(level, players, site.getX() + 0.5, site.getY(), site.getZ() + 0.5, species.width, species.height)) continue;
                return spawnGroup(level, players, species, site, true);
            }
            var type = ModContent.CREATURES.get(species).get();
            if (!SpawnRules.canSpawn(type, level, EntitySpawnReason.NATURAL, site, random)) {
                site = roomNear(level, species, site);
                if (site == null) continue;
            }
            // Nobody may watch the group appear: the next try falls elsewhere.
            if (seen(level, players, site.getX() + 0.5, site.getY(), site.getZ() + 0.5, species.width, species.height)) continue;
            return spawnGroup(level, players, species, site, false);
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

    private static int spawnGroup(ServerLevel level, List<? extends Player> players, Species species, BlockPos anchor, boolean water) {
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
                // A member that would step out from behind the cover its group was anchored in stays unborn.
                if (seen(level, players, x + 0.5, pos.getY(), z + 0.5, species.width, species.height)) continue;
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
