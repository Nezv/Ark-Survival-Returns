package dev.nez.arksurvivalreturns.client;

import java.util.List;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.station.InventoryCrafting;
import dev.nez.arksurvivalreturns.feature.station.WorkstationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Craft button in the place of the inventory's 2x2 grid, in the vanilla and the Curios inventory screens alike: it
 * opens the inventory's own crafting graph ({@link InventoryCrafting}). The recipe book only served the grid, so it is
 * closed and its button removed.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class InventoryCraftButton extends AbstractWidget {
    private static final Identifier SIGIL = ArkSurvivalReturns.id("textures/gui/workstation/inventory_sigil.png");
    // A carved key in the workstation palette (design/workstations/graph_style.json), like the graph's own buttons.
    private static final int OUTLINE = 0xff050604, LIGHT = 0xff3a4034, DARK = 0xff0b0c0a, FACE = 0xff1a1d18,
            HOT = 0xff4b6641, HOT_LIGHT = 0xff8fb07f, TEXT = 0xffe6e8e1;
    private final AbstractContainerScreen<?> screen;

    private InventoryCraftButton(AbstractContainerScreen<?> screen) {
        super(0, 0, 0, 0, Component.literal("Craft"));
        this.screen = screen;
        setTooltip(Tooltip.create(Component.literal("Open the inventory's crafting graph")));
        place();
    }

    /** A survival inventory screen: its menu still carries the hidden 2x2 grid. */
    static boolean inventory(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> container && !(screen instanceof CreativeModeInventoryScreen)
                && container.getMenu().slots.stream().anyMatch(InventoryCrafting::handGrid);
    }

    @SubscribeEvent static void closeRecipeBook(ScreenEvent.Init.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player != null && inventory(event.getScreen())) player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
    }

    @SubscribeEvent static void addButton(ScreenEvent.Init.Post event) {
        if (!inventory(event.getScreen())) return;
        for (var listener : List.copyOf(event.getListenersList()))
            if (listener instanceof ImageButton book && book.getWidth() == 20 && book.getHeight() == 18) event.removeListener(book);
        event.addListener(new InventoryCraftButton((AbstractContainerScreen<?>) event.getScreen()));
    }

    /** Over the grid, its arrow and its result: the box the hidden slots span, wherever the screen put them. */
    private void place() {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (Slot slot : screen.getMenu().slots) {
            if (!InventoryCrafting.handGrid(slot)) continue;
            x0 = Math.min(x0, slot.x); y0 = Math.min(y0, slot.y); x1 = Math.max(x1, slot.x + 16); y1 = Math.max(y1, slot.y + 16);
        }
        visible = x0 < x1;
        if (visible) setRectangle(x1 - x0 + 4, y1 - y0 + 4, screen.getLeftPos() + x0 - 2, screen.getTopPos() + y0 - 2);
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        place();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused();
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? HOT : FACE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hot ? HOT_LIGHT : LIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, DARK);
        int icon = Math.min(32, h - 6);
        g.blit(RenderPipelines.GUI_TEXTURED, SIGIL, x + 4, y + (h - icon) / 2, 0f, 0f, icon, icon, icon, icon);
        var font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        g.text(font, label, x + 8 + icon + (w - 12 - icon - font.width(label)) / 2, y + (h - 8) / 2, TEXT, true);
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        ClientPacketDistributor.sendToServer(new WorkstationPayload.OpenInventory());
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
