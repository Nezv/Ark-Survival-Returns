package dev.nez.arksurvivalreturns.feature.recovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-scoped recovery index: every player's outstanding caches, oldest first.
 *
 * <p>Items are stored as data rather than in a block entity, so a cache survives chunk unloads,
 * server restarts and marker destruction. Lists are capped by the service, not by this storage.
 */
public final class RecoveryData extends SavedData {
    public static final int SCHEMA_VERSION = 1;
    public static final Codec<RecoveryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(data -> data.schemaVersion),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, RecoveryEntry.CODEC.listOf())
                    .optionalFieldOf("players", Map.of()).forGetter(data -> data.players)
    ).apply(instance, RecoveryData::new));
    public static final SavedDataType<RecoveryData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("recoveries"), RecoveryData::new, CODEC);

    private final int schemaVersion;
    private final Map<UUID, List<RecoveryEntry>> players;

    public RecoveryData() {
        this(SCHEMA_VERSION, Map.of());
    }

    public RecoveryData(int schemaVersion, Map<UUID, List<RecoveryEntry>> players) {
        this.schemaVersion = schemaVersion;
        this.players = new LinkedHashMap<>();
        players.forEach((owner, entries) -> this.players.put(owner, new ArrayList<>(entries)));
    }

    public static RecoveryData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<RecoveryEntry> entries(UUID owner) {
        return List.copyOf(players.getOrDefault(owner, List.of()));
    }

    public void add(UUID owner, RecoveryEntry entry) {
        players.computeIfAbsent(owner, key -> new ArrayList<>()).add(entry);
        setDirty();
    }

    public void remove(UUID owner, UUID entryId) {
        List<RecoveryEntry> entries = players.get(owner);
        if (entries == null || !entries.removeIf(entry -> entry.id().equals(entryId))) return;
        if (entries.isEmpty()) players.remove(owner);
        setDirty();
    }

    public void replace(UUID owner, RecoveryEntry entry) {
        List<RecoveryEntry> entries = players.get(owner);
        if (entries == null) return;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(entry.id())) {
                entries.set(i, entry);
                setDirty();
                return;
            }
        }
    }

    /** Owner paired with one of their entries, for block-to-entry lookups. */
    public record Owned(UUID owner, RecoveryEntry entry) {}

    /** Finds the placed entry at a block position; empty when the spot holds no cache. */
    public Optional<Owned> at(ResourceKey<Level> dimension, BlockPos pos) {
        for (var player : players.entrySet()) {
            for (RecoveryEntry entry : player.getValue()) {
                if (entry.pos().filter(p -> p.equals(pos)).isPresent() && entry.dimension().equals(dimension)) {
                    return Optional.of(new Owned(player.getKey(), entry));
                }
            }
        }
        return Optional.empty();
    }

    /** Marks any entry anchored at this position as unplaced instead of deleting its items. */
    public void unplace(ResourceKey<Level> dimension, BlockPos pos) {
        for (var player : players.entrySet()) {
            for (int i = 0; i < player.getValue().size(); i++) {
                RecoveryEntry entry = player.getValue().get(i);
                if (entry.pos().filter(p -> p.equals(pos)).isPresent() && entry.dimension().equals(dimension)) {
                    player.getValue().set(i, entry.unplaced());
                    setDirty();
                    return;
                }
            }
        }
    }

    public void clear(UUID owner) {
        if (players.remove(owner) != null) setDirty();
    }
}
