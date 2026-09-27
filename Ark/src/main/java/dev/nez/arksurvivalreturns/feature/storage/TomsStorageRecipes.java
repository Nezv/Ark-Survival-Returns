package dev.nez.arksurvivalreturns.feature.storage;

import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

/**
 * Tom's Storage (I04) recipes rebuilt from materials an Overworld-only world has. Ender pearls become iron
 * ingots (a simple storage device needs no teleportation), and the redstone parts (comparators, redstone dust
 * and torches) and glowstone become copper. The network then arrives with the metals, not never.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TomsStorageRecipes {
    public static final Map<String, String> SWAPS = Map.of(
            "#c:ender_pearls", "#c:ingots/iron",
            "minecraft:ender_pearl", "#c:ingots/iron",
            "minecraft:comparator", "#c:ingots/copper",
            "#c:dusts/redstone", "#c:ingots/copper",
            "minecraft:redstone", "#c:ingots/copper",
            "#c:dusts/glowstone", "#c:ingots/copper",
            "#c:gems/quartz", "#c:ingots/copper",
            "minecraft:redstone_torch", "minecraft:copper_torch");

    @SubscribeEvent public static void recipes(ModifyRecipeJsonsEvent event) {
        int changed = 0;
        for (var entry : event.getRecipeJsons().entrySet()) {
            if (!entry.getKey().getNamespace().equals("toms_storage") || !(entry.getValue() instanceof JsonObject recipe)) continue;
            boolean touched = false;
            for (var field : recipe.entrySet()) {
                if (field.getKey().equals("result")) continue;
                JsonElement swapped = swap(field.getValue());
                if (swapped != field.getValue()) { field.setValue(swapped); touched = true; }
            }
            if (touched) changed++;
        }
        if (changed > 0) ArkSurvivalReturns.LOGGER.info("Tom's Storage: {} recipes rebuilt without ender pearls or redstone", changed);
    }

    /** The element with every swapped id replaced, or the same instance when nothing changed. */
    private static JsonElement swap(JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            String target = SWAPS.get(primitive.getAsString());
            return target == null ? element : new JsonPrimitive(target);
        }
        boolean changed = false;
        if (element instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                JsonElement swapped = swap(array.get(i));
                if (swapped != array.get(i)) { array.set(i, swapped); changed = true; }
            }
        } else if (element instanceof JsonObject object) {
            for (var field : object.entrySet()) {
                JsonElement swapped = swap(field.getValue());
                if (swapped != field.getValue()) { field.setValue(swapped); changed = true; }
            }
        }
        return changed ? element.deepCopy() : element;
    }

    private TomsStorageRecipes() {}
}
