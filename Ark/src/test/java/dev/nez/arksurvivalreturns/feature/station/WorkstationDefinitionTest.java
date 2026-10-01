package dev.nez.arksurvivalreturns.feature.station;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationDefinitionTest {
    @Test void VerbatimDesignAndRuleSupplementsReconstructShowcaseTrees() throws Exception {
        Gson gson = new Gson();
        for (String bench : new String[]{"armoury", "working_station", "mortar_and_pestle", "medicine_bench", "smithing_table"}) {
            var raw = gson.fromJson(Files.readString(Path.of("design/workstations/" + bench + ".json")), WorkstationDefinition.class);
            var rules = gson.fromJson(Files.readString(Path.of("build/workstations/resources/data/arksurvivalreturns/workstation_rules/" + bench + ".json")), WorkstationDefinition.class);
            var expected = gson.fromJson(Files.readString(Path.of("build/workstations/fixtures/" + bench + ".json")), WorkstationDefinition.class);
            var actual = raw.normalise(rules, item -> true);
            assertEquals(expected.categories().size(), actual.categories().size());
            var a = actual.crafts(); var e = expected.crafts(); assertEquals(e.size(), a.size(), bench);
            for (int n = 0; n < e.size(); n++) {
                assertEquals(e.get(n).category().id(), a.get(n).category().id(), bench);
                assertEquals(e.get(n).entry().family(), a.get(n).entry().family(), bench);
                assertEquals(e.get(n).variant().item(), a.get(n).variant().item(), bench);
                assertEquals(e.get(n).variant().cost(), a.get(n).variant().cost(), bench);
                assertEquals(e.get(n).variant().count(), a.get(n).variant().count(), bench);
                assertEquals(e.get(n).level(), a.get(n).level(), bench);
            }
        }
    }
    @Test void UnregisteredPlannedItemsDisappearButRegisteredArmouryBecomesUsable() throws Exception {
        Gson gson = new Gson();
        var raw = gson.fromJson(Files.readString(Path.of("design/workstations/working_station.json")), WorkstationDefinition.class);
        var resolved = raw.normalise(null, item -> item.equals("arksurvivalreturns:armoury") || !item.startsWith("arksurvivalreturns:"));
        var armoury = resolved.crafts().stream().filter(c -> c.variant().item().equals("arksurvivalreturns:armoury")).findFirst().orElseThrow();
        assertFalse(armoury.entry().planned());
        assertTrue(resolved.crafts().stream().noneMatch(c -> c.variant().item().equals("arksurvivalreturns:mechanical_press")));
    }
}
