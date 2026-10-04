package dev.nez.arksurvivalreturns.client.title;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;

/**
 * The left column of the menu scene. The title screen keeps its buttons there (tools/build_title_scene.py);
 * the world and server menus, which vanilla centres on the screen, are centred on the same column instead, so
 * the camp and the creature stay in view. Only where FancyMenu draws the scene.
 */
public final class MenuColumn {
    /** The title buttons' left margin on both sides of the widest vanilla footer row. */
    public static final int WIDTH = 24 + 308 + 24;
    private static boolean fancyMenu = ModList.get().isLoaded("fancymenu");

    /** The column's width on this screen, or 0 where the screen keeps the vanilla layout. */
    public static int width(Screen screen) {
        if (!fancyMenu || screen.width <= WIDTH || Minecraft.getInstance().level != null) return 0;
        try {
            return dev.nez.arksurvivalreturns.client.title.fancymenu.FancyMenuElements.customizes(screen) ? WIDTH : 0;
        } catch (LinkageError e) {
            fancyMenu = false;
            ArkSurvivalReturns.LOGGER.warn("This FancyMenu version does not fit the Ark menu column; the menus keep the vanilla layout", e);
            return 0;
        }
    }

    /** Moves widgets that vanilla centred on the screen to the column; a list is narrowed to it. */
    public static void apply(Screen screen, Layout layout) {
        int column = width(screen);
        if (column == 0) return;
        int shift = (screen.width - column) / 2;
        layout.visitWidgets(widget -> {
            if (widget instanceof AbstractSelectionList<?> list) list.updateSizeAndPosition(column, list.getHeight(), 0, list.getY());
            else widget.setX(widget.getX() - shift);
        });
    }

    private MenuColumn() {}
}
