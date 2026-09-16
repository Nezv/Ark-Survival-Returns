package dev.nez.arksurvivalreturns.feature.flying;

import java.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;

/** Dimension-local colony records. Spatial indices avoid scans of every explored habitat. */
public final class HabitatData extends SavedData {
    /**
     * Dimension-local colony records. `species` is the authoritative field; the legacy
     * `argentavis` flag is still read so saves written before the collection landed keep loading.
     */
    public record Habitat(UUID id, Species species, BlockPos center, List<BlockPos> nests, Set<UUID> discovered) {
        public Habitat { nests = List.copyOf(nests); discovered = new HashSet<>(discovered); }
        public static final Codec<Habitat> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Habitat::id),
                Codec.BOOL.optionalFieldOf("argentavis", false).forGetter(h -> h.species == Species.ARGENTAVIS),
                BlockPos.CODEC.fieldOf("center").forGetter(Habitat::center),
                BlockPos.CODEC.listOf(1, 4).fieldOf("nests").forGetter(Habitat::nests),
                UUIDUtil.CODEC.listOf().optionalFieldOf("discovered", List.of()).forGetter(h -> h.discovered.stream().sorted().toList()),
                Codec.STRING.optionalFieldOf("species", "").forGetter(h -> h.species.id)
        ).apply(i, (id, argent, center, nests, players, species) ->
                new Habitat(id, flyer(species, argent), center, nests, new HashSet<>(players))));
        private static Species flyer(String id, boolean argentavis) {
            for (var candidate : Species.values())
                if (candidate.flyer() && candidate.id.equals(id)) return candidate;
            return argentavis ? Species.ARGENTAVIS : Species.PTERANODON;
        }
    }
    public static final Codec<HabitatData> CODEC = Habitat.CODEC.listOf().xmap(HabitatData::new,
            d -> d.habitats.values().stream().sorted(Comparator.comparing(Habitat::id)).toList()).fieldOf("habitats").codec();
    public static final SavedDataType<HabitatData> TYPE = new SavedDataType<>(ArkSurvivalReturns.id("flying_habitats"), () -> new HabitatData(List.of()), CODEC);
    private final Map<UUID, Habitat> habitats = new HashMap<>();
    private final Map<BlockPos, UUID> nests = new HashMap<>();
    private final Map<Long, Set<UUID>> cells = new HashMap<>();
    private final Map<UUID, Set<UUID>> discoveries = new HashMap<>();
    public HabitatData(List<Habitat> saved) { saved.forEach(this::index); }
    public static HabitatData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    private static long cell(int x, int z) { return ((long)(x >> 6) << 32) ^ ((z >> 6) & 0xffffffffL); }
    private void index(Habitat h) {
        habitats.put(h.id, h); h.nests.forEach(p -> nests.put(p, h.id));
        cells.computeIfAbsent(cell(h.center.getX(), h.center.getZ()), k -> new HashSet<>()).add(h.id);
        h.discovered.forEach(p -> discoveries.computeIfAbsent(p, k -> new HashSet<>()).add(h.id));
    }
    public void add(Habitat h) { index(h); setDirty(); }
    public Habitat byId(UUID id) { return habitats.get(id); }
    public Habitat atNest(BlockPos pos) { return habitats.get(nests.get(pos)); }
    public List<Habitat> near(BlockPos pos, int radius) {
        var result = new ArrayList<Habitat>();
        for (int x = (pos.getX() - radius) >> 6; x <= (pos.getX() + radius) >> 6; x++)
            for (int z = (pos.getZ() - radius) >> 6; z <= (pos.getZ() + radius) >> 6; z++) {
                var ids = cells.get(((long)x << 32) ^ (z & 0xffffffffL));
                if (ids != null) for (var id : ids) {
                    var h = habitats.get(id);
                    if (horizontalDistanceSqr(h.center, pos) <= (double)radius * radius) result.add(h);
                }
            }
        result.sort(Comparator.comparingDouble(h -> horizontalDistanceSqr(h.center, pos)));
        return result;
    }
    public static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double x = (double)a.getX() - b.getX(), z = (double)a.getZ() - b.getZ(); return x*x + z*z;
    }
    public void discover(Habitat h, UUID player) {
        if (h.discovered.add(player)) { discoveries.computeIfAbsent(player, k -> new HashSet<>()).add(h.id); setDirty(); }
    }
    public List<Habitat> discovered(UUID player, BlockPos viewer, int limit) {
        return discoveries.getOrDefault(player, Set.of()).stream().map(habitats::get).filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(h -> horizontalDistanceSqr(h.center, viewer))).limit(limit).toList();
    }
    public void removeNest(BlockPos pos) {
        var h = atNest(pos); if (h == null) return;
        remove(h.id);
        var remaining = h.nests.stream().filter(p -> !p.equals(pos)).toList();
        if (!remaining.isEmpty()) add(new Habitat(h.id, h.species, h.center, remaining, h.discovered));
    }
    public void remove(UUID id) {
        var h = habitats.remove(id); if (h == null) return;
        h.nests.forEach(nests::remove);
        var bucket = cells.get(cell(h.center.getX(), h.center.getZ()));
        if (bucket != null) { bucket.remove(id); if (bucket.isEmpty()) cells.remove(cell(h.center.getX(), h.center.getZ())); }
        h.discovered.forEach(p -> { var ids = discoveries.get(p); if (ids != null) { ids.remove(id); if (ids.isEmpty()) discoveries.remove(p); } });
        setDirty();
    }
}
