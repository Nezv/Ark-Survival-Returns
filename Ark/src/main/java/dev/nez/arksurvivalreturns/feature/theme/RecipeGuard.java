package dev.nez.arksurvivalreturns.feature.theme;

import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

/**
 * Drops every recipe that produces a removed item or consumes one as an ingredient.
 *
 * <p>Recipes are filtered as raw JSON before deserialization, so a removed recipe never exists
 * in the registry and cannot be unlocked, viewed or crafted. Recipes that stay craftable are
 * untouched, which also keeps the removed entries out of the recipe book.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class RecipeGuard {
    private static final Set<String> REMOVED = ThemePolicy.REMOVED_ITEM_IDS;

    @SubscribeEvent
    public static void recipes(ModifyRecipeJsonsEvent event) {
        var jsons = event.getRecipeJsons();
        int before = jsons.size();
        jsons.entrySet().removeIf(entry -> !entry.getKey().getNamespace().equals(ArkSurvivalReturns.MOD_ID)
                && references(entry.getValue(), REMOVED));
        if (before - jsons.size() > 0) {
            ArkSurvivalReturns.LOGGER.info("Theme alignment dropped {} recipes", before - jsons.size());
        }
    }

    private static boolean references(JsonElement element, Set<String> ids) {
        if (element instanceof JsonPrimitive primitive) {
            return primitive.isString() && ids.contains(primitive.getAsString());
        }
        if (element instanceof JsonArray array) {
            for (var child : array) if (references(child, ids)) return true;
            return false;
        }
        if (element instanceof JsonObject object) {
            for (var child : object.entrySet()) if (references(child.getValue(), ids)) return true;
        }
        return false;
    }

    private RecipeGuard() {}
}
