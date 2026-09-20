package dev.nez.arksurvivalreturns.feature.mass;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Item tags the carried-mass model resolves against, so a data pack can retune every category
 * without a code change. Items in no category fall back to a stack-size heuristic, so an unknown
 * modded item is never free.
 */
public final class MassTags {
    /** 0.05 units each: string, feathers, plant fiber and other near-weightless goods. */
    public static final TagKey<Item> LIGHT = tag("mass/light");
    /** 0.1 units each: berries, seeds and other small loose goods. */
    public static final TagKey<Item> BULK = tag("mass/bulk");
    /** 0.2 units each: prepared food. */
    public static final TagKey<Item> FOOD = tag("mass/food");
    /** 1 unit each: non-block goods that count as a full unit, such as boats and minecarts. */
    public static final TagKey<Item> UNIT = tag("mass/unit");
    /** 2 units each: ores, raw materials and ingots. */
    public static final TagKey<Item> ORE = tag("mass/ore");
    /** 2 units each: tools and weapons. */
    public static final TagKey<Item> TOOL = tag("mass/tool");
    /** 3 units each: equipped armor pieces. */
    public static final TagKey<Item> ARMOR = tag("mass/armor");
    /** Containers the model cannot inspect; counted at the configured flat mass instead. */
    public static final TagKey<Item> UNKNOWN_CONTAINER = tag("mass/unknown_container");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ArkSurvivalReturns.id(path));
    }

    private MassTags() {}
}
