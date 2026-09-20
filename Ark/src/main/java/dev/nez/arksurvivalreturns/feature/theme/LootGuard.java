package dev.nez.arksurvivalreturns.feature.theme;

import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Cleans newly generated loot: enchantment functions and removed items are dropped, and maps
 * that point at a disabled structure are replaced by nothing.
 *
 * <p>The loaded table is sanitized as JSON and parsed back, so every table keeps its mundane
 * entries, its roll counts and its conditions. A table that cannot be re-parsed is left exactly
 * as it was.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class LootGuard {
    private static final JsonObject EMPTY = new JsonObject();
    private static final Set<String> DISABLED_MAP_TAGS = Set.of(
            "minecraft:on_ocean_explorer_maps", "minecraft:on_woodland_explorer_maps",
            "minecraft:on_trial_chambers_maps");

    static { EMPTY.addProperty("type", "minecraft:empty"); }

    @SubscribeEvent
    public static void loot(LootTableLoadEvent event) {
        var table = event.getTable();
        var sanitized = filter(event, table, RegistryOps.create(JsonOps.INSTANCE, event.getRegistries()));
        if (sanitized == null && ServerLifecycleHooks.getCurrentServer() != null) {
            // Some tables hold references owned by a built-in registry rather than by the
            // reload context; the live server access owns those, so it is tried second.
            sanitized = filter(event, table, RegistryOps.create(JsonOps.INSTANCE,
                    ServerLifecycleHooks.getCurrentServer().registryAccess()));
        }
        if (sanitized != null) event.setTable(sanitized);
    }

    /** Returns the cleaned table, or null when this table cannot be round tripped. */
    private static LootTable filter(LootTableLoadEvent event, LootTable table, RegistryOps<JsonElement> ops) {
        try {
            var encoded = LootTable.DIRECT_CODEC.encodeStart(ops, table).getOrThrow();
            if (!(encoded instanceof JsonObject json) || !sanitize(json)) return table;
            return LootTable.DIRECT_CODEC.parse(ops, json).getOrThrow();
        } catch (RuntimeException e) {
            ArkSurvivalReturns.LOGGER.info("Theme alignment left loot table {} untouched", event.getName());
            ArkSurvivalReturns.LOGGER.debug("Untouched loot table {}: {}", event.getName(), e.toString());
            return null;
        }
    }

    /** Rewrites the tree in place; returns true when a function or entry was replaced. */
    private static boolean sanitize(JsonElement element) {
        boolean changed = false;
        if (element instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                var child = array.get(i);
                if (child instanceof JsonObject object && (droppableEntry(object) || droppableFunction(object))) {
                    // Functions are optional, so they are removed; entries are replaced by the
                    // vanilla empty entry, which keeps every pool structurally valid.
                    if (object.has("function")) array.remove(i--);
                    else array.set(i, EMPTY.deepCopy());
                    changed = true;
                    continue;
                }
                changed |= sanitize(child);
            }
        } else if (element instanceof JsonObject object) {
            for (var entry : object.entrySet()) changed |= sanitize(entry.getValue());
        }
        return changed;
    }

    private static boolean droppableEntry(JsonObject object) {
        var type = object.get("type");
        if (type == null || !type.isJsonPrimitive() || !"minecraft:item".equals(type.getAsString())) return false;
        var name = object.get("name");
        if (name != null && name.isJsonPrimitive() && ThemePolicy.REMOVED_ITEM_IDS.contains(name.getAsString()))
            return true;
        // A map pointing at a disabled structure is dropped whole, not blanked into a plain map.
        return hasDisabledExplorationMap(object);
    }

    private static boolean hasDisabledExplorationMap(JsonObject entry) {
        var functions = entry.get("functions");
        if (functions == null || !functions.isJsonArray()) return false;
        for (var element : functions.getAsJsonArray()) {
            if (!(element instanceof JsonObject function)) continue;
            if (!"minecraft:exploration_map".equals(asString(function.get("function")))) continue;
            String destination = asString(function.get("destination"));
            if (destination != null && DISABLED_MAP_TAGS.contains(destination)) return true;
        }
        return false;
    }

    private static String asString(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static boolean droppableFunction(JsonObject object) {
        var function = object.get("function");
        if (function == null || !function.isJsonPrimitive()) return false;
        String name = function.getAsString();
        if (ThemePolicy.ENCHANT_LOOT_FUNCTIONS.contains(name)) return true;
        if ("minecraft:exploration_map".equals(name)) {
            var destination = object.get("destination");
            return destination != null && destination.isJsonPrimitive()
                    && DISABLED_MAP_TAGS.contains(destination.getAsString());
        }
        return false;
    }

    private LootGuard() {}
}
