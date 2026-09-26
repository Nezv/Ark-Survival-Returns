package dev.nez.arksurvivalreturns.feature.tech;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads every {@code data/arksurvivalreturns/tech_tree/*.json} document on each server reload and
 * publishes the merged tree atomically. A broken pack keeps the previous tree instead of leaving the
 * server without one.
 */
public final class TechTreeLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("arksurvivalreturns/tech");

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> documents = new LinkedHashMap<>();
        Map<Identifier, Resource> resources = manager.listResources("tech_tree",
                id -> id.getNamespace().equals(ArkSurvivalReturns.MOD_ID) && id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                documents.put(entry.getKey(), JsonParser.parseReader(reader));
            } catch (Exception exception) {
                LOGGER.error("Failed to read technology tree document {}", entry.getKey(), exception);
            }
        }
        return documents;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> documents, ResourceManager manager, ProfilerFiller profiler) {
        if (documents.isEmpty()) {
            TechTree.set(null);
            return;
        }
        try {
            List<TechTree> parsed = new ArrayList<>();
            for (Map.Entry<Identifier, JsonElement> entry : documents.entrySet()) {
                parsed.add(TechTree.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                        .getOrThrow(error -> new IllegalStateException(entry.getKey() + ": " + error)));
            }
            TechTree tree = TechTree.merge(parsed);
            TechTree.set(tree);
            LOGGER.info("Loaded {} technology nodes from {} document(s)", tree.nodes().size(), documents.size());
        } catch (Exception exception) {
            LOGGER.error("Refusing the technology tree reload; keeping the previous tree", exception);
        }
    }
}
