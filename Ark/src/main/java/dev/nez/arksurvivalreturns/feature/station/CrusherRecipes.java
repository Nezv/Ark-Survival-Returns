package dev.nez.arksurvivalreturns.feature.station;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the crusher grinds: stone down to gravel and sand, ores into doubled raw metal, and the usual
 * powders (bone meal, blaze powder, sugar, dyes from flowers, string from wool). The first matching
 * entry wins, so specific items come before tags.
 */
public final class CrusherRecipes {
    public record Recipe(Predicate<ItemStack> input, Item output, int count, int ticks) {}

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    private static Recipe of(Item input, Item output, int count, int ticks) {
        return new Recipe(stack -> stack.is(input), output, count, ticks);
    }

    private static Recipe of(TagKey<Item> input, Item output, int count, int ticks) {
        return new Recipe(stack -> stack.is(input), output, count, ticks);
    }

    public static final List<Recipe> RECIPES = List.of(
            // Ores: a crushed ore yields twice the raw metal of a pickaxe.
            of(c("ores/iron"), Items.RAW_IRON, 2, 160),
            of(c("ores/gold"), Items.RAW_GOLD, 2, 160),
            of(c("ores/copper"), Items.RAW_COPPER, 6, 160),
            of(c("ores/coal"), Items.COAL, 2, 120),
            of(c("ores/redstone"), Items.REDSTONE, 6, 160),
            of(c("ores/lapis"), Items.LAPIS_LAZULI, 8, 160),
            of(c("ores/diamond"), Items.DIAMOND, 2, 240),
            of(c("ores/emerald"), Items.EMERALD, 2, 240),
            of(c("ores/quartz"), Items.QUARTZ, 3, 160),
            of(Items.RAW_IRON_BLOCK, Items.RAW_IRON, 10, 240),
            of(Items.RAW_GOLD_BLOCK, Items.RAW_GOLD, 10, 240),
            of(Items.RAW_COPPER_BLOCK, Items.RAW_COPPER, 10, 240),
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
            of(Items.BLAZE_ROD, Items.BLAZE_POWDER, 3, 60),
            of(Items.SUGAR_CANE, Items.SUGAR, 2, 40),
            of(Items.FLINT, Items.GUNPOWDER, 1, 120),
            of(ItemTags.WOOL, Items.STRING, 4, 60),
            of(Items.WHEAT, Items.WHEAT_SEEDS, 2, 40),
            of(Items.COCOA_BEANS, Items.BROWN_DYE, 2, 40),
            of(Items.POPPY, Items.RED_DYE, 2, 40),
            of(Items.DANDELION, Items.YELLOW_DYE, 2, 40),
            of(Items.CORNFLOWER, Items.BLUE_DYE, 2, 40),
            of(Items.LILY_OF_THE_VALLEY, Items.WHITE_DYE, 2, 40));

    public static Recipe find(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Recipe recipe : RECIPES) if (recipe.input().test(stack)) return recipe;
        return null;
    }

    private CrusherRecipes() {}
}
