package dev.nez.arksurvivalreturns.feature.station.mixin;

import dev.nez.arksurvivalreturns.feature.station.InventoryCrafting;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The inventory's 2x2 grid is gone: its slots are neither drawn nor clickable, and the Craft button takes their place. */
@Mixin(Slot.class)
abstract class HandGridSlotMixin {
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void ark$noHandGrid(CallbackInfoReturnable<Boolean> cir) {
        if (InventoryCrafting.handGrid((Slot) (Object) this)) cir.setReturnValue(false);
    }
}
