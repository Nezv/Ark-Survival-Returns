package dev.nez.arksurvivalreturns.feature.accessory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks every worn accessory model written by tools/build_accessories.py against what the client loader
 * (client/accessory/WornModels) accepts: known parts, fits and animations, whole-texel cube sizes, and
 * box UVs that stay inside a texture of the declared size. Plain JUnit (no Minecraft on the classpath), so
 * it walks the files; the accessory_catalog GameTest checks that every registered accessory has them.
 */
class WornModelFilesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/arksurvivalreturns");
    private static final Set<String> PARTS = Set.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    private static final Set<String> FITS = Set.of("none", "helmet", "chest", "legs", "boots");
    private static final Set<String> ANIMS = Set.of("sway", "cape", "wing");
    private static final Set<String> FACES = Set.of("down", "up", "north", "south", "east", "west");

    private static java.util.List<String> ids() throws Exception {
        try (var files = Files.list(ASSETS.resolve("worn"))) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        }
    }

    @Test void everyWornModelHasAnIconAndTexture() throws Exception {
        var ids = ids();
        assertTrue(ids.size() >= 36, "the accessory catalogue has at least 36 worn models");
        for (String id : ids) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("textures/entity/accessory/" + id + ".png")), id + " texture");
            var icon = ImageIO.read(ASSETS.resolve("textures/item/accessory/" + id + ".png").toFile());
            assertEquals(16, icon.getWidth(), id + " icon width");
            assertEquals(16, icon.getHeight(), id + " icon height");
        }
    }

    @Test void wornModelsFitTheLoader() throws Exception {
        for (String id : ids()) {
            JsonObject json = JsonParser.parseString(Files.readString(ASSETS.resolve("worn/" + id + ".json"))).getAsJsonObject();
            int texW = json.getAsJsonArray("texture_size").get(0).getAsInt();
            int texH = json.getAsJsonArray("texture_size").get(1).getAsInt();
            var texture = ImageIO.read(ASSETS.resolve("textures/entity/accessory/" + id + ".png").toFile());
            assertEquals(texW, texture.getWidth(), id + " texture width matches the model");
            assertEquals(texH, texture.getHeight(), id + " texture height matches the model");
            if (json.has("glow") && json.get("glow").getAsBoolean()) {
                assertTrue(Files.isRegularFile(ASSETS.resolve("textures/entity/accessory/" + id + "_glow.png")), id + " glow texture");
            }
            Set<String> names = new HashSet<>();
            int cubes = 0;
            for (JsonElement element : json.getAsJsonArray("bones")) {
                JsonObject bone = element.getAsJsonObject();
                String name = bone.get("name").getAsString();
                assertTrue(names.add(name), id + " repeats bone " + name);
                assertTrue(PARTS.contains(bone.get("part").getAsString()), id + "/" + name + " part");
                if (bone.has("fit")) assertTrue(FITS.contains(bone.get("fit").getAsString()), id + "/" + name + " fit");
                if (bone.has("anim")) assertTrue(ANIMS.contains(bone.get("anim").getAsString()), id + "/" + name + " anim");
                if (bone.has("side")) assertTrue(Set.of("right", "left").contains(bone.get("side").getAsString()), id + "/" + name + " side");
                if (bone.has("arms")) assertTrue(Set.of("wide", "slim").contains(bone.get("arms").getAsString()), id + "/" + name + " arms");
                if ("wing".equals(bone.has("anim") ? bone.get("anim").getAsString() : null)) assertTrue(bone.has("open"), id + "/" + name + " open pose");
                for (JsonElement c : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = c.getAsJsonObject();
                    var size = cube.getAsJsonArray("size");
                    int w = size.get(0).getAsInt(), h = size.get(1).getAsInt(), d = size.get(2).getAsInt();
                    for (int k = 0; k < 3; k++) assertEquals(size.get(k).getAsDouble(), size.get(k).getAsInt(), 1e-9, id + " whole-texel sizes");
                    int u = cube.getAsJsonArray("uv").get(0).getAsInt(), v = cube.getAsJsonArray("uv").get(1).getAsInt();
                    assertTrue(u >= 0 && v >= 0, id + " uv origin");
                    assertTrue(u + 2 * (w + d) <= texW, id + "/" + name + " uv runs off the right edge");
                    assertTrue(v + d + h <= texH, id + "/" + name + " uv runs off the bottom edge");
                    if (cube.has("faces")) for (JsonElement f : cube.getAsJsonArray("faces"))
                        assertTrue(FACES.contains(f.getAsString().toLowerCase(Locale.ROOT)), id + " face name");
                    cubes++;
                }
            }
            assertTrue(cubes > 0, id + " has no cubes");
        }
    }
}
