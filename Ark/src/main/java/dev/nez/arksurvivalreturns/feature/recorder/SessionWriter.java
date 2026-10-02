package dev.nez.arksurvivalreturns.feature.recorder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The only thread that touches the disk. Rows arrive on a bounded queue; a full queue drops the row and
 * counts it instead of stalling the game. Lines reach the operating system about once a second, so an
 * abrupt exit keeps every complete line written so far. A normal end appends a footer with the counts,
 * then compresses the file and deletes the plain copy only after the archive reads back identical.
 *
 * <p>The same thread samples the pause flag between rows: a paused integrated server runs no ticks, so
 * the game thread cannot time its own pauses.
 */
public final class SessionWriter {
    /** What ended up on disk. {@code complete} is false when a row was dropped or the writer failed. */
    public record Result(Path file, long rows, long bytes, long dropped, long firstSeq, long lastSeq,
            boolean compressed, String failure) {
        public boolean complete() { return failure == null && dropped == 0; }
    }

    private static final Row END = new Row("end-of-stream");
    private static final long FLUSH_NANOS = 1_000_000_000L;

    private final ArrayBlockingQueue<Row> queue;
    private final Path raw, archive;
    private final BooleanSupplier paused;
    private final AtomicLong pausedNanos = new AtomicLong(), dropped = new AtomicLong();
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Thread thread;
    private volatile String failure;
    private volatile Result result;
    private volatile Consumer<Result> listener;
    private volatile String endReason = "unknown";

    public SessionWriter(Path directory, int capacity, BooleanSupplier paused) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.raw = directory.resolve("session.jsonl");
        this.archive = directory.resolve("session.jsonl.gz");
        this.paused = paused;
        this.thread = new Thread(this::run, "Ark session recorder");
        this.thread.setDaemon(true);
    }

    public void start() { thread.start(); }

    /** Game thread: false means the row is lost (queue full or writer dead) and was counted. */
    public boolean offer(Row row) {
        if (failure == null && queue.offer(row)) return true;
        dropped.incrementAndGet();
        return false;
    }

    /** Game thread: no more rows follow. The writer drains, writes the footer and compresses. */
    public void finish(String reason, Consumer<Result> whenDone) {
        endReason = reason;
        listener = whenDone;
        try {
            // A full queue means the writer is behind; wait briefly rather than lose the end marker.
            if (!queue.offer(END, 5, TimeUnit.SECONDS)) thread.interrupt();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            thread.interrupt();
        }
    }

    public boolean await(long millis) {
        try {
            return finished.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Real time the server spent paused since this writer started. */
    public long pausedNanos() { return pausedNanos.get(); }
    public int depth() { return queue.size(); }
    public long dropped() { return dropped.get(); }
    public String failure() { return failure; }
    public Result result() { return result; }
    public Path rawFile() { return raw; }

    private void run() {
        long rows = 0, firstSeq = -1, lastSeq = -1;
        Counting counter = null;
        OutputStream out = null;
        var line = new StringBuilder(4096);
        var batch = new ArrayList<Row>(4096);
        long lastSample = System.nanoTime(), lastFlush = lastSample;
        boolean ended = false;
        try {
            while (!ended) {
                Row first = queue.poll(50, TimeUnit.MILLISECONDS);
                long now = System.nanoTime();
                if (paused.getAsBoolean()) pausedNanos.addAndGet(now - lastSample);
                lastSample = now;
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, 4095);
                    for (Row row : batch) {
                        if (row == END) { ended = true; break; }
                        if (out == null) {
                            Files.createDirectories(raw.getParent());
                            counter = new Counting(Files.newOutputStream(raw));
                            out = new BufferedOutputStream(counter, 1 << 16);
                        }
                        write(out, line, row);
                        rows++;
                        if (row.seq >= 0) {
                            if (firstSeq < 0) firstSeq = row.seq;
                            lastSeq = row.seq;
                        }
                    }
                    batch.clear();
                }
                if (out != null && now - lastFlush >= FLUSH_NANOS) {
                    out.flush();
                    lastFlush = now;
                }
            }
        } catch (InterruptedException e) {
            failure = "writer interrupted before the end marker";
        } catch (Throwable t) {
            failure = t.toString();
        }
        long bytes = 0;
        boolean compressed = false;
        try {
            if (out != null) {
                out.flush();
                var footer = new Row("footer").put("reason", endReason).put("rows", rows).put("dropped", dropped.get())
                        .put("first_seq", firstSeq).put("last_seq", lastSeq).put("bytes", counter.count)
                        .put("failure", failure);
                write(out, line, footer);
                out.close();
                out = null;
                bytes = counter.count;
                compressed = failure == null && compress();
            }
        } catch (Throwable t) {
            if (failure == null) failure = t.toString();
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
        Path file = rows == 0 ? null : compressed ? archive : raw;
        result = new Result(file, rows, bytes, dropped.get(), firstSeq, lastSeq, compressed, failure);
        finished.countDown();
        var callback = listener;
        if (callback != null) callback.accept(result);
    }

    private static void write(OutputStream out, StringBuilder line, Row row) throws IOException {
        line.setLength(0);
        RowJson.line(line, row);
        line.append('\n');
        out.write(line.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Archive, read it back, and only then drop the plain file. */
    private boolean compress() throws IOException {
        Path temp = archive.resolveSibling(archive.getFileName() + ".tmp");
        var written = new CRC32();
        long length = 0;
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(raw);
                OutputStream gzip = new GZIPOutputStream(Files.newOutputStream(temp), 1 << 16)) {
            for (int read; (read = in.read(buffer)) > 0; ) {
                gzip.write(buffer, 0, read);
                written.update(buffer, 0, read);
                length += read;
            }
        }
        var readBack = new CRC32();
        long readLength = 0;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(temp), 1 << 16)) {
            for (int read; (read = in.read(buffer)) > 0; ) {
                readBack.update(buffer, 0, read);
                readLength += read;
            }
        }
        if (readLength != length || readBack.getValue() != written.getValue()) {
            Files.deleteIfExists(temp);
            return false;
        }
        Files.move(temp, archive, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(raw);
        return true;
    }

    private static final class Counting extends OutputStream {
        private final OutputStream target;
        long count;
        Counting(OutputStream target) { this.target = target; }
        @Override public void write(int value) throws IOException { target.write(value); count++; }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            target.write(bytes, offset, length);
            count += length;
        }
        @Override public void flush() throws IOException { target.flush(); }
        @Override public void close() throws IOException { target.close(); }
    }
}
