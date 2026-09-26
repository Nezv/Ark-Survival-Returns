package dev.nez.arksurvivalreturns.feature.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 27 slots, the vanilla chest menu, loot-table support; one inventory per crate, never merged. */
public final class StorageCrateBlockEntity extends RandomizableContainerBlockEntity {
    public static final int SIZE = 27;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public StorageCrateBlockEntity(BlockPos pos, BlockState state) {
        super(StationContent.STORAGE_CRATE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override public int getContainerSize() { return SIZE; }

    @Override protected NonNullList<ItemStack> getItems() { return items; }

    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override protected Component getDefaultName() {
        return Component.translatable("block.arksurvivalreturns.storage_crate");
    }

    @Override protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!trySaveLootTable(output)) ContainerHelper.saveAllItems(output, items);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(input)) ContainerHelper.loadAllItems(input, items);
    }

    /** Contents spill when the crate is removed; dropping empties each stack, so a second pass drops nothing. */
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, this);
        super.preRemoveSideEffects(pos, state);
    }
}
