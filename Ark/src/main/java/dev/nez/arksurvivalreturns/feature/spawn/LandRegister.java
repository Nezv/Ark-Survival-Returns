package dev.nez.arksurvivalreturns.feature.spawn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
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
 * room for. The population budget counts the {@link WildlifeRegister} against these quotas; a region below one
 * is allowed arrivals at the pace of days, not seconds.
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
                Codec.DOUBLE.fieldOf("day").forGetter(region -> region.day)
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
        private transient @Nullable BiomeProfile profile;

        Region(String biome, int cells) {
            this(biome, cells, 0, 0, 0, 0L, List.of(), NEVER);
        }

        private Region(String biome, int cells, int surveyed, int ground, int water, long height, List<Double> arrivals, double day) {
            this.biome = biome;
            this.cells = cells;
            this.surveyed = surveyed;
            this.ground = ground;
            this.water = water;
            this.height = height;
            for (int i = 0; i < Math.min(arrivals.size(), this.arrivals.length); i++) this.arrivals[i] = arrivals.get(i);
            this.day = day;
        }

        public int surveyed() { return surveyed; }
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

    private BiomeProfile profile(ServerLevel level, Region region) {
        if (region.profile == null) {
            var biome = level.registryAccess().lookupOrThrow(Registries.BIOME).get(Identifier.parse(region.biome));
            region.profile = biome.map(SurfaceBiomes::profile).orElseGet(() -> BiomeProfile.classify(region.biome, Set.of(), 0.8f, 0.4f));
        }
        return region.profile;
    }

    /**
     * Groups of a class the region has room for: its chunks times the groups a chunk holds (the budget's ten
     * groups within 128 blocks) times the class's share, none where no animal of the class lives in its biome.
     * What does not come out whole is rounded up or down by the region itself, always the same way.
     */
    public int quota(ServerLevel level, Region region, WildClass kind) {
        BiomeProfile profile = profile(level, region);
        boolean sea = profile.type() == BiomeProfile.Type.OCEAN || profile.type() == BiomeProfile.Type.RIVER;
        if (sea != (kind == WildClass.SEA)) return 0;
        boolean lives = false;
        for (Species species : Species.values())
            if (species.weight > 0 && WildClass.of(species) == kind && SpeciesRange.lives(species, profile)) lives = true;
        if (!lives) return 0;
        double radius = Config.POPULATION_RADIUS.get();
        double groups = region.cells * Config.POPULATION_GROUPS.get() / (Math.PI * radius * radius / 256.0) * kind.share;
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

    /** Forgets what the region was allowed; for the tests. */
    public void empty(Region region) { java.util.Arrays.fill(region.arrivals, 0.0); }

    /** Puts the region's clock back, as if this many days had passed without anyone looking; for the tests. */
    public void age(Region region, double days) { if (region.day > NEVER) region.day -= days; }

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
