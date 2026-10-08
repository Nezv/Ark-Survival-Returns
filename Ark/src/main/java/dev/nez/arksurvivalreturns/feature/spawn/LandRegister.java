package dev.nez.arksurvivalreturns.feature.spawn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The land as wildlife knows it: every tile of 32 by 32 chunks a player has come near, divided into its biome
 * regions ({@link BiomeRegions}), from the world's own biome layout and without loading a chunk for it. A region
 * carries what its chunks showed when they loaded (how many hold open ground, how many water, how high they
 * lie), which of them were given their first animals, and how many groups of each {@link WildClass} it has
 * room for: fewer on poorer land under the bounded rules ({@link BoundedLife}). The population budget counts the
 * {@link WildlifeRegister} against these quotas; a region below one is allowed arrivals at the pace of days, not seconds. A region also keeps how long players have stayed in it and
 * the day its animals beyond the loaded land last lived a round of their rules ({@link SilentLife}).
 */
public final class LandRegister extends SavedData {
    /** Biomes are read well above the ground, where the world's layout gives the surface biome and no cave. */
    private static final int SAMPLE_Y = 256;
    private static final int SURVEYED = 1, SETTLED = 2, GROUND = 4, WATER = 8;
    private static final double NEVER = -1.0e9;
    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(buffer -> {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }, ByteBuffer::wrap);

    /** One biome region of a tile. */
    public static final class Region {
        static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("biome").forGetter(region -> region.biome),
                Codec.INT.fieldOf("cells").forGetter(region -> region.cells),
                Codec.INT.fieldOf("surveyed").forGetter(region -> region.surveyed),
                Codec.INT.fieldOf("ground").forGetter(region -> region.ground),
                Codec.INT.fieldOf("water").forGetter(region -> region.water),
                Codec.LONG.fieldOf("height").forGetter(region -> region.height),
                Codec.DOUBLE.listOf().fieldOf("arrivals").forGetter(region -> java.util.Arrays.stream(region.arrivals).boxed().toList()),
                Codec.DOUBLE.fieldOf("day").forGetter(region -> region.day),
                Codec.DOUBLE.optionalFieldOf("stayed", 0.0).forGetter(region -> region.stayed),
                Codec.DOUBLE.optionalFieldOf("lived", NEVER).forGetter(region -> region.lived)
        ).apply(i, Region::new));

        public final String biome;
        /** Chunks of the region; of them the ones seen loaded, and of those the ones with open ground and with water. */
        public final int cells;
        int surveyed, ground, water;
        long height;
        /** Groups of each class the region may still take in, grown by the days while it is below its quota. */
        final double[] arrivals = new double[WildClass.values().length];
        /** The game day the arrivals were last grown to; NEVER before the first time. */
        double day;
        /** Game days players have spent in the region, all of them together. */
        double stayed;
        /** The game day its animals beyond the loaded land last lived a round; NEVER before the first time. */
        double lived;
        private transient @Nullable BiomeProfile profile;
        private transient @Nullable Holder<Biome> holder;
        /** The classes with a species whose range holds the region's biome. */
        private transient boolean @Nullable [] home;

        Region(String biome, int cells) {
            this(biome, cells, 0, 0, 0, 0L, List.of(), NEVER, 0.0, NEVER);
        }

        private Region(String biome, int cells, int surveyed, int ground, int water, long height, List<Double> arrivals, double day,
                       double stayed, double lived) {
            this.biome = biome;
            this.cells = cells;
            this.surveyed = surveyed;
            this.ground = ground;
            this.water = water;
            this.height = height;
            for (int i = 0; i < Math.min(arrivals.size(), this.arrivals.length); i++) this.arrivals[i] = arrivals.get(i);
            this.day = day;
            this.stayed = stayed;
            this.lived = lived;
        }

        public int surveyed() { return surveyed; }
        /** Of the chunks seen loaded, the ones with open ground and the ones with water at the surface. */
        public int ground() { return ground; }
        public int wet() { return water; }
        /**
         * The chunks the bounded rules count for a class, and for what feeds a region of the land or of the sea: the
         * ones seen loaded that showed what it stands on. Animals can only be where the land is known, so a region
         * holds, and grows food for, as much as was seen of it.
         */
        public int known(boolean sea) { return sea ? water : ground; }
        public double stayed() { return stayed; }
        public double lived() { return lived; }
        public boolean hasWater() { return water > 0; }
        /** The share of its surveyed chunks that hold water at the surface. */
        public double waterShare() { return surveyed == 0 ? 0.0 : (double) water / surveyed; }
        public double meanHeight() { return surveyed == 0 ? 0.0 : (double) height / surveyed; }
        public double arrivals(WildClass kind) { return arrivals[kind.ordinal()]; }
    }

    /** A tile: the region of each of its chunks, what is known of each chunk, and its regions. */
    public static final class Tile {
        static final Codec<Tile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(tile -> tile.x),
                Codec.INT.fieldOf("z").forGetter(tile -> tile.z),
                BYTES.fieldOf("region").forGetter(tile -> tile.region),
                BYTES.fieldOf("marks").forGetter(tile -> tile.marks),
                Region.CODEC.listOf().fieldOf("regions").forGetter(tile -> tile.regions)
        ).apply(i, Tile::new));

        public final int x, z;
        final byte[] region, marks;
        public final List<Region> regions;

        private Tile(int x, int z, byte[] region, byte[] marks, List<Region> regions) {
            this.x = x;
            this.z = z;
            this.region = region;
            this.marks = marks;
            this.regions = List.copyOf(regions);
        }
    }

    public static final Codec<LandRegister> CODEC = Tile.CODEC.listOf().xmap(LandRegister::new, land -> List.copyOf(land.tiles.values()));
    public static final SavedDataType<LandRegister> TYPE = new SavedDataType<>(ArkSurvivalReturns.id("land_register"), () -> new LandRegister(), CODEC);

    private final Long2ObjectMap<Tile> tiles = new Long2ObjectOpenHashMap<>();

    public LandRegister() {}

    private LandRegister(List<Tile> tiles) {
        for (Tile tile : tiles) if (tile.region.length == BiomeRegions.CELLS && tile.marks.length == BiomeRegions.CELLS) this.tiles.put(key(tile.x, tile.z), tile);
    }

    public static LandRegister get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static long key(int tileX, int tileZ) { return ((long) tileX << 32) | (tileZ & 0xFFFFFFFFL); }

    public int tiles() { return tiles.size(); }

    /** Every tile the land was divided into so far. */
    public Collection<Tile> divided() { return tiles.values(); }

    /** The tile holding this chunk, divided into its regions the first time it is asked for. */
    public Tile tile(ServerLevel level, int chunkX, int chunkZ) {
        int tileX = Math.floorDiv(chunkX, BiomeRegions.SIDE), tileZ = Math.floorDiv(chunkZ, BiomeRegions.SIDE);
        Tile tile = tiles.get(key(tileX, tileZ));
        if (tile == null) {
            var source = level.getChunkSource().getGenerator().getBiomeSource();
            var sampler = level.getChunkSource().randomState().sampler();
            BiomeRegions.Tile divided = BiomeRegions.divide((x, z) -> source.getNoiseBiome(
                    QuartPos.fromBlock(((tileX * BiomeRegions.SIDE + x) << 4) + 8), QuartPos.fromBlock(SAMPLE_Y),
                    QuartPos.fromBlock(((tileZ * BiomeRegions.SIDE + z) << 4) + 8), sampler)
                    .unwrapKey().map(biome -> biome.identifier().toString()).orElse("unregistered"));
            List<Region> regions = new ArrayList<>();
            for (int i = 0; i < divided.biomes().size(); i++) regions.add(new Region(divided.biomes().get(i), divided.cells()[i]));
            tile = new Tile(tileX, tileZ, divided.region(), new byte[BiomeRegions.CELLS], regions);
            tiles.put(key(tileX, tileZ), tile);
            setDirty();
        }
        return tile;
    }

    /** The tile holding this chunk where the land was already divided, or null: for a reader that must not divide new land. */
    public @Nullable Tile known(int chunkX, int chunkZ) {
        return tiles.get(key(Math.floorDiv(chunkX, BiomeRegions.SIDE), Math.floorDiv(chunkZ, BiomeRegions.SIDE)));
    }

    private static int cell(int chunkX, int chunkZ) {
        return Math.floorMod(chunkZ, BiomeRegions.SIDE) * BiomeRegions.SIDE + Math.floorMod(chunkX, BiomeRegions.SIDE);
    }

    /** The biome region this block column lies in. */
    public Region regionAt(ServerLevel level, int blockX, int blockZ) {
        Tile tile = tile(level, blockX >> 4, blockZ >> 4);
        return tile.regions.get(tile.region[cell(blockX >> 4, blockZ >> 4)]);
    }

    public Region region(Tile tile, int chunkX, int chunkZ) { return tile.regions.get(tile.region[cell(chunkX, chunkZ)]); }

    /** Looks at a loaded chunk the first time it is met: its height, whether it holds open ground and whether water. */
    public void survey(ServerLevel level, Tile tile, LevelChunk chunk) {
        int cell = cell(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4);
        if ((tile.marks[cell] & SURVEYED) != 0) return;
        Region region = tile.regions.get(tile.region[cell]);
        boolean ground = false, water = false;
        long height = 0;
        for (int corner = 0; corner < 4; corner++) {
            int x = chunk.getPos().getMinBlockX() + 4 + (corner & 1) * 8, z = chunk.getPos().getMinBlockZ() + 4 + (corner >> 1) * 8;
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            height += top;
            // The height is that of the first free block: what stands on top is one below.
            if (chunk.getFluidState(new net.minecraft.core.BlockPos(x, top - 1, z)).is(FluidTags.WATER)) water = true;
            else if (SpawnRules.surface(level, x, z) != null) ground = true;
        }
        tile.marks[cell] |= (byte) (SURVEYED | (ground ? GROUND : 0) | (water ? WATER : 0));
        region.surveyed++;
        region.height += height / 4;
        if (ground) region.ground++;
        if (water) region.water++;
        setDirty();
    }

    public boolean settled(Tile tile, int chunkX, int chunkZ) { return (tile.marks[cell(chunkX, chunkZ)] & SETTLED) != 0; }

    public void settle(Tile tile, int chunkX, int chunkZ) {
        tile.marks[cell(chunkX, chunkZ)] |= SETTLED;
        setDirty();
    }

    /** Whether the chunk showed what this class stands on: water for the animals of the sea, open ground for the others. */
    public boolean suits(Tile tile, int chunkX, int chunkZ, WildClass kind) {
        return (tile.marks[cell(chunkX, chunkZ)] & (kind == WildClass.SEA ? WATER : GROUND)) != 0;
    }

    public BiomeProfile profile(ServerLevel level, Region region) {
        if (region.profile == null) {
            var biome = level.registryAccess().lookupOrThrow(Registries.BIOME).get(Identifier.parse(region.biome));
            region.holder = biome.orElse(null);
            region.profile = biome.map(SurfaceBiomes::profile).orElseGet(() -> BiomeProfile.classify(region.biome, Set.of(), 0.8f, 0.4f));
            region.home = new boolean[WildClass.values().length];
            for (Species species : Species.values())
                if (species.weight > 0 && SpeciesRange.lives(species, region.profile)) region.home[WildClass.of(species).ordinal()] = true;
        }
        return region.profile;
    }

    /** Whether the region is of the sea: an ocean, or a river wide enough to be a region of its own. */
    public boolean sea(ServerLevel level, Region region) {
        var type = profile(level, region).type();
        return type == BiomeProfile.Type.OCEAN || type == BiomeProfile.Type.RIVER;
    }

    /** Whether the species lives in the region's biome: by its range, or by the data pack's own spawns tag. */
    public boolean lives(ServerLevel level, Region region, Species species) {
        BiomeProfile profile = profile(level, region);
        return region.holder != null && region.holder.is(species.biomes) || SpeciesRange.lives(species, profile);
    }

    /**
     * Groups of a class one chunk of the region holds: the groups a chunk holds (the budget's ten groups within 128
     * blocks) times the class's share, none where no animal of the class lives in its biome. Under the bounded
     * rules poorer land holds fewer: the same share of what it grows.
     */
    public double density(ServerLevel level, Region region, WildClass kind) {
        BiomeProfile profile = profile(level, region);
        if (sea(level, region) != (kind == WildClass.SEA) || !region.home[kind.ordinal()]) return 0;
        double radius = Config.POPULATION_RADIUS.get();
        double groups = Config.POPULATION_GROUPS.get() / (Math.PI * radius * radius / 256.0) * kind.share;
        return SilentLife.bounded() ? groups * BoundedLife.richness(profile, region.hasWater()) : groups;
    }

    /**
     * Groups of a class the region has room for, before rounding: what a chunk holds times its chunks. Under the
     * bounded rules only the chunks count that were seen loaded and showed what the class stands on: its animals
     * can be nowhere else, and a region known by a corner is not filled there with the animals of all of it.
     */
    public double room(ServerLevel level, Region region, WildClass kind) {
        return density(level, region, kind) * (SilentLife.bounded() ? region.known(kind == WildClass.SEA) : region.cells);
    }

    /** The whole groups of a class the region has room for: what does not come out whole is rounded up or down by the region itself, always the same way. */
    public int quota(ServerLevel level, Region region, WildClass kind) {
        double groups = room(level, region, kind);
        if (groups <= 0) return 0;
        int whole = (int) Math.floor(groups);
        long mix = (region.biome.hashCode() * 31L + region.cells) * 0x9E3779B97F4A7C15L + kind.ordinal() * 0xC2B2AE3D27D4EB4FL;
        return whole + (((mix >>> 11) & 0xFFFFF) / (double) 0x100000 < groups - whole ? 1 : 0);
    }

    /**
     * Grows the region's arrivals by the days since it was last looked at, at its whole quota in
     * populationRefillDays, and never beyond what it is short of: a full region has nothing coming.
     *
     * @param count the groups of each class living in it
     */
    public void grow(ServerLevel level, Region region, double today, int[] count) {
        if (region.day <= NEVER || today < region.day) {
            region.day = today;
            return;
        }
        double days = today - region.day;
        if (days < 0.001) return;
        for (WildClass kind : WildClass.values()) {
            int quota = quota(level, region, kind);
            region.arrivals[kind.ordinal()] = Math.min(Math.max(0, quota - count[kind.ordinal()]),
                    region.arrivals[kind.ordinal()] + quota * days / Config.POPULATION_REFILL_DAYS.get());
        }
        region.day = today;
        setDirty();
    }

    public void arrived(Region region, WildClass kind) {
        region.arrivals[kind.ordinal()] = Math.max(0.0, region.arrivals[kind.ordinal()] - 1.0);
        setDirty();
    }

    /** No room was found for a group that was due: what cannot be placed is not saved up beyond one group. */
    public void full(Region region, WildClass kind) {
        region.arrivals[kind.ordinal()] = Math.min(region.arrivals[kind.ordinal()], 1.0);
    }

    /**
     * A chunk of the tile's region that was seen loaded and showed what the class stands on, picked at random: one
     * that is not loaded now where a few tries find one, so the animals put there are records until somebody comes.
     * Null where the region has shown no such chunk. No chunk is loaded for it.
     */
    public @Nullable ChunkPos suited(ServerLevel level, Tile tile, int index, WildClass kind, RandomSource random) {
        int want = SURVEYED | (kind == WildClass.SEA ? WATER : GROUND), found = 0;
        int[] cells = new int[BiomeRegions.CELLS];
        for (int cell = 0; cell < BiomeRegions.CELLS; cell++)
            if (tile.region[cell] == index && (tile.marks[cell] & want) == want) cells[found++] = cell;
        if (found == 0) return null;
        ChunkPos pick = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            int cell = cells[random.nextInt(found)];
            pick = new ChunkPos(tile.x * BiomeRegions.SIDE + cell % BiomeRegions.SIDE, tile.z * BiomeRegions.SIDE + cell / BiomeRegions.SIDE);
            if (level.getChunkSource().getChunkNow(pick.getMinBlockX() >> 4, pick.getMinBlockZ() >> 4) == null) break;
        }
        return pick;
    }

    /** The number of its region for each chunk of the tile, row by row from the north-west: a copy, for a reader outside the game. */
    public byte[] layout(Tile tile) { return tile.region.clone(); }

    /** How many of the region's chunks that were seen loaded lie in each danger zone; at 0 those outside every zone. */
    public int[] dangers(ServerLevel level, Tile tile, int index) {
        int[] count = new int[DangerTier.values().length + 1];
        for (int cell = 0; cell < BiomeRegions.CELLS; cell++) {
            if (tile.region[cell] != index || (tile.marks[cell] & SURVEYED) == 0) continue;
            int danger = ProgressionData.dangerAt(level, new net.minecraft.core.BlockPos(((tile.x * BiomeRegions.SIDE + cell % BiomeRegions.SIDE) << 4) + 8,
                    SAMPLE_Y, ((tile.z * BiomeRegions.SIDE + cell / BiomeRegions.SIDE) << 4) + 8));
            count[danger >= 1 && danger < count.length ? danger : 0]++;
        }
        return count;
    }

    /** A player has spent this much of a day in the region. */
    public void stay(Region region, double days) {
        region.stayed = Math.max(0.0, region.stayed + days);
        setDirty();
    }

    /**
     * The rounds of silent life the region is due, one every so many days and never more than a few at once: what a
     * region fell further behind is not made up. Its first look only starts the clock.
     */
    public int due(Region region, double today, double every, int most) {
        if (region.lived <= NEVER || today < region.lived) {
            region.lived = today;
            setDirty();
            return 0;
        }
        int due = (int) Math.min(most, Math.floor((today - region.lived) / every));
        if (due > 0) {
            region.lived = due == most ? today : region.lived + due * every;
            setDirty();
        }
        return due;
    }

    /** Forgets what the region was allowed; for the tests. */
    public void empty(Region region) { java.util.Arrays.fill(region.arrivals, 0.0); }

    /** Puts the region's clocks back, as if this many days had passed without anyone looking; for the tests. */
    public void age(Region region, double days) {
        if (region.day > NEVER) region.day -= days;
        if (region.lived > NEVER) region.lived -= days;
    }

    /** The groups (packs) of each class living in each region, from the register: loaded or not. */
    public Map<Region, int[]> count(ServerLevel level, WildlifeRegister register) {
        Map<Region, List<Set<UUID>>> packs = new IdentityHashMap<>();
        for (WildlifeRegister.Life life : register.living()) {
            Species species = WildClass.species(life.species());
            if (species == null) continue;
            Region region = regionAt(level, life.x(), life.z());
            List<Set<UUID>> sets = packs.computeIfAbsent(region, ignored -> {
                List<Set<UUID>> made = new ArrayList<>();
                for (int i = 0; i < WildClass.values().length; i++) made.add(new HashSet<>());
                return made;
            });
            sets.get(WildClass.of(species).ordinal()).add(life.pack());
        }
        Map<Region, int[]> counts = new IdentityHashMap<>();
        packs.forEach((region, sets) -> {
            int[] count = new int[sets.size()];
            for (int i = 0; i < count.length; i++) count[i] = sets.get(i).size();
            counts.put(region, count);
        });
        return counts;
    }
}
