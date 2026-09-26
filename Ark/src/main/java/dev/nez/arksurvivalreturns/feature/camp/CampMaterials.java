package dev.nez.arksurvivalreturns.feature.camp;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/** Primitive material tiers. Flint repairs the knife; the keratin tier lives in PrimitiveContent. */
public final class CampMaterials {
    public static final TagKey<Item> FLINT_REPAIR = TagKey.create(Registries.ITEM, ArkSurvivalReturns.id("camp/flint_materials"));
    /** Short-lived but fast: the knife is a tool of opportunity, not a metal replacement. */
    public static final ToolMaterial FLINT = new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 96, 2.5f, 0.5f, 8, FLINT_REPAIR);

    private CampMaterials() {}
}
