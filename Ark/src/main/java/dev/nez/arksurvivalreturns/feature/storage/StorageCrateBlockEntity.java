package dev.nez.arksurvivalreturns.feature.storage;

import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A fixed 27-slot crate. It holds items and nothing else: no ticking, no upgrades, no automation. */
public final class StorageCrateBlockEntity extends BlockEntity implements Container {
    public static final int SIZE = 27;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public StorageCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.STORAGE_CRATE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override public int getContainerSize() { return SIZE; }

    @Override public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        return items.get(slot).isEmpty() ? ItemStack.EMPTY : items.get(slot).split(0);
    }

    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }

    @Override public void clearContent() { items.clear(); }

    @Override protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("Items"), items);
    }

    @Override protected void loadAdditional(ValueInput input) {
        input.child("Items").ifPresent(child -> {
            items.clear();
            ContainerHelper.loadAllItems(child, items);
        });
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) Block.popResource(server, pos, stack);
        }
        items.clear();
    }
}
