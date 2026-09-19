package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Item tags the taming profiles resolve against.
 *
 * <p>Accepted food is expressed as tags so balance can be retuned by a data pack without a code change.
 * Preferred food is a single item per species (see {@link CreatureTamingProfile#preferred()}), because a
 * favourite is one specific food rather than a category.
 */
public final class TamingTags {
    /** Plant food for medium and large herbivores: the three ordinary berries, leaves, carrot, apple, wheat. */
    public static final TagKey<Item> PLANT_FOOD = tag("taming/plant_food");
    /** Small-herbivore plants: the three ordinary berries and leaves. */
    public static final TagKey<Item> SMALL_PLANT_FOOD = tag("taming/small_plant_food");
    /** High-quality plant food used as the favourite tier: carrot, apple, wheat. */
    public static final TagKey<Item> HIGH_QUALITY_PLANT_FOOD = tag("taming/high_quality_plant_food");
    /** Raw land meat. Cooked meat and rotten flesh are deliberately excluded. */
    public static final TagKey<Item> RAW_MEAT = tag("taming/raw_meat");
    /** Raw fish, the accepted and preferred food of flying creatures. */
    public static final TagKey<Item> FISH = tag("taming/fish");
    /** Sedatives. Never valid taming food: narcoberry must not advance progress. */
    public static final TagKey<Item> SEDATIVE = tag("taming/sedative");
    /** Everything a knock-out tame will eat from its inventory. */
    public static final TagKey<Item> KO_FOOD = tag("taming/knockout_food");

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ArkSurvivalReturns.id(path));
    }

    private TamingTags() {}
}
