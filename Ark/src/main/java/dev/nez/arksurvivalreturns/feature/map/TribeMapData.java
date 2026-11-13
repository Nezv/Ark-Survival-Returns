package dev.nez.arksurvivalreturns.feature.map;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.serialization.Codec;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The explored Overworld of every tribe (A01 OPT-14): for each chunk a member has seen, one map colour per column,
 * as vanilla maps pack it. Shared by the tribe, saved with the world, sent to members in small pieces.
 */
public final class TribeMapData extends SavedData {
    public static final int COLUMNS = 256;
    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(buffer -> {
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }, ByteBuffer::wrap);
    private static final Codec<Long> CHUNK = Codec.STRING.xmap(Long::parseLong, String::valueOf);
    private static final Codec<Map<UUID, Map<Long, byte[]>>> TRIBES =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(CHUNK, BYTES));
    public static final Codec<TribeMapData> CODEC = TRIBES.xmap(TribeMapData::new, data -> data.tribes);
    public static final SavedDataType<TribeMapData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("tribe_map"), TribeMapData::new, CODEC);

    private final Map<UUID, Map<Long, byte[]>> tribes = new HashMap<>();

    public TribeMapData() {}

    private TribeMapData(Map<UUID, Map<Long, byte[]>> saved) {
        saved.forEach((tribe, chunks) -> tribes.put(tribe, new LinkedHashMap<>(chunks)));
    }

    public static TribeMapData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean has(UUID tribe, long chunk) {
        var chunks = tribes.get(tribe);
        return chunks != null && chunks.containsKey(chunk);
    }

    public void put(UUID tribe, long chunk, byte[] colours) {
        tribes.computeIfAbsent(tribe, ignored -> new LinkedHashMap<>()).put(chunk, colours);
        setDirty();
    }

    public Map<Long, byte[]> chunks(UUID tribe) {
        return tribes.getOrDefault(tribe, Map.of());
    }

    /** A player bringing their own explored land into a party (A02 BUG-14): the party's map gains it. */
    public boolean merge(UUID from, UUID into) {
        var source = tribes.get(from);
        if (from.equals(into) || source == null || source.isEmpty()) return false;
        var target = tribes.computeIfAbsent(into, ignored -> new LinkedHashMap<>());
        int before = target.size();
        source.forEach(target::putIfAbsent);
        if (target.size() == before) return false;
        setDirty();
        return true;
    }
}
