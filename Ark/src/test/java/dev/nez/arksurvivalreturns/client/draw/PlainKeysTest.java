package dev.nez.arksurvivalreturns.client.draw;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.loading.definition.animation.ActorAnimations;
import com.geckolib.loading.math.MathParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The gate of {@link PlainKeys}: every channel of every bone of every shipped clip, at moments a controller
 * passes through (forwards, standing, backwards, a jump), is worth to it what GeckoLib's own
 * AnimationProcessor.findAnimationPointValue makes of it, bit for bit. The count goes to
 * build/reports/creature-animation-keys.txt.
 */
class PlainKeysTest {
    private static final Path CLIPS = Path.of("src/main/resources/assets/arksurvivalreturns/geckolib/animations/entity");
    private static final double[] MOMENTS = {0.0, 0.013, 0.13, 0.31, 0.5, 0.5, 0.87, 0.999, 1.0, 0.4, 0.02};
    private long compared, left;

    @Test void everyChannelOfEveryClipIsGeckoLibsValue() throws Exception {
        List<Path> files;
        try (Stream<Path> list = Files.list(CLIPS)) {
            files = list.filter(path -> path.getFileName().toString().endsWith(".animation.json")).sorted().toList();
        }
        assertTrue(files.size() >= 40, "the shipped clips are there: " + files.size());
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%-18s %5s %12s %12s%n", "model", "clips", "channels", "geckolib's"));
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".animation.json", "");
            BakedAnimations baked;
            try (Reader reader = Files.newBufferedReader(file)) {
                baked = ActorAnimations.GSON.fromJson(reader, ActorAnimations.class)
                        .bake(Identifier.fromNamespaceAndPath("arksurvivalreturns", "entity/" + name), MathParser.create());
            }
            long before = compared, beforeLeft = left;
            for (Animation animation : baked.animations().values()) {
                AnimationPoint point = AnimationPoint.createFor(animation, null, animation.loopType(), 0.0);
                for (double share : MOMENTS) {
                    point = point.createNext(share * animation.length());
                    compare(name, point);
                }
                compare(name, AnimationPoint.createFor(animation, null, animation.loopType(), 0.66 * animation.length()));
            }
            table.append(String.format(Locale.ROOT, "%-18s %5d %12d %12d%n", name, baked.animations().size(), compared - before, left - beforeLeft));
        }
        table.append(String.format(Locale.ROOT, "%d channel values the same bit for bit as GeckoLib's; %d left to GeckoLib (keys that are neither linear "
                + "nor catmull-rom with both neighbours).%n", compared, left));
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/creature-animation-keys.txt"), table);
        System.out.print(table);
        assertTrue(compared > 1_000_000, "channels compared: " + compared);
        assertTrue(left * 20 < compared, "channels left to GeckoLib: " + left + " of " + (compared + left));
    }

    private void compare(String name, AnimationPoint point) {
        ControllerState state = new ControllerState(point, null, -1.0, 0, false, null, null, null);
        int bones = point.animation().boneAnimations().length;
        for (int bone = 0; bone < bones; bone++) {
            for (AnimationPoint.Transform transform : AnimationPoint.Transform.values()) {
                for (AnimationPoint.Axis axis : AnimationPoint.Axis.values()) {
                    float ours = PlainKeys.value(state, point, bone, transform, axis, null);
                    if (ours != ours) {
                        left++;
                        continue;
                    }
                    float theirs = AnimationProcessor.findAnimationPointValue(null, state, point, null, bone, transform, axis, null);
                    if (Float.floatToRawIntBits(theirs) != Float.floatToRawIntBits(ours))
                        fail(name + " " + point.animation().name() + " at " + point.animTime() + ", " + point.animation().boneAnimations()[bone].boneName()
                                + " " + transform + " " + axis + ": " + ours + ", GeckoLib " + theirs);
                    compared++;
                }
            }
        }
    }
}
