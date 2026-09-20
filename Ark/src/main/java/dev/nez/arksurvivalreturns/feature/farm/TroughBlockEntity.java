package dev.nez.arksurvivalreturns.feature.farm;

import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A trough holds one feeding stack and, on a batch cadence, feeds hungry tamed creatures in range.
 *
 * <p>Only tamed creatures are fed: wild taming stays a player action, and there is no offline
 * catch-up because a block entity simply does not tick while its chunk is unloaded.
 */
public final class TroughBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int batch;

    public TroughBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.TROUGH_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TroughBlockEntity trough) {
        if (level.isClientSide() || trough.items.get(0).isEmpty()) return;
        if (++trough.batch < Config.FARM_BATCH_TICKS.get()) return;
        trough.batch = 0;
        trough.feedNearby((ServerLevel) level);
    }

    /** Merges food into the single stack, up to the configured capacity; returns the amount moved. */
    public int insert(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(FarmTags.TROUGH_FOOD)) return 0;
        ItemStack current = items.get(0);
        int cap = Math.min(Config.FARM_TROUGH_CAPACITY.get(), stack.getMaxStackSize());
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

    public ItemStack extract() {
        ItemStack current = items.get(0);
        if (current.isEmpty()) return ItemStack.EMPTY;
        items.set(0, ItemStack.EMPTY);
        setChanged();
        return current;
    }

    public ItemStack stored() {
        return items.get(0);
    }

    /** Feeds hungry tamed creatures in range; returns how many were fed. Public for the headless suite. */
    public int feedNearby(ServerLevel level) {
        if (!Config.FARM_ENABLED.get() || items.get(0).isEmpty()) return 0;
        double radius = Config.FARM_TROUGH_RADIUS.get();
        List<CreatureEntity> creatures = level.getEntitiesOfClass(CreatureEntity.class,
                new AABB(worldPosition).inflate(radius));
        int fed = 0;
        for (CreatureEntity creature : creatures) {
            if (fed >= Config.FARM_TROUGH_MAX_PER_BATCH.get() || items.get(0).isEmpty()) break;
            if (!creature.isTamed() || TorporService.restricted(creature)) continue;
            var state = TamingService.of(creature);
            if (state.hunger() < Config.FARM_TROUGH_HUNGER.get()) continue;
            items.get(0).shrink(1);
            state.setHunger(Math.max(0.0, state.hunger() - Config.FARM_TROUGH_FEED.get()));
            double heal = Config.FARM_TROUGH_HEAL.get();
            if (heal > 0.0) creature.heal((float) heal);
            fed++;
            setChanged();
        }
        return fed;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("Items"), items);
    }

    @Override protected void loadAdditional(ValueInput input) {
        input.child("Items").ifPresent(child -> {
            items.set(0, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(child, items);
        });
    }

    /** The remaining food drops where the trough stood, even when an explosion removes it. */
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) Block.popResource(server, pos, stack);
        }
        items.clear();
    }
}
