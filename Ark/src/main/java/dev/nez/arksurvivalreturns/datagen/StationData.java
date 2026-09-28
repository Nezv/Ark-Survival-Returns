package dev.nez.arksurvivalreturns.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * State, item, loot, recipe, tag and translation wiring for the workstations authored by
 * tools/build_station_assets.py (Working Station, Storage Crate, smithing table, Medicine Bench, Crusher).
 */
final class StationData {
    private static final String NS = "arksurvivalreturns";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private static final List<String> DIRECTIONS = List.of("north", "east", "south", "west");
    private static final List<String> FACES = List.of("north", "south", "east", "west", "up", "down");
    private static final List<List<String>> EDGES = List.of(List.of("up", "north"), List.of("up", "south"),
            List.of("up", "east"), List.of("up", "west"), List.of("down", "north"), List.of("down", "south"),
            List.of("down", "east"), List.of("down", "west"), List.of("north", "east"), List.of("north", "west"),
            List.of("south", "east"), List.of("south", "west"));

    static void generate(BiConsumer<String, Object> put) {
        for (String id : List.of("working_station", "medicine_bench", "smithing_table")) {
            var variants = new LinkedHashMap<String, Object>();
            for (String facing : DIRECTIONS) variants.put("facing=" + facing, rotated(id, facing));
            put.accept(ASSETS + "blockstates/" + id, Map.of("variants", variants));
            item(put, id, id);
            loot(put, id);
        }
        mortar(put);
        crate(put);
        crusher(put);
        recipes(put);
        tags(put);
    }

    /** The Mortar & Pestle model comes from the design pack (tools/build_prehistoric_camp.py). */
    private static void mortar(BiConsumer<String, Object> put) {
        var variants = new LinkedHashMap<String, Object>();
        for (String facing : DIRECTIONS)
            variants.put("facing=" + facing, Map.of("model", NS + ":block/prehistoric/mortar_empty", "y", DIRECTIONS.indexOf(facing) * 90));
        put.accept(ASSETS + "blockstates/mortar_and_pestle", Map.of("variants", variants));
        put.accept(ASSETS + "models/item/mortar_and_pestle", Map.of("parent", NS + ":block/prehistoric/mortar_empty"));
        put.accept(ASSETS + "items/mortar_and_pestle", Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/mortar_and_pestle")));
        loot(put, "mortar_and_pestle");
    }

    private static void crate(BiConsumer<String, Object> put) {
        var parts = new ArrayList<Object>();
        parts.add(Map.of("apply", Map.of("model", model("storage_crate_core"))));
        // A frame beam shows only where neither face it borders touches another crate.
        for (List<String> edge : EDGES) {
            parts.add(Map.of("when", Map.of(edge.get(0), "false", edge.get(1), "false"),
                    "apply", Map.of("model", model("storage_crate_edge_" + edge.get(0) + "_" + edge.get(1)))));
        }
        for (String side : DIRECTIONS) {
            parts.add(Map.of("when", Map.of(side, "false"), "apply", Map.of("model", model("storage_crate_handle_" + side))));
        }
        put.accept(ASSETS + "blockstates/storage_crate", Map.of("multipart", parts));
        item(put, "storage_crate", "storage_crate_item");
        loot(put, "storage_crate");
    }

    private static void crusher(BiConsumer<String, Object> put) {
        var variants = new LinkedHashMap<String, Object>();
        for (String facing : DIRECTIONS) {
            for (boolean running : List.of(false, true)) {
                for (int spin = 0; spin < 4; spin++) {
                    variants.put("facing=" + facing + ",running=" + running + ",spin=" + spin, rotated("crusher_spin" + spin, facing));
                }
            }
        }
        put.accept(ASSETS + "blockstates/crusher", Map.of("variants", variants));
        item(put, "crusher", "crusher_spin0");
        loot(put, "crusher");
    }

    private static void recipes(BiConsumer<String, Object> put) {
        shaped(put, "working_station", 1, List.of("PP", "LL"), Map.of("P", "#minecraft:planks", "L", "#minecraft:logs"));
        shaped(put, "storage_crate", 1, List.of("PPP", "P P", "PPP"), Map.of("P", "#minecraft:planks"));
        shaped(put, "smithing_table", 1, List.of("II", "SS", "PP"),
                Map.of("I", "minecraft:iron_ingot", "S", "minecraft:smooth_stone", "P", "#minecraft:planks"));
        // Iron Age: the bench waits for iron; its medicine is still to be designed.
        shaped(put, "medicine_bench", 1, List.of("IBI", "PPP", "L L"), Map.of("I", "#c:ingots/iron",
                "B", "minecraft:glass_bottle", "P", "#minecraft:planks", "L", "#minecraft:logs"));
        // A stone bowl and a rock pestle.
        shaped(put, "mortar_and_pestle", 1, List.of(" R ", "C C", " C "), Map.of("R", NS + ":rock", "C", "minecraft:cobblestone"));
        shaped(put, "crusher", 1, List.of("LGL", "C C", "CCC"),
                Map.of("L", "#minecraft:logs", "G", "minecraft:grindstone", "C", "minecraft:cobblestone"));
        // Made only at the Mortar & Pestle (the item tag arksurvivalreturns:mortar).
        shapeless(put, "narcotics", 4, NS + ":narcoberry", NS + ":narcoberry", NS + ":narcoberry", NS + ":narcoberry");
        shapeless(put, "herbal_bandage", 2, NS + ":fiber_bandage", NS + ":fiber_bandage", NS + ":azulberry", NS + ":amarberry",
                NS + ":plant_fiber");
        shapeless(put, "healing_mixture", 1, "minecraft:glass_bottle", NS + ":azulberry", NS + ":azulberry",
                NS + ":tintoberry", NS + ":plant_fiber");
    }

    private static void tags(BiConsumer<String, Object> put) {
        put.accept(DATA + "tags/item/medicine", Map.of("replace", false, "values", List.of()));
        put.accept(DATA + "tags/item/mortar", Map.of("replace", false, "values",
                List.of(NS + ":narcotics", NS + ":herbal_bandage", NS + ":healing_mixture")));
        // Other mods look for chests and workbenches through the common tags.
        for (String kind : List.of("item", "block")) {
            put.accept("data/c/tags/" + kind + "/chests", Map.of("replace", false, "values", List.of(NS + ":storage_crate")));
            put.accept("data/c/tags/" + kind + "/chests/wooden", Map.of("replace", false, "values", List.of(NS + ":storage_crate")));
            put.accept("data/c/tags/" + kind + "/player_workstations/crafting_tables", Map.of("replace", false,
                    "values", List.of(NS + ":working_station")));
        }
        put.accept("data/minecraft/tags/block/mineable/axe", Map.of("replace", false,
                "values", List.of(NS + ":working_station", NS + ":medicine_bench", NS + ":storage_crate")));
        put.accept("data/minecraft/tags/block/mineable/pickaxe", Map.of("replace", false,
                "values", List.of(NS + ":smithing_table", NS + ":crusher", NS + ":primitive_forge", NS + ":mortar_and_pestle",
                        // Sulphur crystals (SulphurData): kept here so this datagen run writes the tag file once.
                        NS + ":sulphur_block", NS + ":budding_sulphur", NS + ":small_sulphur_bud",
                        NS + ":medium_sulphur_bud", NS + ":large_sulphur_bud", NS + ":sulphur_cluster")));
    }

    static void messages(Map<String, String> en, Map<String, String> pt) {
        en.put("block." + NS + ".working_station", "Working Station");
        pt.put("block." + NS + ".working_station", "Estação de trabalho");
        en.put("block." + NS + ".storage_crate", "Storage Crate");
        pt.put("block." + NS + ".storage_crate", "Caixote de armazenamento");
        en.put("block." + NS + ".smithing_table", "Smithing Table");
        pt.put("block." + NS + ".smithing_table", "Mesa de ferraria");
        en.put("block." + NS + ".medicine_bench", "Medicine Bench");
        pt.put("block." + NS + ".medicine_bench", "Bancada de medicina");
        en.put("block." + NS + ".mortar_and_pestle", "Mortar & Pestle");
        pt.put("block." + NS + ".mortar_and_pestle", "Pilão");
        en.put("block." + NS + ".crusher", "Crusher");
        pt.put("block." + NS + ".crusher", "Triturador");
        en.put("item." + NS + ".herbal_bandage", "Herbal Bandage");
        pt.put("item." + NS + ".herbal_bandage", "Bandagem de ervas");
        en.put("item." + NS + ".healing_mixture", "Healing Mixture");
        pt.put("item." + NS + ".healing_mixture", "Mistura curativa");
    }

    static void itemModels(BiConsumer<String, Object> put) {
        for (var entry : Map.of("herbal_bandage", "minecraft:item/paper", "healing_mixture", "minecraft:item/honey_bottle").entrySet()) {
            put.accept(ASSETS + "models/item/" + entry.getKey(), Map.of("parent", "minecraft:item/generated",
                    "textures", Map.of("layer0", entry.getValue())));
            put.accept(ASSETS + "items/" + entry.getKey(), Map.of("model", Map.of("type", "minecraft:model",
                    "model", NS + ":item/" + entry.getKey())));
        }
    }

    private static String model(String name) { return NS + ":block/station/" + name; }

    private static Map<String, Object> rotated(String name, String facing) {
        return Map.of("model", model(name), "y", DIRECTIONS.indexOf(facing) * 90);
    }

    private static void item(BiConsumer<String, Object> put, String id, String blockModel) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", model(blockModel)));
        put.accept(ASSETS + "items/" + id, Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/" + id)));
    }

    private static void loot(BiConsumer<String, Object> put, String id) {
        put.accept(DATA + "loot_table/blocks/" + id, Map.of("type", "minecraft:block", "pools", List.of(Map.of("rolls", 1,
                "conditions", List.of(Map.of("condition", "minecraft:survives_explosion")),
                "entries", List.of(Map.of("type", "minecraft:item", "name", NS + ":" + id))))));
    }

    private static void shaped(BiConsumer<String, Object> put, String id, int count, List<String> pattern, Map<String, String> key) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:crafting_shaped", "category", "misc", "group", id,
                "pattern", pattern, "key", key, "result", Map.of("count", count, "id", NS + ":" + id)));
    }

    private static void shapeless(BiConsumer<String, Object> put, String id, int count, String... ingredients) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:crafting_shapeless", "category", "misc", "group", id,
                "ingredients", List.of(ingredients), "result", Map.of("count", count, "id", NS + ":" + id)));
    }

    private StationData() {}
}
