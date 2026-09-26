package dev.nez.arksurvivalreturns.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import dev.nez.arksurvivalreturns.registry.ModContent;

/** State, recipe and item wiring for the authored models in tools/build_camp_assets.py and build_prehistoric_camp.py. */
final class CampAssetsData {
    private static final String NS = "arksurvivalreturns";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final List<String> DIRECTIONS = List.of("north", "east", "south", "west");

    /** Primitive Bedroll: two halves like a bed, the grass models from build_prehistoric_camp.py. */
    static void bedroll(BiConsumer<String, Object> put) {
        var variants = new LinkedHashMap<String, Object>();
        for (String facing : DIRECTIONS) for (String part : List.of("foot", "head")) {
            variants.put("facing=" + facing + ",part=" + part, Map.of("model", NS + ":block/prehistoric/primitive_bedroll_" + part,
                    "y", DIRECTIONS.indexOf(facing) * 90));
        }
        put.accept(ASSETS + "blockstates/bedroll", Map.of("variants", variants));
        item(put, "bedroll", NS + ":block/prehistoric/primitive_bedroll_rolled");
    }

    static void farm(BiConsumer<String, Object> put) {
        for (String wood : ModContent.TROUGHS.keySet()) {
            String id = wood.equals("oak") ? "trough" : wood + "_trough";
            model(put, id, "trough_" + wood, "trough_" + wood);
            var parts = new ArrayList<Object>();
            for (String facing : DIRECTIONS) {
                parts.add(Map.of("when", Map.of("facing", facing), "apply", rotated("trough_" + wood, facing)));
                parts.add(Map.of("when", Map.of("facing", facing, "filled", "true"), "apply", rotated("trough_feed", facing)));
            }
            put.accept(ASSETS + "blockstates/" + id, Map.of("multipart", parts));
            put.accept("data/" + NS + "/loot_table/blocks/" + id, Map.of("type", "minecraft:block", "pools", List.of(
                    Map.of("rolls", 1, "conditions", List.of(Map.of("condition", "minecraft:survives_explosion")),
                            "entries", List.of(Map.of("type", "minecraft:item", "name", NS + ":" + id))))));
            put.accept("data/" + NS + "/recipe/" + id, Map.of("type", "minecraft:crafting_shaped", "category", "misc",
                    "group", "feeding_trough", "pattern", List.of("P P", "PRP", " F "),
                    "key", Map.of("P", "minecraft:" + wood + "_planks", "R", "minecraft:resin_clump", "F", NS + ":plant_fiber"),
                    "result", Map.of("count", 1, "id", NS + ":" + id)));
        }
        put.accept("data/" + NS + "/tags/item/feeding_troughs", Map.of("replace", false,
                "values", ModContent.TROUGHS.keySet().stream().map(wood -> NS + ":" + (wood.equals("oak") ? "trough" : wood + "_trough")).toList()));
        // Creakings are removed by the survival theme; scraping spruce keeps resin reachable.
        put.accept("data/" + NS + "/recipe/spruce_resin", Map.of("type", "minecraft:crafting_shapeless", "category", "misc",
                "ingredients", List.of("minecraft:spruce_log", "minecraft:flint"), "result", Map.of("count", 2, "id", "minecraft:resin_clump")));
        // Two blocks tall: the lower half carries the hanging food and rations, the upper half is frame only.
        item(put, "drying_rack", NS + ":block/camp/drying_rack_item");
        var parts = new ArrayList<Object>();
        for (String facing : DIRECTIONS) {
            parts.add(Map.of("when", Map.of("facing", facing, "half", "lower"), "apply", rotated("drying_rack_lower", facing)));
            parts.add(Map.of("when", Map.of("facing", facing, "half", "upper"), "apply", rotated("drying_rack_upper", facing)));
            for (String food : List.of("meat", "fish", "berries")) {
                for (int slot = 1; slot <= 3; slot++) {
                    String counts = slot == 1 ? "1|2|3" : slot == 2 ? "2|3" : "3";
                    parts.add(Map.of("when", Map.of("facing", facing, "half", "lower", "food", food, "hanging", counts),
                            "apply", rotated("rack_" + food + "_" + slot, facing)));
                }
            }
            parts.add(Map.of("when", Map.of("facing", facing, "half", "lower", "ready", "true"), "apply", rotated("rack_ready", facing)));
        }
        put.accept(ASSETS + "blockstates/drying_rack", Map.of("multipart", parts));
    }

    static void pot(BiConsumer<String, Object> put) {
        model(put, "cooking_pot", "cooking_pot", "cooking_pot");
        var variants = new LinkedHashMap<String, Object>();
        for (String facing : DIRECTIONS) {
            for (boolean fire : List.of(false, true)) variants.put("facing=" + facing + ",on_campfire=" + fire,
                    rotated(fire ? "cooking_pot_campfire" : "cooking_pot", facing));
        }
        put.accept(ASSETS + "blockstates/cooking_pot", Map.of("variants", variants));
    }

    private static Map<String, Object> rotated(String model, String facing) {
        return Map.of("model", NS + ":block/camp/" + model, "y", DIRECTIONS.indexOf(facing) * 90);
    }

    private static void item(BiConsumer<String, Object> put, String id, String model) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", model));
        put.accept(ASSETS + "items/" + id, Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/" + id)));
    }

    private static void model(BiConsumer<String, Object> put, String id, String block, String item) {
        put.accept(ASSETS + "models/block/" + id, Map.of("parent", NS + ":block/camp/" + block));
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", NS + ":block/camp/" + item));
        put.accept(ASSETS + "items/" + id, Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/" + id)));
    }

    private CampAssetsData() {}
}
