package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlockEntity;
import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The vanilla dispenser panel reused for the kitchen: the dispenser grid holds two of the four inputs,
 * the remaining pair and the meal slot get drawn slot sprites, and the player inventory sits where the
 * texture already expects it. No new GUI art.
 */
public final class CookingPotScreen extends AbstractContainerScreen<CookingPotMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

    public CookingPotScreen(CookingPotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0f, 0f, this.imageWidth, this.imageHeight,
                256, 256);
        for (int slot = 0; slot < CookingPotBlockEntity.INPUT_SLOTS; slot++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    xo + 62 + (slot % 2) * 18, yo + 17 + (slot / 2) * 18, 18, 18);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, xo + 116, yo + 26, 18, 18);
        graphics.text(font, Component.translatable(menu.isHeated()
                ? "kitchen.arksurvivalreturns.heated" : "kitchen.arksurvivalreturns.needs_fire"), xo + 8, yo + 60, 0xFF514638, false);
    }
}
