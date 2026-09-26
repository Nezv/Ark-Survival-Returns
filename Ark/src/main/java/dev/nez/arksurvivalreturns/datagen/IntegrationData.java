package dev.nez.arksurvivalreturns.datagen;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Data for optional integrations (config/integrations.json). Everything here is inert when the other mod
 * is absent: Curios ignores unknown data folders, and tag entries for other mods are optional.
 */
final class IntegrationData {
    private static final String NS = "arksurvivalreturns";

    static void generate(BiConsumer<String, Object> put) {
        curios(put);
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
