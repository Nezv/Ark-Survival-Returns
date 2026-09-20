package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.feature.storage.StorageCrateMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The vanilla shulker-box panel reused for the crate: it already draws a 3x9 grid and the inventory. */
public final class StorageCrateScreen extends AbstractContainerScreen<StorageCrateMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");

    public StorageCrateScreen(StorageCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0f, 0f, this.imageWidth, this.imageHeight,
                256, 256);
    }
}
