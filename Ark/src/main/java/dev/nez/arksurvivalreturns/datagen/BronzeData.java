package dev.nez.arksurvivalreturns.datagen;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Bronze Age metal and gear data: tin ore worldgen, the alloy chain, tools, weapons and armour. */
final class BronzeData {
    private static final String NS = "arksurvivalreturns";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";

    private final BiConsumer<String, Object> put;

    private BronzeData(BiConsumer<String, Object> put) {
        this.put = put;
    }

    static void generate(BiConsumer<String, Object> put) {
        var data = new BronzeData(put);
        data.tinOre();
        data.worldgen();
        data.alloy();
        data.tools();
        data.weapons();
        data.armor();
        data.tags();
    }

    // ------------------------------------------------------------------------------------- tin ore

    private void tinOre() {
        cubeAll("tin_ore");
        cubeAll("deepslate_tin_ore");
        oreLoot("tin_ore", "raw_tin");
        oreLoot("deepslate_tin_ore", "raw_tin");
    }

    private void cubeAll(String id) {
        String texture = NS + ":block/" + id;
        put.accept(ASSETS + "models/block/" + id, Map.of("parent", "minecraft:block/cube_all", "textures", Map.of("all", texture)));
        put.accept(ASSETS + "blockstates/" + id, Map.of("variants", Map.of("", Map.of("model", NS + ":block/" + id))));
        blockItem(id, NS + ":block/" + id);
    }

    /** The copper/iron ore pattern: silk touch keeps the block, otherwise raw ore with a fortune bonus. */
    private void oreLoot(String ore, String rawItem) {
        var silkTouch = Map.of("type", "minecraft:item", "name", NS + ":" + ore, "conditions", List.of(
                Map.of("condition", "minecraft:match_tool", "predicate", Map.of("predicates", Map.of(
                        "minecraft:enchantments", List.of(Map.of("enchantments", "minecraft:silk_touch", "levels", Map.of("min", 1))))))));
        var rawDrop = Map.of("type", "minecraft:item", "name", NS + ":" + rawItem, "functions", List.of(
                Map.of("function", "minecraft:set_count", "add", false, "count", Map.of("type", "minecraft:uniform", "min", 2.0, "max", 5.0)),
                Map.of("function", "minecraft:apply_bonus", "enchantment", "minecraft:fortune", "formula", "minecraft:ore_drops"),
                Map.of("function", "minecraft:explosion_decay")));
        var alternatives = Map.of("type", "minecraft:alternatives", "children", List.of(silkTouch, rawDrop));
        put.accept(DATA + "loot_table/blocks/" + ore, Map.of("type", "minecraft:block",
                "pools", List.of(Map.of("rolls", 1, "entries", List.of(alternatives)))));
    }

    /** One vein per chunk, a bit rarer than vanilla copper's sixteen, from bedrock-adjacent to sea level and above. */
    private void worldgen() {
        put.accept(DATA + "worldgen/configured_feature/tin_ore", Map.of("type", "minecraft:ore", "config", Map.of(
                "discard_chance_on_air_exposure", 0.0,
                "targets", List.of(
                        Map.of("state", Map.of("Name", NS + ":tin_ore"),
                                "target", Map.of("predicate_type", "minecraft:tag_match", "tag", "minecraft:stone_ore_replaceables")),
                        Map.of("state", Map.of("Name", NS + ":deepslate_tin_ore"),
                                "target", Map.of("predicate_type", "minecraft:tag_match", "tag", "minecraft:deepslate_ore_replaceables"))),
                "size", 8)));
        put.accept(DATA + "worldgen/placed_feature/tin_ore", Map.of("feature", NS + ":tin_ore", "placement", List.of(
                Map.of("type", "minecraft:count", "count", 10),
                Map.of("type", "minecraft:in_square"),
                Map.of("type", "minecraft:height_range", "height", Map.of("type", "minecraft:trapezoid",
                        "min_inclusive", Map.of("absolute", -16), "max_inclusive", Map.of("absolute", 96))),
                Map.of("type", "minecraft:biome"))));
        put.accept(DATA + "neoforge/biome_modifier/tin_ore", Map.of("type", "neoforge:add_features",
                "biomes", "#minecraft:is_overworld", "features", NS + ":tin_ore", "step", "underground_ores"));
    }

    // ---------------------------------------------------------------------------------------- alloy

    private void alloy() {
        flatItem("raw_tin");
        flatItem("tin_ingot");
        flatItem("bronze_blend");
        flatItem("bronze_ingot");
        // Three copper, one tin: the alloy step, then smelted at the Primitive Forge like any other metal.
        shapeless("bronze_blend", NS + ":bronze_blend", 4,
                "minecraft:copper_ingot", "minecraft:copper_ingot", "minecraft:copper_ingot", NS + ":tin_ingot");
        smelting("bronze_ingot", NS + ":bronze_blend", NS + ":bronze_ingot", 200, 1.0f);
        // Tin ore and raw tin both reduce to tin_ingot; the forge only reads minecraft:smelting, blasting is kept
        // for the vanilla recipe book and any future blast source.
        smelting("tin_ingot_from_smelting_ore", "#c:ores/tin", NS + ":tin_ingot", 200, 0.7f);
        blasting("tin_ingot_from_blasting_ore", "#c:ores/tin", NS + ":tin_ingot", 100, 0.7f);
        smelting("tin_ingot_from_smelting_raw", NS + ":raw_tin", NS + ":tin_ingot", 200, 0.7f);
        blasting("tin_ingot_from_blasting_raw", NS + ":raw_tin", NS + ":tin_ingot", 100, 0.7f);
    }

    // ---------------------------------------------------------------------------------------- tools

    private void tools() {
        for (String tool : List.of("bronze_pickaxe", "bronze_axe", "bronze_shovel", "bronze_hoe")) handheld(tool);
        Map<String, String> key = Map.of("X", NS + ":bronze_ingot", "#", "minecraft:stick");
        shaped("bronze_pickaxe", NS + ":bronze_pickaxe", 1, List.of("XXX", " # ", " # "), key);
        shaped("bronze_axe", NS + ":bronze_axe", 1, List.of("XX", "X#", " #"), key);
        shaped("bronze_shovel", NS + ":bronze_shovel", 1, List.of("X", "#", "#"), key);
        shaped("bronze_hoe", NS + ":bronze_hoe", 1, List.of("XX", " #", " #"), key);
    }

    // -------------------------------------------------------------------------------------- weapons

    /** The two Bronze weapons, either unlocking "Knight of the Realm": a longsword and a heavy hammer. */
    private void weapons() {
        handheld("bronze_longsword");
        handheld("bronze_hammer");
        Map<String, String> key = Map.of("X", NS + ":bronze_ingot", "#", "minecraft:stick");
        // A wider, two-handed-feeling blade: three ingots around a single-cell offset, not the vanilla column.
        shaped("bronze_longsword", NS + ":bronze_longsword", 1, List.of(" X", "XX", " #"), key);
        // Heavier still: five ingots around a haft, distinct from the pickaxe's plain center-stick shape.
        shaped("bronze_hammer", NS + ":bronze_hammer", 1, List.of("XXX", "X#X", " # "), key);
        // Better Combat (I11) movesets; ignored when the mod is absent. The longsword reads as a claymore
        // (two-handed, range bonus); the hammer keeps its own two-handed slam preset.
        weapon("bronze_longsword", "bettercombat:claymore");
        weapon("bronze_hammer", "bettercombat:hammer");
    }

    private void weapon(String item, String preset) {
        put.accept(DATA + "weapon_attributes/" + item, Map.of("parent", preset));
    }

    // --------------------------------------------------------------------------------------- armor

    private void armor() {
        String texture = NS + ":bronze";
        put.accept(ASSETS + "equipment/bronze", Map.of("layers", Map.of(
                "humanoid", List.of(Map.of("texture", texture)),
                "humanoid_leggings", List.of(Map.of("texture", texture)),
                "humanoid_baby", List.of(Map.of("texture", texture)))));
        for (String piece : PrimitiveData.ARMOR) flatItem("bronze_" + piece);
        Map<String, String> key = Map.of("X", NS + ":bronze_ingot");
        shaped("bronze_helmet", NS + ":bronze_helmet", 1, List.of("XXX", "X X"), key);
        shaped("bronze_chestplate", NS + ":bronze_chestplate", 1, List.of("X X", "XXX", "XXX"), key);
        shaped("bronze_leggings", NS + ":bronze_leggings", 1, List.of("XXX", "X X", "X X"), key);
        shaped("bronze_boots", NS + ":bronze_boots", 1, List.of("X X", "X X"), key);
    }

    // ----------------------------------------------------------------------------------------- tags

    /**
     * block/mineable/pickaxe lives in StationData, and the vanilla tool/armour tags (pickaxes, axes, swords,
     * head_armor, c:item/armors/... and so on) live in PrimitiveData beside rock and keratin: each of those
     * paths is written exactly once for the whole mod, so the bronze entries were added there instead of here.
     */
    private void tags() {
        tag("minecraft", "block/needs_stone_tool", NS + ":tin_ore", NS + ":deepslate_tin_ore");
        tag("c", "block/ores", NS + ":tin_ore", NS + ":deepslate_tin_ore");
        tag("c", "block/ores/tin", NS + ":tin_ore", NS + ":deepslate_tin_ore");
        tag("c", "item/ores/tin", NS + ":tin_ore", NS + ":deepslate_tin_ore");
        tag("c", "item/raw_materials/tin", NS + ":raw_tin");
        tag("c", "item/ingots/tin", NS + ":tin_ingot");
        tag("c", "item/ingots/bronze", NS + ":bronze_ingot");
    }

    private void tag(String namespace, String path, String... values) {
        put.accept("data/" + namespace + "/tags/" + path, Map.of("replace", false, "values", List.of(values)));
    }

    // ------------------------------------------------------------------------------------- helpers

    private void flatItem(String id) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", NS + ":item/" + id)));
        itemDefinition(id);
    }

    private void handheld(String id) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", "minecraft:item/handheld",
                "textures", Map.of("layer0", NS + ":item/" + id)));
        itemDefinition(id);
    }

    private void blockItem(String id, String model) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", model));
        itemDefinition(id);
    }

    private void itemDefinition(String id) {
        put.accept(ASSETS + "items/" + id, Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/" + id)));
    }

    private void shaped(String id, String result, int count, List<String> pattern, Map<String, String> key) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:crafting_shaped", "category", "equipment", "group", id,
                "pattern", pattern, "key", key, "result", Map.of("count", count, "id", result)));
    }

    private void shapeless(String id, String result, int count, String... ingredients) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:crafting_shapeless", "category", "misc", "group", id,
                "ingredients", List.of(ingredients), "result", Map.of("count", count, "id", result)));
    }

    private void smelting(String id, String ingredient, String result, int cookingTime, float experience) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:smelting", "category", "misc", "group", id,
                "cookingtime", cookingTime, "experience", experience, "ingredient", ingredient, "result", Map.of("id", result)));
    }

    private void blasting(String id, String ingredient, String result, int cookingTime, float experience) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:blasting", "category", "misc", "group", id,
                "cookingtime", cookingTime, "experience", experience, "ingredient", ingredient, "result", Map.of("id", result)));
    }

    // -------------------------------------------------------------------------------------- lang

    static void lang(Map<String, String> en, Map<String, String> pt) {
        name(en, pt, "block", "tin_ore", "Tin Ore", "Minério de estanho");
        name(en, pt, "item", "tin_ore", "Tin Ore", "Minério de estanho");
        name(en, pt, "block", "deepslate_tin_ore", "Deepslate Tin Ore", "Minério de estanho em ardósia profunda");
        name(en, pt, "item", "deepslate_tin_ore", "Deepslate Tin Ore", "Minério de estanho em ardósia profunda");
        name(en, pt, "item", "raw_tin", "Raw Tin", "Estanho bruto");
        name(en, pt, "item", "tin_ingot", "Tin Ingot", "Lingote de estanho");
        name(en, pt, "item", "bronze_blend", "Bronze Blend", "Mistura de bronze");
        name(en, pt, "item", "bronze_ingot", "Bronze Ingot", "Lingote de bronze");
        name(en, pt, "item", "bronze_pickaxe", "Bronze Pickaxe", "Picareta de bronze");
        name(en, pt, "item", "bronze_axe", "Bronze Axe", "Machado de bronze");
        name(en, pt, "item", "bronze_shovel", "Bronze Shovel", "Pá de bronze");
        name(en, pt, "item", "bronze_hoe", "Bronze Hoe", "Enxada de bronze");
        name(en, pt, "item", "bronze_longsword", "Bronze Longsword", "Espadão de bronze");
        name(en, pt, "item", "bronze_hammer", "Bronze Hammer", "Martelo de bronze");
        name(en, pt, "item", "bronze_helmet", "Bronze Helmet", "Elmo de bronze");
        name(en, pt, "item", "bronze_chestplate", "Bronze Chestplate", "Peitoral de bronze");
        name(en, pt, "item", "bronze_leggings", "Bronze Leggings", "Perneiras de bronze");
        name(en, pt, "item", "bronze_boots", "Bronze Boots", "Botas de bronze");
    }

    private static void name(Map<String, String> en, Map<String, String> pt, String kind, String id, String english, String portuguese) {
        en.put(kind + "." + NS + "." + id, english);
        pt.put(kind + "." + NS + "." + id, portuguese);
    }

}
