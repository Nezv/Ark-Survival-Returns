package dev.nez.arksurvivalreturns.feature.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Player-placed block positions per dimension, so a work job never harvests something a survivor
 * built. The set is bounded with FIFO eviction: protection is a safety rule, not an audit log, and a
 * very old entry falling out only means the job may harvest a block the player placed long ago.
 */
public final class WorkProtection extends SavedData {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_ENTRIES = 65536;
    public static final Codec<WorkProtection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(data -> data.schemaVersion),
            Codec.LONG.listOf().optionalFieldOf("placed", List.of()).forGetter(data -> List.copyOf(data.placed))
    ).apply(instance, WorkProtection::new));
    public static final SavedDataType<WorkProtection> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("work_protection"), WorkProtection::new, CODEC);

    private final int schemaVersion;
    private final Set<Long> placed;
    private final Deque<Long> order;

    public WorkProtection() {
        this(SCHEMA_VERSION, List.of());
    }

    public WorkProtection(int schemaVersion, List<Long> placed) {
        this.schemaVersion = schemaVersion;
        this.placed = new HashSet<>(placed);
        this.order = new ArrayDeque<>(placed);
    }

    public static WorkProtection get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void record(BlockPos pos) {
        long key = pos.asLong();
        if (!placed.add(key)) return;
        order.addLast(key);
        while (order.size() > MAX_ENTRIES) placed.remove(order.removeFirst());
        setDirty();
    }

    public void forget(BlockPos pos) {
        long key = pos.asLong();
        if (placed.remove(key)) {
            order.remove(key);
            setDirty();
        }
    }

    public boolean protectedAt(BlockPos pos) {
        return placed.contains(pos.asLong());
    }

    public int size() {
        return placed.size();
    }

    /** Copy for tests and diagnostics. */
    public List<Long> snapshot() {
        return new ArrayList<>(order);
    }
}
