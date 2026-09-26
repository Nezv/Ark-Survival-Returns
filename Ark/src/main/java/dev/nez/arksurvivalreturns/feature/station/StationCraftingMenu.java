package dev.nez.arksurvivalreturns.feature.station;

import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The vanilla 3x3 crafting grid bound to an Ark station instead of the crafting table. Each station owns a
 * share of the recipes: the result slot stays empty for anything the station does not make, so the Working
 * Station leaves medicine to the Medicine Bench and the bench makes nothing else. The client opens the
 * ordinary crafting screen; the filter runs on the server, which owns the result slot.
 */
public final class StationCraftingMenu extends CraftingMenu {
    private final ContainerLevelAccess access;
    private final Block station;
    private final Predicate<ItemStack> makes;

    public StationCraftingMenu(int containerId, Inventory inventory, ContainerLevelAccess access, Block station,
            Predicate<ItemStack> makes) {
        super(containerId, inventory, access);
        this.access = access;
        this.station = station;
        this.makes = makes;
    }

    @Override public void slotsChanged(Container container) {
        super.slotsChanged(container);
        filter();
    }

    /** Also catches results placed by the recipe book, which bypasses slotsChanged. */
    @Override public void broadcastChanges() {
        filter();
        super.broadcastChanges();
    }

    private void filter() {
        ItemStack result = resultSlots.getItem(0);
        if (!result.isEmpty() && !makes.test(result)) resultSlots.setItem(0, ItemStack.EMPTY);
    }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, station);
    }
}
