package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.drop.LootCrateMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Storage Crate's panel cut down to one row: its title band, a plain band carrying as many slot wells as
 * the crate has, then its inventory half. Nothing is drawn that the crate's own texture does not hold.
 */
public final class LootCrateScreen extends AbstractContainerScreen<LootCrateMenu> {
    private static final Identifier PANEL = ArkSurvivalReturns.id("textures/gui/container/storage_crate.png");
    private static final Identifier FRAME = ArkSurvivalReturns.id("textures/gui/container/storage_crate_frame.png");
    /** Rows of the crate panel: the title band, a plain strip of its inventory label band, and where that band starts. */
    private static final int TITLE = 17, PLAIN = 72, PLAIN_ROWS = 9, LOWER = 71, LOWER_ROWS = 97;

    public LootCrateScreen(LootCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, TITLE + 18 + LOWER_ROWS);
        inventoryLabelY = imageHeight - 94;
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        super.extractBackground(graphics, mx, my, delta);
        StorageFrames.draw(graphics, this, FRAME, 168, 32, 50, 36);
        blit(graphics, 0, 0, 0, 0, 176, TITLE);
        blit(graphics, 0, TITLE, 0, PLAIN, 176, PLAIN_ROWS);
        blit(graphics, 0, TITLE + PLAIN_ROWS, 0, PLAIN, 176, PLAIN_ROWS);
        blit(graphics, 0, TITLE + 18, 0, LOWER, 176, LOWER_ROWS);
        int slots = menu.crateSlots();
        for (int i = 0; i < slots; i++) blit(graphics, LootCrateMenu.rowX(slots) - 1 + i * 18, LootCrateMenu.ROW_Y - 1, 7, 17, 18, 18);
    }

    private void blit(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, leftPos + x, topPos + y, u, v, width, height, 256, 256);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mx, int my) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xffe6e8e1, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff8a8f86, false);
    }
}
