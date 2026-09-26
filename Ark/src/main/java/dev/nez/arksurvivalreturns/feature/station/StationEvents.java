package dev.nez.arksurvivalreturns.feature.station;

import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The replaced vanilla blocks fall back to the Ark stations. Their own recipes are dropped, recipes that
 * use them as an ingredient (hoppers, chest minecarts, shulker boxes, the crafter...) take the Ark block
 * instead, and vanilla items that still arrive from loot or trades turn into the Ark block in the
 * player's inventory. Blocks already placed by world generation stay as they are.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class StationEvents {
    /** Vanilla id -> Ark id, for recipes. */
    private static final Map<String, String> REPLACED = Map.of(
            "minecraft:crafting_table", ArkSurvivalReturns.MOD_ID + ":working_station",
            "minecraft:chest", ArkSurvivalReturns.MOD_ID + ":storage_crate",
            "minecraft:trapped_chest", ArkSurvivalReturns.MOD_ID + ":storage_crate",
            "minecraft:smithing_table", ArkSurvivalReturns.MOD_ID + ":smithing_table");

    @SubscribeEvent public static void recipes(ModifyRecipeJsonsEvent event) {
        int before = event.getRecipeJsons().size();
        event.getRecipeJsons().entrySet().removeIf(entry -> !entry.getKey().getNamespace().equals(ArkSurvivalReturns.MOD_ID)
                && REPLACED.containsKey(result(entry.getValue())));
        int swapped = 0;
        for (var entry : event.getRecipeJsons().entrySet()) {
            if (!(entry.getValue() instanceof JsonObject recipe)) continue;
            for (var field : recipe.entrySet()) {
                if (field.getKey().equals("result")) continue;
                JsonElement replaced = swap(field.getValue());
                if (replaced != field.getValue()) { field.setValue(replaced); swapped++; }
            }
        }
        ArkSurvivalReturns.LOGGER.info("Workstations: removed {} vanilla recipes, rewired {} ingredients",
                before - event.getRecipeJsons().size(), swapped);
    }

    /** Returns the element with every replaced id swapped, or the same instance when nothing changed. */
    private static JsonElement swap(JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            String target = REPLACED.get(primitive.getAsString());
            return target == null ? element : new JsonPrimitive(target);
        }
        boolean changed = false;
        if (element instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                JsonElement replaced = swap(array.get(i));
                if (replaced != array.get(i)) { array.set(i, replaced); changed = true; }
            }
        } else if (element instanceof JsonObject object) {
            for (var field : object.entrySet()) {
                JsonElement replaced = swap(field.getValue());
                if (replaced != field.getValue()) { field.setValue(replaced); changed = true; }
            }
        }
        return changed ? element.deepCopy() : element;
    }

    private static String result(JsonElement recipe) {
        if (!(recipe instanceof JsonObject object) || !(object.get("result") instanceof JsonObject result)) return "";
        return result.has("id") ? result.get("id").getAsString() : "";
    }

    /** Once a second: vanilla crafting tables, chests and smithing tables in the inventory become Ark blocks. */
    @SubscribeEvent public static void convert(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            Item target = fallback(stack);
            if (target != null) inventory.setItem(slot, new ItemStack(target, stack.getCount()));
        }
    }

    static Item fallback(ItemStack stack) {
        if (stack.is(Items.CRAFTING_TABLE)) return StationContent.WORKING_STATION_ITEM.get();
        if (stack.is(Items.CHEST) || stack.is(Items.TRAPPED_CHEST)) return StationContent.STORAGE_CRATE_ITEM.get();
        if (stack.is(Items.SMITHING_TABLE)) return StationContent.SMITHING_TABLE_ITEM.get();
        return null;
    }

    private StationEvents() {}
}
