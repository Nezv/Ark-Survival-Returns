package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server adapter: on-demand surveys only, no ticking, chunk tickets or saved population changes. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class SurfaceBiomes {
    /** One profile per biome until its tags change: spawning and shelter checks ask for it constantly. */
    private static final Map<Holder<Biome>, BiomeProfile> PROFILES = new ConcurrentHashMap<>();

    public static BiomeProfile profile(Holder<Biome> biome) { return PROFILES.computeIfAbsent(biome, SurfaceBiomes::classify); }

    @SubscribeEvent public static void reloaded(TagsUpdatedEvent event) { PROFILES.clear(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { PROFILES.clear(); }

    private static BiomeProfile classify(Holder<Biome> biome) {
        String id = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
        return BiomeProfile.classify(id, biome.tags().map(tag -> tag.location().toString()).collect(Collectors.toSet()),
                biome.value().getBaseTemperature(), biome.value().getModifiedClimateSettings().downfall());
    }

    public static Optional<BiomePatchSurvey.Patch> survey(ServerLevel level, int x, int z, int radius) {
        // Profiles live for this survey only: biome tags and climate can change after a datapack reload.
        var profiles = new HashMap<String, BiomeProfile>();
        return BiomePatchSurvey.measure(x, z, radius, (sampleX, sampleZ) -> {
            var chunk = level.getChunkSource().getChunkNow(sampleX >> 4, sampleZ >> 4);
            if (chunk == null) return null;
            int y = Math.clamp(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    sampleX & 15, sampleZ & 15) + 1, level.getMinY(), level.getMaxY() - 1);
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(sampleX, y, sampleZ))) return null;
            // Use the top surface (water surface for oceans), even if the observer is flying or mining.
            // Chunk-local quart biome lookup cannot request neighboring chunks through BiomeManager.
            var biome = chunk.getNoiseBiome(sampleX >> 2, y >> 2, sampleZ >> 2);
            String id = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
            BiomeProfile profile = profiles.computeIfAbsent(id, ignored -> profile(biome));
            return new BiomePatchSurvey.Sample(profile, y);
        });
    }

    private SurfaceBiomes() {}
}
