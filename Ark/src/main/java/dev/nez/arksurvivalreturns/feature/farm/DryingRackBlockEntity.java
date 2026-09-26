package dev.nez.arksurvivalreturns.feature.farm;

import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.primitive.DriedMeatItem;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.tech.TechTrigger;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A drying rack converts raw food into portable dried food on a batch cadence.
 *
 * <p>Slot zero holds raw input, slot one accumulates the finished food. Meat and fish become Dried
 * Meat I; berries become dried rations. Once the raw input is gone, dried meat left hanging keeps
 * curing into Dried Meat II and III. The rack only advances while its chunk is loaded, so a long
 * absence never produces a windfall.
 */
public final class DryingRackBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int batch;
    private int progress;
    /** Batches the finished dried meat has hung with no raw food left to dry. */
    private int curing;

    public DryingRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.DRYING_RACK_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DryingRackBlockEntity rack) {
        if (level.isClientSide()) return;
        rack.syncDisplay();
        if (rack.items.get(0).isEmpty() && !rack.items.get(1).is(PrimitiveContent.DRIED_MEAT.get())) return;
        if (++rack.batch < Config.FARM_BATCH_TICKS.get()) return;
        rack.batch = 0;
        rack.advance();
    }

    /** Advances one drying batch; public so the headless suite can drive it deterministically. */
    public boolean advance() {
        ItemStack input = items.get(0);
        if (input.isEmpty()) return cure();
        if (!input.is(FarmTags.DRYING_INPUTS)) return false;
        ItemStack result = driedFrom(input);
        ItemStack output = items.get(1);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result) || output.getCount() >= output.getMaxStackSize()))
            return false;
        progress++;
        if (progress < Config.FARM_DRYING_BATCHES.get()) {
            setChanged();
            return true;
        }
        progress = 0;
        curing = 0;
        input.shrink(1);
        if (output.isEmpty()) items.set(1, result);
        else output.grow(1);
        setChanged();
        return true;
    }

    /** Hanging dried meat cures one batch; the whole shelf moves up a tier together. */
    private boolean cure() {
        ItemStack output = items.get(1);
        if (!output.is(PrimitiveContent.DRIED_MEAT.get())) return false;
        int tier = DriedMeatItem.tier(output);
        if (tier >= DriedMeatItem.MAX_TIER) return false;
        curing++;
        int needed = tier == 1 ? Config.FARM_DRIED_TIER_TWO.get() : Config.FARM_DRIED_TIER_THREE.get();
        if (curing >= needed) DriedMeatItem.setTier(output, tier + 1);
        setChanged();
        return true;
    }

    /** What one raw item becomes: meat and fish cure into dried meat, everything else into a ration. */
    private ItemStack driedFrom(ItemStack input) {
        if (input.is(net.minecraft.tags.ItemTags.MEAT) || input.is(net.minecraft.tags.ItemTags.FISHES)) {
            ItemStack meat = DriedMeatItem.withTier(1);
            stamp(meat);
            return meat;
        }
        ItemStack ration = new ItemStack(ModContent.DRIED_RATION.get());
        stamp(ration);
        return ration;
    }

    public int curing() {
        return curing;
    }

    /** Dried food carries the in-game day it was made, so the aged-food objectives can verify the wait. */
    private void stamp(ItemStack stack) {
        long day = getLevel() instanceof ServerLevel server ? server.getGameTime() / 24000L : 0L;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TechTrigger.DRIED_DAY_TAG, day));
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
            curing = 0;
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

    @Override public void setChanged() {
        super.setChanged();
        syncDisplay();
    }

    private void syncDisplay() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof DryingRackBlock)) return;
        ItemStack input = items.get(0);
        DryingRackBlock.Food food = input.is(net.minecraft.tags.ItemTags.FISHES) ? DryingRackBlock.Food.FISH
                : input.is(net.minecraft.tags.ItemTags.MEAT) ? DryingRackBlock.Food.MEAT : DryingRackBlock.Food.BERRIES;
        BlockState display = state.setValue(DryingRackBlock.HANGING, Math.min(3, input.getCount()))
                .setValue(DryingRackBlock.FOOD, input.isEmpty() ? DryingRackBlock.Food.MEAT : food)
                .setValue(DryingRackBlock.READY, !items.get(1).isEmpty());
        if (display != state) level.setBlock(worldPosition, display, Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("Items"), items);
        output.putInt("Progress", progress);
        output.putInt("Curing", curing);
    }

    @Override protected void loadAdditional(ValueInput input) {
        input.child("Items").ifPresent(child -> {
            items.set(0, ItemStack.EMPTY);
            items.set(1, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(child, items);
        });
        progress = input.getIntOr("Progress", 0);
        curing = input.getIntOr("Curing", 0);
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
