package dev.nez.arksurvivalreturns.client.mixin;

import dev.nez.arksurvivalreturns.client.StorageFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "com.tom.storagemod.screen.InventoryLinkScreen", remap = false)
abstract class TomLinkLabelsMixin {
    @ModifyConstant(method = "extractLabels", constant = @Constant(intValue = -12566464))
    private int ark$linkLabels(int color) {
        return StorageFrames.themedTom(this) ? 0xffe6e8e1 : color;
    }
}
