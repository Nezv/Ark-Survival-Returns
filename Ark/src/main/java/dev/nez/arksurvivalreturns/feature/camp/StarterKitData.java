package dev.nez.arksurvivalreturns.feature.camp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** One-time starter kits, so a fresh survivor can always establish camp. */
public final class StarterKitData extends SavedData {
    public static final int SCHEMA_VERSION = 1;
    public static final Codec<StarterKitData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(data -> data.schemaVersion),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("granted", List.of())
                    .forGetter(data -> List.copyOf(data.granted))
    ).apply(instance, StarterKitData::new));
    public static final SavedDataType<StarterKitData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("starter_kits"), StarterKitData::new, CODEC);

    private final int schemaVersion;
    private final Set<UUID> granted;

    public StarterKitData() {
        this(SCHEMA_VERSION, List.of());
    }

    public StarterKitData(int schemaVersion, List<UUID> granted) {
        this.schemaVersion = schemaVersion;
        this.granted = new HashSet<>(granted);
    }

    public static StarterKitData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Marks the player once; false means the kit was already granted. */
    public boolean markGranted(UUID player) {
        if (!granted.add(player)) return false;
        setDirty();
        return true;
    }

    public boolean granted(UUID player) {
        return granted.contains(player);
    }
}
