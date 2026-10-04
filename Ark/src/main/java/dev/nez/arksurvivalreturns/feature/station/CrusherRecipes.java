package dev.nez.arksurvivalreturns.feature.station;

import java.util.List;
import java.util.function.Predicate;
import dev.nez.arksurvivalreturns.feature.sulphur.SulphurContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the crusher grinds: stone down to gravel and sand, the usual powders (bone meal,
 * sugar, dyes from flowers, string from wool), and gunpowder from coal and sulphur. The first matching
 * entry wins, so specific items come before tags.
 *
 * <p>Every recipe needs its primary ingredient in the hopper slot; a two-ingredient recipe also needs
 * its secondary ingredient in the second input slot ({@link CrusherBlockEntity#INPUT_B}). No flint
 * recipe exists on purpose: this crusher never turns flint into gunpowder.
 */
public final class CrusherRecipes {
    public record Recipe(Predicate<ItemStack> primary, int primaryCount,
            Predicate<ItemStack> secondary, int secondaryCount, Item output, int count, int ticks) {
        public boolean twoIngredient() { return secondary != null; }
    }

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    private static Recipe of(Item input, Item output, int count, int ticks) {
        return new Recipe(stack -> stack.is(input), 1, null, 0, output, count, ticks);
    }

    private static Recipe of(TagKey<Item> input, Item output, int count, int ticks) {
        return new Recipe(stack -> stack.is(input), 1, null, 0, output, count, ticks);
    }

    /** The secondary is a predicate so mod items resolve lazily, not while this list is built. */
    private static Recipe of2(Item primary, int primaryCount, Predicate<ItemStack> secondary, int secondaryCount, Item output, int count, int ticks) {
        return new Recipe(stack -> stack.is(primary), primaryCount, secondary, secondaryCount, output, count, ticks);
    }

    public static final List<Recipe> RECIPES = List.of(
            // Stone down the chain: stone -> cobblestone -> gravel -> sand.
            of(Items.STONE, Items.COBBLESTONE, 1, 60),
            of(Items.COBBLESTONE, Items.GRAVEL, 1, 80),
            of(Items.COBBLED_DEEPSLATE, Items.GRAVEL, 1, 100),
            of(Items.GRAVEL, Items.SAND, 1, 80),
            of(Items.SANDSTONE, Items.SAND, 4, 80),
            of(Items.RED_SANDSTONE, Items.RED_SAND, 4, 80),
            of(Items.TERRACOTTA, Items.RED_SAND, 1, 80),
            of(Items.GLOWSTONE, Items.GLOWSTONE_DUST, 4, 60),
            of(Items.CLAY, Items.CLAY_BALL, 4, 40),
            // Powders and fibres.
            of(Items.BONE, Items.BONE_MEAL, 5, 40),
            of(Items.BONE_BLOCK, Items.BONE_MEAL, 12, 100),
            of(Items.SUGAR_CANE, Items.SUGAR, 2, 40),
            of(ItemTags.WOOL, Items.STRING, 4, 60),
            of(Items.WHEAT, Items.WHEAT_SEEDS, 2, 40),
            of(Items.COCOA_BEANS, Items.BROWN_DYE, 2, 40),
            of(Items.POPPY, Items.RED_DYE, 2, 40),
            of(Items.DANDELION, Items.YELLOW_DYE, 2, 40),
            of(Items.CORNFLOWER, Items.BLUE_DYE, 2, 40),
            of(Items.LILY_OF_THE_VALLEY, Items.WHITE_DYE, 2, 40),
            // Bronze Age gunpowder: coal in the hopper, sulphur in the second slot.
            of2(Items.COAL, 2, stack -> stack.is(SulphurContent.SULPHUR.get()), 2, Items.GUNPOWDER, 2, 100));

    /** Backward-compatible single-stack lookup: only ever matches a recipe with no secondary ingredient. */
    public static Recipe find(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Recipe recipe : RECIPES) if (!recipe.twoIngredient() && recipe.primary().test(stack)) return recipe;
        return null;
    }

    /** True when this stack could ever fill the primary (hopper) slot of some recipe. */
    public static boolean acceptsPrimary(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Recipe recipe : RECIPES) if (recipe.primary().test(stack)) return true;
        return false;
    }

    /** True when this stack could ever fill the second input slot of some two-ingredient recipe. */
    public static boolean acceptsSecondary(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Recipe recipe : RECIPES) if (recipe.twoIngredient() && recipe.secondary().test(stack)) return true;
        return false;
    }

    /** Either slot: used to decide whether a hand click should be consumed by the crusher at all. */
    public static boolean accepts(ItemStack stack) {
        return acceptsPrimary(stack) || acceptsSecondary(stack);
    }

    /** The real tick-time match: the primary slot's content plus, for two-ingredient recipes, the second slot's. */
    public static Recipe match(ItemStack primaryStack, ItemStack secondaryStack) {
        if (primaryStack.isEmpty()) return null;
        for (Recipe recipe : RECIPES) {
            if (!recipe.primary().test(primaryStack) || primaryStack.getCount() < recipe.primaryCount()) continue;
            if (recipe.twoIngredient() && (secondaryStack.isEmpty() || !recipe.secondary().test(secondaryStack)
                    || secondaryStack.getCount() < recipe.secondaryCount())) continue;
            return recipe;
        }
        return null;
    }

    private CrusherRecipes() {}
}
