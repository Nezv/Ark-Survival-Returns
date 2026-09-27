package dev.nez.arksurvivalreturns.feature.primitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * A shaped tool recipe with one binding (Plant Fiber) that may sit in any free slot of the grid: the rock set is
 * the vanilla tool pattern lashed with a cord wherever it fits.
 *
 * <p>It extends {@link ShapedRecipe} so the recipe book and recipe viewers treat it as shaped: they show and
 * auto-place the pattern with the binding in its first free cell ({@link #shown}), which also matches. Matching
 * itself takes any one binding out of the grid and checks the rest against the bare pattern, mirrored or not.
 */
public final class BoundShapedRecipe extends ShapedRecipe {
    public static final MapCodec<BoundShapedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.bare),
            Ingredient.CODEC.fieldOf("binding").forGetter(r -> r.binding),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result)
    ).apply(i, BoundShapedRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoundShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
            ShapedRecipePattern.STREAM_CODEC, r -> r.bare,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.binding,
            ItemStackTemplate.STREAM_CODEC, r -> r.result,
            BoundShapedRecipe::new);
    public static final RecipeSerializer<BoundShapedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ShapedRecipePattern bare;
    private final Ingredient binding;
    private final ItemStackTemplate result;

    public BoundShapedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern bare,
                             Ingredient binding, ItemStackTemplate result) {
        super(commonInfo, bookInfo, shown(bare, binding), result);
        this.bare = bare;
        this.binding = binding;
        this.result = result;
    }

    /** The pattern as displayed: the binding in the first free cell, or in a new column when the pattern is full. */
    static ShapedRecipePattern shown(ShapedRecipePattern bare, Ingredient binding) {
        List<Optional<Ingredient>> cells = new ArrayList<>(bare.ingredients());
        int free = cells.indexOf(Optional.<Ingredient>empty());
        if (free >= 0) {
            cells.set(free, Optional.of(binding));
            return new ShapedRecipePattern(bare.width(), bare.height(), cells, Optional.empty());
        }
        int width = bare.width() + 1;
        List<Optional<Ingredient>> wider = new ArrayList<>();
        for (int y = 0; y < bare.height(); y++) {
            for (int x = 0; x < bare.width(); x++) wider.add(cells.get(y * bare.width() + x));
            wider.add(y == 0 ? Optional.of(binding) : Optional.empty());
        }
        return new ShapedRecipePattern(width, bare.height(), wider, Optional.empty());
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        for (int slot = 0; slot < input.size(); slot++) {
            if (!binding.test(input.getItem(slot))) continue;
            List<ItemStack> rest = new ArrayList<>(input.items());
            rest.set(slot, ItemStack.EMPTY);
            if (bare.matches(CraftingInput.of(input.width(), input.height(), rest))) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) SERIALIZER;
    }
}
