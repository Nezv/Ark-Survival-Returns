package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.HashSet;
import java.util.Set;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BehaviorModelsTest {
    private static final JsonObject EXPORT = BehaviorModels.export(new BehaviorTier.Radii(64, 128, 256, 8), 13000, 23000, 0.5);

    private static JsonObject model(String id) {
        for (var m : EXPORT.getAsJsonArray("models")) if (m.getAsJsonObject().get("id").getAsString().equals(id)) return m.getAsJsonObject();
        throw new AssertionError("missing model " + id);
    }

    private static JsonObject matrix(String model, String tier) {
        for (var t : model(model).getAsJsonArray("tiers"))
            if (t.getAsJsonObject().get("tier").getAsString().equals(tier)) return t.getAsJsonObject().getAsJsonObject("matrix");
        throw new AssertionError("missing tier " + tier);
    }

    private static boolean has(JsonObject matrix, String from, String to) {
        return matrix.has(from) && matrix.getAsJsonObject(from).has(to);
    }

    @Test void everyModelDocumentsAllThreeTiers() {
        assertEquals(4, EXPORT.getAsJsonArray("models").size());
        for (var id : new String[]{"land_herbivore", "land_carnivore", "flyer", "aquatic"})
            for (var tier : new String[]{"FULL", "AMBIENT", "DORMANT"}) assertFalse(matrix(id, tier).isEmpty(), id + " " + tier);
        assertEquals(3, EXPORT.getAsJsonArray("tiers").size());
    }

    @Test void recordedMatricesContainTheEncounterChainsOfTheRealMind() {
        var carnivore = matrix("land_carnivore", "FULL");
        assertTrue(has(carnivore, "ALERT", "THREATEN"));
        assertTrue(has(carnivore, "THREATEN", "HUNT"));
        assertTrue(has(carnivore, "HUNT", "RETURN_HOME"));
        assertTrue(has(carnivore, "HUNT", "FEED"), "a kill ends the hunt at the carcass");
        assertTrue(has(carnivore, "SLEEP", "ALERT"), "woken sleepers are documented");
        var herbivore = matrix("land_herbivore", "FULL");
        assertTrue(has(herbivore, "ROAM", "ALERT"));
        assertTrue(has(herbivore, "ALERT", "FLEE") || has(herbivore, "ROAM", "FLEE"));
        assertTrue(has(herbivore, "FLEE", "REGROUP") || has(herbivore, "FLEE", "DEFEND"));
        assertFalse(herbivore.has("HUNT") || has(herbivore, "ROAM", "HUNT"), "herbivores never hunt");
        var water = matrix("aquatic", "FULL");
        assertFalse(water.has("SLEEP") || water.has("DRINK") || water.has("FORAGE") || water.has("SEEK_WATER"),
                "water model has no sleep, thirst or graze");
        assertFalse(matrix("land_carnivore", "AMBIENT").has("GRAZE"), "carnivores never graze, so no graze row");
    }

    @Test void flyersShowOneSampleLapPerCurveKind() {
        var curves = model("flyer").getAsJsonArray("curves");
        assertEquals(FlightPath.Kind.values().length, curves.size());
        for (var c : curves) {
            var points = c.getAsJsonObject().getAsJsonArray("points");
            var first = points.get(0).getAsJsonArray();
            var last = points.get(points.size() - 1).getAsJsonArray();
            if (!c.getAsJsonObject().get("kind").getAsString().equals("THERMAL"))
                assertEquals(first.toString(), last.toString(), "a lap closes on itself");
            else assertTrue(last.get(1).getAsDouble() > first.get(1).getAsDouble(), "a thermal gains height");
        }
    }

    @Test void everyCellQuotesARealRule() {
        Set<String> labels = new HashSet<>();
        for (var reason : WildlifeMind.Reason.values()) labels.add(reason.label());
        labels.add("woken by a hit, a noise or a close player");
        for (var id : new String[]{"land_herbivore", "land_carnivore", "aquatic"}) {
            var full = matrix(id, "FULL");
            for (var from : full.keySet())
                for (var to : full.getAsJsonObject(from).keySet())
                    for (var why : full.getAsJsonObject(from).getAsJsonArray(to)) assertTrue(labels.contains(why.getAsString()), why.getAsString());
        }
    }

    @Test void bridgesShowTheCadenceRecordedFromTheChoreographer() {
        boolean rexWarned = false, rexChargedWithoutSecondRoar = false, preyStartled = false;
        for (var b : model("land_carnivore").getAsJsonArray("bridges")) {
            var bridge = b.getAsJsonObject();
            var beats = bridge.getAsJsonArray("beats");
            String first = beats.get(0).getAsJsonObject().get("action").getAsString();
            String last = beats.get(beats.size() - 1).getAsJsonObject().get("action").getAsString();
            if (bridge.get("to").getAsString().equals("THREATEN")) rexWarned = beats.toString().contains("ROAR");
            if (bridge.get("from").getAsString().equals("THREATEN") && bridge.get("to").getAsString().equals("HUNT"))
                rexChargedWithoutSecondRoar = first.equals("CHASE") && !beats.toString().contains("ROAR");
            if (bridge.get("to").getAsString().equals("HUNT")) assertEquals("CHASE", last);
        }
        for (var b : model("land_herbivore").getAsJsonArray("bridges")) {
            var bridge = b.getAsJsonObject();
            if (bridge.get("from").getAsString().equals("ROAM") && bridge.get("to").getAsString().equals("FLEE"))
                preyStartled = bridge.getAsJsonArray("beats").get(0).getAsJsonObject().get("action").getAsString().equals("STARTLE");
        }
        assertTrue(rexWarned && rexChargedWithoutSecondRoar && preyStartled);
    }
}
