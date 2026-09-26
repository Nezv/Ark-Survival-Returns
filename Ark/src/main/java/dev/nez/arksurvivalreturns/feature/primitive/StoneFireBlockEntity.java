package dev.nez.arksurvivalreturns.feature.primitive;

import java.util.List;
import java.util.Optional;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stone fire state: burn time, the spit (up to four of one raw food) and the cooked pile.
 *
 * <p>It cooks any campfire recipe, one item at a time, only while lit, only in loaded chunks, and never
 * while a cooking pot sits on top (the pot takes the heat instead).
 */
public final class StoneFireBlockEntity extends BlockEntity {
    public static final int SPIT = 0;
    public static final int COOKED = 1;
    public static final int SPIT_CAPACITY = 4;
    public static final int COOKED_CAPACITY = 16;

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int burnTicks;
    private int cookTicks;

    public StoneFireBlockEntity(BlockPos pos, BlockState state) {
        super(PrimitiveContent.STONE_FIRE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StoneFireBlockEntity fire) {
        if (level.isClientSide()) return;
        fire.step();
    }

    /** One game tick; public so the headless suite can drive it deterministically. */
    public void step() {
        if (!isLit()) return;
        if (--burnTicks <= 0) {
            burnTicks = 0;
            cookTicks = 0;
            setChanged();
            return;
        }
        ItemStack raw = items.get(SPIT);
        if (!raw.isEmpty() && !getBlockState().getValue(StoneFireBlock.POT)) {
            var recipe = recipe(raw);
            if (recipe.isEmpty()) return;
            int total = cookTime(recipe.get());
            if (++cookTicks >= total) finish(recipe.get());
            else if (cookTicks == total / 2 || cookTicks == 1) setChanged();
        }
        if (burnTicks % 200 == 0) setChanged();
    }

    private void finish(RecipeHolder<CampfireCookingRecipe> recipe) {
        ItemStack raw = items.get(SPIT);
        SingleRecipeInput input = new SingleRecipeInput(raw);
        ItemStack result = recipe.value().assemble(input);
        ItemStack cooked = items.get(COOKED);
        if (!cooked.isEmpty() && (!ItemStack.isSameItemSameComponents(cooked, result)
                || cooked.getCount() + result.getCount() > COOKED_CAPACITY)) {
            cookTicks = cookTime(recipe) - 1; // Wait for the cooked pile to be taken.
            return;
        }
        cookTicks = 0;
        raw.shrink(1);
        if (cooked.isEmpty()) items.set(COOKED, result);
        else cooked.grow(result.getCount());
        setChanged();
    }

    private int cookTime(RecipeHolder<CampfireCookingRecipe> recipe) {
        return Math.max(20, (int) Math.round(recipe.value().cookingTime() * Config.PRIMITIVE_FIRE_COOK_FACTOR.get()));
    }

    private Optional<RecipeHolder<CampfireCookingRecipe>> recipe(ItemStack stack) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()) return Optional.empty();
        return server.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack), server);
    }

    public boolean isLit() { return burnTicks > 0 && getBlockState().getValue(StoneFireBlock.LIT); }
    public boolean hasFuel() { return burnTicks > 0; }
    public int burnTicks() { return burnTicks; }
    public int cookTicks() { return cookTicks; }
    public ItemStack spit() { return items.get(SPIT); }
    public ItemStack cooked() { return items.get(COOKED); }

    public boolean isCookable(ItemStack stack) {
        return level != null && !level.isClientSide() ? recipe(stack).isPresent() : !stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.FOOD);
    }

    public boolean isFuel(ItemStack stack) {
        return level != null && !stack.is(Items.LAVA_BUCKET) && stack.getBurnTime(RecipeType.SMELTING, level.fuelValues()) > 0;
    }

    /** Stacks one fuel item; refused when the fire already holds as much as it can. */
    public boolean addFuel(ItemStack stack) {
        if (!isFuel(stack)) return false;
        int burn = (int) Math.round(stack.getBurnTime(RecipeType.SMELTING, level.fuelValues()) * Config.PRIMITIVE_FIRE_FUEL_FACTOR.get());
        if (burnTicks > 0 && burnTicks + burn > Config.PRIMITIVE_FIRE_MAX_FUEL.get()) return false;
        burnTicks = Math.min(Config.PRIMITIVE_FIRE_MAX_FUEL.get(), burnTicks + Math.max(1, burn));
        stack.shrink(1);
        setChanged();
        return true;
    }

    /** Lights stacked fuel; returns true when the fire went from dark to lit. */
    public boolean light() {
        if (burnTicks <= 0 || getBlockState().getValue(StoneFireBlock.LIT)) return false;
        level.setBlock(worldPosition, getBlockState().setValue(StoneFireBlock.LIT, true), Block.UPDATE_ALL);
        setChanged();
        return true;
    }

    /** Raw food onto the spit, up to four of the same item; returns the amount moved. */
    public int insertFood(ItemStack stack) {
        if (getBlockState().getValue(StoneFireBlock.POT) || !isCookable(stack)) return 0;
        ItemStack current = items.get(SPIT);
        if (current.isEmpty()) {
            int moved = Math.min(SPIT_CAPACITY, stack.getCount());
            items.set(SPIT, stack.copyWithCount(moved));
            stack.shrink(moved);
            cookTicks = 0;
            setChanged();
            return moved;
        }
        if (!ItemStack.isSameItemSameComponents(current, stack)) return 0;
        int moved = Math.min(SPIT_CAPACITY - current.getCount(), stack.getCount());
        if (moved <= 0) return 0;
        current.grow(moved);
        stack.shrink(moved);
        setChanged();
        return moved;
    }

    /** Cooked food first, then the raw food back off the spit. */
    public ItemStack extract() {
        for (int slot : new int[]{COOKED, SPIT}) {
            if (items.get(slot).isEmpty()) continue;
            ItemStack taken = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            if (slot == SPIT) cookTicks = 0;
            setChanged();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    @Override public void setChanged() {
        super.setChanged();
        syncDisplay();
    }

    private void syncDisplay() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof StoneFireBlock)) return;
        boolean lit = state.getValue(StoneFireBlock.LIT) && burnTicks > 0;
        StoneFireBlock.Spit spit = !items.get(SPIT).isEmpty()
                ? (cookTicks > 0 && recipe(items.get(SPIT)).map(r -> cookTicks * 2 >= cookTime(r)).orElse(false)
                        ? StoneFireBlock.Spit.SEARED : StoneFireBlock.Spit.RAW)
                : !items.get(COOKED).isEmpty() ? StoneFireBlock.Spit.COOKED : StoneFireBlock.Spit.NONE;
        BlockState display = state.setValue(StoneFireBlock.LIT, lit).setValue(StoneFireBlock.FUELED, burnTicks > 0)
                .setValue(StoneFireBlock.SPIT, spit);
        if (display != state) level.setBlock(worldPosition, display, Block.UPDATE_ALL);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("Items"), items);
        output.putInt("Burn", burnTicks);
        output.putInt("Cook", cookTicks);
    }

    @Override protected void loadAdditional(ValueInput input) {
        input.child("Items").ifPresent(child -> {
            items.set(SPIT, ItemStack.EMPTY);
            items.set(COOKED, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(child, items);
        });
        burnTicks = input.getIntOr("Burn", 0);
        cookTicks = input.getIntOr("Cook", 0);
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
