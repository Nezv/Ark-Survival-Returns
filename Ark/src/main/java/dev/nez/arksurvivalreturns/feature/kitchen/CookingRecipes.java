package dev.nez.arksurvivalreturns.feature.kitchen;

import java.util.List;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The fixed primitive meals. All four input slots must be filled and every recipe consumes exactly one
 * item per slot, so a pot never partially consumes a stack and never needs a recipe type.
 */
public final class CookingRecipes {
    public static final int SLOTS = 4;

    public static @Nullable ItemStack match(List<ItemStack> slots) {
        if (slots.size() < SLOTS) return null;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (slots.get(slot).isEmpty()) return null;
        }
        if (count(slots, stack -> stack.is(ModContent.DRIED_RATION.get())) == 2
                && count(slots, stack -> stack.is(ItemTags.MEAT)) == 1
                && count(slots, stack -> stack.is(Items.CARROT)) == 1) {
            return new ItemStack(ModContent.HEARTY_STEW.get());
        }
        if (count(slots, stack -> stack.is(ModContent.DRIED_RATION.get())) == 2
                && count(slots, CookingRecipes::isArkBerry) == 2) {
            return new ItemStack(ModContent.TRAIL_MIX.get());
        }
        return null;
    }

    private static boolean isArkBerry(ItemStack stack) {
        for (var berry : ModContent.BERRIES.values()) {
            if (stack.is(berry.get())) return true;
        }
        return false;
    }

    private static int count(List<ItemStack> slots, java.util.function.Predicate<ItemStack> predicate) {
        int total = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (predicate.test(slots.get(slot))) total++;
        }
        return total;
    }

    /** Convenience for documentation and future data-driven recipes. */
    public static List<Item> knownMeals() {
        return List.of(ModContent.HEARTY_STEW.get(), ModContent.TRAIL_MIX.get());
    }

    private CookingRecipes() {}
}
