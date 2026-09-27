package dev.nez.arksurvivalreturns.feature.station.mixin;

import java.util.Optional;
import dev.nez.arksurvivalreturns.feature.station.StationContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Crafter makes no station-only result either. */
@Mixin(CrafterBlock.class)
abstract class CrafterStationOnlyMixin {
    @Inject(method = "getPotentialResults", at = @At("RETURN"), cancellable = true)
    private static void ark$stationOnly(ServerLevel level, CraftingInput input,
            CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
        cir.getReturnValue().ifPresent(recipe -> {
            var out = recipe.value().assemble(input);
            if (StationContent.mortar(out) || StationContent.medicine(out)) cir.setReturnValue(Optional.empty());
        });
    }
}
