package dev.nez.arksurvivalreturns.client.title.fancymenu;

import de.keksuccino.fancymenu.customization.ScreenCustomization;
import de.keksuccino.fancymenu.customization.element.ElementRegistry;
import net.minecraft.client.gui.screens.Screen;

/**
 * Ark's FancyMenu elements. Only touched when FancyMenu is loaded. They are registered during mod construction:
 * FancyMenu reads the layouts after every mod has been constructed and drops elements whose type it does not know.
 */
public final class FancyMenuElements {
    public static void register() {
        ElementRegistry.register(new CreatureElementBuilder());
    }

    /** Whether FancyMenu lays this screen out: the screens listed in customizablemenus.txt, which show the menu scene. */
    public static boolean customizes(Screen screen) {
        return ScreenCustomization.isCustomizationEnabledForScreen(screen);
    }

    private FancyMenuElements() {}
}
