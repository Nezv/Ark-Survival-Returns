package dev.nez.arksurvivalreturns.feature.levels.mixin;

import java.util.List;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The normal crafting event lacks a recipe ID; this callback supplies the exact completed recipe. */
@Mixin(ServerPlayer.class)
abstract class RecipeXpMixin {
    @Inject(method = "triggerRecipeCrafted", at = @At("TAIL"))
    private void ark$firstCraft(RecipeHolder<?> recipe, List<ItemStack> ingredients, CallbackInfo ci) {
        ArkLevels.crafted((ServerPlayer) (Object) this, recipe.id().identifier());
    }
}
