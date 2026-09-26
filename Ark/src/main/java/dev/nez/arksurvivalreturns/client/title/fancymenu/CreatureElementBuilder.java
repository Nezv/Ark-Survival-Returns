package dev.nez.arksurvivalreturns.client.title.fancymenu;

import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.customization.element.ElementBuilder;
import de.keksuccino.fancymenu.customization.element.SerializedElement;
import de.keksuccino.fancymenu.customization.layout.editor.LayoutEditorScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Registers "Ark Creature" with FancyMenu; its properties (de)serialise themselves through the property map. */
public class CreatureElementBuilder extends ElementBuilder<CreatureElement, CreatureEditorElement> {
    /** element_type in layout files. */
    public static final String ID = "arksurvivalreturns_creature";

    public CreatureElementBuilder() { super(ID); }

    @Override
    public CreatureElement buildDefaultInstance() {
        CreatureElement element = new CreatureElement(this);
        element.baseWidth = 320;
        element.baseHeight = 180;
        return element;
    }

    @Override public CreatureElement deserializeElement(SerializedElement serialized) { return buildDefaultInstance(); }

    @Override protected SerializedElement serializeElement(CreatureElement element, SerializedElement serializeTo) { return serializeTo; }

    @Override
    public CreatureEditorElement wrapIntoEditorElement(CreatureElement element, LayoutEditorScreen editor) {
        return new CreatureEditorElement(element, editor);
    }

    @Override public Component getDisplayName(@Nullable AbstractElement element) { return Component.translatable("arksurvivalreturns.fancymenu.creature"); }

    @Override
    public Component[] getDescription(@Nullable AbstractElement element) {
        return new Component[]{Component.translatable("arksurvivalreturns.fancymenu.creature.desc")};
    }
}
