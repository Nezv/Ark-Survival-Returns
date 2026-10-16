package dev.nez.arksurvivalreturns.feature.station.mixin;

import dev.nez.arksurvivalreturns.feature.inventory.SidePockets;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The side pockets extend the inventory: what it cannot take overflows into them, and they drop with it on death. */
@Mixin(Inventory.class)
abstract class InventoryPocketsMixin {
    @Shadow @Final public Player player;

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
    private void ark$overflow(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot != -1 || stack.isEmpty() || player.level().isClientSide()) return;
        int before = stack.getCount();
        SidePockets.of(player).stow(stack);
        if (stack.getCount() < before) cir.setReturnValue(true);
    }

    @Inject(method = "dropAll", at = @At("TAIL"))
    private void ark$dropPockets(CallbackInfo ci) {
        SidePockets.of(player).dropAll(player);
    }
}
