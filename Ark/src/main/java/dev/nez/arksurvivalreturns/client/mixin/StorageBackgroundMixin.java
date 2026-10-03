package dev.nez.arksurvivalreturns.client.mixin;

import dev.nez.arksurvivalreturns.client.StorageFrames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class StorageBackgroundMixin {
    @Inject(method = "extractBackground", at = @At("RETURN"))
    private void ark$storageFrame(GuiGraphicsExtractor graphics, int mx, int my, float delta, CallbackInfo ci) {
        StorageFrames.tomBackground(this, graphics);
    }
}
