package dev.nez.arksurvivalreturns.client.mixin;

import dev.nez.arksurvivalreturns.client.StorageFrames;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Optional mod target; no Tom classes are resolved when the integration is absent. */
@Pseudo
@Mixin(targets = "com.tom.storagemod.screen.AbstractStorageTerminalScreen", remap = false)
abstract class TomTerminalMixin {
    @Shadow @Final public int guiHeight;
    @Shadow @Final public int textureSlotCount;

    @ModifyConstant(method = "init", constant = @Constant(intValue = 30))
    private int ark$frameSpace(int padding) {
        if (!StorageFrames.themedTom(this)) return padding;
        int minimumPanel = guiHeight - textureSlotCount * 18 + 18;
        return Math.max(padding, Math.min(112, ((Screen) (Object) this).height - minimumPanel));
    }

    @ModifyConstant(method = "extractLabels", constant = @Constant(intValue = -12566464))
    private int ark$infoColor(int color) {
        return StorageFrames.themedTom(this) ? 0xffe6e8e1 : color;
    }
}
