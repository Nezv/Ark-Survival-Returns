package dev.nez.arksurvivalreturns.feature.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Slot 0 is the hopper (fed from above or the sides, or by hand), slot 1 the chute (taken from below or by
 * hand). One item is ground at a time; the crusher stalls rather than overfill the chute.
 */
public final class CrusherBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int INPUT = 0, OUTPUT = 1;
    private static final int[] TOP_AND_SIDES = {INPUT}, BOTTOM = {OUTPUT};
    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int progress;

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(StationContent.CRUSHER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrusherBlockEntity crusher) {
        var recipe = CrusherRecipes.find(crusher.items.get(INPUT));
        boolean running = recipe != null && crusher.fits(recipe);
        if (!running) {
            crusher.progress = 0;
            if (state.getValue(CrusherBlock.RUNNING)) level.setBlock(pos, state.setValue(CrusherBlock.RUNNING, false), Block.UPDATE_CLIENTS);
            return;
        }
        // The flywheel turns a quarter of its tooth pitch every three ticks while grinding.
        if (level.getGameTime() % 3 == 0 || !state.getValue(CrusherBlock.RUNNING)) {
            state = state.setValue(CrusherBlock.RUNNING, true).setValue(CrusherBlock.SPIN, (state.getValue(CrusherBlock.SPIN) + 1) % 4);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 16 == 0) {
            level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35f, 0.7f + level.getRandom().nextFloat() * 0.2f);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.01);
            }
        }
        if (++crusher.progress < recipe.ticks()) return;
        crusher.progress = 0;
        crusher.items.get(INPUT).shrink(1);
        ItemStack out = crusher.items.get(OUTPUT);
        if (out.isEmpty()) crusher.items.set(OUTPUT, new ItemStack(recipe.output(), recipe.count()));
        else out.grow(recipe.count());
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6f, 0.8f);
        crusher.setChanged();
    }

    private boolean fits(CrusherRecipes.Recipe recipe) {
        ItemStack out = items.get(OUTPUT);
        if (out.isEmpty()) return true;
        return out.is(recipe.output()) && out.getCount() + recipe.count() <= out.getMaxStackSize();
    }

    /** Moves as much of the stack as fits into the hopper; returns the amount moved. */
    public int insert(ItemStack stack) {
        if (CrusherRecipes.find(stack) == null) return 0;
        ItemStack current = items.get(INPUT);
        if (current.isEmpty()) {
            items.set(INPUT, stack.split(stack.getMaxStackSize()));
            setChanged();
            return items.get(INPUT).getCount();
        }
        if (!ItemStack.isSameItemSameComponents(current, stack)) return 0;
        int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
        if (moved <= 0) return 0;
        current.grow(moved);
        stack.shrink(moved);
        setChanged();
        return moved;
    }

    public ItemStack takeOutput() {
        ItemStack out = items.get(OUTPUT);
        items.set(OUTPUT, ItemStack.EMPTY);
        if (!out.isEmpty()) setChanged();
        return out;
    }

    @Override public int getContainerSize() { return 2; }

    @Override protected NonNullList<ItemStack> getItems() { return items; }

    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override protected Component getDefaultName() {
        return Component.translatable("block.arksurvivalreturns.crusher");
    }

    /** No screen: the crusher is worked by hand and by hoppers. */
    @Override protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) { return null; }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && CrusherRecipes.find(stack) != null;
    }

    @Override public boolean canTakeItem(Container target, int slot, ItemStack stack) { return slot == OUTPUT; }

    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.DOWN ? BOTTOM : TOP_AND_SIDES; }

    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT && side == Direction.DOWN;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Progress", progress);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("Progress", 0);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, this);
        super.preRemoveSideEffects(pos, state);
    }
}
