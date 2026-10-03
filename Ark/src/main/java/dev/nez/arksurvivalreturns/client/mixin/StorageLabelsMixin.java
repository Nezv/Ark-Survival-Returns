package dev.nez.arksurvivalreturns.client.mixin;

import dev.nez.arksurvivalreturns.client.StorageFrames;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AbstractContainerScreen.class)
abstract class StorageLabelsMixin {
    @ModifyConstant(method = "extractLabels", constant = @Constant(intValue = -12566464))
    private int ark$stoneLabels(int color) {
        return StorageFrames.themedTom(this) ? 0xffe6e8e1 : color;
    }
}
