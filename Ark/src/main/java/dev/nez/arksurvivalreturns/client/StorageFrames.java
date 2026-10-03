package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Drawn before the native panels: sidebar buttons and floating slots retain their backgrounds. */
public final class StorageFrames {
    public static String tomPanel(Object screen) {
        return switch (screen.getClass().getName()) {
            case "com.tom.storagemod.screen.StorageTerminalScreen" -> "storage_terminal";
            case "com.tom.storagemod.screen.CraftingTerminalScreen" -> "crafting_terminal";
            case "com.tom.storagemod.screen.InventoryLinkScreen" -> "inventory_link";
            case "com.tom.storagemod.screen.InventoryConfiguratorScreen" -> "inventory_configurator";
            case "com.tom.storagemod.screen.LevelEmitterScreen" -> "level_emitter";
            case "com.tom.storagemod.screen.TagItemFilterScreen" -> "tag_filter";
            default -> null;
        };
    }

    public static boolean themedTom(Object screen) {
        return ArkUiPack.enabled() && tomPanel(screen) != null;
    }

    public static void tomBackground(Object screen, GuiGraphicsExtractor graphics) {
        if (!themedTom(screen) || !(screen instanceof AbstractContainerScreen<?> container)) return;
        String panel = tomPanel(screen);
        int sourceHeight = switch (panel) {
            case "storage_terminal" -> 202;
            case "crafting_terminal" -> 256;
            default -> 166;
        };
        draw(graphics, container, ArkSurvivalReturns.id("textures/gui/storage/toms_storage/" + panel + "_frame.png"),
                sourceHeight, 44, 64, 36);
    }

    public static void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Identifier texture,
                            int sourceHeight, int margin, int top, int bottom) {
        int x = screen.getLeftPos() - margin, y = screen.getTopPos();
        int w = screen.getImageWidth() + margin * 2, h = screen.getImageHeight();
        int textureHeight = sourceHeight + top + bottom;
        // Keep slots at native scale. Only exterior ornament contracts when screen space is limited.
        int head = Math.min(top, Math.max(0, y));
        int foot = Math.min(bottom, Math.max(0, screen.height - y - h));
        if (head > 0) g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y - head, 0, 0,
                w, head, w, top, w, textureHeight);
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, top,
                w, h, w, sourceHeight, w, textureHeight);
        if (foot > 0) g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y + h, 0, top + sourceHeight,
                w, foot, w, bottom, w, textureHeight);
    }

    private StorageFrames() {}
}
