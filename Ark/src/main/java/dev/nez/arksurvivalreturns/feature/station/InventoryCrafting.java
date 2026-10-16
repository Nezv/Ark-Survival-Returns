package dev.nez.arksurvivalreturns.feature.station;

import dev.nez.arksurvivalreturns.feature.station.mixin.ResultSlotAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;

/**
 * The inventory crafts like a bench: its 2x2 grid is gone, and the Craft button in its place opens the inventory's own
 * workstation graph (design/workstations/inventory.json), carried by the player instead of bound to a block. It makes
 * the field crafts and, until their benches are built, the small recipes of the benches without a screen.
 */
public final class InventoryCrafting {
    public static final String STATION = "arksurvivalreturns:inventory";

    /** The 2x2 grid of the inventory screens (vanilla, creative and the Curios one) and its result slot. */
    public static boolean handGrid(Slot slot) {
        CraftingContainer grid = slot instanceof ResultSlot result ? ((ResultSlotAccessor) result).ark$craftSlots()
                : slot.container instanceof CraftingContainer container ? container : null;
        return grid != null && grid.getWidth() == 2 && grid.getHeight() == 2;
    }

    public static void open(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || WorkstationCatalog.get(STATION) == null) return;
        var at = player.blockPosition();
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new WorkstationMenu(id, inventory, at, STATION),
                Component.literal("Inventory")), data -> { data.writeBlockPos(at); data.writeUtf(STATION, 128); });
    }

    private InventoryCrafting() {}
}
