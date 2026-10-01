package dev.nez.arksurvivalreturns.feature.tech.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Inventory.add can return false after a partial transfer; the pickup post event must still fire. */
@Mixin(ItemEntity.class)
abstract class PartialPickupMixin {
    @Redirect(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean ark$reportTransferredItems(Inventory inventory, ItemStack stack) {
        int before = stack.getCount();
        boolean added = inventory.add(stack);
        return added || stack.getCount() < before;
    }
}
