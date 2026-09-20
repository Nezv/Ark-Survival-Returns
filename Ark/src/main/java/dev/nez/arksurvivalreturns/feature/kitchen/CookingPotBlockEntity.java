package dev.nez.arksurvivalreturns.feature.kitchen;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Cooking pot: four ingredient slots and one meal slot. Every batch it checks its fixed recipe list and,
 * after the configured number of batches, consumes one item per slot and produces the meal. It ticks only
 * while loaded, so no absence ever finishes a batch.
 */
public final class CookingPotBlockEntity extends BlockEntity implements Container, ContainerData {
    public static final int INPUT_SLOTS = CookingRecipes.SLOTS;
    public static final int OUTPUT_SLOT = INPUT_SLOTS;
    public static final int SIZE = INPUT_SLOTS + 1;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int batch;
    private int progress;

    public CookingPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.COOKING_POT_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot) {
        if (level.isClientSide()) return;
        if (++pot.batch < Config.FARM_BATCH_TICKS.get()) return;
        pot.batch = 0;
        pot.advance();
    }

    /** One cooking batch; public so the headless suite can drive it deterministically. */
    public boolean advance() {
        if (!Config.KITCHEN_ENABLED.get()) return false;
        ItemStack result = CookingRecipes.match(items.subList(0, INPUT_SLOTS));
        if (result == null) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return false;
        }
        ItemStack output = items.get(OUTPUT_SLOT);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result)
                || output.getCount() + result.getCount() > output.getMaxStackSize())) {
            return false;
        }
        progress++;
        if (progress < Config.KITCHEN_COOK_BATCHES.get()) {
            setChanged();
            return true;
        }
        progress = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) items.get(slot).shrink(1);
        if (output.isEmpty()) items.set(OUTPUT_SLOT, result);
        else output.grow(result.getCount());
        setChanged();
        return true;
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

    @Override public ItemStack removeItemNoUpdate(int slot) { return items.get(slot).isEmpty() ? ItemStack.EMPTY : items.get(slot).split(0); }

    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot < INPUT_SLOTS; }

    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }

    @Override public void clearContent() { items.clear(); }

    @Override public int getCount() { return 2; }

    @Override public int get(int index) { return index == 0 ? progress : Config.KITCHEN_COOK_BATCHES.get(); }

    @Override public void set(int index, int value) {
        if (index == 0) progress = value;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("Items"), items);
        output.putInt("Progress", progress);
    }

    @Override protected void loadAdditional(ValueInput input) {
        input.child("Items").ifPresent(child -> {
            items.clear();
            ContainerHelper.loadAllItems(child, items);
        });
        progress = input.getIntOr("Progress", 0);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) Block.popResource(server, pos, stack);
        }
        items.clear();
    }
}
