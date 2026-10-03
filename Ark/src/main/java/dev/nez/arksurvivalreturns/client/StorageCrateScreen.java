package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.station.StorageCrateMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public final class StorageCrateScreen extends AbstractContainerScreen<StorageCrateMenu> {
    private static final Identifier PANEL = ArkSurvivalReturns.id("textures/gui/container/storage_crate.png");
    private static final Identifier FRAME = ArkSurvivalReturns.id("textures/gui/container/storage_crate_frame.png");

    public StorageCrateScreen(StorageCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 168);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        super.extractBackground(graphics, mx, my, delta);
        StorageFrames.draw(graphics, this, FRAME, 168, 32, 50, 36);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, leftPos, topPos, 0, 0, 176, 168, 256, 256);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xffe6e8e1, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff8a8f86, false);
    }
}
