package dev.nez.arksurvivalreturns.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Sulphur crystals (an amethyst-alike ore chain), the cave patch that grows them, the Crusher's
 * gunpowder recipe and the explosive arrow. Models, blockstates, loot tables, tags, recipes and lang
 * for everything Plan D owns; the tech tree and every PNG belong to other agents.
 */
final class SulphurData {
    private static final String NS = "arksurvivalreturns";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private static final List<String> BUD_STAGES = List.of("small_sulphur_bud", "medium_sulphur_bud", "large_sulphur_bud", "sulphur_cluster");

    private final BiConsumer<String, Object> put;

    private SulphurData(BiConsumer<String, Object> put) {
        this.put = put;
    }

    static void generate(BiConsumer<String, Object> put) {
        var data = new SulphurData(put);
        data.blocks();
        data.buds();
        data.worldgen();
        data.explosiveArrow();
        data.tags();
    }

    // ------------------------------------------------------------------------------------- blocks

    private void blocks() {
        // Sulphur block: a plain cube, crafts from four sulphur like amethyst block from four shards.
        put.accept(ASSETS + "models/block/sulphur_block", Map.of("parent", "minecraft:block/cube_all",
                "textures", Map.of("all", NS + ":block/sulphur_block")));
        put.accept(ASSETS + "blockstates/sulphur_block", Map.of("variants", Map.of("", Map.of("model", NS + ":block/sulphur_block"))));
        blockItem("sulphur_block", NS + ":block/sulphur_block");
        blockLootSelf("sulphur_block");
        flatItem("sulphur", NS + ":item/sulphur");
        shaped2x2("sulphur_block", NS + ":sulphur_block", NS + ":sulphur");

        // Budding sulphur: random-ticks and grows buds on free faces; no loot table, so it never drops,
        // not even with silk touch, exactly like budding amethyst.
        put.accept(ASSETS + "models/block/budding_sulphur", Map.of("parent", "minecraft:block/cube_all",
                "textures", Map.of("all", NS + ":block/budding_sulphur")));
        put.accept(ASSETS + "blockstates/budding_sulphur", Map.of("variants", Map.of("", Map.of("model", NS + ":block/budding_sulphur"))));
        blockItem("budding_sulphur", NS + ":block/budding_sulphur");
        put.accept(DATA + "loot_table/blocks/budding_sulphur", Map.of("type", "minecraft:block"));
    }

    // ---------------------------------------------------------------------------------- buds/cluster

    /** Small/medium/large bud and the cluster: cross models like amethyst, facing-only blockstates. */
    private void buds() {
        for (String id : BUD_STAGES) {
            put.accept(ASSETS + "models/block/" + id, Map.of("parent", "minecraft:block/cross", "textures", Map.of("cross", NS + ":block/" + id)));
            put.accept(ASSETS + "blockstates/" + id, facingVariants(id));
            budItem(id);
        }
        for (String id : List.of("small_sulphur_bud", "medium_sulphur_bud", "large_sulphur_bud")) {
            put.accept(DATA + "loot_table/blocks/" + id, silkTouchOnlyLoot(id));
        }
        put.accept(DATA + "loot_table/blocks/sulphur_cluster", clusterLoot());
    }

    private Map<String, Object> facingVariants(String id) {
        var variants = new LinkedHashMap<String, Object>();
        variants.put("facing=down", Map.of("model", NS + ":block/" + id, "x", 180));
        variants.put("facing=east", Map.of("model", NS + ":block/" + id, "x", 90, "y", 90));
        variants.put("facing=north", Map.of("model", NS + ":block/" + id, "x", 90));
        variants.put("facing=south", Map.of("model", NS + ":block/" + id, "x", 90, "y", 180));
        variants.put("facing=up", Map.of("model", NS + ":block/" + id));
        variants.put("facing=west", Map.of("model", NS + ":block/" + id, "x", 90, "y", 270));
        return Map.of("variants", variants);
    }

    /** 4 with a pickaxe (fortune applies), 2 without, silk touch keeps the cluster itself. Mirrors amethyst_cluster.json. */
    private Map<String, Object> clusterLoot() {
        var pickaxeDrop = Map.of("type", "minecraft:item",
                "conditions", List.of(Map.of("condition", "minecraft:match_tool", "predicate", Map.of("items", "#minecraft:cluster_max_harvestables"))),
                "functions", List.of(
                        Map.of("function", "minecraft:set_count", "count", 4, "add", false),
                        Map.of("function", "minecraft:apply_bonus", "enchantment", "minecraft:fortune", "formula", "minecraft:ore_drops")),
                "name", NS + ":sulphur");
        var handDrop = Map.of("type", "minecraft:item",
                "functions", List.of(
                        Map.of("function", "minecraft:set_count", "count", 2, "add", false),
                        Map.of("function", "minecraft:explosion_decay")),
                "name", NS + ":sulphur");
        var silkTouchDrop = Map.of("type", "minecraft:item", "conditions", List.of(silkTouch()), "name", NS + ":sulphur_cluster");
        return Map.of("type", "minecraft:block", "pools", List.of(Map.of("rolls", 1, "entries", List.of(
                Map.of("type", "minecraft:alternatives", "children", List.of(silkTouchDrop,
                        Map.of("type", "minecraft:alternatives", "children", List.of(pickaxeDrop, handDrop))))))));
    }

    private Map<String, Object> silkTouchOnlyLoot(String id) {
        return Map.of("type", "minecraft:block", "pools", List.of(Map.of("rolls", 1, "conditions", List.of(silkTouch()),
                "entries", List.of(Map.of("type", "minecraft:item", "name", NS + ":" + id)))));
    }

    private Map<String, Object> silkTouch() {
        return Map.of("condition", "minecraft:match_tool", "predicate", Map.of("predicates", Map.of("minecraft:enchantments",
                List.of(Map.of("enchantments", "minecraft:silk_touch", "levels", Map.of("min", 1))))));
    }

    // ------------------------------------------------------------------------------------ worldgen

    /**
     * Small patches on cave floors, everywhere in the Overworld: a count of 4-8 attempts per chunk, each
     * sampled across the whole cave height range and scanned down onto solid ground (the same placement
     * chain vanilla uses for lush-cave moss and clay), so caves are common enough that a player
     * exploring any one of them finds sulphur without every wall of every cave being lined with it.
     */
    private void worldgen() {
        put.accept(DATA + "worldgen/configured_feature/sulphur_patch", Map.of("type", NS + ":sulphur_patch", "config", Map.of()));
        put.accept(DATA + "worldgen/placed_feature/sulphur_patch", Map.of("feature", NS + ":sulphur_patch", "placement", List.of(
                Map.of("type", "minecraft:count", "count", Map.of("type", "minecraft:uniform", "min_inclusive", 4, "max_inclusive", 8)),
                Map.of("type", "minecraft:in_square"),
                Map.of("type", "minecraft:height_range", "height", Map.of("type", "minecraft:uniform",
                        "min_inclusive", Map.of("above_bottom", 0), "max_inclusive", Map.of("absolute", 256))),
                Map.of("type", "minecraft:environment_scan", "direction_of_search", "down",
                        "target_condition", Map.of("type", "minecraft:solid"),
                        "allowed_search_condition", Map.of("type", "minecraft:matching_block_tag", "tag", "minecraft:air"),
                        "max_steps", 12),
                Map.of("type", "minecraft:random_offset", "xz_spread", 0, "y_spread", 1),
                Map.of("type", "minecraft:biome"))));
        put.accept(DATA + "neoforge/biome_modifier/sulphur_patches", Map.of("type", "neoforge:add_features",
                "biomes", "#minecraft:is_overworld", "features", NS + ":sulphur_patch", "step", "underground_decoration"));
    }

    // ------------------------------------------------------------------------------ explosive arrow

    /** Working Station recipe: a plus of four arrows around one gunpowder, like the vanilla arrow's own 3x3-friendly shape. */
    private void explosiveArrow() {
        flatItem("explosive_arrow", NS + ":item/explosive_arrow");
        put.accept(DATA + "recipe/explosive_arrow", Map.of("type", "minecraft:crafting_shaped", "category", "equipment", "group", "explosive_arrow",
                "pattern", List.of(" A ", "AGA", " A "),
                "key", Map.of("A", "minecraft:arrow", "G", "minecraft:gunpowder"),
                "result", Map.of("count", 4, "id", NS + ":explosive_arrow")));
    }

    // ---------------------------------------------------------------------------------------- tags

    private void tags() {
        // The mineable/pickaxe tag for these six blocks is added in StationData.tags() so this
        // datagen run only writes that shared vanilla tag file once.
        tag("c", "gems/sulphur", NS + ":sulphur");
        // The tag bows, crossbows and dispensers all read to decide what counts as ammunition.
        tag("minecraft", "item/arrows", NS + ":explosive_arrow");
    }

    private void tag(String namespace, String path, String... values) {
        put.accept("data/" + namespace + "/tags/" + path, Map.of("replace", false, "values", List.of(values)));
    }

    // ------------------------------------------------------------------------------------- helpers

    private void flatItem(String id, String texture) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", "minecraft:item/generated", "textures", Map.of("layer0", texture)));
        itemDefinition(id);
    }

    /** Bud/cluster item icons reuse the block texture, exactly like vanilla's amethyst bud items. */
    private void budItem(String id) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", "minecraft:item/generated", "textures", Map.of("layer0", NS + ":block/" + id)));
        itemDefinition(id);
    }

    private void blockItem(String id, String model) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", model));
        itemDefinition(id);
    }

    private void itemDefinition(String id) {
        put.accept(ASSETS + "items/" + id, Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/" + id)));
    }

    private void blockLootSelf(String id) {
        put.accept(DATA + "loot_table/blocks/" + id, Map.of("type", "minecraft:block", "pools", List.of(Map.of("rolls", 1,
                "conditions", List.of(Map.of("condition", "minecraft:survives_explosion")),
                "entries", List.of(Map.of("type", "minecraft:item", "name", NS + ":" + id))))));
    }

    private void shaped2x2(String id, String result, String ingredient) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:crafting_shaped", "category", "building", "group", id,
                "pattern", List.of("##", "##"), "key", Map.of("#", ingredient), "result", Map.of("id", result)));
    }

    // ---------------------------------------------------------------------------------------- lang

    static void lang(Map<String, String> en, Map<String, String> pt) {
        name(en, pt, "item", "sulphur", "Sulphur", "Enxofre");
        name(en, pt, "block", "sulphur_block", "Sulphur Block", "Bloco de enxofre");
        name(en, pt, "block", "budding_sulphur", "Budding Sulphur", "Enxofre germinante");
        name(en, pt, "block", "small_sulphur_bud", "Small Sulphur Bud", "Broto pequeno de enxofre");
        name(en, pt, "block", "medium_sulphur_bud", "Medium Sulphur Bud", "Broto médio de enxofre");
        name(en, pt, "block", "large_sulphur_bud", "Large Sulphur Bud", "Broto grande de enxofre");
        name(en, pt, "block", "sulphur_cluster", "Sulphur Cluster", "Aglomerado de enxofre");
        name(en, pt, "item", "explosive_arrow", "Explosive Arrow", "Flecha explosiva");
        en.put("entity." + NS + ".explosive_arrow", "Explosive Arrow");
        pt.put("entity." + NS + ".explosive_arrow", "Flecha explosiva");
    }

    private static void name(Map<String, String> en, Map<String, String> pt, String kind, String id, String english, String portuguese) {
        en.put(kind + "." + NS + "." + id, english);
        pt.put(kind + "." + NS + "." + id, portuguese);
    }
}
