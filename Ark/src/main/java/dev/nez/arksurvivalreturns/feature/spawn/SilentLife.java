package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.aquatic.Water;
import dev.nez.arksurvivalreturns.feature.creature.LevelScaling;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.recorder.SessionRecorder;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * The life of the animals beyond the loaded land. There a wild animal is its record in the {@link WildlifeRegister}
 * and nothing else, and the records of a biome region ({@link LandRegister}) live by rounds of cheap rules: an
 * animal past its span dies of age, a group feeds, a hungry pack of hunters is matched against a group of its
 * region by the odds of their strength, a fed group below its size may gain a young, and the group shifts a little.
 * The rules weigh the species, the size of the group, the water of the region and its hunters. A region gets a round
 * each silentRoundDays, and silentLivedInRounds times as often once players have stayed a day in it.
 *
 * <p>No record leaves the chunk it was left in, so the body saved there stays its own. As the chunk loads the body
 * is brought to its record (the place and the hunger), a record born meanwhile gets a body, and the body of one
 * that died stays out. Only groups with no member in the world, and none somebody is taming, take part.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class SilentLife {
    public static final String AGE = "age", STARVED = "starved";
    /** A region is lived in once players have stayed this many game days in it. */
    public static final double STAY_DAYS = 1.0;
    /** Blocks a group may shift each way in a round; every animal stops at the edge of the chunk it was left in. */
    public static final int SHIFT = 8;
    /** Hunger after grazing and after a kill, what a round adds to a hunter's, from where a pack hunts and below which a group breeds. */
    public static final double GRAZED = 0.2, FED = 0.05, APPETITE = 0.3, HUNTS_FROM = 0.6, BREEDS_BELOW = 0.5;
    /**
     * Odds a round: of a young in a fed group below its size, and what is left of them in a region without water;
     * of a death in a pack that starves; and of one in a pack that prey standing its ground has beaten off.
     */
    public static final double BIRTH = 0.25, DRY = 0.5, STARVES = 0.25, STRIKES_BACK = 0.3;
    private static final int ROUNDS_AT_ONCE = 4, PROBES = 6, MISSES = 12;
    private static final int NO_ROOM = 0, WAIT = 1, BORN = 2;
    /** Looks in which a record found no room for its body in its loaded chunk. */
    private static final Map<UUID, Integer> MISSED = new HashMap<>();

    /** A group in a round: its records and the hunger they share. */
    private static final class Herd {
        final Species species;
        final List<WildlifeRegister.Life> members = new ArrayList<>();
        double hunger;

        Herd(Species species) { this.species = species; }
    }

    /** A region in a round: the groups that take part, and how many groups and groups of hunters it holds in all, loaded or not. */
    private record Country(List<Herd> herds, int[] groups) {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var level = event.getServer().overworld();
        var register = WildlifeRegister.get(level);
        if (!register.returning().isEmpty()) welcome(level, register);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { MISSED.clear(); }

    /** One look from the population budget: the stays counted, the rounds that are due lived, and bodies given to the records in loaded chunks. */
    static void pass(ServerLevel level, List<? extends Player> players) {
        if (Config.SILENT_LIFE.get()) {
            var land = LandRegister.get(level);
            var register = WildlifeRegister.get(level);
            double today = today(level);
            for (var player : players)
                land.stay(land.regionAt(level, player.getBlockX(), player.getBlockZ()), Config.POPULATION_INTERVAL.get() / 24000.0);
            for (var entry : gather(level, land, register).entrySet()) {
                if (entry.getValue().herds().isEmpty()) continue;
                int rounds = land.due(entry.getKey(), today, every(entry.getKey()), ROUNDS_AT_ONCE);
                for (int round = 0; round < rounds; round++) round(level, register, entry.getKey(), entry.getValue(), today);
            }
        }
        embody(level, players);
    }

    /** Game days between two rounds in the region: fewer once players have stayed a day in it. */
    public static double every(LandRegister.Region region) {
        return Config.SILENT_ROUND_DAYS.get() / (region.stayed() >= STAY_DAYS ? Config.SILENT_LIVED_IN_ROUNDS.get() : 1);
    }

    /** One round for the region now, whatever its clock says; for the tests. */
    public static void round(ServerLevel level, LandRegister.Region region) {
        var register = WildlifeRegister.get(level);
        var country = gather(level, LandRegister.get(level), register).get(region);
        if (country != null) round(level, register, region, country, today(level));
    }

    /** Game days a species lives: the larger the animal, the longer. */
    public static double lifespan(Species species) {
        return Config.WILD_LIFESPAN_DAYS.get() * (0.5 + species.health / 100.0);
    }

    /**
     * The day an animal dies of age: a quarter to five quarters of its species' span after it appeared, the same
     * whenever it is asked. The budget places animals of any age, so the first of a new land do not die together.
     */
    public static double lastDay(WildlifeRegister.Life life, Species species) {
        long mix = (life.id().getMostSignificantBits() ^ life.id().getLeastSignificantBits()) * 0x9E3779B97F4A7C15L;
        return life.appeared() + lifespan(species) * (0.25 + ((mix ^ mix >>> 32) >>> 11) / (double) (1L << 53));
    }

    private static double today(ServerLevel level) {
        return level.getServer().overworld().getGameTime() / 24000.0;
    }

    /** The regions with their groups, from the register: a group takes part while none of its animals is in the world or in somebody's hands. */
    private static Map<LandRegister.Region, Country> gather(ServerLevel level, LandRegister land, WildlifeRegister register) {
        Map<UUID, Herd> packs = new HashMap<>();
        Set<UUID> held = new HashSet<>();
        for (WildlifeRegister.Life life : register.living()) {
            Species species = WildClass.species(life.species());
            if (species == null) continue;
            packs.computeIfAbsent(life.pack(), ignored -> new Herd(species)).members.add(life);
            if (life.claimed() || level.getEntity(life.id()) != null) held.add(life.pack());
        }
        Map<LandRegister.Region, Country> countries = new IdentityHashMap<>();
        packs.forEach((pack, herd) -> {
            var first = herd.members.getFirst();
            var country = countries.computeIfAbsent(land.regionAt(level, first.x(), first.z()), ignored -> new Country(new ArrayList<>(), new int[2]));
            country.groups()[0]++;
            if (herd.species.predator) country.groups()[1]++;
            if (!held.contains(pack)) country.herds().add(herd);
        });
        return countries;
    }

    private static void round(ServerLevel level, WildlifeRegister register, LandRegister.Region region, Country country, double today) {
        var random = level.getRandom();
        var herds = country.herds();
        // Age first: an animal past its span is not there to feed, to breed or to be hunted.
        for (Herd herd : herds) {
            herd.hunger = 0;
            for (var members = herd.members.iterator(); members.hasNext();) {
                var life = members.next();
                if (today >= lastDay(life, herd.species)) {
                    register.end(level, life, AGE);
                    members.remove();
                } else herd.hunger += life.hunger();
            }
            if (!herd.members.isEmpty()) herd.hunger /= herd.members.size();
        }
        // Plant eaters graze where they stand; hunters grow hungry, and hunt once they are.
        for (Herd herd : herds) {
            if (herd.members.isEmpty()) continue;
            if (!herd.species.predator) herd.hunger = GRAZED;
            else {
                herd.hunger = Math.min(1.0, herd.hunger + APPETITE);
                if (herd.hunger >= HUNTS_FROM) hunt(level, register, herd, herds);
            }
        }
        boolean water = region.hasWater();
        int groups = country.groups()[0], hunters = country.groups()[1];
        for (Herd herd : herds) {
            int size = herd.members.size();
            // A young for a fed pair or more, below the size the species keeps: less likely without water in the
            // region and, for what is hunted, the more of the region's groups are hunters.
            if (size >= 2 && size < herd.species.maxGroup && herd.hunger < BREEDS_BELOW) {
                double odds = BIRTH * (water || herd.species.aquatic() || herd.species.coldAdapted() ? 1.0 : DRY)
                        * (herd.species.predator || groups == 0 ? 1.0 : 1.0 - (double) hunters / groups);
                if (random.nextDouble() < odds) {
                    var parent = herd.members.get(random.nextInt(size));
                    herd.members.add(new WildlifeRegister.Life(UUID.randomUUID(), parent.species(), parent.pack(), parent.level(),
                            parent.x(), parent.y(), parent.z(), today, today, false, herd.hunger, false, true, false));
                }
            }
            // The group shifts as one and each animal stops at the edge of its chunk; a flyer keeps its place and its height.
            int dx = herd.species.flyer() ? 0 : random.nextInt(SHIFT * 2 + 1) - SHIFT;
            int dz = herd.species.flyer() ? 0 : random.nextInt(SHIFT * 2 + 1) - SHIFT;
            for (int i = 0; i < herd.members.size(); i++) {
                var life = herd.members.get(i);
                int west = life.x() & ~15, north = life.z() & ~15;
                var now = life.after(Math.clamp(life.x() + dx, west, west + 15), Math.clamp(life.z() + dz, north, north + 15), herd.hunger);
                herd.members.set(i, now);
                register.put(now);
            }
        }
    }

    /**
     * A hungry pack is matched against one group of its region, taken by the odds it has against each: its strength
     * over the strength of both. It wins, the weakest of the prey dies and the pack is fed; or it loses, and prey
     * that stands its ground may kill one of the pack. A pack that goes on starving loses a member to hunger.
     */
    private static void hunt(ServerLevel level, WildlifeRegister register, Herd pack, List<Herd> herds) {
        var random = level.getRandom();
        double might = power(pack), total = 0;
        List<Herd> prey = new ArrayList<>();
        List<Double> odds = new ArrayList<>();
        for (Herd herd : herds) {
            if (herd == pack || herd.members.isEmpty() || !preys(pack.species, herd.species)) continue;
            double chance = might / (might + power(herd));
            prey.add(herd);
            odds.add(chance);
            total += chance;
        }
        if (!prey.isEmpty()) {
            double roll = random.nextDouble() * total;
            int pick = 0;
            while (pick < prey.size() - 1 && (roll -= odds.get(pick)) >= 0) pick++;
            Herd herd = prey.get(pick);
            if (random.nextDouble() < odds.get(pick)) {
                register.end(level, weakest(herd), pack.species.id);
                pack.hunger = FED;
            } else if (!herd.species.timid() && random.nextDouble() < STRIKES_BACK * (1.0 - odds.get(pick)))
                register.end(level, weakest(pack), herd.species.id);
        }
        if (pack.hunger >= 1.0 && !pack.members.isEmpty() && random.nextDouble() < STARVES) register.end(level, weakest(pack), STARVED);
    }

    /** What a hunter hunts beyond the loaded land: the plant eaters of its realm that keep to the ground or the water, and a giant among hunters the lesser hunters too. */
    private static boolean preys(Species hunter, Species prey) {
        return hunter.aquatic() == prey.aquatic() && !prey.flyer() && (!prey.predator || hunter.apex() && !prey.apex());
    }

    /** The strength of a group: the health times the damage of each of its animals at its level. */
    private static double power(Herd herd) {
        double health = Config.HEALTH_GROWTH.get(), damage = Config.DAMAGE_GROWTH.get(), sum = 0;
        for (var life : herd.members)
            sum += LevelScaling.health(herd.species.health, life.level(), health) * LevelScaling.damage(herd.species.damage, life.level(), damage);
        return sum;
    }

    /** Takes the animal of the lowest level out of the group. */
    private static WildlifeRegister.Life weakest(Herd herd) {
        int pick = 0;
        for (int i = 1; i < herd.members.size(); i++) if (herd.members.get(i).level() < herd.members.get(pick).level()) pick = i;
        return herd.members.remove(pick);
    }

    /**
     * Gives a body to every record in a loaded chunk that has none in the world: one born beyond the loaded land, or
     * one whose saved body did not come back with the chunk. One that finds no room in its chunk, look after look,
     * is taken off the register.
     */
    public static void embody(ServerLevel level, List<? extends Player> players) {
        var register = WildlifeRegister.get(level);
        List<WildlifeRegister.Life> waiting = new ArrayList<>();
        for (var life : register.living()) {
            int chunkX = life.x() >> 4, chunkZ = life.z() >> 4;
            if (level.getEntity(life.id()) == null && level.getChunkSource().getChunkNow(chunkX, chunkZ) != null
                    && level.areEntitiesLoaded(ChunkPos.pack(chunkX, chunkZ))) waiting.add(life);
        }
        for (var life : waiting) {
            Species species = WildClass.species(life.species());
            if (species == null) continue;
            int outcome = body(level, players, life, species);
            if (outcome == BORN) MISSED.remove(life.id());
            else if (outcome == NO_ROOM && MISSED.merge(life.id(), 1, Integer::sum) >= MISSES) {
                MISSED.remove(life.id());
                register.end(level, life, WildlifeRegister.REMOVED);
            }
        }
    }

    /** Makes the animal of a record where it is, or elsewhere in its chunk, away from every player and out of their sight. */
    private static int body(ServerLevel level, List<? extends Player> players, WildlifeRegister.Life life, Species species) {
        var random = level.getRandom();
        double near = Config.POPULATION_MIN_DISTANCE.get();
        boolean watched = false;
        for (int probe = 0; probe <= PROBES; probe++) {
            int x = probe == 0 ? life.x() : (life.x() & ~15) + random.nextInt(16), z = probe == 0 ? life.z() : (life.z() & ~15) + random.nextInt(16);
            BlockPos site = site(level, species, x, z);
            if (site == null) continue;
            boolean close = NaturalPopulations.seen(level, players, x + 0.5, site.getY(), z + 0.5, species.width, species.height);
            for (var player : players) {
                double dx = player.getX() - (x + 0.5), dz = player.getZ() - (z + 0.5);
                if (dx * dx + dz * dz < near * near) close = true;
            }
            if (close) {
                watched = true;
                continue;
            }
            var creature = ModContent.CREATURES.get(species).get().create(level, EntitySpawnReason.NATURAL);
            if (creature == null) return NO_ROOM;
            creature.setUUID(life.id());
            creature.snapTo(x + 0.5, site.getY(), z + 0.5, random.nextFloat() * 360f, 0f);
            // Its level is the record's: set before the spawn rolls one.
            creature.initializeLevel(life.level());
            EventHooks.finalizeMobSpawn(creature, level, level.getCurrentDifficultyAt(creature.blockPosition()), EntitySpawnReason.NATURAL, null);
            if (creature.isSpawnCancelled()) {
                creature.discard();
                return NO_ROOM;
            }
            creature.joinPack(life.pack());
            var mind = creature.wildlife().mind();
            mind.restoreNeeds(life.hunger(), mind.thirst(), mind.fatigue());
            SessionRecorder.note(creature, "silent_body");
            level.addFreshEntityWithPassengers(creature);
            return level.getEntity(life.id()) == creature ? BORN : NO_ROOM;
        }
        return watched ? WAIT : NO_ROOM;
    }

    /**
     * Brings each body that is back in the world to its record: to its hunger, and to where the group shifted if it
     * can stand there and no player watches it go or come.
     */
    public static void welcome(ServerLevel level, WildlifeRegister register) {
        var back = List.copyOf(register.returning());
        register.returning().clear();
        var players = level.players();
        for (var creature : back) {
            if (creature.isRemoved() || !creature.isAlive()) continue;
            var life = register.life(creature.getUUID());
            if (life != null && life.silent()) {
                var species = creature.species();
                if (life.x() != creature.getBlockX() || life.z() != creature.getBlockZ()) {
                    BlockPos site = site(level, species, life.x(), life.z());
                    if (site != null && !NaturalPopulations.seen(level, players, creature)
                            && !NaturalPopulations.seen(level, players, site.getX() + 0.5, site.getY(), site.getZ() + 0.5, species.width, species.height)) {
                        creature.getNavigation().stop();
                        creature.snapTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, creature.getYRot(), creature.getXRot());
                    }
                }
                var mind = creature.wildlife().mind();
                mind.restoreNeeds(life.hunger(), mind.thirst(), mind.fatigue());
            }
            register.keep(level, creature);
        }
    }

    /** Where the body of the species can be at this column of a loaded chunk: on ground it can stand on, or in water deep enough for an animal of the sea. */
    private static @Nullable BlockPos site(ServerLevel level, Species species, int x, int z) {
        if (species.aquatic()) {
            BlockPos water = Water.surfaceWater(level, x, z);
            return water != null && Water.siteAllowed(level, species, water) ? water : null;
        }
        BlockPos ground = SpawnRules.surface(level, x, z);
        return ground != null && SpawnRules.stands(level, species, ground) ? ground : null;
    }

    private SilentLife() {}
}
