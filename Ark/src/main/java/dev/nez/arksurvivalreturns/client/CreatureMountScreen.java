package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.feature.cargo.CargoTransferPayload;
import dev.nez.arksurvivalreturns.feature.taming.CreatureMountMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

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
    private static final int BUTTON_WIDTH = 36;
    private static final int BUTTON_HEIGHT = 14;
    private static final int UNLOAD_X = 136, UNLOAD_Y = 36;
    private static final int LOAD_X = 136, LOAD_Y = 54;
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
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, xo + 7, yo + 35, 18, 18);
        drawButton(graphics, xo + UNLOAD_X, yo + UNLOAD_Y, "screen.arksurvivalreturns.unload");
        drawButton(graphics, xo + LOAD_X, yo + LOAD_Y, "screen.arksurvivalreturns.load");
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
        graphics.text(this.font, Component.translatable("screen.arksurvivalreturns.cargo",
                this.menu.rawCargoMass(), this.menu.rawCargoMax()), 8, 68, LABEL, false);
    }

    /** Bulk transfer buttons; disabled while the creature is not resolvable on this side. */
    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, String key) {
        boolean enabled = this.menu.creature() != null;
        boolean hover = enabled && inside(this.xMouse, this.yMouse, x, y);
        graphics.fill(x - 4, y, x + BUTTON_WIDTH + 4, y + BUTTON_HEIGHT, hover ? 0xFF5A5A5A : 0xFF3A3A3A);
        Component text = Component.translatable(key);
        graphics.text(this.font, text, x + (BUTTON_WIDTH + 8 - this.font.width(text)) / 2, y + 3,
                enabled ? 0xFFEDEDED : 0xFF777777, false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - 4 && mouseX < x + BUTTON_WIDTH + 4 && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.menu.creature() != null) {
            double mouseX = event.x(), mouseY = event.y();
            if (inside(mouseX, mouseY, this.leftPos + UNLOAD_X, this.topPos + UNLOAD_Y)) {
                ClientPacketDistributor.sendToServer(new CargoTransferPayload(this.menu.creature().getId(), false));
                return true;
            }
            if (inside(mouseX, mouseY, this.leftPos + LOAD_X, this.topPos + LOAD_Y)) {
                ClientPacketDistributor.sendToServer(new CargoTransferPayload(this.menu.creature().getId(), true));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
