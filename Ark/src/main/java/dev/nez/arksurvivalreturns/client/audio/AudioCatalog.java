package dev.nez.arksurvivalreturns.client.audio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.List;
import java.util.Map;

/** Resource-pack-controlled catalog shared by footsteps and environmental ambience. */
public final class AudioCatalog extends SimplePreparableReloadListener<AudioCatalog.Data> {
    public static final AudioCatalog INSTANCE = new AudioCatalog();
    private static final Gson GSON = new Gson();
    private static final Identifier LOCATION = ArkSurvivalReturns.id("audio/catalog.json");
    private volatile Data data = Data.EMPTY;

    public record Footstep(String walk, String run, float volume, float pitchVariance) {}
    public record Ambience(List<String> sounds, List<String> biomeContains, String dimension,
                           Boolean raining, Boolean night, Boolean underwater, Integer maximumSkyLight,
                           int minimumDelay, int maximumDelay, float volume) {}
    public record Data(Map<String, Footstep> footsteps, Map<String, String> soundTypes,
                       List<Ambience> ambience, String fallbackMaterial) {
        private static final Data EMPTY = new Data(Map.of(), Map.of(), List.of(), "stone");
    }

    public Data get() { return data; }

    @Override protected Data prepare(ResourceManager resources, ProfilerFiller profiler) {
        return resources.getResource(LOCATION).map(resource -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                Data loaded = GSON.fromJson(root, Data.class);
                return loaded == null ? Data.EMPTY : loaded;
            } catch (Exception exception) {
                ArkSurvivalReturns.LOGGER.error("Could not load {}", LOCATION, exception);
                return Data.EMPTY;
            }
        }).orElse(Data.EMPTY);
    }

    @Override protected void apply(Data prepared, ResourceManager resources, ProfilerFiller profiler) {
        data = prepared;
        ArkSurvivalReturns.LOGGER.info("Loaded {} footstep materials and {} ambience profiles",
                prepared.footsteps().size(), prepared.ambience().size());
    }
}
