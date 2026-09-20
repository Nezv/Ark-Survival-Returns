package dev.nez.arksurvivalreturns.feature.farm;

import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A drying rack converts raw food into portable dried rations on a batch cadence.
 *
 * <p>Slot zero holds raw input, slot one accumulates the finished ration. The rack only advances
 * while its chunk is loaded, so a long absence never produces a windfall.
 */
public final class DryingRackBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int batch;
    private int progress;

    public DryingRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.DRYING_RACK_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DryingRackBlockEntity rack) {
        if (level.isClientSide() || rack.items.get(0).isEmpty()) return;
        if (++rack.batch < Config.FARM_BATCH_TICKS.get()) return;
        rack.batch = 0;
        rack.advance();
    }

    /** Advances one drying batch; public so the headless suite can drive it deterministically. */
    public boolean advance() {
        ItemStack input = items.get(0);
        if (input.isEmpty() || !input.is(FarmTags.DRYING_INPUTS)) return false;
        ItemStack output = items.get(1);
        if (!output.isEmpty() && (!output.is(ModContent.DRIED_RATION.get()) || output.getCount() >= output.getMaxStackSize()))
            return false;
        progress++;
        if (progress < Config.FARM_DRYING_BATCHES.get()) {
            setChanged();
            return true;
        }
        progress = 0;
        input.shrink(1);
        if (output.isEmpty()) items.set(1, new ItemStack(ModContent.DRIED_RATION.get()));
        else output.grow(1);
        setChanged();
        return true;
    }

    /** Raw food into slot zero, up to the configured load; returns the amount moved. */
    public int insert(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(FarmTags.DRYING_INPUTS)) return 0;
        ItemStack current = items.get(0);
        int cap = Math.min(Config.FARM_DRYING_CAPACITY.get(), stack.getMaxStackSize());
        if (current.isEmpty()) {
            int moved = Math.min(cap, stack.getCount());
            items.set(0, stack.copyWithCount(moved));
            stack.shrink(moved);
            setChanged();
            return moved;
        }
        if (!ItemStack.isSameItemSameComponents(current, stack)) return 0;
        int moved = Math.min(cap - current.getCount(), stack.getCount());
        if (moved <= 0) return 0;
        current.grow(moved);
        stack.shrink(moved);
        setChanged();
        return moved;
    }

    /** Empty hand takes the finished ration first, then the raw input back. */
    public ItemStack extract() {
        if (!items.get(1).isEmpty()) {
            ItemStack output = items.get(1);
            items.set(1, ItemStack.EMPTY);
            setChanged();
            return output;
        }
        ItemStack input = items.get(0);
        if (input.isEmpty()) return ItemStack.EMPTY;
        items.set(0, ItemStack.EMPTY);
        progress = 0;
        setChanged();
        return input;
    }

    public ItemStack input() {
        return items.get(0);
    }

    public ItemStack output() {
        return items.get(1);
    }

    public int progress() {
        return progress;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("Items"), items);
        output.putInt("Progress", progress);
    }

    @Override protected void loadAdditional(ValueInput input) {
        input.child("Items").ifPresent(child -> {
            items.set(0, ItemStack.EMPTY);
            items.set(1, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(child, items);
        });
        progress = input.getIntOr("Progress", 0);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        List<ItemStack> drops = List.copyOf(items);
        items.clear();
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) Block.popResource(server, pos, stack);
        }
    }
}
