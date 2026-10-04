package dev.nez.arksurvivalreturns.client.title;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The FancyMenu layouts (written by tools/build_title_scene.py): the universal scene layout and the title
 * screen's, read the way FancyMenu 3.9.12's PropertiesParser reads them. FancyMenu drops what it does not
 * understand without a word, so this catches a mistyped key or a shader that no longer matches its source
 * before anyone opens the client.
 */
class TitleLayoutTest {
    private static final Path SCENE = Path.of("config/fancymenu/customization/ark_menu_scene.txt");
    private static final Path LAYOUT = Path.of("config/fancymenu/customization/ark_title_screen.txt");
    private static final String NEWLINE = "%%!serialized_property_newline!%%";

    record Container(String type, Map<String, String> values) {
        String get(String key) { return values.get(key); }
    }

    /** PropertiesParser.deserializeSetFromFancyString, minus the legacy branches. */
    static List<Container> parse(String text) {
        List<Container> containers = new ArrayList<>();
        Container current = null;
        for (String line : text.replace("\r", "\n").split("\n")) {
            String compact = line.replaceAll("[\\p{Z}\\s]+", "");
            if (compact.endsWith("{")) {
                assertNull(current, "a container opens inside another: " + compact);
                current = new Container(compact.substring(0, compact.length() - 1), new LinkedHashMap<>());
            } else if (compact.startsWith("}") && current != null) {
                containers.add(current);
                current = null;
            } else if (current != null && compact.contains("=")) {
                String value = line.split("=", 2)[1];
                current.values().put(compact.split("=", 2)[0], value.startsWith(" ") ? value.substring(1) : value);
            }
        }
        assertNull(current, "the last container is not closed");
        return containers;
    }

    private static List<Container> layout() throws Exception { return layout(LAYOUT); }

    private static List<Container> scene() throws Exception { return layout(SCENE); }

    private static List<Container> layout(Path file) throws Exception {
        String text = Files.readString(file).replace("\r\n", "\n"); // a Windows checkout has CRLF endings
        assertTrue(text.startsWith("type = fancymenu_layout\n"));
        return parse(text);
    }

    private static Container meta(List<Container> containers) {
        return containers.stream().filter(c -> c.type().equals("layout-meta")).findFirst().orElseThrow();
    }

    private static Container element(List<Container> containers, String type) {
        return containers.stream().filter(c -> type.equals(c.get("element_type"))).findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " element"));
    }

    /** FancyMenu applies layouts only to screens switched on in customizablemenus.txt, keyed by class name. */
    @Test void menuScreenCustomizationIsSwitchedOn() throws Exception {
        String text = Files.readString(Path.of("config/fancymenu/customizablemenus.txt")).replace("\r\n", "\n");
        assertTrue(text.startsWith("type = customizablemenus\n"));
        Set<String> screens = new TreeSet<>();
        for (Container c : parse(text)) screens.add(c.type());
        // The title screen and the three screens MenuColumn lays out (client/mixin/MenuColumnMixin).
        for (String screen : List.of("net.minecraft.client.gui.screens.TitleScreen",
                "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen",
                "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen",
                "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen")) {
            assertTrue(screens.contains(screen), screen + " must be customizable, or FancyMenu draws no scene on it");
        }
    }

    @Test void sceneLayoutIsUniversalWithTheShaderBackground() throws Exception {
        List<Container> containers = scene();
        assertEquals("%fancymenu:universal_layout%", meta(containers).get("identifier"), "one scene for every menu");
        assertEquals("true", meta(containers).get("render_custom_elements_behind_vanilla"), "the scene stays behind the buttons");
        Container background = containers.stream().filter(c -> c.type().equals("menu_background")).findFirst().orElseThrow();
        assertEquals("glsl", background.get("background_type"));
        assertEquals("resource0", background.get("image_ichannel0_input"), "unrouted channels sample FancyMenu's missing texture");
        assertEquals("resource1", background.get("image_ichannel1_input"));
    }

    @Test void titleLayoutLeavesTheSceneToTheUniversalLayout() throws Exception {
        List<Container> containers = layout();
        assertEquals("title_screen", meta(containers).get("identifier"));
        assertEquals("true", meta(containers).get("render_custom_elements_behind_vanilla"), "the logo stays behind the buttons");
        assertTrue(containers.stream().noneMatch(c -> c.type().equals("menu_background")), "a second background would draw the scene twice");
        assertTrue(Integer.parseInt(meta(containers).get("layout_index")) > Integer.parseInt(meta(scene()).get("layout_index")),
                "the logo draws over the scene");
    }

    @Test void inlineShadersAreTheShaderSources() throws Exception {
        List<Container> containers = scene();
        Container background = containers.stream().filter(c -> c.type().equals("menu_background")).findFirst().orElseThrow();
        assertShader("background", background);
        assertShader("foreground", element(containers, "glsl_shader"));
    }

    private static void assertShader(String name, Container container) throws Exception {
        String inline = container.get("inline_shader_source");
        assertNotNull(inline, name + " shader");
        assertFalse(inline.contains("\n"), "one line in the layout file");
        String source = Files.readString(Path.of("tools/title_scene/" + name + ".glsl")).replace("\r\n", "\n").strip();
        assertEquals(source, inline.replace(NEWLINE, "\n"), name + ".glsl changed: rerun tools/build_title_scene.py --layout");
        assertTrue(source.contains("void mainImage(out vec4 fragColor, in vec2 fragCoord)"), "Shadertoy entry point");
        assertEquals("shadertoy", container.get("compile_mode"));
    }

    @Test void creatureElementUsesTheElementsOwnKeys() throws Exception {
        String element = Files.readString(Path.of("src/main/java/dev/nez/arksurvivalreturns/client/title/fancymenu/CreatureElement.java"));
        Set<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("Property\\.\\w+Property\\(\"(\\w+)\"").matcher(element);
        while (m.find()) keys.add(m.group(1));
        assertTrue(keys.size() >= 15, "creature properties found: " + keys);
        Set<String> base = Set.of("element_type", "instance_identifier", "anchor_point", "x", "y", "width", "height",
                "stretch_x", "stretch_y", "stay_on_screen");
        Container creature = element(scene(), "arksurvivalreturns_creature");
        for (String key : creature.values().keySet()) {
            assertTrue(base.contains(key) || keys.contains(key), "CreatureElement has no property " + key);
        }
        assertTrue(creature.values().keySet().containsAll(keys), "every creature setting is written out");
        assertEquals("true", creature.get("stretch_x"));
        assertEquals("true", creature.get("stretch_y"), "stretched, so scene units match the shaders");
    }

    @Test void everyTitleButtonIsPlacedOrHidden() throws Exception {
        Map<String, Container> buttons = new LinkedHashMap<>();
        for (Container c : layout()) if (c.type().equals("vanilla_button")) buttons.put(c.get("instance_identifier"), c);
        for (String id : List.of("mc_titlescreen_singleplayer_button", "mc_titlescreen_multiplayer_button",
                "forge_titlescreen_mods_button", "mc_titlescreen_options_button", "mc_titlescreen_quit_button")) {
            Container button = buttons.get(id);
            assertNotNull(button, id);
            assertEquals("mid-left", button.get("anchor_point"), id);
            assertEquals("false", button.get("is_hidden"), id);
        }
        for (String id : List.of("mc_titlescreen_language_button", "mc_titlescreen_accessibility_button",
                "mc_titlescreen_realms_button", "minecraft_logo_widget", "minecraft_splash_widget")) {
            assertEquals("true", buttons.get(id).get("is_hidden"), id);
        }
    }
}
