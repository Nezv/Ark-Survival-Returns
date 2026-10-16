package dev.nez.arksurvivalreturns.feature.station.mixin;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The grid a result slot crafts from: the inventory's is 2x2. */
@Mixin(ResultSlot.class)
public interface ResultSlotAccessor {
    @Accessor("craftSlots")
    CraftingContainer ark$craftSlots();
}
