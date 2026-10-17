package dev.nez.arksurvivalreturns.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The designed eyes' client side against what tools/build_creature_eyes.py builds: the lids are scaled by the
 * builder's numbers, a shut eye is covered, the calm eyes turn to their hunting colour only behind a blink, and
 * the models carry designed eyes exactly where Species says they do.
 */
class CreatureEyesTest {
    private static final Path MODELS = Path.of("src/main/resources/assets/arksurvivalreturns/geckolib/models/entity");

    private static String number(float value) { return String.format(Locale.ROOT, "%.2f", value); }

    @Test void lidContractIsTheBuilders() throws Exception {
        String builder = Files.readString(Path.of("tools/build_creature_eyes.py"));
        assertTrue(builder.contains("LID_TUCK = {'upper': " + number(CreatureEyes.UPPER_TUCK) + ", 'lower': " + number(CreatureEyes.LOWER_TUCK) + "}"),
                "build_creature_eyes.LID_TUCK changed: CreatureEyes.UPPER_TUCK and LOWER_TUCK must follow");
        assertTrue(builder.contains("LID_SHUT = {'upper': " + number(CreatureEyes.UPPER_SHUT) + ", 'lower': " + number(CreatureEyes.LOWER_SHUT) + "}"),
                "build_creature_eyes.LID_SHUT changed: CreatureEyes.UPPER_SHUT and LOWER_SHUT must follow");
        String preview = Files.readString(Path.of("tools/preview_creature_eyes.py"));
        for (CreatureEyes.Look look : CreatureEyes.Look.values()) {
            if (look == CreatureEyes.Look.DROWSY || look == CreatureEyes.Look.WINCE) continue;
            String state = look.name().toLowerCase(Locale.ROOT);
            Matcher row = Pattern.compile("'" + state + "': dict\\(closure=(-?[0-9.]+), drop=(-?[0-9.]+), tilt=(-?[0-9]+), slit=([0-9.]+)").matcher(preview);
            assertTrue(row.find(), "preview_creature_eyes.STATES has no '" + state + "'");
            assertEquals(look.closure, Float.parseFloat(row.group(1)), 1e-4, state + " closure");
            assertEquals(look.drop, Float.parseFloat(row.group(2)), 1e-4, state + " drop");
            assertEquals(look.tilt, Float.parseFloat(row.group(3)), 1e-4, state + " tilt");
            assertEquals(look.slit, Float.parseFloat(row.group(4)), 1e-4, state + " slit");
        }
    }

    @Test void lidsCoverWhatTheClosureSays() {
        assertEquals(.30f, CreatureEyes.cover(0f, .30f, .70f), 1e-6, "a rest closure is the species' own lid");
        assertEquals(.70f, CreatureEyes.cover(1f, .30f, .70f), 1e-6, "a shut eye is covered the same whatever the lid's rest");
        assertEquals(.70f, CreatureEyes.cover(1f, .05f, .70f), 1e-6);
        assertEquals(.50f, CreatureEyes.cover(.5f, .30f, .70f), 1e-6);
        assertEquals(0f, CreatureEyes.cover(-1f, .30f, .70f), 1e-6, "wide open uncovers the eye");
        assertEquals(.15f, CreatureEyes.cover(-.5f, .30f, .70f), 1e-6);
    }

    @Test void shutEyesAreCovered() {
        CreatureEyes eyes = new CreatureEyes(3);
        for (float t = 0; t < 2f; t += 1 / 60f) eyes.update(t, CreatureEyes.Look.CLOSED, false, 1f);
        assertTrue(eyes.shut() > .98f, "after easing the eye is shut: " + eyes.shut());
    }

    @Test void theColourTurnsBehindABlink() {
        CreatureEyes eyes = new CreatureEyes(11);
        float t = 0;
        for (; t < 3f; t += 1 / 60f) eyes.update(t, CreatureEyes.Look.CALM, false, 1f);
        assertFalse(eyes.red());
        boolean turned = false;
        for (float end = t + 1f; t < end; t += 1 / 60f) {
            eyes.update(t, CreatureEyes.Look.HUNT, true, 1f);
            if (eyes.red() && !turned) {
                turned = true;
                assertTrue(eyes.shut() > .8f, "turned with the eye open: " + eyes.shut());
            }
        }
        assertTrue(turned, "still calm a second into the hunt");
    }

    @Test void blinksComeNowAndThen() {
        CreatureEyes eyes = new CreatureEyes(5);
        int blinks = 0;
        boolean shut = false;
        for (float t = 0; t < 30f; t += 1 / 60f) {
            eyes.update(t, CreatureEyes.Look.CALM, false, 1f);
            boolean now = eyes.shut() > .9f;
            if (now && !shut) blinks++;
            shut = now;
        }
        assertTrue(blinks >= 4 && blinks <= 16, "blinks in half a minute: " + blinks);
    }

    /** Designed eyes are the ones Species.designedEyes says, in every model: the renderer shows what the species claims. */
    @Test void modelsCarryDesignedEyesWhereTheSpeciesHasThem() throws Exception {
        int designed = 0;
        for (Species species : Species.values()) {
            String model = Files.readString(MODELS.resolve(species.id + ".geo.json"));
            boolean has = model.contains("\"name\":\"ark_eye_l\"");
            assertEquals(species.designedEyes(), has, species.id + ": designed eyes in the model and in Species disagree");
            if (has) {
                designed++;
                assertTrue(model.contains("\"name\":\"ark_eyeball_calm_l\"") && model.contains("\"name\":\"ark_eyeball_calm_r\""), species.id);
                assertFalse(model.contains("\"name\":\"ark_eye_calm\""), species.id + ": the generated sets stay out of a designed model");
            }
        }
        assertTrue(designed >= 38, "designed species: " + designed);
    }
}
