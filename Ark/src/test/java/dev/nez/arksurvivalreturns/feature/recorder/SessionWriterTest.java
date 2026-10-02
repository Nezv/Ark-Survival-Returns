package dev.nez.arksurvivalreturns.feature.recorder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SessionWriterTest {
    private static Row row(String type, long seq, int tick) {
        var row = new Row(type);
        row.seq = seq;
        row.tick = tick;
        return row;
    }

    private static List<String> lines(Path archive) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(archive)), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }

    @Test void aRowIsOneCompactJsonObject() {
        var row = row("s", 7, 42).put("e", 3).put("hp", 82.0f).put("x", 0.1).put("name", "a \"b\"\n").flag("near", true)
                .flag("far", false).xyz("p", 1.5, -2.0, 3.25).xyz("v", 0.0784f, 0f, -0.5f).block("home", 1, 2, 3)
                .put("nan", Double.NaN).put("big", 123456789012L).row("in", new Row("x").put("a", 1))
                .list("rows", List.of(new Object[]{1, 2.5, 3f}, new Object[]{"t", true}));
        row.nanos = 1500;
        var out = new StringBuilder();
        RowJson.line(out, row);
        assertEquals("{\"t\":\"s\",\"s\":7,\"k\":42,\"ns\":1500,\"e\":3,\"hp\":82,\"x\":0.1,\"name\":\"a \\\"b\\\"\\n\",\"near\":true,"
                + "\"p\":[1.5,-2,3.25],\"v\":[0.0784,0,-0.5],\"home\":[1,2,3],\"nan\":null,\"big\":123456789012,"
                + "\"in\":{\"a\":1},\"rows\":[[1,2.5,3],[\"t\",true]]}", out.toString());
    }

    @Test void aNormalEndWritesTheFooterAndLeavesOnlyTheVerifiedArchive(@TempDir Path directory) throws IOException {
        var writer = new SessionWriter(directory, 64, () -> false);
        writer.start();
        for (int i = 0; i < 20; i++) assertTrue(writer.offer(row("s", i, i / 2).put("e", i)));
        var seen = new SessionWriter.Result[1];
        writer.finish("deadline", result -> seen[0] = result);
        assertTrue(writer.await(10_000), "The writer never finished");
        var result = writer.result();
        assertSame(result, seen[0]);
        assertTrue(result.complete());
        assertTrue(result.compressed());
        assertEquals(20, result.rows());
        assertEquals(0, result.firstSeq());
        assertEquals(19, result.lastSeq());
        assertFalse(Files.exists(directory.resolve("session.jsonl")), "The plain file outlived its verified archive");
        var lines = lines(directory.resolve("session.jsonl.gz"));
        assertEquals(21, lines.size());
        assertEquals("{\"t\":\"s\",\"s\":0,\"k\":0,\"e\":0}", lines.getFirst());
        assertTrue(lines.getLast().startsWith("{\"t\":\"footer\",\"k\":0,\"reason\":\"deadline\",\"rows\":20,\"dropped\":0,\"first_seq\":0,\"last_seq\":19,"),
                lines.getLast());
    }

    @Test void aFullQueueDropsTheRowAndTheFooterSaysSo(@TempDir Path directory) throws IOException {
        var writer = new SessionWriter(directory, 4, () -> false);
        int accepted = 0;
        for (int i = 0; i < 6; i++) if (writer.offer(row("s", i, 0))) accepted++;
        assertEquals(4, accepted);
        assertEquals(2, writer.dropped());
        writer.start();
        writer.finish("deadline", null);
        assertTrue(writer.await(10_000));
        var result = writer.result();
        assertFalse(result.complete(), "A recording that lost rows was reported complete");
        assertEquals(4, result.rows());
        assertEquals(2, result.dropped());
        // The sequence numbers show where the hole is: 4 and 5 never arrived.
        assertEquals(3, result.lastSeq());
        assertTrue(lines(directory.resolve("session.jsonl.gz")).getLast().contains("\"dropped\":2"));
    }

    @Test void pausedTimeIsMeasuredBetweenRows(@TempDir Path directory) throws Exception {
        var paused = new boolean[]{true};
        var writer = new SessionWriter(directory, 4, () -> paused[0]);
        writer.start();
        Thread.sleep(300);
        paused[0] = false;
        Thread.sleep(150);
        long measured = writer.pausedNanos();
        assertTrue(measured > 150_000_000L && measured < 450_000_000L, "Paused time off: " + measured);
        writer.finish("cancelled", null);
        assertTrue(writer.await(10_000));
        assertNull(writer.result().file(), "A cancelled recording left a file");
        assertEquals(0, writer.result().rows());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
}
