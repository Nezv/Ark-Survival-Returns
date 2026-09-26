package dev.nez.arksurvivalreturns.feature.kitchen;

import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Four ingredient slots, one meal slot, the player inventory, and the cooking progress data. */
public final class CookingPotMenu extends AbstractContainerMenu {
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX = 1;

    private final Container pot;
    private final ContainerData data;

    public CookingPotMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModContent.COOKING_POT_MENU.get(), containerId);
        var blockEntity = inventory.player.level().getBlockEntity(pos);
        this.pot = blockEntity instanceof CookingPotBlockEntity entity ? entity
                : new SimpleContainer(CookingPotBlockEntity.SIZE);
        this.data = blockEntity instanceof CookingPotBlockEntity entity ? entity
                : new SimpleContainerData(3);
        layout(inventory);
        addDataSlots(data);
    }

    public boolean isHeated() { return data.get(2) != 0; }

    private void layout(Inventory inventory) {
        for (int slot = 0; slot < CookingPotBlockEntity.INPUT_SLOTS; slot++) {
            addSlot(new Slot(pot, slot, 62 + (slot % 2) * 18, 17 + (slot / 2) * 18));
        }
        addSlot(new Slot(pot, CookingPotBlockEntity.OUTPUT_SLOT, 116, 26) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
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
        if (index < CookingPotBlockEntity.SIZE) {
            if (!moveItemStackTo(stack, CookingPotBlockEntity.SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, CookingPotBlockEntity.INPUT_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override public boolean stillValid(Player player) {
        return pot.stillValid(player);
    }
}
