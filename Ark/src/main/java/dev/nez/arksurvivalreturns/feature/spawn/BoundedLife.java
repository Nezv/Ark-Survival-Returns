package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.recorder.SessionRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * The bounded rules of the life beyond the loaded land (spawning.silentRules = BOUNDED): the model that
 * tools/existence_model.js lives through without the game, here on the register itself. Every daily odd of a record
 * is a clamped linear function of the pressure on what it eats, so a region can neither empty its land nor outgrow it.
 *
 * <p>A region grows a supply of appetites a day: its chunks that were seen loaded and showed open ground (water, for
 * a region of the sea) times spawning.boundedLife.supply times the richness of its biome. An appetite is what an animal of 50 base HP eats; another eats (HP / 50) ^ 0.75. The plant eaters share
 * the supply, the hunters what the plant eaters carry, the hunting giants what both carry, and the flyers a small
 * share of the supply of their own. The pressure p on a role is its appetite over its food, counted over every
 * record of the region, in the world or not. From it come the three functions the odds are made of, each kept
 * between 0 and 1: the room left (1 - p), the food found (2 - p) and, for what is hunted, the pressure of its hunters.
 *
 * <p>Each day a record takes a meal: found, it is a step better fed; missed, a step hungrier (fed, hungry, starving).
 * A starving one may die of it, any may be hunted, a fed one with another of its species in the region may have a
 * young where the land can feed it as well, and one past its span dies of age. A young stays with its group while
 * there is room in it, else joins another of its kind, else founds one elsewhere in the region. A class below its
 * quota of groups takes arrivals in at the pace of spawning.populationRefillDays, and a class at its quota still
 * meets wanderers; of either only as many come as the land can feed. They come as records, into chunks that were
 * seen loaded; no chunk is loaded for them. Only groups with no animal in the world or in somebody's hands live by
 * these odds, like under the first rules; the others eat all the same.
 */
public final class BoundedLife {
    /** What a species eats: the land, or those who do. */
    public enum Role { GRAZER, HUNTER, APEX, FLYER }

    /** How appetite grows with size, what snow leaves of a biome's supply, and the share of it the flyers have for themselves. */
    public static final double SIZE_EXPONENT = 0.75, SNOW = 0.5, FLYER_SHARE = 0.07;
    /** The sea feeds its plant eaters little and its hunters much of their own: shares of a sea region's supply. */
    public static final double SEA_GRAZER = 0.05, SEA_HUNTER = 0.5, SEA_APEX = 0.12;
    /** The step of hunger a meal moves, and the hunger an arrival comes with and a young is born with. */
    public static final double MEAL = 0.5, ARRIVES = 0.55, BORN = 0.5;
    /** At the first settling of a chunk the plant eaters are placed before those who eat them. */
    static final WildClass[][] SETTLING = {{WildClass.GRAZER, WildClass.FLYER, WildClass.SEA}, {WildClass.HUNTER, WildClass.APEX}};
    private static final int ROLES = Role.values().length;
    /** What a biome grows, against the richest plain or forest; the values of design/existence/model.json. */
    private static final EnumMap<BiomeProfile.Type, Double> FERTILITY = new EnumMap<>(BiomeProfile.Type.class);

    static {
        for (var type : BiomeProfile.Type.values()) FERTILITY.put(type, 0.6);
        FERTILITY.put(BiomeProfile.Type.GRASSLAND, 1.0);
        FERTILITY.put(BiomeProfile.Type.FOREST, 1.0);
        FERTILITY.put(BiomeProfile.Type.JUNGLE, 1.2);
        FERTILITY.put(BiomeProfile.Type.WETLAND, 1.1);
        FERTILITY.put(BiomeProfile.Type.SAVANNA, 0.85);
        FERTILITY.put(BiomeProfile.Type.TAIGA, 0.8);
        FERTILITY.put(BiomeProfile.Type.RIVER, 0.8);
        FERTILITY.put(BiomeProfile.Type.SHRUBLAND, 0.6);
        FERTILITY.put(BiomeProfile.Type.COAST, 0.6);
        FERTILITY.put(BiomeProfile.Type.MUSHROOM, 0.6);
        FERTILITY.put(BiomeProfile.Type.MOUNTAIN, 0.5);
        FERTILITY.put(BiomeProfile.Type.TUNDRA, 0.5);
        FERTILITY.put(BiomeProfile.Type.SKY_ISLAND, 0.5);
        FERTILITY.put(BiomeProfile.Type.BADLANDS, 0.4);
        FERTILITY.put(BiomeProfile.Type.GEOTHERMAL, 0.4);
        FERTILITY.put(BiomeProfile.Type.DESERT, 0.35);
        FERTILITY.put(BiomeProfile.Type.VOLCANIC, 0.3);
        FERTILITY.put(BiomeProfile.Type.CAVE, 0.3);
        FERTILITY.put(BiomeProfile.Type.OCEAN, 1.0);
        FERTILITY.put(BiomeProfile.Type.UNKNOWN, 0.6);
    }

    public static Role role(Species species) {
        return species.predator ? species.apex() ? Role.APEX : Role.HUNTER : species.flyer() ? Role.FLYER : Role.GRAZER;
    }

    /** What the species eats a day, in appetites of an animal of 50 base HP. */
    public static double appetite(Species species) { return Math.pow(species.health / 50.0, SIZE_EXPONENT); }

    /** How fast the species lives: 1 for an animal of 50 base HP, less for a larger one. */
    public static double tempo(Species species) { return 1.0 / (0.5 + species.health / 100.0); }

    public static double fertility(BiomeProfile.Type type) { return FERTILITY.get(type); }

    /** What a region of this biome grows against the richest: less under snow and without water at the surface. */
    public static double richness(BiomeProfile profile, boolean water) {
        return fertility(profile.type()) * (profile.snowy() ? SNOW : 1.0) * (water ? 1.0 : Config.BOUNDED_DRY.get());
    }

    private static double clamp(double value) { return value < 0.0 ? 0.0 : Math.min(value, 1.0); }

    /**
     * The appetites of a region and what feeds them: for each role its animals, its appetite, its food and the
     * pressure of the one on the other, with the room left, the food found and the hunters at its heels. An animal
     * out of its element (one of the land in a region of the sea, or the reverse) is a guest: it eats nothing here.
     */
    public static final class Census {
        public final boolean sea;
        public final double supply, richness;
        public final int[] animals = new int[ROLES];
        public final double[] demand = new double[ROLES], food = new double[ROLES], pressure = new double[ROLES];
        final double[] room = new double[ROLES], found = new double[ROLES], hunted = new double[ROLES];
        public int guests;
        private final EnumMap<Species, int[]> alive = new EnumMap<>(Species.class);

        Census(boolean sea, double supply, double richness) {
            this.sea = sea;
            this.supply = supply;
            this.richness = richness;
        }

        boolean home(Species species) { return species.aquatic() == sea; }

        int alive(Species species) {
            int[] count = alive.get(species);
            return count == null ? 0 : count[0];
        }

        /** So many more of the species live in the region; what feeds the others is weighed again only by {@link #weigh}. */
        void add(Species species, int members) {
            if (!home(species)) {
                guests += members;
                return;
            }
            int role = role(species).ordinal();
            animals[role] += members;
            demand[role] += members * appetite(species);
            alive.computeIfAbsent(species, ignored -> new int[1])[0] += members;
        }

        private double food(Role role) {
            double carryHunter = Config.BOUNDED_CARRY_HUNTER.get(), carryApex = Config.BOUNDED_CARRY_APEX.get();
            double grazers = demand[Role.GRAZER.ordinal()], hunters = demand[Role.HUNTER.ordinal()];
            return switch (role) {
                case GRAZER -> (sea ? SEA_GRAZER : 1.0) * supply;
                case HUNTER -> (sea ? SEA_HUNTER * supply : 0.0) + carryHunter * grazers;
                case APEX -> (sea ? SEA_APEX * supply : 0.0) + carryApex * (grazers + hunters);
                case FLYER -> sea ? 0.0 : FLYER_SHARE * supply;
            };
        }

        /** The food and the pressures from the appetites as they are now. */
        void weigh() {
            for (Role role : Role.values()) {
                int i = role.ordinal();
                food[i] = food(role);
                pressure[i] = food[i] > 0 ? demand[i] / food[i] : demand[i] > 0 ? 2.0 : 0.0;
                room[i] = clamp(1.0 - pressure[i]);
                found[i] = clamp(2.0 - pressure[i]);
                hunted[i] = 0;
            }
            int grazer = Role.GRAZER.ordinal(), hunter = Role.HUNTER.ordinal(), apex = Role.APEX.ordinal();
            if (demand[hunter] > 0 && Config.BOUNDED_CARRY_HUNTER.get() > 0) hunted[grazer] += clamp(pressure[hunter]);
            if (demand[apex] > 0 && Config.BOUNDED_CARRY_APEX.get() > 0) {
                hunted[grazer] += clamp(pressure[apex]);
                hunted[hunter] += clamp(pressure[apex]);
            }
        }

        /** How many of a group of the species the land can feed as well: as many as keep the pressure on their food at or below 1. */
        public int fed(Species species, int size) {
            if (!home(species)) return 0;
            int role = role(species).ordinal();
            return (int) Math.max(0, Math.min(size, Math.floor((food[role] - demand[role]) / appetite(species) + 1.0e-9)));
        }

        /** The species a hunted animal of the role fell to: one of those that eat it here, by their pressure and their appetite. */
        String hunter(int prey, RandomSource random) {
            double total = 0;
            for (var entry : alive.entrySet()) total += weight(entry.getKey(), entry.getValue()[0], prey);
            double roll = random.nextDouble() * total;
            for (var entry : alive.entrySet()) {
                double weight = weight(entry.getKey(), entry.getValue()[0], prey);
                if (weight > 0 && (roll -= weight) < 0) return entry.getKey().id;
            }
            return SilentLife.HUNTED;
        }

        private double weight(Species species, int members, int prey) {
            Role role = role(species);
            boolean eats = role == Role.APEX ? prey == Role.GRAZER.ordinal() || prey == Role.HUNTER.ordinal()
                    : role == Role.HUNTER && prey == Role.GRAZER.ordinal();
            return eats ? clamp(pressure[role.ordinal()]) * members * appetite(species) : 0.0;
        }
    }

    /**
     * What a round did in a region, for the session recorder: the region as the round found it (by role its animals,
     * in the world or not, their appetite, their food and the pressure), the groups and the animals by role that
     * lived the round, and what came of it.
     */
    public record Report(LandRegister.Region region, int tileX, int tileZ, int index, double day, double days, boolean sea, double supply,
                         double richness, int[] animals, double[] demand, double[] food, double[] pressure, int guests, int groups, int[] taking,
                         int aged, int hunted, int starved, int born, int arrived) {}

    /** A young that is due: the group of its parent, and the parent. */
    private record Young(SilentLife.Herd herd, WildlifeRegister.Life parent) {}

    private static Census start(ServerLevel level, LandRegister land, LandRegister.Region region) {
        double richness = richness(land.profile(level, region), region.hasWater());
        boolean sea = land.sea(level, region);
        return new Census(sea, region.known(sea) * Config.BOUNDED_SUPPLY.get() * richness, richness);
    }

    private static Census census(ServerLevel level, LandRegister land, LandRegister.Region region, SilentLife.Country country) {
        Census census = start(level, land, region);
        for (var herd : country.herds()) census.add(herd.species, herd.members.size());
        for (var herd : country.held()) census.add(herd.species, herd.members.size());
        census.weigh();
        return census;
    }

    /** The census of every region with a living record, from the register: for the budget, which settles a chunk only with what the land can feed. */
    static Map<LandRegister.Region, Census> censuses(ServerLevel level, LandRegister land, WildlifeRegister register) {
        Map<LandRegister.Region, Census> all = new IdentityHashMap<>();
        for (WildlifeRegister.Life life : register.living()) {
            Species species = WildClass.species(life.species());
            if (species == null) continue;
            var region = land.regionAt(level, life.x(), life.z());
            all.computeIfAbsent(region, ignored -> start(level, land, region)).add(species, 1);
        }
        all.values().forEach(Census::weigh);
        return all;
    }

    /** The census of one region now, from the register; for /arkwildlife land. */
    public static Census of(ServerLevel level, LandRegister.Region region) {
        var land = LandRegister.get(level);
        Census census = censuses(level, land, WildlifeRegister.get(level)).get(region);
        return census != null ? census : empty(level, land, region);
    }

    /** The census of a region nobody of the register lives in. */
    static Census empty(ServerLevel level, LandRegister land, LandRegister.Region region) {
        Census census = start(level, land, region);
        census.weigh();
        return census;
    }

    /** The rounds that are due, for every region whose land was seen or whose records live: an emptied region too takes its arrivals in. */
    static void pass(ServerLevel level, LandRegister land, WildlifeRegister register, Map<LandRegister.Region, SilentLife.Country> countries,
                     double today) {
        long began = SessionRecorder.on() ? System.nanoTime() : 0;
        int regions = 0, rounds = 0;
        for (LandRegister.Tile tile : land.divided()) {
            for (int index = 0; index < tile.regions.size(); index++) {
                var region = tile.regions.get(index);
                var country = countries.get(region);
                if (country == null && region.surveyed() == 0) continue;
                double every = SilentLife.every(region);
                int due = land.due(region, today, every, SilentLife.ROUNDS_AT_ONCE);
                if (due == 0) continue;
                if (country == null) countries.put(region, country = new SilentLife.Country(new ArrayList<>(), new ArrayList<>(), new int[2]));
                regions++;
                for (int round = 0; round < due; round++, rounds++) round(level, land, register, tile, index, country, today, every);
            }
        }
        if (began != 0 && rounds > 0) SessionRecorder.rounds(System.nanoTime() - began, regions, rounds);
    }

    /** One round for the region now, whatever its clock says; for the tests. */
    static void round(ServerLevel level, LandRegister.Region region, SilentLife.Country country, double today, double days) {
        var land = LandRegister.get(level);
        for (LandRegister.Tile tile : land.divided()) {
            int index = tile.regions.indexOf(region);
            if (index >= 0) round(level, land, WildlifeRegister.get(level), tile, index, country, today, days);
        }
    }

    private static void round(ServerLevel level, LandRegister land, WildlifeRegister register, LandRegister.Tile tile, int index,
                              SilentLife.Country country, double today, double days) {
        var region = tile.regions.get(index);
        var random = level.getRandom();
        Census now = census(level, land, region, country);
        int[] animals = now.animals.clone();
        double[] demand = now.demand.clone(), food = now.food.clone(), pressure = now.pressure.clone();
        int guests = now.guests;
        double meal = Math.min(1.0, days), starve = 1.0 - Math.pow(1.0 - Config.BOUNDED_STARVE.get(), days);
        double kill = Config.BOUNDED_KILL.get(), fecundity = Config.BOUNDED_FECUNDITY.get();
        int groups = 0, aged = 0, hunted = 0, starved = 0, born = 0, arrived = 0;
        int[] taking = new int[ROLES];
        List<Young> due = new ArrayList<>();
        for (SilentLife.Herd herd : country.herds()) {
            if (herd.members.isEmpty()) continue;
            Species species = herd.species;
            boolean home = now.home(species);
            int role = role(species).ordinal();
            groups++;
            if (home) taking[role] += herd.members.size();
            double caught = 1.0 - Math.pow(1.0 - Math.min(1.0, kill * tempo(species) * now.hunted[role]), days);
            double young = 1.0 - Math.pow(1.0 - Math.min(1.0, fecundity / SilentLife.lifespan(species) * now.room[role]), days);
            boolean mate = now.alive(species) >= 2;
            for (int i = herd.members.size() - 1; i >= 0; i--) {
                var life = herd.members.get(i);
                // Age first: an animal past its span is not there to feed, to breed or to be hunted.
                if (today >= SilentLife.lastDay(life, species)) {
                    herd.members.remove(i);
                    register.end(level, life, SilentLife.AGE);
                    aged++;
                    continue;
                }
                if (!home) continue;
                if (random.nextDouble() < caught) {
                    herd.members.remove(i);
                    register.end(level, life, now.hunter(role, random));
                    hunted++;
                    continue;
                }
                // A meal a day: found, the animal is a step better fed; missed, a step hungrier.
                double hunger = life.hunger();
                if (random.nextDouble() < meal)
                    hunger = random.nextDouble() < now.found[role] ? Math.max(0.0, hunger - MEAL) : Math.min(1.0, hunger + MEAL);
                if (hunger >= 1.0 && random.nextDouble() < starve) {
                    herd.members.remove(i);
                    register.end(level, life, SilentLife.STARVED);
                    starved++;
                    continue;
                }
                if (hunger != life.hunger()) herd.members.set(i, life = life.after(life.x(), life.z(), hunger));
                if (hunger <= 0.0 && mate && random.nextDouble() < young) due.add(new Young(herd, life));
            }
        }
        // A young is born only where the land can feed it as well. It stays with its group while there is room in it;
        // from a full group it joins another of its kind that has room, and where every group is full it founds one
        // of its own, elsewhere in the region: it has no body, so no chunk holds it yet.
        for (Young one : due) {
            Species species = one.herd().species;
            if (now.fed(species, 1) == 0) continue;
            now.add(species, 1);
            SilentLife.Herd home = !one.herd().members.isEmpty() && one.herd().members.size() < species.maxGroup ? one.herd() : null;
            for (int i = 0; home == null && i < country.herds().size(); i++) {
                var other = country.herds().get(i);
                if (other.species == species && !other.members.isEmpty() && other.members.size() < species.maxGroup) home = other;
            }
            int x = one.parent().x(), y = one.parent().y(), z = one.parent().z();
            UUID pack;
            if (home != null) {
                var beside = home.members.getFirst();
                x = beside.x();
                y = beside.y();
                z = beside.z();
                pack = beside.pack();
            } else {
                ChunkPos chunk = land.suited(level, tile, index, WildClass.of(species), random);
                if (chunk != null) {
                    x = chunk.getMinBlockX() + random.nextInt(16);
                    y = height(region);
                    z = chunk.getMinBlockZ() + random.nextInt(16);
                }
                pack = UUID.randomUUID();
                country.herds().add(home = new SilentLife.Herd(species));
            }
            var child = new WildlifeRegister.Life(UUID.randomUUID(), species.id, pack, level(level, x, y, z), x, y, z, today, today, false, BORN,
                    false, true, false);
            home.members.add(child);
            register.put(child);
            SessionRecorder.lifeBorn(child);
            born++;
        }
        // The group shifts as one and each animal stops at the edge of its chunk; a flyer keeps its place and its height.
        for (SilentLife.Herd herd : country.herds()) {
            int dx = herd.species.flyer() ? 0 : random.nextInt(SilentLife.SHIFT * 2 + 1) - SilentLife.SHIFT;
            int dz = herd.species.flyer() ? 0 : random.nextInt(SilentLife.SHIFT * 2 + 1) - SilentLife.SHIFT;
            for (int i = 0; i < herd.members.size(); i++) {
                var life = herd.members.get(i);
                int west = life.x() & ~15, north = life.z() & ~15;
                var moved = life.after(Math.clamp(life.x() + dx, west, west + 15), Math.clamp(life.z() + dz, north, north + 15), life.hunger());
                herd.members.set(i, moved);
                register.put(moved);
            }
        }
        // Arrivals: a class below its quota of groups is allowed its whole quota in populationRefillDays, never more
        // than it is short of; one at its quota still meets wanderers, so a species the region has lost comes back.
        int[] count = new int[WildClass.values().length];
        for (var herd : country.herds()) if (!herd.members.isEmpty()) count[WildClass.of(herd.species).ordinal()]++;
        for (var herd : country.held()) if (!herd.members.isEmpty()) count[WildClass.of(herd.species).ordinal()]++;
        land.grow(level, region, today, count);
        double wander = Config.BOUNDED_WANDER.get();
        for (WildClass kind : WildClass.values()) {
            int quota = land.quota(level, region, kind);
            while (region.arrivals(kind) >= 1.0 && count[kind.ordinal()] < quota) {
                int came = come(level, land, register, tile, index, country, now, kind, today);
                if (came == 0) {
                    land.full(region, kind);
                    break;
                }
                land.arrived(region, kind);
                count[kind.ordinal()]++;
                arrived += came;
            }
            if (wander > 0)
                for (double passing = land.room(level, region, kind) * days / wander; passing > 0; passing -= 1.0)
                    if (random.nextDouble() < passing) arrived += come(level, land, register, tile, index, country, now, kind, today);
        }
        if (SessionRecorder.on())
            SessionRecorder.round(new Report(region, tile.x, tile.z, index, today, days, now.sea, now.supply, now.richness, animals, demand, food,
                    pressure, guests, groups, taking, aged, hunted, starved, born, arrived));
    }

    /**
     * A group of the class comes to the region as records: into a chunk that showed what the class stands on, of a
     * species that lives in the biome and the danger zone there, and only as many of it as the land can feed. The
     * animals that came; none where the land feeds no more of them.
     */
    private static int come(ServerLevel level, LandRegister land, WildlifeRegister register, LandRegister.Tile tile, int index,
                            SilentLife.Country country, Census now, WildClass kind, double today) {
        var region = tile.regions.get(index);
        var random = level.getRandom();
        ChunkPos chunk = land.suited(level, tile, index, kind, random);
        if (chunk == null) return 0;
        int x = chunk.getMinBlockX() + random.nextInt(16), z = chunk.getMinBlockZ() + random.nextInt(16), y = height(region);
        Species species = pick(level, land, region, kind, x, y, z);
        if (species == null) return 0;
        int size = now.fed(species, species.minGroup + random.nextInt(1 + species.maxGroup - species.minGroup));
        if (size == 0) return 0;
        UUID pack = UUID.randomUUID();
        var herd = new SilentLife.Herd(species);
        int west = x & ~15, north = z & ~15;
        for (int i = 0; i < size; i++) {
            int px = i == 0 ? x : Math.clamp(x + random.nextInt(7) - 3, west, west + 15), pz = i == 0 ? z : Math.clamp(z + random.nextInt(7) - 3, north, north + 15);
            var life = new WildlifeRegister.Life(UUID.randomUUID(), species.id, pack, level(level, px, y, pz), px, y, pz, today, today, false,
                    ARRIVES, false, true, false);
            herd.members.add(life);
            register.put(life);
            SessionRecorder.lifeArrived(life);
        }
        country.herds().add(herd);
        now.add(species, size);
        return size;
    }

    /** A species of the class that lives in the region's biome and in the danger zone of the place, by its weight. */
    private static @Nullable Species pick(ServerLevel level, LandRegister land, LandRegister.Region region, WildClass kind, int x, int y, int z) {
        int danger = ProgressionData.dangerAt(level, new BlockPos(x, y, z));
        if (danger < 1) return null;
        List<Species> eligible = new ArrayList<>();
        double total = 0;
        for (Species species : Species.values()) {
            if (species.weight <= 0 || WildClass.of(species) != kind || danger < species.minimumDanger() || !land.lives(level, region, species)) continue;
            eligible.add(species);
            total += species.weight;
        }
        if (eligible.isEmpty()) return null;
        double roll = level.getRandom().nextDouble() * total;
        for (Species species : eligible) if ((roll -= species.weight) < 0) return species;
        return eligible.getLast();
    }

    /** The level of an animal that appears at the place: a roll of its danger zone's range, like a body's. */
    private static int level(ServerLevel level, int x, int y, int z) {
        int danger = ProgressionData.dangerAt(level, new BlockPos(x, y, z));
        var tier = danger >= 1 && danger <= DangerTier.values().length ? DangerTier.values()[danger - 1] : DangerTier.EASY;
        int low = Config.MIN_LEVEL.get(tier).get(), high = Config.MAX_LEVEL.get(tier).get();
        return Math.min(low, high) + level.getRandom().nextInt(Math.abs(low - high) + 1);
    }

    /** The height a record that never had a body is entered at; its body finds the ground when its chunk loads. */
    private static int height(LandRegister.Region region) { return (int) Math.round(region.meanHeight()); }

    private BoundedLife() {}
}
