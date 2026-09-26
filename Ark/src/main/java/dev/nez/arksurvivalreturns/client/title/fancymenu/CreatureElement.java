package dev.nez.arksurvivalreturns.client.title.fancymenu;

import java.util.List;
import java.util.Objects;
import de.keksuccino.fancymenu.customization.element.AbstractElement;
import de.keksuccino.fancymenu.customization.element.ElementBuilder;
import de.keksuccino.fancymenu.util.properties.Property;
import de.keksuccino.fancymenu.util.rendering.DrawableColor;
import dev.nez.arksurvivalreturns.client.title.TitleScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * FancyMenu layout element "Ark Creature": an Ark creature standing in the menu scene, idling and looking
 * around, lit by the scene's lightning ({@link TitleScene}). Stretch it over the screen and place the creature
 * in scene units, the units of the title shaders.
 */
public class CreatureElement extends AbstractElement {
    static final String KEY = "arksurvivalreturns.fancymenu.creature.";

    public final Property.StringProperty creature = putProperty(Property.stringProperty("creature", "tyrannosaurus", false, false, KEY + "species"));
    public final Property.StringProperty textureVariant = putProperty(Property.stringProperty("texture_variant", "midnight", false, false, KEY + "texture_variant"));
    public final Property.StringProperty idleClip = putProperty(Property.stringProperty("idle_clip", "", false, false, KEY + "idle_clip"));
    public final Property.FloatProperty sceneX = putProperty(Property.floatProperty("scene_x", 0.5F, KEY + "scene_x"));
    public final Property.FloatProperty sceneGround = putProperty(Property.floatProperty("scene_ground", 0.16F, KEY + "scene_ground"));
    public final Property.FloatProperty sceneHeight = putProperty(Property.floatProperty("scene_height", 0.4F, KEY + "scene_height"));
    public final Property.FloatProperty bodyYaw = putProperty(Property.floatProperty("body_yaw", -38.0F, KEY + "body_yaw"));
    public final Property.FloatProperty cameraPitch = putProperty(Property.floatProperty("camera_pitch", -5.0F, KEY + "camera_pitch"));
    public final Property.BooleanProperty lookAround = putProperty(Property.booleanProperty("look_around", true, KEY + "look_around"));
    public final Property.FloatProperty lookRange = putProperty(Property.floatProperty("look_range", 38.0F, KEY + "look_range"));
    public final Property.ColorProperty tint = putProperty(Property.hexColorProperty("tint", "#6E6A68", false, KEY + "tint"));
    public final Property.FloatProperty lightningBoost = putProperty(Property.floatProperty("lightning_boost", 0.8F, KEY + "lightning_boost"));
    public final Property.BooleanProperty thunder = putProperty(Property.booleanProperty("thunder", true, KEY + "thunder"));
    public final Property.FloatProperty thunderVolume = putProperty(Property.floatProperty("thunder_volume", 0.7F, KEY + "thunder_volume"));
    public final Property.FloatProperty parallax = putProperty(Property.floatProperty("parallax", 0.012F, KEY + "parallax"));

    private final TitleScene scene = new TitleScene();

    public CreatureElement(ElementBuilder<?, ?> builder) { super(builder); }

    /** Every setting, in the order the editor's right-click menu lists them. */
    List<Property<?>> settingProperties() {
        return List.of(creature, textureVariant, idleClip, sceneX, sceneGround, sceneHeight, bodyYaw, cameraPitch, lookAround,
                lookRange, tint, lightningBoost, thunder, thunderVolume, parallax);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        if (!this.shouldRender()) return;
        DrawableColor color = tint.getDrawable();
        TitleScene.Settings settings = new TitleScene.Settings(
                Objects.requireNonNullElse(creature.getString(), "tyrannosaurus").trim(),
                Objects.requireNonNullElse(textureVariant.getString(), "").trim(),
                Objects.requireNonNullElse(idleClip.getString(), "").trim(),
                sceneX.getFloat(), sceneGround.getFloat(), sceneHeight.getFloat(), bodyYaw.getFloat(), cameraPitch.getFloat(),
                lookAround.getBoolean(), lookRange.getFloat(), color == null ? 0xFFFFFFFF : color.getColorInt() | 0xFF000000,
                lightningBoost.getFloat(), thunder.getBoolean(), thunderVolume.getFloat(), parallax.getFloat());
        scene.extract(graphics, settings, getAbsoluteX(), getAbsoluteY(), getAbsoluteWidth(), getAbsoluteHeight(),
                mouseX, mouseY, partial, isEditor());
    }
}
