package dev.nez.arksurvivalreturns.feature.farm;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Data-pack tags for the homestead stations, so feeding and drying stay tunable without code. */
public final class FarmTags {
    /** Food a trough accepts. */
    public static final TagKey<Item> TROUGH_FOOD =
            TagKey.create(Registries.ITEM, ArkSurvivalReturns.id("farm/trough_food"));
    /** Raw food the drying rack converts into a dried ration. */
    public static final TagKey<Item> DRYING_INPUTS =
            TagKey.create(Registries.ITEM, ArkSurvivalReturns.id("farm/drying_inputs"));
    /** Ground a planted Ark berry bush may sit on. */
    public static final TagKey<Block> PLANTABLE_ON =
            TagKey.create(Registries.BLOCK, ArkSurvivalReturns.id("farm/plantable_on"));

    private FarmTags() {}
}
