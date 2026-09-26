package dev.nez.arksurvivalreturns.client.title;

import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Framing of the menu creature from its shipped geometry. */
class ModelBoundsTest {
    @Test void tyrannosaurusIsLongerThanTallAndFacesPlusZ() throws Exception {
        String json = Files.readString(Path.of("src/main/resources/assets/arksurvivalreturns/geckolib/models/entity/tyrannosaurus.geo.json"));
        ModelBounds bounds = ModelBounds.of(JsonParser.parseString(json).getAsJsonObject());
        assertTrue(bounds.height() > 5 && bounds.height() < 30, "height " + bounds.height());
        assertTrue(bounds.maxZ() - bounds.minZ() > bounds.height(), "a rex is longer than it is tall");
        assertTrue(bounds.minY() > -1, "stands on its feet at y = 0");
        // The tail reaches further back (-Z) than the snout reaches forward (+Z).
        assertTrue(bounds.centerZ() < 0, "centre z " + bounds.centerZ());
        assertTrue(Math.abs(bounds.centerX()) < 1, "symmetric left to right");
        assertTrue(bounds.radius() >= (bounds.maxZ() - bounds.minZ()) / 2);
    }

    @Test void missingGeometryFallsBackToAUnitBox() {
        assertEquals(ModelBounds.UNIT, ModelBounds.of(JsonParser.parseString("{}").getAsJsonObject()));
    }
}
