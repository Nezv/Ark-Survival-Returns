package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayList;
import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

/**
 * Saved predator-prey abundance of every overworld region a player has been near ({@link LedgerModel}).
 * A region is advanced lazily, over the in-game days since it was last read, so land nobody visits keeps
 * changing without ticking. The population budget reads it to size and mix the wildlife it places; the
 * only outside inputs are animals players kill or tame. Wild predation is the model's own term and is not
 * reported, and budget culls are not deaths, so nothing is counted twice.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class RegionalLedger extends SavedData {
    /** Share of a placed population that is carnivorous, used to weigh the two densities into one. */
    public static final double PREDATOR_SHARE = 0.35;

    private record Entry(int x, int z, double prey, double predators, double day) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Entry::x),
                Codec.INT.fieldOf("z").forGetter(Entry::z),
                Codec.DOUBLE.fieldOf("prey").forGetter(Entry::prey),
                Codec.DOUBLE.fieldOf("predators").forGetter(Entry::predators),
                Codec.DOUBLE.fieldOf("day").forGetter(Entry::day)
        ).apply(i, Entry::new));
    }

    public static final Codec<RegionalLedger> CODEC = Entry.CODEC.listOf().xmap(entries -> new RegionalLedger(entries), RegionalLedger::entries);
    public static final SavedDataType<RegionalLedger> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("regional_ledger"), () -> new RegionalLedger(), CODEC);

    private static @Nullable LedgerModel model;
    private static boolean modelCycles;
    private static double modelDays;

    private static final class Region {
        LedgerModel.State state;
        double day;
        Region(LedgerModel.State state, double day) { this.state = state; this.day = day; }
    }

    private final Long2ObjectMap<Region> regions = new Long2ObjectOpenHashMap<>();

    public RegionalLedger() {}

    private RegionalLedger(List<Entry> entries) {
        for (var entry : entries)
            regions.put(key(entry.x(), entry.z()), new Region(new LedgerModel.State(entry.prey(), entry.predators()), entry.day()));
    }

    private List<Entry> entries() {
        var out = new ArrayList<Entry>(regions.size());
        for (var entry : regions.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            var region = entry.getValue();
            out.add(new Entry((int) (key >> 32), (int) key, region.state.prey(), region.state.predators(), region.day));
        }
        return out;
    }

    /** The overworld ledger; the population budget only runs there. */
    public static RegionalLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The model for the current configuration, rebuilt when the cycle settings change. */
    public static synchronized LedgerModel model() {
        boolean cycles = Config.LEDGER_CYCLES.get();
        double days = Config.LEDGER_CYCLE_DAYS.get();
        if (model == null || cycles != modelCycles || days != modelDays) {
            model = new LedgerModel(cycles, days);
            modelCycles = cycles;
            modelDays = days;
        }
        return model;
    }

    public static int regionOf(int block) { return Math.floorDiv(block, Config.LEDGER_REGION_SIZE.get()); }

    private static long key(int regionX, int regionZ) { return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL); }

    /** The region holding this block column, advanced to the current day. */
    public LedgerModel.State at(ServerLevel level, int x, int z) {
        int regionX = regionOf(x), regionZ = regionOf(z);
        double today = level.getServer().overworld().getGameTime() / 24000.0;
        var region = regions.get(key(regionX, regionZ));
        if (region == null) {
            long seed = level.getServer().overworld().getSeed() ^ regionX * 0x9E3779B97F4A7C15L ^ regionZ * 0xC2B2AE3D27D4EB4FL;
            region = new Region(model().initial(seed), today);
            regions.put(key(regionX, regionZ), region);
            setDirty();
        } else if (today - region.day > 0.01) {
            region.state = model().advance(region.state, today - region.day);
            region.day = today;
            setDirty();
        }
        return region.state;
    }

    /** Prey and predators weighed into one abundance, 1 at balance. */
    public double abundance(ServerLevel level, int x, int z) {
        var state = at(level, x, z);
        return state.prey() * (1 - PREDATOR_SHARE) + state.predators() * PREDATOR_SHARE;
    }

    /** One animal of a side as a share of that side's balance in a region of the configured size and density. */
    public static double animalShare(boolean predator) {
        double chunks = Math.pow(Config.LEDGER_REGION_SIZE.get(), 2) / 256.0;
        double animals = Config.POPULATION_DENSITY.get() * chunks * (predator ? PREDATOR_SHARE : 1 - PREDATOR_SHARE);
        return 1 / Math.max(1, animals);
    }

    /** Takes one animal out of the region at this position. */
    public void remove(ServerLevel level, BlockPos pos, boolean predator) {
        var state = at(level, pos.getX(), pos.getZ());
        regions.get(key(regionOf(pos.getX()), regionOf(pos.getZ()))).state = LedgerModel.remove(state, predator, animalShare(predator));
        setDirty();
    }

    /** A natural animal leaves the wild population for good. */
    public static void record(CreatureEntity creature) {
        if (!(creature.level() instanceof ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || !creature.isNaturalWildlife() || !NaturalPopulations.ledger()) return;
        get(level).remove(level, creature.blockPosition(), creature.species().predator);
    }

    /** Hunting by players and their tames counts; deaths among wild animals belong to the model. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void died(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof CreatureEntity creature) || creature.level().isClientSide()) return;
        if (byPlayer(event.getSource().getEntity()) || byPlayer(creature.getKillCredit())) record(creature);
    }

    private static boolean byPlayer(@Nullable Entity attacker) {
        return attacker instanceof Player || attacker instanceof CreatureEntity tame && TamingService.of(tame).tamed();
    }
}
