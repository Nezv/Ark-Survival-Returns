package dev.nez.arksurvivalreturns.datagen;

import java.util.Map;

/** Labels of the "Ark Creature" FancyMenu element (client/title/fancymenu) in the layout editor. */
final class TitleData {
    private static final String KEY = "arksurvivalreturns.fancymenu.creature";

    static void messages(Map<String, String> en, Map<String, String> pt) {
        label(en, pt, "", "Ark Creature", "Criatura Ark");
        label(en, pt, ".desc", "An Ark creature standing in the menu scene: its idle animation, a head that looks around, lit by the scene's lightning.",
                "Uma criatura Ark na cena do menu: animação de repouso, cabeça que olha em volta, iluminada pelos relâmpagos da cena.");
        label(en, pt, ".menu", "Creature", "Criatura");
        label(en, pt, ".species", "Species ID", "ID da espécie");
        label(en, pt, ".texture_variant", "Skin", "Pele");
        label(en, pt, ".idle_clip", "Idle Animation", "Animação de repouso");
        label(en, pt, ".scene_x", "Scene X (screen heights from the centre)", "Cena X (alturas de tela a partir do centro)");
        label(en, pt, ".scene_ground", "Ground Line (screen heights from the bottom)", "Linha do chão (alturas de tela a partir da base)");
        label(en, pt, ".scene_height", "Height (screen heights)", "Altura (alturas de tela)");
        label(en, pt, ".body_yaw", "Body Yaw (0 faces you, 90 faces right)", "Rotação do corpo (0 de frente, 90 para a direita)");
        label(en, pt, ".camera_pitch", "Camera Pitch (positive looks down on it)", "Inclinação da câmera (positivo olha de cima)");
        label(en, pt, ".look_around", "Look Around: %s", "Olhar em volta: %s");
        label(en, pt, ".look_range", "Look Range (degrees)", "Alcance do olhar (graus)");
        label(en, pt, ".tint", "Night Tint [HEX]", "Tom noturno [HEX]");
        label(en, pt, ".lightning_boost", "Lightning Brightness", "Brilho dos relâmpagos");
        label(en, pt, ".thunder", "Thunder: %s", "Trovão: %s");
        label(en, pt, ".thunder_volume", "Thunder Volume", "Volume do trovão");
        label(en, pt, ".parallax", "Parallax (screen heights)", "Paralaxe (alturas de tela)");
    }

    private static void label(Map<String, String> en, Map<String, String> pt, String suffix, String english, String portuguese) {
        en.put(KEY + suffix, english);
        pt.put(KEY + suffix, portuguese);
    }

    private TitleData() {}
}
