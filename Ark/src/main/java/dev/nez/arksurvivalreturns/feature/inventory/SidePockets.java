package dev.nez.arksurvivalreturns.feature.inventory;

import java.util.Collections;
import java.util.List;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The six side pockets: a storage column on each side of the inventory's three rows, so the storage is eleven columns
 * wide (the hotbar stays nine). They hold items like the rest of the inventory: a pickup or a craft that finds the
 * inventory full goes into them, crafting pays from them, they weigh, and they drop on death unless the inventory is
 * kept. Order: left column top to bottom, then the right one.
 */
public final class SidePockets extends ItemStacksResourceHandler {
    public static final int SIZE = 6;
    private final Player owner;

    public SidePockets(IAttachmentHolder holder) {
        super(SIZE);
        owner = holder instanceof Player player ? player : null;
    }

    public static SidePockets of(Player player) { return player.getData(InventoryContent.POCKETS); }

    @Override protected void onContentsChanged(int index, ItemStack previous) {
        if (owner instanceof ServerPlayer player) MassService.markDirty(player);
    }

    /** The pocket stacks themselves (not copies), in slot order. */
    public List<ItemStack> stacks() { return Collections.unmodifiableList(stacks); }

    /** Puts as much of the stack as fits into the pockets, onto matching stacks first; the rest stays in the stack. */
    public void stow(ItemStack stack) {
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < SIZE && !stack.isEmpty(); i++) {
                ItemStack held = stacks.get(i), previous = held.copy();
                if (pass == 0 && !held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) {
                    int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                    if (moved <= 0) continue;
                    held.grow(moved); stack.shrink(moved);
                } else if (pass == 1 && held.isEmpty()) {
                    stacks.set(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
                } else continue;
                onContentsChanged(i, previous);
            }
        }
    }

    /** Takes some of one pocket's stack away (a craft paying from it). */
    public void take(int index, int amount) {
        ItemStack held = stacks.get(index), previous = held.copy();
        held.shrink(amount);
        if (held.isEmpty()) stacks.set(index, ItemStack.EMPTY);
        onContentsChanged(index, previous);
    }

    /** Death without a kept inventory: everything in the pockets falls with the rest. */
    public void dropAll(Player player) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack held = stacks.get(i);
            if (held.isEmpty()) continue;
            stacks.set(i, ItemStack.EMPTY);
            player.drop(held, true, false);
            onContentsChanged(i, held);
        }
    }
}
