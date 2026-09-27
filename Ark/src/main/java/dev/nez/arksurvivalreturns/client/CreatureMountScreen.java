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
import java.util.List;
import java.util.Optional;
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
    /** Buttons sit between the chest grid (ends at x 133) and the panel frame (starts at x 172). */
    private static final int BUTTON_WIDTH = 32;
    private static final int BUTTON_HEIGHT = 14;
    private static final int UNLOAD_X = 137, UNLOAD_Y = 36;
    private static final int LOAD_X = 137, LOAD_Y = 54;
    /** The creature preview box, panel-relative. */
    private static final int PREVIEW_X = 26, PREVIEW_Y = 18, PREVIEW_SIZE = 52;
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
            // Vanilla's fixed 17 px per block suits a horse; scale so every species fits the 52 px box.
            int size = Math.clamp(Math.round(34f / Math.max(creature.getBbWidth(), creature.getBbHeight())), 4, 30);
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, xo + PREVIEW_X, yo + PREVIEW_Y,
                    xo + PREVIEW_X + PREVIEW_SIZE, yo + PREVIEW_Y + PREVIEW_SIZE, size, 0.25f, this.xMouse, this.yMouse, creature);
        }
    }

    /**
     * Title on the left, hunger right-aligned on the same row; the title is trimmed so a long name never runs into it.
     * Taming, torpor and cargo live in the preview's tooltip: the horse panel has no free row for them.
     */
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // extractLabels runs inside the pose already translated by leftPos/topPos, so these stay panel-relative.
        Component hunger = Component.translatable("screen.arksurvivalreturns.hunger", this.menu.rawHunger());
        int hungerX = this.imageWidth - 8 - this.font.width(hunger);
        graphics.text(this.font, this.font.plainSubstrByWidth(this.title.getString(), hungerX - 12 - this.titleLabelX),
                this.titleLabelX, this.titleLabelY, LABEL, false);
        graphics.text(this.font, hunger, hungerX, this.titleLabelY, LABEL, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL, false);
    }

    @Override protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!isHovering(PREVIEW_X, PREVIEW_Y, PREVIEW_SIZE, PREVIEW_SIZE, mouseX, mouseY)) return;
        int progress = this.menu.rawProgress();
        graphics.setTooltipForNextFrame(this.font, List.of(
                Component.translatable("screen.arksurvivalreturns.taming", progress / 100 + "." + (progress % 100) / 10 + "%"),
                Component.translatable("screen.arksurvivalreturns.torpor", this.menu.rawTorpor(), this.menu.rawTorporMax()),
                Component.translatable("screen.arksurvivalreturns.cargo", this.menu.rawCargoMass(), this.menu.rawCargoMax())),
                Optional.empty(), mouseX, mouseY);
    }

    /** Bulk transfer buttons; disabled while the creature is not resolvable on this side. */
    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, String key) {
        boolean enabled = this.menu.creature() != null;
        boolean hover = enabled && inside(this.xMouse, this.yMouse, x, y);
        graphics.fill(x - 2, y, x + BUTTON_WIDTH + 2, y + BUTTON_HEIGHT, hover ? 0xFF5A5A5A : 0xFF3A3A3A);
        Component text = Component.translatable(key);
        // Longer translations ("Descarregar") shrink to the button instead of spilling over the frame.
        int width = this.font.width(text);
        float scale = Math.min(1f, (BUTTON_WIDTH + 2f) / Math.max(1, width));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + BUTTON_WIDTH / 2f - width * scale / 2f, y + BUTTON_HEIGHT / 2f - 4f * scale);
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, text, 0, 0, enabled ? 0xFFEDEDED : 0xFF777777, false);
        graphics.pose().popMatrix();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - 2 && mouseX < x + BUTTON_WIDTH + 2 && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
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
