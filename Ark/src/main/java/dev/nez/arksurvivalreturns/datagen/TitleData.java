package dev.nez.arksurvivalreturns.datagen;

import java.util.Map;

/** Labels of the "Ark Creature" FancyMenu element (client/title/fancymenu) in the layout editor. */
final class TitleData {
    private static final String KEY = "arksurvivalreturns.fancymenu.creature";

    static void messages(Map<String, String> en) {
        label(en, "", "Ark Creature");
        label(en, ".desc", "An Ark creature standing in the menu scene: its idle animation, a head that looks around, lit by the scene's lightning.");
        label(en, ".menu", "Creature");
        label(en, ".species", "Species ID");
        label(en, ".texture_variant", "Skin");
        label(en, ".idle_clip", "Idle Animation");
        label(en, ".scene_x", "Scene X (screen heights from the centre)");
        label(en, ".scene_ground", "Ground Line (screen heights from the bottom)");
        label(en, ".scene_height", "Height (screen heights)");
        label(en, ".body_yaw", "Body Yaw (0 faces you, 90 faces right)");
        label(en, ".camera_pitch", "Camera Pitch (positive looks down on it)");
        label(en, ".look_around", "Look Around: %s");
        label(en, ".look_range", "Look Range (degrees)");
        label(en, ".tint", "Night Tint [HEX]");
        label(en, ".lightning_boost", "Lightning Brightness");
        label(en, ".thunder", "Thunder: %s");
        label(en, ".thunder_volume", "Thunder Volume");
        label(en, ".parallax", "Parallax (screen heights)");
    }

    private static void label(Map<String, String> en, String suffix, String english) {
        en.put(KEY + suffix, english);
    }

    private TitleData() {}
}
