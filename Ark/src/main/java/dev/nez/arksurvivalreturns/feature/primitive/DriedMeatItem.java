package dev.nez.arksurvivalreturns.feature.primitive;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

/**
 * Dried meat, graded I to III by how long it hung on a drying rack.
 *
 * <p>The tier is stored in the stack's custom data, and the stack's food and consume components are
 * rewritten with it, so stacks of different tiers never merge and each tier eats differently:
 * I is plain preserved meat, II adds haste, III adds strength and a short regeneration.
 */
public final class DriedMeatItem extends Item {
    public static final String TIER_TAG = "ark_dryness";
    public static final int MAX_TIER = 3;
    private static final String[] NUMERALS = {"I", "II", "III"};

    public DriedMeatItem(Properties properties) {
        super(properties);
    }

    public static int tier(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        int tier = data == null ? 1 : data.copyTag().getIntOr(TIER_TAG, 1);
        return Math.clamp(tier, 1, MAX_TIER);
    }

    /** Sets the tier and the matching food values; the stack keeps its count and other data. */
    public static ItemStack setTier(ItemStack stack, int tier) {
        int clamped = Math.clamp(tier, 1, MAX_TIER);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TIER_TAG, clamped));
        stack.set(DataComponents.FOOD, food(clamped));
        stack.set(DataComponents.CONSUMABLE, consumable(clamped));
        return stack;
    }

    public static ItemStack withTier(int tier) {
        return setTier(new ItemStack(PrimitiveContent.DRIED_MEAT.get()), tier);
    }

    public static FoodProperties food(int tier) {
        return switch (tier) {
            case 3 -> new FoodProperties.Builder().nutrition(8).saturationModifier(1.0f).build();
            case 2 -> new FoodProperties.Builder().nutrition(7).saturationModifier(0.8f).build();
            default -> new FoodProperties.Builder().nutrition(5).saturationModifier(0.6f).build();
        };
    }

    public static Consumable consumable(int tier) {
        var builder = Consumable.builder().consumeSeconds(1.6f).animation(ItemUseAnimation.EAT);
        if (tier == 2) {
            builder.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HASTE, 1800, 0)));
        } else if (tier >= 3) {
            builder.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                    new MobEffectInstance(MobEffects.STRENGTH, 2400, 0),
                    new MobEffectInstance(MobEffects.REGENERATION, 200, 0))));
        }
        return builder.build();
    }

    @Override public Component getName(ItemStack stack) {
        return Component.translatable("item.arksurvivalreturns.dried_meat.tier", super.getName(stack), NUMERALS[tier(stack) - 1]);
    }
}
