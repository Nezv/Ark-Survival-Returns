package dev.nez.arksurvivalreturns.feature.drop;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One centred row of 3 to 9 slots over the player's inventory. Loot is taken out, never put in. */
public final class LootCrateMenu extends AbstractContainerMenu {
    public static final int ROW_Y = 18, INVENTORY_Y = 49, HOTBAR_Y = 107;
    private final Container crate;
    private final int size;

    public LootCrateMenu(int id, Inventory inventory, int size) {
        this(id, inventory, new SimpleContainer(size), size);
    }

    public LootCrateMenu(int id, Inventory inventory, Container crate, int size) {
        super(DropContent.LOOT_CRATE_MENU.get(), id);
        checkContainerSize(crate, size);
        this.crate = crate;
        this.size = size;
        crate.startOpen(inventory.player);
        for (int i = 0; i < size; i++) {
            addSlot(new Slot(crate, i, rowX(size) + i * 18, ROW_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, HOTBAR_Y));
    }

    /** Left edge of the first slot's item, so the row sits in the middle of the nine-wide panel. */
    public static int rowX(int size) { return 8 + (9 - size) * 9; }

    public int crateSlots() { return size; }

    @Override public boolean stillValid(Player player) { return crate.stillValid(player); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index >= size || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        if (!moveItemStackTo(stack, size, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        crate.stopOpen(player);
    }
}
