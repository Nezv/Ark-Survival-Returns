package dev.nez.arksurvivalreturns.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;

/**
 * State, loot and translation wiring for the supply drops authored by tools/build_drop_assets.py. A crate never
 * rolls more stacks than it has slots (3, 5, 7, 9), and each tier reaches one step further up the progression:
 * camp supplies, taming and hides, bronze, then iron, harness and powder.
 */
final class DropData {
    private static final String NS = "arksurvivalreturns";

    static void generate(BiConsumer<String, Object> put) {
        var variants = new LinkedHashMap<String, Object>();
        for (SupplyTier tier : SupplyTier.values())
            variants.put("tier=" + tier.id, Map.of("model", NS + ":block/drop/loot_crate_" + tier.id));
        put.accept("assets/" + NS + "/blockstates/loot_crate", Map.of("variants", variants));
        loot(put, SupplyTier.WHITE, 2, List.of(
                entry("plant_fiber", 8, 16, 6), entry("minecraft:flint", 2, 5, 5), entry("narcoberry", 6, 12, 5),
                entry("fiber_bandage", 1, 3, 5), entry("dried_ration", 2, 4, 4), entry("minecraft:torch", 4, 8, 4),
                entry("minecraft:arrow", 6, 12, 4), entry("tranquilizer_arrow", 3, 6, 3), entry("minecraft:leather", 2, 4, 3),
                entry("stone_hatchet", 1, 1, 1), entry("flint_knife", 1, 1, 1)));
        loot(put, SupplyTier.GREEN, 3, List.of(
                entry("narcotics", 3, 6, 5), entry("tranquilizer_arrow", 6, 12, 5), entry("herbal_bandage", 2, 4, 4),
                entry("minecraft:leather", 4, 8, 4), entry("healing_mixture", 1, 2, 3), entry("cooked_prime_meat", 2, 4, 3),
                entry("keratin", 3, 6, 3), entry("thick_pelt", 2, 4, 3), entry("minecraft:raw_copper", 4, 8, 3),
                entry("raw_tin", 3, 6, 3), entry("minecraft:bow", 1, 1, 1), entry("fire_starter", 1, 1, 1),
                entry("rock_pickaxe", 1, 1, 1), entry("minecraft:lead", 1, 2, 1)));
        loot(put, SupplyTier.BLUE, 5, List.of(
                entry("bronze_ingot", 3, 6, 5), entry("improved_tranquilizer_arrow", 4, 8, 4), entry("minecraft:copper_ingot", 4, 8, 3),
                entry("tin_ingot", 3, 6, 3), entry("healing_mixture", 2, 3, 3), entry("vitamins", 1, 2, 3),
                entry("hearty_stew", 1, 2, 3), entry("trail_mix", 2, 4, 3), entry("sulphur", 4, 8, 3),
                entry("minecraft:gunpowder", 3, 6, 3), entry("amber", 1, 2, 2), entry("pack_harness", 1, 1, 2),
                entry("bronze_pickaxe", 1, 1, 1), entry("bronze_axe", 1, 1, 1), entry("bronze_longsword", 1, 1, 1),
                entry("bronze_helmet", 1, 1, 1), entry("bronze_chestplate", 1, 1, 1), entry("bronze_leggings", 1, 1, 1),
                entry("bronze_boots", 1, 1, 1)));
        loot(put, SupplyTier.PURPLE, 7, List.of(
                entry("minecraft:iron_ingot", 4, 8, 5), entry("bronze_ingot", 6, 10, 4), entry("explosive_arrow", 3, 6, 4),
                entry("improved_tranquilizer_arrow", 8, 16, 4), entry("minecraft:gunpowder", 6, 12, 3), entry("healing_mixture", 3, 4, 3),
                entry("vitamins", 2, 3, 3), entry("hearty_stew", 2, 4, 3), entry("amber", 2, 4, 3),
                entry("reinforced_harness", 1, 1, 2), entry("minecraft:saddle", 1, 1, 2), entry("bronze_longsword", 1, 1, 2),
                entry("bronze_chestplate", 1, 1, 2), entry("bronze_leggings", 1, 1, 2), entry("keratin_chestplate", 1, 1, 1),
                entry("keratin_helmet", 1, 1, 1), entry("bronze_hammer", 1, 1, 1)));
    }

    /** One pool: between {@code least} stacks and as many as the crate has slots. */
    private static void loot(BiConsumer<String, Object> put, SupplyTier tier, int least, List<Object> entries) {
        put.accept("data/" + NS + "/loot_table/chests/supply_drop/" + tier.id, Map.of("type", "minecraft:chest", "pools", List.of(
                Map.of("rolls", Map.of("type", "minecraft:uniform", "min", least, "max", tier.slots), "entries", entries))));
    }

    private static Object entry(String name, int low, int high, int weight) {
        var functions = new ArrayList<Object>();
        if (high > 1) functions.add(Map.of("function", "minecraft:set_count",
                "count", Map.of("type", "minecraft:uniform", "min", low, "max", high)));
        var entry = new LinkedHashMap<String, Object>();
        entry.put("type", "minecraft:item");
        entry.put("name", name.contains(":") ? name : NS + ":" + name);
        entry.put("weight", weight);
        if (!functions.isEmpty()) entry.put("functions", functions);
        return entry;
    }

    static void messages(Map<String, String> en, Map<String, String> pt) {
        en.put("block." + NS + ".loot_crate", "Loot Crate");
        pt.put("block." + NS + ".loot_crate", "Caixa de Saque");
        en.put("entity." + NS + ".supply_drop", "Supply Drop");
        pt.put("entity." + NS + ".supply_drop", "Suprimentos");
        en.put("drop." + NS + ".incoming", "A %s supply drop is coming down at %s.");
        pt.put("drop." + NS + ".incoming", "Suprimentos (%s) descendo em %s.");
        en.put("drop." + NS + ".no_place", "No loaded ground near you for a supply drop.");
        pt.put("drop." + NS + ".no_place", "Não há terreno carregado por perto para os suprimentos.");
        var names = Map.of(SupplyTier.WHITE, List.of("white", "White", "branco", "branca"),
                SupplyTier.GREEN, List.of("green", "Green", "verde", "verde"),
                SupplyTier.BLUE, List.of("blue", "Blue", "azul", "azul"),
                SupplyTier.PURPLE, List.of("purple", "Purple", "roxo", "roxa"));
        for (SupplyTier tier : SupplyTier.values()) {
            var name = names.get(tier);
            en.put("drop." + NS + ".tier." + tier.id, name.get(0));
            pt.put("drop." + NS + ".tier." + tier.id, name.get(2));
            en.put("container." + NS + ".loot_crate." + tier.id, name.get(1) + " Loot Crate");
            pt.put("container." + NS + ".loot_crate." + tier.id, "Caixa de Saque " + name.get(3));
        }
    }

    private DropData() {}
}
