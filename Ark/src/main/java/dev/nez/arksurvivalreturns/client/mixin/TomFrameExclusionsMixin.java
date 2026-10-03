package dev.nez.arksurvivalreturns.client.mixin;

import com.tom.storagemod.screen.IScreen;
import dev.nez.arksurvivalreturns.client.StorageFrames;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extend Tom's own JEI exclusion contract to include the carved exterior. */
@Pseudo
@Mixin(targets = {
        "com.tom.storagemod.screen.AbstractStorageTerminalScreen",
        "com.tom.storagemod.screen.InventoryLinkScreen",
        "com.tom.storagemod.screen.InventoryConfiguratorScreen",
        "com.tom.storagemod.screen.LevelEmitterScreen",
        "com.tom.storagemod.screen.TagItemFilterScreen"
}, remap = false)
abstract class TomFrameExclusionsMixin {
    @Inject(method = "getExclusionAreas", at = @At("TAIL"))
    private void ark$frameBounds(Consumer<IScreen.Box> areas, CallbackInfo ci) {
        if (!StorageFrames.themedTom(this)) return;
        var screen = (AbstractContainerScreen<?>) (Object) this;
        int head = Math.min(64, Math.max(0, screen.getTopPos()));
        int foot = Math.min(36, Math.max(0, screen.height - screen.getTopPos() - screen.getImageHeight()));
        areas.accept(new IScreen.Box(screen.getLeftPos() - 44, screen.getTopPos() - head,
                screen.getImageWidth() + 88, screen.getImageHeight() + head + foot));
    }
}
