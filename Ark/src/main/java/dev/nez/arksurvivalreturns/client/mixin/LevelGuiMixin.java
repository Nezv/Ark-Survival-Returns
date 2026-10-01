package dev.nez.arksurvivalreturns.client.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep permanent progress visible even without the temporary vanilla XP pickup timer. */
@Mixin(Gui.class)
abstract class LevelGuiMixin {
    @Inject(method = "willPrioritizeExperienceInfo", at = @At("HEAD"), cancellable = true)
    private void ark$permanentProgress(CallbackInfoReturnable<Boolean> ci) { ci.setReturnValue(true); }
}
