package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.feature.taming.CreatureMountMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The vanilla horse GUI reused for a creature: same texture, same slot coordinates, same 3x3 chest block
 * and the same saddle sprite. Only the menu type differs, because the vanilla mount menu cannot report a
 * menu type and therefore cannot travel through the vanilla open-screen path in this version.
 *
 * <p>Progress, appetite and sedation come from the menu's synchronized data slots, so the client never
 * computes them and never sees the inventory of a creature it may not access.
 */
public final class CreatureMountScreen extends AbstractContainerScreen<CreatureMountMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/horse.png");
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier CHEST_SLOTS_SPRITE = Identifier.withDefaultNamespace("container/horse/chest_slots");
    private static final int LABEL = -12566464;
    private float xMouse;
    private float yMouse;

    public CreatureMountScreen(CreatureMountMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.xMouse = mouseX;
        this.yMouse = mouseY;
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, xo, yo, 0f, 0f, this.imageWidth, this.imageHeight,
                256, 256);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CHEST_SLOTS_SPRITE, 90, 54, 0, 0,
                xo + 79, yo + 17, CreatureMountMenu.STORAGE_COLUMNS * 18, 54);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, xo + 7, yo + 17, 18, 18);
        var creature = this.menu.creature();
        if (creature != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, xo + 26, yo + 18, xo + 78, yo + 70, 17,
                    0.25f, this.xMouse, this.yMouse, creature);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        // extractLabels runs inside the pose already translated by leftPos/topPos, so these stay panel-relative.
        int progress = this.menu.rawProgress();
        graphics.text(this.font, Component.translatable("screen.arksurvivalreturns.taming",
                progress / 100 + "." + (progress % 100) / 10 + "%"), 8, 56, LABEL, false);
        graphics.text(this.font, Component.translatable("screen.arksurvivalreturns.hunger",
                this.menu.rawHunger()), 90, 6, LABEL, false);
        graphics.text(this.font, Component.translatable("screen.arksurvivalreturns.torpor",
                this.menu.rawTorpor(), this.menu.rawTorporMax()), 90, 74, LABEL, false);
    }
}
