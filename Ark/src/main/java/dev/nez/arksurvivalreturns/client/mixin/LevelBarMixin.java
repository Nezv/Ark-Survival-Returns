package dev.nez.arksurvivalreturns.client.mixin;

import dev.nez.arksurvivalreturns.client.LevelClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the experience bar, preserving the contextual jump bar. */
@Mixin(ExperienceBarRenderer.class)
abstract class LevelBarMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void ark$progress(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        LevelClient.renderBar(graphics);
        ci.cancel();
    }
}
