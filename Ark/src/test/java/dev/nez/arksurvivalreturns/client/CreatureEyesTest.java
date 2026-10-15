package dev.nez.arksurvivalreturns.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The designed eyes' client side against what tools/build_creature_eyes.py builds: the lids are scaled by the
 * builder's numbers, a shut eye is covered, and the yellow turns red only behind a blink.
 */
class CreatureEyesTest {
    private static String number(float value) { return String.format(Locale.ROOT, "%.2f", value); }

    @Test void lidContractIsTheBuilders() throws Exception {
        String builder = Files.readString(Path.of("tools/build_creature_eyes.py"));
        assertTrue(builder.contains("LID_REST = {'upper': " + number(CreatureEyes.UPPER_REST) + ", 'lower': " + number(CreatureEyes.LOWER_REST) + "}"),
                "build_creature_eyes.LID_REST changed: CreatureEyes.UPPER_REST and LOWER_REST must follow");
        assertTrue(builder.contains("LID_TUCK = {'upper': " + number(CreatureEyes.UPPER_TUCK) + ", 'lower': " + number(CreatureEyes.LOWER_TUCK) + "}"),
                "build_creature_eyes.LID_TUCK changed: CreatureEyes.UPPER_TUCK and LOWER_TUCK must follow");
        String preview = Files.readString(Path.of("tools/preview_creature_eyes.py"));
        for (CreatureEyes.Look look : CreatureEyes.Look.values()) {
            if (look == CreatureEyes.Look.DROWSY || look == CreatureEyes.Look.WINCE) continue;
            String state = look == CreatureEyes.Look.CLOSED ? "closed" : look.name().toLowerCase(Locale.ROOT);
            assertTrue(preview.contains("'" + state + "': dict(upper=" + number(look.upper).substring(1) + ", lower=" + number(look.lower).substring(1)),
                    "preview_creature_eyes.STATES['" + state + "'] shows other lids than CreatureEyes." + look);
        }
    }

    @Test void shutEyesAreCovered() {
        assertTrue(CreatureEyes.Look.CLOSED.upper + CreatureEyes.Look.CLOSED.lower > 1.05f, "the lids of a shut eye overlap");
        CreatureEyes eyes = new CreatureEyes(3);
        for (float t = 0; t < 2f; t += 1 / 60f) eyes.update(t, CreatureEyes.Look.CLOSED, false, 1f);
        assertTrue(eyes.covered() > 1.05f, "after easing the eye is shut: " + eyes.covered());
    }

    @Test void theRedComesBehindABlink() {
        CreatureEyes eyes = new CreatureEyes(11);
        float t = 0;
        for (; t < 3f; t += 1 / 60f) eyes.update(t, CreatureEyes.Look.CALM, false, 1f);
        assertFalse(eyes.red());
        boolean turned = false;
        for (float end = t + 1f; t < end; t += 1 / 60f) {
            eyes.update(t, CreatureEyes.Look.HUNT, true, 1f);
            if (eyes.red() && !turned) {
                turned = true;
                assertTrue(eyes.covered() > .9f, "turned red with the eye open: " + eyes.covered());
            }
        }
        assertTrue(turned, "still yellow a second into the hunt");
    }

    @Test void blinksComeNowAndThen() {
        CreatureEyes eyes = new CreatureEyes(5);
        int blinks = 0;
        boolean shut = false;
        for (float t = 0; t < 30f; t += 1 / 60f) {
            eyes.update(t, CreatureEyes.Look.CALM, false, 1f);
            boolean now = eyes.covered() > .9f;
            if (now && !shut) blinks++;
            shut = now;
        }
        assertTrue(blinks >= 4 && blinks <= 16, "blinks in half a minute: " + blinks);
    }
}
