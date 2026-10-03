package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Surface habitat identity, independent of danger, species weights and player population targets. */
public record BiomeProfile(String biomeId, Type type, Climate climate, Moisture moisture,
                           boolean mountainous, boolean snowy, TreeCover treeCover) {
    public enum Type {
        OCEAN, RIVER, COAST, WETLAND, MUSHROOM, JUNGLE, TAIGA, FOREST, BADLANDS,
        SAVANNA, DESERT, SHRUBLAND, GRASSLAND, MOUNTAIN, TUNDRA, VOLCANIC,
        GEOTHERMAL, SKY_ISLAND, CAVE, UNKNOWN;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum TreeCover { WOODED, SCATTERED, OPEN }
    public boolean treeBiome() { return treeCover != TreeCover.OPEN; }

    public enum Climate { COLD, TEMPERATE, HOT }
    public enum Moisture { DRY, MODERATE, WET }

    // Shipped biomes whose tags omit their cover, or only describe their terrain. Exact IDs avoid
    // guessing another mod's ecology from its name. Datapack ecology/<type> tags override these.
    private static final Map<String, Type> EXCEPTIONS = Map.ofEntries(
            Map.entry("minecraft:cherry_grove", Type.FOREST),
            Map.entry("minecraft:meadow", Type.GRASSLAND),
            Map.entry("minecraft:windswept_forest", Type.FOREST),
            Map.entry("terralith:alpha_islands", Type.GRASSLAND),
            Map.entry("terralith:alpha_islands_winter", Type.TUNDRA),
            Map.entry("terralith:blooming_plateau", Type.GRASSLAND),
            Map.entry("terralith:hot_shrubland", Type.SHRUBLAND),
            Map.entry("terralith:cold_shrubland", Type.SHRUBLAND),
            Map.entry("terralith:rocky_shrubland", Type.SHRUBLAND),
            Map.entry("terralith:shrubland", Type.SHRUBLAND),
            Map.entry("terralith:lush_valley", Type.GRASSLAND),
            Map.entry("terralith:shield_clearing", Type.GRASSLAND),
            Map.entry("terralith:valley_clearing", Type.GRASSLAND),
            Map.entry("terralith:stony_spires", Type.MOUNTAIN),
            Map.entry("terralith:windswept_spires", Type.MOUNTAIN),
            Map.entry("terralith:warped_mesa", Type.BADLANDS),
            Map.entry("terralith:volcanic_crater", Type.VOLCANIC),
            Map.entry("terralith:volcanic_peaks", Type.VOLCANIC),
            Map.entry("terralith:yellowstone", Type.GEOTHERMAL));

    public static BiomeProfile classify(String id, Set<String> tags, float temperature, float downfall) {
        Type type = type(id, tags);
        boolean snowy = has(tags, "is_snowy");
        Climate climate = has(tags, "is_cold") || snowy || temperature < 0.3f ? Climate.COLD
                : has(tags, "is_hot") || temperature >= 1.0f ? Climate.HOT : Climate.TEMPERATE;
        Moisture moisture = has(tags, "is_dry") || downfall < 0.3f ? Moisture.DRY
                : has(tags, "is_wet") || downfall >= 0.8f ? Moisture.WET : Moisture.MODERATE;
        boolean mountains = has(tags, "is_mountain") || has(tags, "is_hill")
                || tags.contains("terralith:cliffs") || type == Type.MOUNTAIN || type == Type.VOLCANIC;
        return new BiomeProfile(id, type, climate, moisture, mountains, snowy, trees(id, type, tags));
    }

    private static TreeCover trees(String id, Type type, Set<String> tags) {
        // A biome may support trees without every point providing shelter. Actual canopy is checked separately.
        if (tags.contains("arksurvivalreturns:ecology/trees/open")) return TreeCover.OPEN;
        if (tags.contains("arksurvivalreturns:ecology/trees/wooded")) return TreeCover.WOODED;
        if (tags.contains("arksurvivalreturns:ecology/trees/scattered")) return TreeCover.SCATTERED;
        if (type == Type.FOREST || type == Type.TAIGA || type == Type.JUNGLE
                || id.equals("minecraft:mangrove_swamp")) return TreeCover.WOODED;
        if (type == Type.SAVANNA || type == Type.WETLAND || id.equals("minecraft:wooded_badlands"))
            return TreeCover.SCATTERED;
        return TreeCover.OPEN;
    }

    private static Type type(String id, Set<String> tags) {
        // If a pack assigns conflicting overrides, enum order gives a deterministic result.
        for (Type type : Type.values())
            if (tags.contains("arksurvivalreturns:ecology/" + type.id())) return type;
        Type exception = EXCEPTIONS.get(id);
        if (exception != null) return exception;
        if (has(tags, "is_cave") || has(tags, "is_underground")) return Type.CAVE;
        if (has(tags, "is_ocean")) return Type.OCEAN;
        if (has(tags, "is_river")) return Type.RIVER;
        if (has(tags, "is_beach") || has(tags, "is_stony_shores")) return Type.COAST;
        if (has(tags, "is_swamp")) return Type.WETLAND;
        if (tags.contains("terralith:skylands")) return Type.SKY_ISLAND;
        if (has(tags, "is_mushroom")) return Type.MUSHROOM;
        if (has(tags, "is_jungle")) return Type.JUNGLE;
        if (has(tags, "is_taiga")) return Type.TAIGA;
        if (has(tags, "is_badlands")) return Type.BADLANDS;
        if (has(tags, "is_savanna")) return Type.SAVANNA;
        if (has(tags, "is_desert")) return Type.DESERT;
        if (has(tags, "is_forest")) return Type.FOREST;
        if (has(tags, "is_plains")) return Type.GRASSLAND;
        if (has(tags, "is_mountain") || has(tags, "is_hill") || tags.contains("terralith:cliffs"))
            return Type.MOUNTAIN;
        if (has(tags, "is_snowy")) return Type.TUNDRA;
        return Type.UNKNOWN;
    }

    private static boolean has(Set<String> tags, String path) {
        return tags.contains("minecraft:" + path) || tags.contains("c:" + path);
    }
}
