package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The kitchen panel: the 2x2 ingredient grid, a progress arrow and the meal slot, with the heat status centred
 * under the grid. The background is the dispenser panel cut down by tools/build_ui_pack.py (the Ark UI pack
 * ships a re-skinned copy), so every frame sits exactly under a CookingPotMenu slot.
 */
public final class CookingPotScreen extends AbstractContainerScreen<CookingPotMenu> {
    private static final Identifier BACKGROUND = ArkSurvivalReturns.id("textures/gui/container/cooking_pot.png");
    /** Empty arrow position on the panel; the filled arrow is stored beside the panel at u 176, v 0. */
    private static final int ARROW_X = 98, ARROW_Y = 29, ARROW_WIDTH = 16, ARROW_HEIGHT = 11;
    private static final int STATUS = 0xFF514638;
    /** Between the ingredient grid (ends at y 52) and the inventory label (y 72). */
    private static final int STATUS_Y = 58;

    public CookingPotScreen(CookingPotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);
        int max = this.menu.maxProgress();
        int done = max <= 0 ? 0 : Math.min(ARROW_WIDTH, ARROW_WIDTH * this.menu.progress() / max);
        if (done > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo + ARROW_X, yo + ARROW_Y, 176f, 0f, done, ARROW_HEIGHT, 256, 256);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component status = Component.translatable(this.menu.isHeated()
                ? "kitchen.arksurvivalreturns.heated" : "kitchen.arksurvivalreturns.needs_fire");
        graphics.text(this.font, status, (this.imageWidth - this.font.width(status)) / 2, STATUS_Y, STATUS, false);
    }
}
