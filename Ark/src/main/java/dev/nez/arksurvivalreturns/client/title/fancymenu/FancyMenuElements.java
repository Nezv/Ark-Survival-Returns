package dev.nez.arksurvivalreturns.client.title.fancymenu;

import de.keksuccino.fancymenu.customization.element.ElementRegistry;

/**
 * Ark's FancyMenu elements. Only touched when FancyMenu is loaded, and during mod construction: FancyMenu reads
 * the layouts after every mod has been constructed and drops elements whose type it does not know.
 */
public final class FancyMenuElements {
    public static void register() {
        ElementRegistry.register(new CreatureElementBuilder());
    }

    private FancyMenuElements() {}
}
