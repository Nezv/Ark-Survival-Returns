package dev.nez.arksurvivalreturns.feature.station.mixin;

import dev.nez.arksurvivalreturns.feature.station.StationContent;
import dev.nez.arksurvivalreturns.feature.station.StationCraftingMenu;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Station-only results (Mortar & Pestle, Medicine Bench) never appear in any other crafting grid. */
@Mixin(CraftingMenu.class)
abstract class StationOnlyResultsMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void ark$stationOnly(AbstractContainerMenu menu, ServerLevel level, Player player, CraftingContainer grid,
            ResultContainer result, @Nullable RecipeHolder<CraftingRecipe> hint, CallbackInfo ci) {
        ItemStack out = result.getItem(0);
        if (menu instanceof StationCraftingMenu || !(StationContent.mortar(out) || StationContent.medicine(out))) return;
        result.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        ((ServerPlayer) player).connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
    }
}
