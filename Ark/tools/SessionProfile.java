import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

/**
 * Says where a thread's time went during the measured phases of a benchmark run recorded with
 * {@code tools/session_bench.py --profile}:
 * {@code java tools/SessionProfile.java <run folder> [thread] [rows] [method]}.
 * Reads profile.jfr, the phase times in meta.json and the frame times in frames.csv, and prints for the thread
 * (default: Render thread) and each phase:
 * the share of its samples by owner (the mod whose code is nearest the top of the stack, so a driver call counts
 * for whoever made it), by the method the thread was in and by the methods on the stack; the same for the slow
 * frames alone (twice the median or longer), with the methods that are there more often than elsewhere; the
 * garbage collector's pauses; who allocates. With a method (part of Class.method), what it calls and who calls it.
 */
public final class SessionProfile {
    private static final Map<String, String> OWNERS = new LinkedHashMap<>();

    static {
        OWNERS.put("dev.nez.arksurvivalreturns.", "Ark");
        OWNERS.put("software.bernie.", "GeckoLib");
        OWNERS.put("com.geckolib.", "GeckoLib");
        OWNERS.put("net.irisshaders.", "Iris");
        OWNERS.put("net.caffeinemc.", "Sodium");
        OWNERS.put("com.seibel.distanthorizons.", "Distant Horizons");
        OWNERS.put("DistantHorizons.", "Distant Horizons");
        OWNERS.put("loaderCommon.", "Distant Horizons");
        OWNERS.put("com.leonardoinc22.shortgrass.", "Grassier Grass");
        OWNERS.put("net.diebuddies.", "Physics Mod");
        OWNERS.put("physicsmod.", "Physics Mod");
        OWNERS.put("xaero.", "Xaero");
        OWNERS.put("dev.ftb.", "FTB");
        OWNERS.put("de.keksuccino.", "FancyMenu");
        OWNERS.put("mezz.jei.", "JEI");
        OWNERS.put("com.sonicether.", "Sound Physics");
        OWNERS.put("team.creative.", "AmbientSounds");
        OWNERS.put("net.bettercombat.", "Better Combat");
        OWNERS.put("top.theillusivec4.", "Curios");
    }

    private record Sample(long at, double weight, List<String> frames) {}

    public static void main(String[] arguments) throws Exception {
        Path folder = Path.of(arguments[0]);
        String thread = arguments.length > 1 ? arguments[1] : "Render thread";
        int rows = arguments.length > 2 ? Integer.parseInt(arguments[2]) : 30;
        String focus = arguments.length > 3 ? arguments[3] : null;
        String meta = Files.readString(folder.resolve("meta.json"));
        List<String> frameLines = Files.readAllLines(folder.resolve("frames.csv"));
        for (String phase : List.of("pan", "flight")) {
            Matcher found = Pattern.compile("\"" + phase + "\"\\s*:\\s*\\{\\s*\"epoch_ms\"\\s*:\\s*(\\d+),\\s*\"seconds\"\\s*:\\s*(\\d+)").matcher(meta);
            if (!found.find()) continue;
            long begin = Long.parseLong(found.group(1)), end = begin + Long.parseLong(found.group(2)) * 1000;
            List<Sample> samples = new ArrayList<>();
            Map<String, Double> allocators = new TreeMap<>(), allocatingThreads = new TreeMap<>();
            double allocated = 0, pauses = 0, longest = 0;
            int collections = 0;
            try (RecordingFile file = new RecordingFile(folder.resolve("profile.jfr"))) {
                while (file.hasMoreEvents()) {
                    RecordedEvent event = file.readEvent();
                    String name = event.getEventType().getName();
                    long at = event.getStartTime().toEpochMilli();
                    if (at < begin || at >= end) continue;
                    if (name.equals("jdk.GarbageCollection")) {
                        collections++;
                        pauses += event.getDuration("sumOfPauses").toNanos() / 1e6;
                        longest = Math.max(longest, event.getDuration("longestPause").toNanos() / 1e6);
                    }
                    if (name.equals("jdk.ObjectAllocationSample") && event.getStackTrace() != null && event.getThread("eventThread") != null) {
                        double bytes = event.getLong("weight");
                        allocated += bytes;
                        allocators.merge(owner(frames(event)), bytes, Double::sum);
                        allocatingThreads.merge(String.valueOf(event.getThread("eventThread").getJavaName()).replaceAll("[-\\[ ]*\\d+\\]?$", ""), bytes, Double::sum);
                    }
                    boolean running = name.equals("jdk.ExecutionSample");
                    if (!running && !name.equals("jdk.NativeMethodSample") || event.getStackTrace() == null) continue;
                    var sampled = event.getThread("sampledThread");
                    // The game renames its first thread; the recording may know it by either name.
                    if (sampled == null || !(thread.equals(sampled.getJavaName())
                            || thread.equals("Render thread") && "main".equals(sampled.getJavaName()))) continue;
                    // The two kinds of sample are taken at different rates; each stands for its own stretch of time.
                    samples.add(new Sample(at, running ? 10 : 20, frames(event)));
                }
            }
            double total = samples.stream().mapToDouble(Sample::weight).sum();
            System.out.printf("%n== %s, %s: %.1f s sampled of %d s%n", thread, phase, total / 1000, (end - begin) / 1000);
            print("by owner", shares(samples, false, true), total, rows);
            print("in the method itself", shares(samples, true, false), total, rows);
            print("with the method on the stack", shares(samples, false, false), total, rows * 3);
            if (focus != null) around(samples, total, rows, focus);

            // Frame start times, from the phase's start and the frame durations in order.
            double[] durations = frameLines.stream().filter(line -> line.startsWith(phase + ","))
                    .mapToDouble(line -> Long.parseLong(line.split(",")[1]) / 1e6).toArray();
            if (durations.length > 0 && thread.equals("Render thread")) {
                double[] starts = new double[durations.length];
                for (int i = 1; i < durations.length; i++) starts[i] = starts[i - 1] + durations[i - 1];
                double[] ordered = durations.clone();
                Arrays.sort(ordered);
                double limit = 2 * ordered[ordered.length / 2];
                List<Sample> slow = new ArrayList<>();
                for (Sample sample : samples) {
                    int index = Arrays.binarySearch(starts, sample.at() - begin);
                    if (index < 0) index = Math.max(0, -index - 2);
                    if (durations[index] >= limit) slow.add(sample);
                }
                double slowTime = Arrays.stream(durations).filter(value -> value >= limit).sum();
                double slowTotal = slow.stream().mapToDouble(Sample::weight).sum();
                System.out.printf("-- slow frames (%.0f ms or longer): %d of %d frames, %.1f %% of the phase's time%n", limit,
                        Arrays.stream(durations).filter(value -> value >= limit).count(), durations.length,
                        100 * slowTime / Arrays.stream(durations).sum());
                if (slowTotal > 0) {
                    print("slow frames by owner", shares(slow, false, true), slowTotal, rows);
                    Map<String, Double> all = shares(samples, false, false), there = shares(slow, false, false), excess = new TreeMap<>();
                    for (var entry : there.entrySet())
                        excess.put(entry.getKey(), entry.getValue() / slowTotal - all.getOrDefault(entry.getKey(), 0.0) / total);
                    System.out.println("-- on the stack in slow frames more than elsewhere (share there, share overall)");
                    excess.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(rows)
                            .forEach(entry -> System.out.printf("%5.1f%% %5.1f%%  %s%n", 100 * there.get(entry.getKey()) / slowTotal,
                                    100 * all.get(entry.getKey()) / total, entry.getKey()));
                }
            }
            System.out.printf("-- garbage collector: %d collections, %.0f ms paused in all, longest %.1f ms%n", collections, pauses, longest);
            System.out.printf("-- allocation, all threads: %.0f MB a second%n", allocated / 1048576 / ((end - begin) / 1000.0));
            print("allocated by owner", allocators, allocated, 10);
            print("allocated by thread", allocatingThreads, allocated, 8);
        }
    }

    private static List<String> frames(RecordedEvent event) {
        List<String> frames = new ArrayList<>();
        for (RecordedFrame frame : event.getStackTrace().getFrames())
            frames.add(frame.getMethod().getType().getName() + "." + frame.getMethod().getName());
        return frames;
    }

    private static Map<String, Double> shares(List<Sample> samples, boolean top, boolean owners) {
        Map<String, Double> shares = new TreeMap<>();
        for (Sample sample : samples) {
            if (owners) shares.merge(owner(sample.frames()), sample.weight(), Double::sum);
            else if (top) shares.merge(sample.frames().get(0), sample.weight(), Double::sum);
            else for (String frame : new HashSet<>(sample.frames())) shares.merge(frame, sample.weight(), Double::sum);
        }
        return shares;
    }

    /** What the method calls and who calls it, counted at its frame nearest the root. */
    private static void around(List<Sample> samples, double total, int rows, String focus) {
        Map<String, Double> callees = new TreeMap<>(), callers = new TreeMap<>(), owners = new TreeMap<>();
        double with = 0;
        for (Sample sample : samples) {
            List<String> frames = sample.frames();
            int index = -1;
            for (int i = frames.size() - 1; i >= 0; i--) if (frames.get(i).contains(focus)) { index = i; break; }
            if (index < 0) continue;
            with += sample.weight();
            callees.merge(index == 0 ? "(itself)" : frames.get(index - 1), sample.weight(), Double::sum);
            if (index + 1 < frames.size()) callers.merge(frames.get(index + 1), sample.weight(), Double::sum);
            owners.merge(owner(frames.subList(0, index + 1)), sample.weight(), Double::sum);
        }
        System.out.printf("-- %s: on the stack in %.1f %% of the samples (shares below are of the whole thread)%n", focus, 100 * with / total);
        print("it calls", callees, total, rows);
        print("called by", callers, total, 8);
        print("owner below it", owners, total, 12);
    }

    private static String owner(List<String> frames) {
        for (String frame : frames)
            for (var owner : OWNERS.entrySet())
                if (frame.startsWith(owner.getKey())) return owner.getValue();
        return "Minecraft, Java and the driver";
    }

    private static void print(String title, Map<String, Double> shares, double total, int rows) {
        System.out.println("-- " + title);
        shares.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(rows)
                .forEach(entry -> System.out.printf("%5.1f%%  %s%n", 100 * entry.getValue() / total, entry.getKey()));
    }
}
