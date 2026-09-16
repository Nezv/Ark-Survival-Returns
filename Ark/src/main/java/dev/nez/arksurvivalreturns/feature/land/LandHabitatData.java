package dev.nez.arksurvivalreturns.feature.land;

import java.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.phys.Vec3;

/** Persistent group occupancy includes unloaded members; runtime navigation is never serialized. */
public final class LandHabitatData extends SavedData {
    public static final Codec<Species> SPECIES_CODEC = Codec.STRING.comapFlatMap(name -> {
        for (var species : Species.values()) if (!species.flyer() && species.id.equals(name)) return DataResult.success(species);
        return DataResult.error(() -> "Unknown land species: " + name);
    }, s -> s.id);
    /**
     * Per-group runtime state. Everything except the occupancy fields is transient: navigation,
     * destinations and the pool radius are rebuilt from saved data after a load.
     */
    public static final class Habitat {
        public final UUID id;
        public final Species species;
        public BlockPos center, water;
        public final int capacity;
        public final Set<UUID> members, discovered;
        public final GroupNeeds needs;
        public long replacementAt;
        public boolean valid;
        public final Map<UUID, CreatureEntity> loaded = new HashMap<>();
        public BehaviorState routine = BehaviorState.ROAM;
        public Vec3 destination;
        public double heading;
        public long nextPlan, nextCheck;
        /** Runtime horizontal roam radius of a water pool; rebuilt on load, not serialized. */
        public int radius = 24;
        public Habitat(UUID id, Species species, BlockPos center, BlockPos water, int capacity,
                List<UUID> members, List<UUID> discovered, double hunger, long replacementAt, boolean valid) {
            this.id = id; this.species = species; this.center = center.immutable(); this.water = water.immutable();
            this.capacity = capacity; this.members = new HashSet<>(members); this.discovered = new HashSet<>(discovered);
            needs = new GroupNeeds(hunger); this.replacementAt = replacementAt; this.valid = valid;
        }
        public static final Codec<Habitat> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(h -> h.id),
                SPECIES_CODEC.fieldOf("species").forGetter(h -> h.species),
                BlockPos.CODEC.fieldOf("center").forGetter(h -> h.center),
                BlockPos.CODEC.fieldOf("water").forGetter(h -> h.water),
                Codec.intRange(1, 128).fieldOf("capacity").forGetter(h -> h.capacity),
                UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(h -> h.members.stream().sorted().toList()),
                UUIDUtil.CODEC.listOf().optionalFieldOf("discovered", List.of()).forGetter(h -> h.discovered.stream().sorted().toList()),
                Codec.DOUBLE.fieldOf("hunger").forGetter(h -> h.needs.hunger()),
                Codec.LONG.optionalFieldOf("replacementAt", 0L).forGetter(h -> h.replacementAt),
                Codec.BOOL.optionalFieldOf("valid", true).forGetter(h -> h.valid)
        ).apply(i, Habitat::new));
    }
    public static final Codec<LandHabitatData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(d -> 1),
            Habitat.CODEC.listOf().fieldOf("habitats").forGetter(d -> d.habitats.values().stream().sorted(Comparator.comparing(h -> h.id)).toList())
    ).apply(i, (version, habitats) -> new LandHabitatData(habitats)));
    public static final SavedDataType<LandHabitatData> TYPE = new SavedDataType<>(ArkSurvivalReturns.id("land_habitats"), () -> new LandHabitatData(List.of()), CODEC);
    private final Map<UUID, Habitat> habitats = new HashMap<>();
    private final Map<Long, Set<UUID>> cells = new HashMap<>();
    private final Map<UUID, Set<UUID>> discoveries = new HashMap<>();
    public LandHabitatData(List<Habitat> saved) { saved.forEach(this::index); }
    public static LandHabitatData get(ServerLevel world) { return world.getDataStorage().computeIfAbsent(TYPE); }
    private static long cell(int x, int z) { return ((long)(x >> 6) << 32) ^ ((z >> 6) & 0xffffffffL); }
    private void index(Habitat h) {
        habitats.put(h.id, h);
        cells.computeIfAbsent(cell(h.center.getX(), h.center.getZ()), k -> new HashSet<>()).add(h.id);
        h.discovered.forEach(p -> discoveries.computeIfAbsent(p, k -> new HashSet<>()).add(h.id));
    }
    public Habitat byId(UUID id) { return habitats.get(id); }
    public void add(Habitat habitat) { remove(habitat.id); index(habitat); setDirty(); }
    public void relocate(Habitat h, BlockPos center, BlockPos water) {
        remove(h.id); h.center = center.immutable(); h.water = water.immutable(); h.valid = true;
        h.destination = null; h.nextPlan = 0; index(h); setDirty();
    }
    public void remove(UUID id) {
        var h = habitats.remove(id); if (h == null) return;
        var bucket = cells.get(cell(h.center.getX(), h.center.getZ()));
        if (bucket != null) { bucket.remove(id); if (bucket.isEmpty()) cells.remove(cell(h.center.getX(), h.center.getZ())); }
        h.discovered.forEach(p -> { var set = discoveries.get(p); if (set != null) { set.remove(id); if (set.isEmpty()) discoveries.remove(p); } });
        setDirty();
    }
    public List<Habitat> near(BlockPos pos, int radius) {
        var found = new ArrayList<Habitat>();
        for (int x = (pos.getX()-radius)>>6; x <= (pos.getX()+radius)>>6; x++)
            for (int z = (pos.getZ()-radius)>>6; z <= (pos.getZ()+radius)>>6; z++) {
                var ids = cells.get(((long)x<<32) ^ (z&0xffffffffL));
                if (ids != null) for (var id : ids) { var h = habitats.get(id); if (distanceSqr(pos, h.center) <= (double)radius*radius) found.add(h); }
            }
        found.sort(Comparator.comparingDouble(h -> distanceSqr(pos, h.center))); return found;
    }
    public void discover(Habitat h, UUID player) {
        if (h.discovered.add(player)) { discoveries.computeIfAbsent(player, k -> new HashSet<>()).add(h.id); setDirty(); }
    }
    public List<Habitat> discovered(UUID player, BlockPos viewer, int limit) {
        return discoveries.getOrDefault(player, Set.of()).stream().map(habitats::get).filter(Objects::nonNull)
                .sorted(Comparator.<Habitat>comparingDouble(h -> distanceSqr(viewer, h.center)).thenComparing(h -> h.id)).limit(limit).toList();
    }
    public static double distanceSqr(BlockPos a, BlockPos b) { double x=(double)a.getX()-b.getX(), z=(double)a.getZ()-b.getZ(); return x*x+z*z; }
}
