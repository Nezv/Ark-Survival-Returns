package dev.nez.arksurvivalreturns.feature.station;

import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/** A distinct screen identity with vanilla chest inventory, shift-click and validity rules. */
public final class StorageCrateMenu extends ChestMenu {
    public StorageCrateMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(StorageCrateBlockEntity.SIZE));
    }

    public StorageCrateMenu(int id, Inventory inventory, Container crate) {
        super(ModContent.STORAGE_CRATE_MENU.get(), id, inventory, crate, 3);
    }
}
