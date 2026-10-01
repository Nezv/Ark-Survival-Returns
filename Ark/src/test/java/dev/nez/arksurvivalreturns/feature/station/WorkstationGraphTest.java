package dev.nez.arksurvivalreturns.feature.station;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationGraphTest {
    @Test void threeSavedClickSequencesMatchNodeOracle() throws Exception {
        Gson gson = new Gson();
        Path bench = Path.of("build/workstations/fixtures/armoury.json");
        Path style = Path.of("design/workstations/graph_style.json");
        WorkstationDefinition definition = gson.fromJson(Files.readString(bench), WorkstationDefinition.class);
        WorkstationStyle settings = gson.fromJson(Files.readString(style), WorkstationStyle.class);
        List<List<String>> sequences = gson.fromJson(Files.readString(Path.of("src/test/resources/workstation_clicks.json")),
                new TypeToken<List<List<String>>>() {}.getType());
        assertEquals(3, sequences.size());
        for (List<String> sequence : sequences) {
            List<String> command = new ArrayList<>(List.of("node", "tools/workstation_graph.js", bench.toString(), style.toString()));
            command.addAll(sequence);
            Process node = new ProcessBuilder(command).redirectErrorStream(true).start();
            assertTrue(node.waitFor(30, TimeUnit.SECONDS), "Node oracle timed out");
            String output = new String(node.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, node.exitValue(), output);
            JsonObject expected = gson.fromJson(output, JsonObject.class);
            WorkstationGraph graph = new WorkstationGraph(definition, settings); graph.level = 99;
            for (String key : sequence) {
                var clicked = graph.find(key);
                if (!key.equals("back")) assertNotNull(clicked, "Saved click no longer exists: " + key);
                graph.click(clicked); graph.settle(900);
            }
            assertEquals(expected.get("ticks").getAsInt(), graph.ticks, sequence.toString());
            var nodes = expected.getAsJsonArray("nodes"); assertEquals(nodes.size(), graph.nodes.size());
            for (int i = 0; i < nodes.size(); i++) {
                var golden = nodes.get(i).getAsJsonObject(); var actual = graph.nodes.get(i);
                assertEquals(golden.get("key").getAsString(), actual.key);
                assertEquals(golden.get("x").getAsDouble(), Math.floor(actual.x * 100 + .5) / 100, 0.000001, actual.key);
                assertEquals(golden.get("y").getAsDouble(), Math.floor(actual.y * 100 + .5) / 100, 0.000001, actual.key);
            }
        }
    }
}
