package dev.nez.arksurvivalreturns.client.title.fancymenu;

import de.keksuccino.fancymenu.customization.element.editor.AbstractEditorElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import de.keksuccino.fancymenu.util.properties.Property;
import de.keksuccino.fancymenu.util.rendering.ui.contextmenu.v2.ContextMenu;
import net.minecraft.network.chat.Component;

/** The creature's settings in the FancyMenu layout editor, under one "Creature" submenu. */
public class CreatureEditorElement extends AbstractEditorElement<CreatureEditorElement, CreatureElement> {
    public CreatureEditorElement(CreatureElement element, LayoutEditorScreen editor) { super(element, editor); }

    @Override
    public void init() {
        super.init();
        ContextMenu settings = new ContextMenu();
        this.rightClickMenu.addSubMenuEntry("ark_creature_settings", Component.translatable(CreatureElement.KEY + "menu"), settings);
        for (Property<?> property : this.element.settingProperties()) property.buildContextMenuEntryAndAddTo(settings, this);
    }
}
