import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
 * {@code tools/session_bench.py --profile}: {@code java tools/SessionProfile.java <run folder> [thread] [rows]}.
 * Reads profile.jfr and the phase times in meta.json, and prints for the thread (default: Render thread) and
 * each phase the share of its samples by owner (the mod whose code is nearest the top of the stack, so a
 * driver call counts for whoever made it), by the methods the thread was in, and by the methods on the stack.
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

    public static void main(String[] arguments) throws Exception {
        Path folder = Path.of(arguments[0]);
        String thread = arguments.length > 1 ? arguments[1] : "Render thread";
        int rows = arguments.length > 2 ? Integer.parseInt(arguments[2]) : 30;
        String meta = Files.readString(folder.resolve("meta.json"));
        for (String phase : List.of("pan", "flight")) {
            Matcher found = Pattern.compile("\"" + phase + "\"\\s*:\\s*\\{\\s*\"epoch_ms\"\\s*:\\s*(\\d+),\\s*\"seconds\"\\s*:\\s*(\\d+)").matcher(meta);
            if (!found.find()) continue;
            long begin = Long.parseLong(found.group(1)), end = begin + Long.parseLong(found.group(2)) * 1000;
            Map<String, Double> owners = new TreeMap<>(), self = new TreeMap<>(), within = new TreeMap<>();
            double total = 0;
            try (RecordingFile file = new RecordingFile(folder.resolve("profile.jfr"))) {
                while (file.hasMoreEvents()) {
                    RecordedEvent event = file.readEvent();
                    String name = event.getEventType().getName();
                    boolean running = name.equals("jdk.ExecutionSample");
                    if (!running && !name.equals("jdk.NativeMethodSample")) continue;
                    long at = event.getStartTime().toEpochMilli();
                    if (at < begin || at >= end || event.getStackTrace() == null) continue;
                    var sampled = event.getThread("sampledThread");
                    // The game renames its first thread; the recording may know it by either name.
                    if (sampled == null || !(thread.equals(sampled.getJavaName())
                            || thread.equals("Render thread") && "main".equals(sampled.getJavaName()))) continue;
                    // The two kinds of sample are taken at different rates; each stands for its own stretch of time.
                    double weight = running ? 10 : 20;
                    List<String> frames = new ArrayList<>();
                    for (RecordedFrame frame : event.getStackTrace().getFrames())
                        frames.add(frame.getMethod().getType().getName() + "." + frame.getMethod().getName());
                    total += weight;
                    self.merge(frames.get(0), weight, Double::sum);
                    for (String frame : new HashSet<>(frames)) within.merge(frame, weight, Double::sum);
                    owners.merge(owner(frames), weight, Double::sum);
                }
            }
            System.out.printf("%n== %s, %s: %.1f s sampled of %d s%n", thread, phase, total / 1000, (end - begin) / 1000);
            print("by owner", owners, total, rows);
            print("in the method itself", self, total, rows);
            print("with the method on the stack", within, total, rows * 3);
        }
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
