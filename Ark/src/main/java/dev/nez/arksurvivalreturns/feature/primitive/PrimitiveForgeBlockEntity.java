package dev.nez.arksurvivalreturns.feature.primitive;

import java.util.List;
import java.util.Optional;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The primitive forge: the furnace replacement for everything that is not food.
 *
 * <p>It runs every {@code minecraft:smelting} recipe whose result is not edible (ores, sand, clay,
 * charcoal, and any mod's smelting recipes), burns ordinary furnace fuel, and only works in loaded chunks.
 * Food is refused on purpose; it belongs on the stone fire.
 */
public final class PrimitiveForgeBlockEntity extends BlockEntity {
    public static final int INPUT = 0;
    public static final int FUEL = 1;
    public static final int OUTPUT = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int burnTicks;
    private int burnTotal;
    private int smeltTicks;
    private float experience;

    public PrimitiveForgeBlockEntity(BlockPos pos, BlockState state) {
        super(PrimitiveContent.PRIMITIVE_FORGE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PrimitiveForgeBlockEntity forge) {
        if (level.isClientSide()) return;
        forge.step();
    }

    /** One game tick; public so the headless suite can drive it deterministically. */
    public void step() {
        boolean wasBurning = burnTicks > 0;
        if (burnTicks > 0) burnTicks--;
        var recipe = recipe(items.get(INPUT));
        boolean canSmelt = recipe.isPresent() && fits(recipe.get());
        if (canSmelt && burnTicks == 0 && isFuel(items.get(FUEL))) {
            ItemStack fuel = items.get(FUEL);
            burnTotal = burnTicks = fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues());
            var remainder = fuel.getCraftingRemainder();
            fuel.shrink(1);
            if (fuel.isEmpty() && remainder != null) items.set(FUEL, remainder.create());
        }
        if (canSmelt && burnTicks > 0) {
            if (++smeltTicks >= smeltTime(recipe.get())) {
                smeltTicks = 0;
                ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(items.get(INPUT)));
                items.get(INPUT).shrink(1);
                ItemStack output = items.get(OUTPUT);
                if (output.isEmpty()) items.set(OUTPUT, result);
                else output.grow(result.getCount());
                experience += recipe.get().value().experience();
                setChanged();
            }
        } else if (smeltTicks > 0) {
            smeltTicks = Math.max(0, smeltTicks - 2);
        }
        if (wasBurning != burnTicks > 0) setChanged();
    }

    private boolean fits(RecipeHolder<SmeltingRecipe> recipe) {
        ItemStack result = recipe.value().assemble(new SingleRecipeInput(items.get(INPUT)));
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private int smeltTime(RecipeHolder<SmeltingRecipe> recipe) {
        return Math.max(20, (int) Math.round(recipe.value().cookingTime() * Config.PRIMITIVE_FORGE_SPEED.get()));
    }

    /** A smelting recipe for the stack whose result is not food; food is the stone fire's job. */
    public Optional<RecipeHolder<SmeltingRecipe>> recipe(ItemStack stack) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()) return Optional.empty();
        var input = new SingleRecipeInput(stack);
        return server.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, server)
                .filter(holder -> !holder.value().assemble(input).has(DataComponents.FOOD));
    }

    public boolean isSmeltable(ItemStack stack) { return recipe(stack).isPresent(); }

    public boolean isFuel(ItemStack stack) {
        return level != null && !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING, level.fuelValues()) > 0;
    }

    public boolean isBurning() { return burnTicks > 0; }
    public int smeltTicks() { return smeltTicks; }
    public ItemStack input() { return items.get(INPUT); }
    public ItemStack fuel() { return items.get(FUEL); }
    public ItemStack output() { return items.get(OUTPUT); }

    /** Merges the stack into one slot; returns the amount moved. */
    public int insert(ItemStack stack, int slot) {
        if (stack.isEmpty()) return 0;
        if (slot == INPUT && !isSmeltable(stack) || slot == FUEL && !isFuel(stack)) return 0;
        ItemStack current = items.get(slot);
        if (current.isEmpty()) {
            int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
            items.set(slot, stack.copyWithCount(moved));
            stack.shrink(moved);
            if (slot == INPUT) smeltTicks = 0;
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

    /** Output first (paying the stored experience), then the input, then leftover fuel. */
    public ItemStack extract() {
        for (int slot : new int[]{OUTPUT, INPUT, FUEL}) {
            if (items.get(slot).isEmpty()) continue;
            ItemStack taken = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            if (slot == INPUT) smeltTicks = 0;
            if (slot == OUTPUT && level instanceof ServerLevel server && experience > 0) {
                ExperienceOrb.award(server, net.minecraft.world.phys.Vec3.atCenterOf(worldPosition), (int) Math.floor(experience));
                experience = 0;
            }
            setChanged();
            return taken;
        }
        return ItemStack.EMPTY;
    }

    @Override public void setChanged() {
        super.setChanged();
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (state.getBlock() instanceof PrimitiveForgeBlock && state.getValue(PrimitiveForgeBlock.LIT) != burnTicks > 0) {
            level.setBlock(worldPosition, state.setValue(PrimitiveForgeBlock.LIT, burnTicks > 0), Block.UPDATE_ALL);
        }
    }

    @Override protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child("Items"), items);
        output.putInt("Burn", burnTicks);
        output.putInt("BurnTotal", burnTotal);
        output.putInt("Smelt", smeltTicks);
        output.putFloat("Experience", experience);
    }

    @Override protected void loadAdditional(ValueInput input) {
        input.child("Items").ifPresent(child -> {
            for (int slot = 0; slot < items.size(); slot++) items.set(slot, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(child, items);
        });
        burnTicks = input.getIntOr("Burn", 0);
        burnTotal = input.getIntOr("BurnTotal", 0);
        smeltTicks = input.getIntOr("Smelt", 0);
        experience = input.getFloatOr("Experience", 0f);
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
