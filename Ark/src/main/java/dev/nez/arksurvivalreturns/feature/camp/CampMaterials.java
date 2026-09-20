package dev.nez.arksurvivalreturns.feature.camp;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Primitive material tiers. Flint repairs both the knife and the spear. */
public final class CampMaterials {
    public static final TagKey<Item> FLINT_REPAIR = TagKey.create(Registries.ITEM, ArkSurvivalReturns.id("camp/flint_materials"));
    /** Short-lived but fast: the knife is a tool of opportunity, not a metal replacement. */
    public static final ToolMaterial FLINT = new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 96, 2.5f, 0.5f, 8, FLINT_REPAIR);

    /** Extra reach at the cost of swing speed; the spear trades damage rate for safety. */
    public static Item.Properties spear(Item.Properties properties) {
        var modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 3.5, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ArkSurvivalReturns.id("spear_reach"), 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        return properties.stacksTo(1).durability(140).repairable(FLINT_REPAIR).attributes(modifiers);
    }

    private CampMaterials() {}
}
