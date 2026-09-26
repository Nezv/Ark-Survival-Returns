package dev.nez.arksurvivalreturns.feature.tech;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-scoped technology progress, one entry per FTB Teams tribe (or a solo player UUID). */
public final class TechProgressData extends SavedData {
    public static final int SCHEMA_VERSION = 2;
    public static final Codec<TechProgressData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(data -> data.schemaVersion),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, TechTribeProgress.CODEC)
                    .optionalFieldOf("tribes", Map.of()).forGetter(data -> data.tribes)
    ).apply(instance, TechProgressData::new));
    public static final SavedDataType<TechProgressData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("tech_progress_v2"), TechProgressData::new, CODEC);

    private final int schemaVersion;
    private final Map<UUID, TechTribeProgress> tribes;

    public TechProgressData() {
        this(SCHEMA_VERSION, Map.of());
    }

    public TechProgressData(int schemaVersion, Map<UUID, TechTribeProgress> tribes) {
        this.schemaVersion = schemaVersion;
        this.tribes = new LinkedHashMap<>(tribes);
    }

    public static TechProgressData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Existing progress or a fresh empty one; the caller marks the data dirty when it changes. */
    public TechTribeProgress progress(UUID tribe) {
        TechTribeProgress existing = tribes.get(tribe);
        return existing != null ? existing : new TechTribeProgress();
    }

    public void store(UUID tribe, TechTribeProgress progress) {
        tribes.put(tribe, progress);
        setDirty();
    }

    /** Operator reset: forget one tribe's technology state. */
    public boolean reset(UUID tribe) {
        if (tribes.remove(tribe) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public Map<UUID, TechTribeProgress> tribes() {
        return Map.copyOf(tribes);
    }
}
