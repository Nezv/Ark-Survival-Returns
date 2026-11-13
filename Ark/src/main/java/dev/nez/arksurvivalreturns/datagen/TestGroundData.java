package dev.nez.arksurvivalreturns.datagen;

import java.util.Map;
import java.util.function.BiConsumer;

/** The Specimen Post's block, item and names, and the Testing Ground world type's name. */
final class TestGroundData {
    private static final String NS = "arksurvivalreturns";
    private static final String ASSETS = "assets/" + NS + "/";

    static void generate(BiConsumer<String, Object> put) {
        // A structure-block face: the post reads as a development block, not part of the survival game.
        put.accept(ASSETS + "models/block/specimen_post", Map.of("parent", "minecraft:block/cube_bottom_top",
                "textures", Map.of("side", "minecraft:block/structure_block_data", "top", "minecraft:block/structure_block",
                        "bottom", "minecraft:block/structure_block")));
        put.accept(ASSETS + "blockstates/specimen_post", Map.of("variants", Map.of("", Map.of("model", NS + ":block/specimen_post"))));
        put.accept(ASSETS + "models/item/specimen_post", Map.of("parent", NS + ":block/specimen_post"));
        put.accept(ASSETS + "items/specimen_post", Map.of("model", Map.of("type", "minecraft:model", "model", NS + ":item/specimen_post")));
    }

    static void lang(Map<String, String> en) {
        en.put("block." + NS + ".specimen_post", "Specimen Post");
        en.put("generator." + NS + ".testing_ground", "Ark Testing Ground");
        en.put("biome." + NS + ".testing_ground", "Testing Ground");
    }

    private TestGroundData() {}
}
