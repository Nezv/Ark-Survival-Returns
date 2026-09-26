package dev.nez.arksurvivalreturns.feature.station;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.level.block.state.BlockState;

/** The vanilla smithing menu, valid at the Ark smithing table instead of the vanilla one. */
public final class StationSmithingMenu extends SmithingMenu {
    public StationSmithingMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(containerId, inventory, access);
    }

    @Override protected boolean isValidBlock(BlockState state) {
        return state.is(StationContent.SMITHING_TABLE.get());
    }
}
