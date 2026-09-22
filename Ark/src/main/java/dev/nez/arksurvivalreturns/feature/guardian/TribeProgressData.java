package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Tribe-scoped progression flags; the shared workshop schematic is the first one. */
public final class TribeProgressData extends SavedData {
    public static final String WORKSHOP_SCHEMATIC = "workshop_schematic";
    public static final int SCHEMA_VERSION = 1;
    private static final Codec<Set<String>> FLAG_CODEC = Codec.STRING.listOf().xmap(LinkedHashSet::new, List::copyOf);
    public static final Codec<TribeProgressData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(data -> data.schemaVersion),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, FLAG_CODEC)
                    .optionalFieldOf("flags", Map.<UUID, Set<String>>of()).forGetter(data -> data.flags)
    ).apply(instance, TribeProgressData::new));
    public static final SavedDataType<TribeProgressData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("tribe_progress"), TribeProgressData::new, CODEC);

    private final int schemaVersion;
    private final Map<UUID, Set<String>> flags;

    public TribeProgressData() {
        this(SCHEMA_VERSION, Map.of());
    }

    public TribeProgressData(int schemaVersion, Map<UUID, Set<String>> flags) {
        this.schemaVersion = schemaVersion;
        this.flags = new LinkedHashMap<>();
        flags.forEach((tribe, values) -> this.flags.put(tribe, new LinkedHashSet<>(values)));
    }

    public static TribeProgressData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean has(UUID tribe, String flag) {
        return flags.getOrDefault(tribe, Set.of()).contains(flag);
    }

    /** Grants a flag; returns true only when this call changed the stored state. */
    public boolean grant(UUID tribe, String flag) {
        if (flags.computeIfAbsent(tribe, key -> new LinkedHashSet<>()).add(flag)) {
            setDirty();
            return true;
        }
        return false;
    }

    public Set<String> flags(UUID tribe) {
        return Collections.unmodifiableSet(flags.getOrDefault(tribe, Set.of()));
    }
}
