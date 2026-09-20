package dev.nez.arksurvivalreturns.feature.storage;

import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The crate's 27 slots and the player inventory, laid out like a single chest. */
public final class StorageCrateMenu extends AbstractContainerMenu {
    private final Container crate;

    public StorageCrateMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModContent.STORAGE_CRATE_MENU.get(), containerId);
        var blockEntity = inventory.player.level().getBlockEntity(pos);
        this.crate = blockEntity instanceof StorageCrateBlockEntity entity ? entity
                : new SimpleContainer(StorageCrateBlockEntity.SIZE);
        layout(inventory);
    }

    private void layout(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(crate, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < StorageCrateBlockEntity.SIZE) {
            if (!moveItemStackTo(stack, StorageCrateBlockEntity.SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, StorageCrateBlockEntity.SIZE, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override public boolean stillValid(Player player) {
        return crate.stillValid(player);
    }
}
