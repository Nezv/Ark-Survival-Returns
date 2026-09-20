package dev.nez.arksurvivalreturns.feature.farm;

import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/** Maps the four Ark berries to their plantable bushes. */
public final class FarmCrops {
    public static @Nullable Block bushFor(Item item) {
        if (item == ModContent.BERRIES.get("tintoberry").get()) return ModContent.TINTOBERRY_BUSH.get();
        if (item == ModContent.BERRIES.get("amarberry").get()) return ModContent.AMARBERRY_BUSH.get();
        if (item == ModContent.BERRIES.get("azulberry").get()) return ModContent.AZULBERRY_BUSH.get();
        if (item == ModContent.BERRIES.get("narcoberry").get()) return ModContent.NARCOBERRY_BUSH.get();
        return null;
    }

    private FarmCrops() {}
}
