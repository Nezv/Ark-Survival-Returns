package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-scoped encounter index, one record per ritual anchor.
 *
 * <p>State lives in data rather than on the boss entity, so a reload while the boss chunk is
 * unloaded cannot lose the attempt, duplicate a boss or issue rewards twice.
 */
public final class GuardianData extends SavedData {
    public static final int SCHEMA_VERSION = 1;
    public static final Codec<GuardianData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(data -> data.schemaVersion),
            Codec.unboundedMap(Codec.STRING, GuardianEncounter.CODEC)
                    .optionalFieldOf("encounters", Map.of()).forGetter(data -> data.encounters)
    ).apply(instance, GuardianData::new));
    public static final SavedDataType<GuardianData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("guardians"), GuardianData::new, CODEC);

    private final int schemaVersion;
    private final Map<String, GuardianEncounter> encounters;

    public GuardianData() {
        this(SCHEMA_VERSION, Map.of());
    }

    public GuardianData(int schemaVersion, Map<String, GuardianEncounter> encounters) {
        this.schemaVersion = schemaVersion;
        this.encounters = new LinkedHashMap<>(encounters);
    }

    public static GuardianData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<GuardianEncounter> find(String key) {
        return Optional.ofNullable(encounters.get(key));
    }

    /** Stores the record only when it actually changed, so idle ticks never dirty the save. */
    public void put(GuardianEncounter encounter) {
        GuardianEncounter previous = encounters.put(encounter.key(), encounter);
        if (!encounter.equals(previous)) setDirty();
    }

    public void remove(String key) {
        if (encounters.remove(key) != null) setDirty();
    }

    public List<GuardianEncounter> all() {
        return List.copyOf(encounters.values());
    }
}
