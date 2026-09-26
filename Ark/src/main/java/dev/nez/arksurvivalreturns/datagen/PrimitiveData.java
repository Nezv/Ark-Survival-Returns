package dev.nez.arksurvivalreturns.datagen;

import java.util.*;
import java.util.function.BiConsumer;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.primitive.DinoMeat;
import dev.nez.arksurvivalreturns.feature.primitive.LooseRockBlock;
import dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlock;

/** Prehistoric progression data: rocks, stone tools, the stone fire, the forge, dried and dinosaur meats. */
final class PrimitiveData {
    private static final String NS = "arksurvivalreturns";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private static final List<String> DIRECTIONS = List.of("north", "east", "south", "west");

    private final BiConsumer<String, Object> put;

    private PrimitiveData(BiConsumer<String, Object> put) {
        this.put = put;
    }

    static void generate(BiConsumer<String, Object> put) {
        var data = new PrimitiveData(put);
        data.rocks();
        data.tools();
        data.stoneFire();
        data.forge();
        data.meats();
        data.creatureLoot();
        data.tags();
    }

    // --------------------------------------------------------------------------------------- rocks

    private void rocks() {
        var variants = new LinkedHashMap<String, Object>();
        for (LooseRockBlock.Variant variant : LooseRockBlock.Variant.values()) {
            String name = variant.getSerializedName();
            String texture = "minecraft:block/" + name;
            put.accept(ASSETS + "models/block/loose_rock_" + name, Map.of(
                    "textures", Map.of("particle", texture, "rock", texture),
                    "elements", List.of(pebble(4, 5, 8, 2, 9), pebble(9, 7, 12, 1.5, 10), pebble(6, 10, 8, 1, 12))));
            variants.put("variant=" + name, Map.of("model", NS + ":block/loose_rock_" + name));
        }
        put.accept(ASSETS + "blockstates/loose_rock", Map.of("variants", variants));
        blockLoot("loose_rock", NS + ":rock");
        flatItem("rock", NS + ":item/rock", false);
        worldgen();
    }

    private static Map<String, Object> pebble(double x, double z, double xx, double y, double zz) {
        var faces = new LinkedHashMap<String, Object>();
        for (String side : List.of("north", "south", "east", "west", "up")) faces.put(side, Map.of("texture", "#rock"));
        return Map.of("from", List.of(x, 0, z), "to", List.of(xx, y, zz), "faces", faces);
    }

    /** One loose rock feature added to every Overworld biome; the feature itself refuses water and leaves. */
    private void worldgen() {
        put.accept(DATA + "worldgen/configured_feature/loose_rock", Map.of("type", NS + ":loose_rock", "config", Map.of()));
        put.accept(DATA + "worldgen/placed_feature/loose_rock", Map.of("feature", NS + ":loose_rock", "placement", List.of(
                Map.of("type", "minecraft:count", "count", Map.of("type", "minecraft:uniform", "min_inclusive", 2, "max_inclusive", 5)),
                Map.of("type", "minecraft:in_square"),
                Map.of("type", "minecraft:heightmap", "heightmap", "OCEAN_FLOOR_WG"),
                Map.of("type", "minecraft:biome"))));
        put.accept(DATA + "neoforge/biome_modifier/loose_rocks", Map.of("type", "neoforge:add_features",
                "biomes", "#minecraft:is_overworld", "features", NS + ":loose_rock", "step", "vegetal_decoration"));
    }

    // --------------------------------------------------------------------------------------- tools

    private void tools() {
        flatItem("stone_knife", NS + ":item/stone_knife", true);
        flatItem("stone_hatchet", "minecraft:item/stone_axe", true);
        flatItem("fire_starter", NS + ":item/fire_starter", true);
        shapeless("stone_knife", NS + ":stone_knife", 1, NS + ":rock", NS + ":rock", NS + ":plant_fiber");
        shapeless("stone_hatchet", NS + ":stone_hatchet", 1, NS + ":rock", "minecraft:stick", NS + ":plant_fiber");
        shapeless("fire_starter", NS + ":fire_starter", 1, "minecraft:stick", "minecraft:stick", NS + ":plant_fiber");
        shaped("cobblestone_from_rocks", "minecraft:cobblestone", 1, List.of("RR", "RR"), Map.of("R", NS + ":rock"));
        shaped("lead_from_fiber", "minecraft:lead", 1, List.of("FF ", "FF ", "  F"), Map.of("F", NS + ":plant_fiber"));
    }

    // ---------------------------------------------------------------------------------- stone fire

    private void stoneFire() {
        var variants = new LinkedHashMap<String, Object>();
        for (String facing : DIRECTIONS) {
            for (boolean lit : List.of(false, true)) for (boolean fueled : List.of(false, true))
                for (boolean pot : List.of(false, true)) for (StoneFireBlock.Spit spit : StoneFireBlock.Spit.values()) {
                    String model;
                    if (!lit) model = fueled ? "stone_fire_fueled" : "stone_fire_empty";
                    else if (pot) model = "stone_fire_pot_base";
                    else model = spit == StoneFireBlock.Spit.NONE ? "stone_fire_lit" : "stone_fire_" + spit.getSerializedName();
                    String key = "facing=" + facing + ",fueled=" + fueled + ",lit=" + lit + ",pot=" + pot + ",spit=" + spit.getSerializedName();
                    variants.put(key, Map.of("model", NS + ":block/prehistoric/" + model, "y", DIRECTIONS.indexOf(facing) * 90));
                }
        }
        put.accept(ASSETS + "blockstates/stone_fire", Map.of("variants", variants));
        blockItem("stone_fire", NS + ":block/prehistoric/stone_fire_fueled");
        blockLoot("stone_fire", NS + ":stone_fire");
        shaped("stone_fire", NS + ":stone_fire", 1, List.of("RSR", "RRR"), Map.of("R", NS + ":rock", "S", "minecraft:stick"));
    }

    // --------------------------------------------------------------------------------------- forge

    /** Models come from tools/build_prehistoric_camp.py: a two-block bloomery, the lower half owns the drop. */
    private void forge() {
        var variants = new LinkedHashMap<String, Object>();
        for (String facing : DIRECTIONS) for (String half : List.of("lower", "upper")) for (boolean lit : List.of(false, true)) {
            variants.put("facing=" + facing + ",half=" + half + ",lit=" + lit, Map.of("model",
                    NS + ":block/prehistoric/primitive_forge" + (lit ? "_lit_" : "_") + half, "y", DIRECTIONS.indexOf(facing) * 90));
        }
        put.accept(ASSETS + "blockstates/primitive_forge", Map.of("variants", variants));
        blockItem("primitive_forge", NS + ":block/prehistoric/primitive_forge_item");
        put.accept(DATA + "loot_table/blocks/primitive_forge", lowerHalfLoot("primitive_forge"));
        shaped("primitive_forge", NS + ":primitive_forge", 1, List.of("CCC", "CFC", "BBB"),
                Map.of("C", "minecraft:clay_ball", "F", NS + ":stone_fire", "B", "minecraft:cobblestone"));
    }

    /** Two-block stations drop from their lower half only; the upper half breaks along with it. */
    static Map<String, Object> lowerHalfLoot(String block) {
        return Map.of("type", "minecraft:block", "pools", List.of(Map.of("rolls", 1,
                "conditions", List.of(Map.of("condition", "minecraft:survives_explosion"),
                        Map.of("condition", "minecraft:block_state_property", "block", NS + ":" + block, "properties", Map.of("half", "lower"))),
                "entries", List.of(Map.of("type", "minecraft:item", "name", NS + ":" + block)))));
    }

    // --------------------------------------------------------------------------------------- meats

    private void meats() {
        bigItem("dried_meat");
        for (DinoMeat meat : DinoMeat.values()) {
            bigItem(meat.rawId());
            bigItem(meat.cookedId());
            put.accept(DATA + "recipe/" + meat.cookedId() + "_from_campfire_cooking", Map.of("type", "minecraft:campfire_cooking",
                    "category", "food", "cookingtime", 600, "experience", 0.35,
                    "ingredient", NS + ":" + meat.rawId(), "result", Map.of("id", NS + ":" + meat.cookedId())));
            // Smelting exists only so burning creatures drop cooked meat; the forge refuses food.
            put.accept(DATA + "recipe/" + meat.cookedId(), Map.of("type", "minecraft:smelting",
                    "category", "food", "cookingtime", 200, "experience", 0.35,
                    "ingredient", NS + ":" + meat.rawId(), "result", Map.of("id", NS + ":" + meat.cookedId())));
        }
    }

    /** Dinosaur carcasses: meat by body plan, prime cuts from big creatures, hide, feathers and bone. */
    private void creatureLoot() {
        for (Species species : Species.values()) {
            DinoMeat meat = DinoMeat.of(species);
            var pools = new ArrayList<Object>();
            if (meat != null) {
                int max = Math.clamp(Math.round(species.health / 20.0), 1, 10);
                int min = Math.max(1, max / 2);
                pools.add(pool(NS + ":" + meat.rawId(), min, max, List.of(
                        Map.of("function", "minecraft:furnace_smelt", "conditions", List.of(Map.of(
                                "condition", "minecraft:entity_properties", "entity", "this",
                                "predicate", Map.of("flags", Map.of("is_on_fire", true))))))));
                if (species.health >= DinoMeat.PRIME_HEALTH) {
                    var prime = new LinkedHashMap<>(pool(NS + ":" + DinoMeat.PRIME.rawId(), 1, Math.max(1, (int) (species.health / 60)), List.of()));
                    prime.put("conditions", List.of(Map.of("condition", "minecraft:random_chance", "chance", 0.6)));
                    pools.add(prime);
                }
                if (DinoMeat.hide(species)) pools.add(pool("minecraft:leather", 0, Math.clamp(Math.round(species.health / 40.0), 1, 6), List.of()));
                if (meat == DinoMeat.BIRD) pools.add(pool("minecraft:feather", 1, 3, List.of()));
                pools.add(pool("minecraft:bone", 0, 2, List.of()));
            }
            put.accept(DATA + "loot_table/entities/" + species.id, Map.of("type", "minecraft:entity", "pools", pools));
        }
    }

    private static Map<String, Object> pool(String item, int min, int max, List<Map<String, Object>> extra) {
        var functions = new ArrayList<Object>();
        functions.add(Map.of("function", "minecraft:set_count", "count", Map.of("type", "minecraft:uniform", "min", min, "max", max)));
        functions.addAll(extra);
        functions.add(Map.of("function", "minecraft:enchanted_count_increase", "enchantment", "minecraft:looting",
                "count", Map.of("type", "minecraft:uniform", "min", 0, "max", 1)));
        return Map.of("rolls", 1, "entries", List.of(Map.of("type", "minecraft:item", "name", item, "functions", functions)));
    }

    // ---------------------------------------------------------------------------------------- tags

    private void tags() {
        tag(NS, "item/primitive/rock_materials", NS + ":rock");
        tag(NS, "block/primitive/loose_rock_ground", "#minecraft:dirt", "#minecraft:sand", "#minecraft:terracotta",
                "minecraft:gravel", "minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
                "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:calcite", "minecraft:tuff", "minecraft:mud",
                "minecraft:packed_mud", "minecraft:snow_block", "minecraft:cobblestone", "minecraft:mossy_cobblestone");
        tag("minecraft", "item/axes", NS + ":stone_hatchet");
        tag("minecraft", "item/swords", NS + ":stone_knife");
        List<String> raw = new ArrayList<>(), cooked = new ArrayList<>();
        for (DinoMeat meat : DinoMeat.values()) {
            raw.add(NS + ":" + meat.rawId());
            cooked.add(NS + ":" + meat.cookedId());
        }
        var allMeat = new ArrayList<>(raw);
        allMeat.addAll(cooked);
        tag("minecraft", "item/meat", allMeat.toArray(String[]::new));
        tag("c", "item/foods/raw_meat", raw.toArray(String[]::new));
        tag("c", "item/foods/cooked_meat", cooked.toArray(String[]::new));
    }

    private void tag(String namespace, String path, String... values) {
        put.accept("data/" + namespace + "/tags/" + path, Map.of("replace", false, "values", List.of(values)));
    }

    // ------------------------------------------------------------------------------------- helpers

    private void flatItem(String id, String texture, boolean handheld) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", handheld ? "minecraft:item/handheld" : "minecraft:item/generated",
                "textures", Map.of("layer0", texture)));
        itemDefinition(id);
    }

    /** Dinosaur-sized cuts: the vanilla-derived sprite rendered larger in hand, on the ground and in frames. */
    private void bigItem(String id) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", "minecraft:item/generated",
                "textures", Map.of("layer0", NS + ":item/" + id),
                "display", Map.of(
                        "ground", Map.of("rotation", List.of(0, 0, 0), "translation", List.of(0, 2, 0), "scale", List.of(0.7, 0.7, 0.7)),
                        "thirdperson_righthand", Map.of("rotation", List.of(0, 0, 0), "translation", List.of(0, 3, 1), "scale", List.of(0.72, 0.72, 0.72)),
                        "firstperson_righthand", Map.of("rotation", List.of(0, -90, 25), "translation", List.of(1.13, 3.2, 1.13), "scale", List.of(0.88, 0.88, 0.88)),
                        "fixed", Map.of("rotation", List.of(0, 180, 0), "scale", List.of(1.3, 1.3, 1.3)))));
        itemDefinition(id);
    }

    private void blockItem(String id, String model) {
        put.accept(ASSETS + "models/item/" + id, Map.of("parent", model));
        itemDefinition(id);
    }

    private void itemDefinition(String id) {
        put.accept(ASSETS + "items/" + id, Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/" + id)));
    }

    private void blockLoot(String block, String item) {
        put.accept(DATA + "loot_table/blocks/" + block, Map.of("type", "minecraft:block", "pools", List.of(Map.of("rolls", 1,
                "conditions", List.of(Map.of("condition", "minecraft:survives_explosion")),
                "entries", List.of(Map.of("type", "minecraft:item", "name", item))))));
    }

    private void shapeless(String id, String result, int count, String... ingredients) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:crafting_shapeless", "category", "equipment", "group", id,
                "ingredients", List.of(ingredients), "result", Map.of("count", count, "id", result)));
    }

    private void shaped(String id, String result, int count, List<String> pattern, Map<String, String> key) {
        put.accept(DATA + "recipe/" + id, Map.of("type", "minecraft:crafting_shaped", "category", "misc", "group", id,
                "pattern", pattern, "key", key, "result", Map.of("count", count, "id", result)));
    }

    // ---------------------------------------------------------------------------------------- lang

    static void lang(Map<String, String> en, Map<String, String> pt) {
        name(en, pt, "item", "rock", "Rock", "Pedra");
        name(en, pt, "block", "loose_rock", "Loose Rock", "Pedra solta");
        name(en, pt, "item", "stone_knife", "Stone Knife", "Faca de pedra");
        name(en, pt, "item", "stone_hatchet", "Stone Hatchet", "Machadinha de pedra");
        name(en, pt, "item", "fire_starter", "Fire Starter", "Acendedor de fogo");
        name(en, pt, "block", "stone_fire", "Stone Fire", "Fogueira de pedras");
        name(en, pt, "item", "stone_fire", "Stone Fire", "Fogueira de pedras");
        name(en, pt, "block", "primitive_forge", "Primitive Forge", "Forja primitiva");
        name(en, pt, "item", "primitive_forge", "Primitive Forge", "Forja primitiva");
        name(en, pt, "item", "dried_meat", "Dried Meat", "Carne seca");
        en.put("item." + NS + ".dried_meat.tier", "%s %s");
        pt.put("item." + NS + ".dried_meat.tier", "%s %s");
        String[][] meats = {
                {"herbivore", "Herbivore", "de herbívoro"}, {"carnivore", "Carnivore", "de carnívoro"},
                {"prime", "Prime", "nobre"}, {"bird", "Bird", "de ave"}, {"reptile", "Reptile", "de réptil"},
                {"game", "Game", "de caça"}, {"marine", "Marine", "marinha"}};
        for (String[] meat : meats) {
            name(en, pt, "item", "raw_" + meat[0] + "_meat", "Raw " + meat[1] + " Meat", "Carne " + meat[2] + " crua");
            name(en, pt, "item", "cooked_" + meat[0] + "_meat", "Cooked " + meat[1] + " Meat", "Carne " + meat[2] + " cozida");
        }
        en.put("primitive." + NS + ".furnace_replaced", "Furnaces are cold here. Cook food on a Stone Fire; smelt in a Primitive Forge.");
        pt.put("primitive." + NS + ".furnace_replaced", "Fornalhas não funcionam aqui. Cozinhe na Fogueira de pedras; funda na Forja primitiva.");
    }

    private static void name(Map<String, String> en, Map<String, String> pt, String kind, String id, String english, String portuguese) {
        en.put(kind + "." + NS + "." + id, english);
        pt.put(kind + "." + NS + "." + id, portuguese);
    }
}
