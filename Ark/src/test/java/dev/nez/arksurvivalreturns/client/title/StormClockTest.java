package dev.nez.arksurvivalreturns.client.title;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The strike clock must stay identical to the one the title shaders compute on the GPU. */
class StormClockTest {
    @Test void hashMatchesTheShaderHash() {
        // Reference values of lowbias32 on unsigned 32-bit integers (tools/preview_title_scene.py strike_time).
        assertEquals(0, StormClock.hash(0));
        assertEquals(1753845952, StormClock.hash(1));
        assertEquals(388445122, StormClock.hash(42));
        assertEquals(272217977, StormClock.hash(198888888));
    }

    @Test void shadersUseTheSameSchedule() throws Exception {
        for (String layer : new String[]{"background", "foreground"}) {
            String source = Files.readString(Path.of("tools/title_scene/" + layer + ".glsl"));
            for (String fragment : new String[]{"x *= 0x7feb352du;", "x *= 0x846ca68bu;", "fmUnixTimeSeconds / 9 - k",
                    "h % 100u >= 40u", "(h >> 8u) % 7000u", "(h >> 20u) % 100u", "t > 1.8", "exp(-t * 7.0)",
                    "0.8 * exp(-abs(t - 0.14) * 32.0) * float((h >> 4u) & 1u)",
                    "0.6 * exp(-abs(t - 0.33) * 26.0) * float((h >> 5u) & 1u)"}) {
                assertTrue(source.contains(fragment), layer + ".glsl lost the strike clock: " + fragment);
            }
        }
    }

    @Test void aboutFourSlotsInTenStrikeEarlyInTheirSlot() {
        int strikes = 0;
        for (long slot = 198_000_000L; slot < 198_010_000L; slot++) {
            StormClock.Strike strike = StormClock.strike(slot);
            if (strike == null) continue;
            strikes++;
            long offset = strike.startMillis() - slot * StormClock.SLOT_SECONDS * 1000L;
            assertTrue(offset >= 0 && offset < 7000, "a strike starts in the first 7 s of its slot");
            assertTrue(strike.strength() >= 0.55f && strike.strength() <= 1f);
            assertTrue(strike.boltX() >= -1f && strike.boltX() < 1f);
            assertTrue(strike.thunderDelaySeconds() >= 1f && strike.thunderDelaySeconds() <= 4f, "thunder follows within 4 s");
        }
        assertEquals(0.40, strikes / 10_000.0, 0.02);
    }

    @Test void flashRisesAtTheStrikeAndFades() {
        long slot = 198_000_000L;
        while (StormClock.strike(slot) == null) slot++;
        StormClock.Strike strike = StormClock.strike(slot);
        long start = strike.startMillis();
        assertEquals(0f, strike.flash(-0.5), "dark before the strike");
        assertTrue(StormClock.flash(start + 10) > 0.5f, "bright right after it");
        assertTrue(strike.flash(1.5) < 0.05f, "nearly gone after 1.5 s");
        assertEquals(0f, strike.flash(1.9), "over after 1.8 s, like the shaders");
    }
}
