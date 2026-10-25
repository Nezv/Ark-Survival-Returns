package dev.nez.arksurvivalreturns.datagen;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import dev.nez.arksurvivalreturns.feature.theme.ThemePolicy;

/**
 * Data for pack integrations (config/integrations.json). Biome replacements require the pack's
 * Terralith/Lithostitched stack; Curios ignores its data folders when absent.
 */
final class IntegrationData {
    private static final String NS = "arksurvivalreturns";

    static void generate(BiConsumer<String, Object> put) {
        curios(put);
        biomes(put);
    }

    private static void biomes(BiConsumer<String, Object> put) {
        var replacements = new java.util.TreeMap<>(ThemePolicy.BIOME_REPLACEMENTS);
        put.accept("data/" + NS + "/tags/worldgen/biome/theme/removed", Map.of(
                "replace", true, "values", replacements.keySet().stream()
                        .map(id -> Map.of("id", id, "required", false)).toList()));
        replacements.forEach((target, replacement) -> put.accept(
                "data/" + NS + "/lithostitched/biome_injector/remove_" + target.replace(':', '_'), Map.of(
                        "type", "lithostitched:replace_fully", "dimension", "minecraft:overworld",
                        "targets", target, "replacement", replacement)));
    }

    /**
     * I07: every Curios slot type, laid out around the player by the Ark fork of Curios (ArkLayout.java):
     * two head slots (crown, hat), necklace, back, body, two bracelets, hands, two rings, belt, legs (added by
     * Ark), two feet (socks, shoes), charm and the universal curio slot.
     */
    private static void curios(BiConsumer<String, Object> put) {
        String slots = "data/" + NS + "/curios/slots/";
        put.accept(slots + "head", Map.of("size", 2));
        put.accept(slots + "feet", Map.of("size", 2));
        put.accept(slots + "ring", Map.of("size", 2));
        put.accept(slots + "bracelet", Map.of("size", 2));
        put.accept(slots + "legs", Map.of("order", 185, "icon", NS + ":slot/empty_legs_slot", "validators", List.of("curios:tag")));
        put.accept("data/" + NS + "/curios/entities/ark_player", Map.of(
                "entities", List.of("#curios:player_like"),
                "slots", List.of("head", "necklace", "back", "body", "bracelet", "hands", "ring", "belt", "legs", "feet",
                        "charm", "curio")));
    }

    private IntegrationData() {}
}
