package dev.nez.arksurvivalreturns.feature.spawn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LedgerModelTest {
    @Test void balanceIsAFixedPointInBothModes() {
        for (boolean cycles : new boolean[]{false, true}) {
            var state = new LedgerModel(cycles, 7).advance(new LedgerModel.State(1, 1), 30);
            assertEquals(1, state.prey(), 1e-6, "prey left balance, cycles=" + cycles);
            assertEquals(1, state.predators(), 1e-6, "predators left balance, cycles=" + cycles);
        }
    }

    @Test void stableRegionsRecoverFromHunting() {
        var model = new LedgerModel(false, 7);
        var hunted = LedgerModel.remove(new LedgerModel.State(1, 1), false, 0.5);
        assertEquals(0.5, hunted.prey(), 1e-12);
        var later = model.advance(hunted, 200);
        assertEquals(1, later.prey(), 0.01);
        assertEquals(1, later.predators(), 0.01);
    }

    @Test void cyclingRegionsKeepCyclingWithinBounds() {
        var model = new LedgerModel(true, 7);
        var state = model.initial(42);
        double low = Double.MAX_VALUE, high = 0, lowPredators = Double.MAX_VALUE, highPredators = 0;
        for (int i = 0; i < 112; i++) {
            state = model.advance(state, 0.25);
            low = Math.min(low, state.prey()); high = Math.max(high, state.prey());
            lowPredators = Math.min(lowPredators, state.predators()); highPredators = Math.max(highPredators, state.predators());
        }
        assertTrue(high - low > 0.8, "prey barely moved: " + low + ".." + high);
        assertTrue(low > 0.3 && high < 2.5, "prey left the cycle's range: " + low + ".." + high);
        assertTrue(lowPredators > 0.3 && highPredators < 2.5, "predators left the cycle's range: " + lowPredators + ".." + highPredators);
    }

    @Test void cycleLengthFollowsTheConfiguredDays() {
        for (double days : new double[]{4, 7, 20}) {
            var model = new LedgerModel(true, days);
            var state = model.initial(7);
            double step = days / 200, previous = state.prey(), first = -1, last = -1;
            boolean rising = false;
            int peaks = 0;
            for (int i = 1; i <= 2000; i++) {
                state = model.advance(state, step);
                boolean nowRising = state.prey() > previous;
                if (rising && !nowRising) {
                    if (first < 0) first = (i - 1) * step;
                    last = (i - 1) * step;
                    peaks++;
                }
                rising = nowRising;
                previous = state.prey();
            }
            assertTrue(peaks >= 3, "too few cycles for " + days + " days");
            assertEquals(days, (last - first) / (peaks - 1), days * 0.05, "cycle length for " + days + " days");
        }
    }

    @Test void readingLateEqualsReadingOften() {
        var model = new LedgerModel(true, 7);
        var once = model.advance(model.initial(3), 9);
        var often = model.initial(3);
        for (int i = 0; i < 18; i++) often = model.advance(often, 0.5);
        assertEquals(once.prey(), often.prey(), 1e-3);
        assertEquals(once.predators(), often.predators(), 1e-3);
    }

    @Test void longAbsencesAreBoundedAndStayOnTheCycle() {
        var model = new LedgerModel(true, 7);
        long start = System.nanoTime();
        var state = model.advance(model.initial(11), 1_000_000);
        assertTrue(System.nanoTime() - start < 2_000_000_000L, "a long absence took too long");
        assertTrue(state.prey() > 0.3 && state.prey() < 2.5 && state.predators() > 0.3 && state.predators() < 2.5,
                "state after a long absence: " + state);
    }

    @Test void removalStopsAtTheFloorAndStartsAreReproducible() {
        var state = LedgerModel.remove(new LedgerModel.State(0.05, 1), false, 1);
        assertEquals(LedgerModel.FLOOR, state.prey(), 1e-12);
        var model = new LedgerModel(true, 7);
        assertEquals(model.initial(99), model.initial(99));
        int distinct = 0;
        for (long seed = 0; seed < 20; seed++) if (!model.initial(seed).equals(model.initial(seed + 1000))) distinct++;
        assertTrue(distinct > 15, "region starts barely vary");
    }
}
