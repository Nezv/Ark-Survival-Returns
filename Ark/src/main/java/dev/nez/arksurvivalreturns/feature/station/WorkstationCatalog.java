package dev.nez.arksurvivalreturns.feature.station;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** An atomic server snapshot. Client state is held separately so integrated servers never share it. */
public final class WorkstationCatalog extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
    public static final Set<String> PHASE_A = Set.of("armoury", "working_station", "mortar_and_pestle", "medicine_bench", "smithing_table", "saddlery");
    private static volatile Map<String, WorkstationDefinition> stations = Map.of();
    public static Map<String, WorkstationDefinition> all() { return stations; }
    public static WorkstationDefinition get(String station) { return stations.get(station); }
    @Override protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> documents = new LinkedHashMap<>();
        for (String directory : new String[]{"workstation", "workstation_rules"}) {
            for (var resource : manager.listResources(directory, id -> id.getNamespace().equals(ArkSurvivalReturns.MOD_ID) && id.getPath().endsWith(".json")).entrySet()) {
                try (var reader = resource.getValue().openAsReader()) { documents.put(resource.getKey(), JsonParser.parseReader(reader)); }
                catch (Exception e) { throw new IllegalStateException("Cannot read workstation " + resource.getKey(), e); }
            }
        }
        return documents;
    }
    @Override protected void apply(Map<Identifier, JsonElement> documents, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, WorkstationDefinition> next = new LinkedHashMap<>();
        for (var document : documents.entrySet()) {
            if (!document.getKey().getPath().startsWith("workstation/")) continue;
            var definition = WorkstationCodecs.STATION.parse(JsonOps.INSTANCE, document.getValue()).getOrThrow();
            Identifier station = Identifier.parse(definition.station());
            if (!station.getNamespace().equals(ArkSurvivalReturns.MOD_ID) || !PHASE_A.contains(station.getPath())) continue;
            JsonElement rules = documents.get(ArkSurvivalReturns.id("workstation_rules/" + station.getPath() + ".json"));
            WorkstationDefinition supplement = rules == null ? null : WorkstationCodecs.STATION.parse(JsonOps.INSTANCE, rules).getOrThrow();
            var resolved = definition.normalise(supplement, id -> {
                Identifier key = Identifier.tryParse(id);
                return key != null && BuiltInRegistries.ITEM.containsKey(key);
            });
            for (var craft : resolved.crafts()) {
                if (craft.variant().cost().isEmpty()) throw new IllegalArgumentException("Empty cost for " + craft.variant().item());
                for (String selector : craft.variant().cost().keySet()) {
                    if (Identifier.tryParse(selector.startsWith("#") ? selector.substring(1) : selector) == null)
                        throw new IllegalArgumentException("Invalid cost selector: " + selector);
                }
            }
            if (next.put(definition.station(), resolved) != null) throw new IllegalArgumentException("Duplicate workstation " + definition.station());
        }
        stations = java.util.Collections.unmodifiableMap(next);
        ArkSurvivalReturns.LOGGER.info("Loaded {} phase A workstation graphs ({} recipes)", next.size(), next.values().stream().mapToInt(s -> s.crafts().size()).sum());
    }
}
