package dev.nez.arksurvivalreturns.feature.forge;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Fixed processing recipes for the primitive stations. Keeping them in code avoids a recipe type for a
 * handful of conversions while the inputs and outputs stay ordinary vanilla items a data pack can tag.
 */
public final class ForgeRecipes {
    /** The primitive forge: raw ore and stone become their furnace product without fuel. */
    public static @Nullable ItemStack forgeResult(ItemStack input) {
        if (input.isEmpty()) return null;
        if (input.is(Items.RAW_IRON)) return new ItemStack(Items.IRON_INGOT);
        if (input.is(Items.RAW_COPPER)) return new ItemStack(Items.COPPER_INGOT);
        if (input.is(Items.RAW_GOLD)) return new ItemStack(Items.GOLD_INGOT);
        if (input.is(Items.SAND) || input.is(Items.RED_SAND)) return new ItemStack(Items.GLASS);
        if (input.is(Items.COBBLESTONE)) return new ItemStack(Items.STONE);
        if (input.is(Items.COBBLED_DEEPSLATE)) return new ItemStack(Items.DEEPSLATE);
        if (input.is(Items.CLAY_BALL)) return new ItemStack(Items.BRICK);
        return null;
    }

    /** The charcoal kiln reduces logs to charcoal with no fuel of its own. */
    public static @Nullable ItemStack kilnResult(ItemStack input) {
        if (input.isEmpty()) return null;
        if (input.is(ItemTags.LOGS) || input.is(ItemTags.PLANKS) || input.is(ItemTags.SAPLINGS)) {
            return new ItemStack(Items.CHARCOAL);
        }
        return null;
    }

    private ForgeRecipes() {}
}
