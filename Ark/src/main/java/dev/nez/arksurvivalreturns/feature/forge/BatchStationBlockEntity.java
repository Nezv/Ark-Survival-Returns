package dev.nez.arksurvivalreturns.feature.forge;

import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Shared behavior for the one-input, one-output processing stations: an input slot, an output slot and
 * a progress counter that advances on the shared batch cadence. Only loaded chunks tick, so nothing is
 * ever produced during an absence.
 */
public abstract class BatchStationBlockEntity extends BlockEntity implements Container {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int SIZE = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int batch;
    private int progress;

    protected BatchStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** What one input becomes after {@link #batches()} batches, or null when the input is rejected. */
    protected abstract @Nullable ItemStack result(ItemStack input);

    /** How many batches this station needs per item. */
    protected abstract int batches();

    public static void tick(Level level, BlockPos pos, BlockState state, BatchStationBlockEntity station) {
        if (level.isClientSide()) return;
        if (++station.batch < Config.FARM_BATCH_TICKS.get()) return;
        station.batch = 0;
        station.advance();
    }

    /** One processing batch; public so the headless suite can drive it deterministically. */
    public boolean advance() {
        if (!Config.FORGE_ENABLED.get()) return false;
        ItemStack input = items.get(INPUT);
        ItemStack result = input.isEmpty() ? null : result(input);
        if (result == null) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return false;
        }
        ItemStack output = items.get(OUTPUT);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result)
                || output.getCount() + result.getCount() > output.getMaxStackSize())) {
            return false;
        }
        progress++;
        if (progress < batches()) {
            setChanged();
            return true;
        }
        progress = 0;
        input.shrink(1);
        if (output.isEmpty()) items.set(OUTPUT, result);
        else output.grow(result.getCount());
        setChanged();
        return true;
    }

    public int progress() {
        return progress;
    }

    /** Merges a valid input into the input slot; returns the amount moved. */
    public int insert(ItemStack stack) {
        if (stack.isEmpty() || result(stack) == null) return 0;
        ItemStack current = items.get(INPUT);
        if (current.isEmpty()) {
            int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
            items.set(INPUT, stack.copyWithCount(moved));
            stack.shrink(moved);
            setChanged();
            return moved;
        }
        if (!ItemStack.isSameItemSameComponents(current, stack)) return 0;
        int moved = Math.min(current.getMaxStackSize() - current.getCount(), stack.getCount());
        if (moved <= 0) return 0;
        current.grow(moved);
        stack.shrink(moved);
        setChanged();
        return moved;
    }

    /** Empty hand takes the output first, then the input back. */
    public ItemStack extractAny() {
        if (!items.get(OUTPUT).isEmpty()) {
            ItemStack output = items.get(OUTPUT);
            items.set(OUTPUT, ItemStack.EMPTY);
            setChanged();
            return output;
        }
        ItemStack input = items.get(INPUT);
        if (input.isEmpty()) return ItemStack.EMPTY;
        items.set(INPUT, ItemStack.EMPTY);
        progress = 0;
        setChanged();
        return input;
    }

    /** True when the current input/output state can accept another cycle. */
    public boolean ready() {
        return !items.get(INPUT).isEmpty() && result(items.get(INPUT)) != null;
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

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && result(stack) != null;
    }

    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }

    @Override public void clearContent() { items.clear(); }

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
