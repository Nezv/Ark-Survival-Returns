package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

/** Discovered by JEI only when it is installed; reserve space around the crate's frame. */
@JeiPlugin
public final class StorageUiJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid() { return ArkSurvivalReturns.id("storage_ui"); }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(StorageCrateScreen.class, new IGuiContainerHandler<StorageCrateScreen>() {
            @Override public List<Rect2i> getGuiExtraAreas(StorageCrateScreen screen) {
                int head = Math.min(50, Math.max(0, screen.getTopPos()));
                int foot = Math.min(36, Math.max(0, screen.height - screen.getTopPos() - screen.getImageHeight()));
                return List.of(new Rect2i(screen.getLeftPos() - 32, screen.getTopPos() - head,
                        screen.getImageWidth() + 64, screen.getImageHeight() + head + foot));
            }
        });
    }
}
