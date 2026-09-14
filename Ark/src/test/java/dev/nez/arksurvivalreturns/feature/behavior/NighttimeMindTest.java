package dev.nez.arksurvivalreturns.feature.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NighttimeMindTest {
    private static WildlifeMind.Observation quiet(boolean night) {
        return new WildlifeMind.Observation(0, false, false, false, false, false, false, false, true, night, 1);
    }
    private static WildlifeMind.Routine routine(boolean night, boolean sleep, boolean safe, boolean danger, boolean defended, boolean cornered) {
        return new WildlifeMind.Routine(true, night, sleep, safe, danger, defended, cornered, 200, 2);
    }
    private static WildlifeMind mind(boolean predator) {
        var brain = new WildlifeMind(predator, false, predator);
        brain.restoreNeeds(0.1, 0.1, 0);
        return brain;
    }
    @Test void scheduledSleepSurvivesZeroFatigueAndDayHerbivoresStayActive() {
        for (boolean predator : new boolean[]{false, true}) {
            var brain = mind(predator);
            for (int i = 0; i < 300; i++) assertEquals(BehaviorState.SLEEP,
                    brain.step(quiet(!predator), 10, routine(!predator, true, true, false, false, false)));
            assertEquals(0, brain.fatigue());
        }
        assertNotEquals(BehaviorState.SLEEP, mind(false).step(quiet(false), 10, routine(false, false, true, false, false, false)));
    }
    @Test void hungerDoublesOnlyForNightLandPredatorsAndFeedingStopsSearch() {
        var day = mind(true); var night = mind(true); var herb = mind(false); var legacy = mind(true);
        for (int i = 0; i < 100; i++) {
            day.step(quiet(false), 10, routine(false, false, true, false, false, false));
            night.step(quiet(true), 10, routine(true, false, true, false, false, false));
            herb.step(quiet(true), 10, routine(true, true, true, false, false, false));
            legacy.step(quiet(true), 10);
        }
        assertEquals((day.hunger() - 0.1) * 2, night.hunger() - 0.1, 1e-10);
        assertEquals(day.hunger(), herb.hunger(), 1e-10);
        assertEquals(day.hunger(), legacy.hunger(), 1e-10);
        night.restoreNeeds(0.8, 0.1, 0);
        assertEquals(BehaviorState.SEARCH, night.step(quiet(true), 10, routine(true, false, true, false, false, false)));
        night.ate();
        assertEquals(BehaviorState.FEED, night.step(quiet(true), 10, routine(true, false, true, false, false, false)));
        for (int i = 0; i < 100; i++) assertNotEquals(BehaviorState.SEARCH,
                night.step(quiet(true), 10, routine(true, false, true, false, false, false)));
    }
    @Test void daytimePreyDoesNotTriggerHuntAndDamageStillDefends() {
        var brain = mind(true); brain.restoreNeeds(0.8, 0.1, 0.1);
        var prey = new WildlifeMind.Observation(1, true, true, false, false, false, false, false, false, false, 1);
        for (int i = 0; i < 40; i++) assertFalse(brain.step(prey, 10, routine(false, true, true, false, false, false)).combat());
        var attacked = new WildlifeMind.Observation(1, true, true, true, true, false, false, false, false, false, 1);
        assertEquals(BehaviorState.DEFEND, brain.step(attacked, 10, routine(false, true, true, true, false, false)));
    }
    @Test void nightHerdFleesBeforeStandingAndInjuredAnimalsKeepFleeing() {
        var brain = mind(false);
        var threat = new WildlifeMind.Observation(1, true, false, true, true, false, false, false, false, true, 1);
        var herd = routine(true, true, true, true, true, false);
        assertEquals(BehaviorState.FLEE, brain.step(threat, 10, herd));
        for (int i = 0; i < 5; i++) assertEquals(BehaviorState.FLEE, brain.step(threat, 10, herd));
        assertEquals(BehaviorState.DEFEND, brain.step(threat, 10, herd));
        assertEquals(BehaviorState.DEFEND, brain.step(threat, 10, herd));
        var injured = new WildlifeMind.Observation(1, true, false, true, true, false, false, false, false, true, 0.3);
        assertEquals(BehaviorState.FLEE, brain.step(injured, 10, herd));
        assertEquals(BehaviorState.DEFEND, mind(false).step(threat, 10, routine(true, true, true, true, false, true)));
    }
    @Test void wakingRequiresCalmBeforeSleepAndHazardsCannotSleep() {
        var brain = mind(true); var day = routine(false, true, true, false, false, false);
        assertEquals(BehaviorState.SLEEP, brain.step(quiet(false), 10, day));
        brain.interruptSleep(200);
        assertEquals(BehaviorState.ALERT, brain.state());
        for (int i = 0; i < 19; i++) assertNotEquals(BehaviorState.SLEEP, brain.step(quiet(false), 10, day));
        assertEquals(BehaviorState.SLEEP, brain.step(quiet(false), 10, day));
        assertNotEquals(BehaviorState.SLEEP, brain.step(quiet(false), 10, routine(false, true, false, false, false, false)));
        brain.restoreCalm(99999); assertEquals(1200, brain.calmTicksRemaining());
        brain.restoreCalm(-1); assertEquals(0, brain.calmTicksRemaining());
    }
    @Test void dawnDoesNotForceSleepInAnEncounterAndNoiseDoesNotAuthorizeCombat() {
        var brain = mind(true); brain.restoreNeeds(0.8, 0.1, 0);
        var prey = new WildlifeMind.Observation(1, true, true, false, false, false, false, false, false, true, 1);
        for (int i = 0; i < 8; i++) brain.step(prey, 10, routine(true, false, true, false, false, false));
        assertEquals(BehaviorState.HUNT, brain.state());
        assertNotEquals(BehaviorState.SLEEP, brain.step(prey, 10, routine(false, true, true, false, false, false)));
        var sound = new WildlifeMind.Observation(0.65, false, true, false, false, false, false, false, false, true, 1);
        for (int i = 0; i < 100; i++) assertFalse(brain.step(sound, 10, routine(true, false, true, false, false, false)).combat());
    }
    @Test void clockWrapDelaysSleepShareAndVisionAreDeterministic() {
        assertTrue(NighttimeCycle.night(13000, 13000, 23000));
        assertFalse(NighttimeCycle.night(23000, 13000, 23000));
        assertTrue(NighttimeCycle.night(24000 + 13000, 13000, 23000));
        assertTrue(NighttimeCycle.night(-1, 23000, 1000));
        assertFalse(NighttimeCycle.night(15000, 13000, 13000));
        assertFalse(NighttimeCycle.individualNight(13000, 600, 13000, 23000, 600));
        assertTrue(NighttimeCycle.individualNight(13600, 600, 13000, 23000, 600));
        assertFalse(NighttimeCycle.individualNight(23600, 600, 13000, 23000, 600));
        int asleep = 0;
        for (int t = 0; t < 2000; t++) if (NighttimeCycle.sleepWanted(t, Long.MIN_VALUE, true, false, 0.7)) asleep++;
        assertEquals(1400, asleep);
        assertEquals(62.4, NighttimeCycle.sight(48, true, true, 1.3), 1e-10);
        assertEquals(41.6, NighttimeCycle.sight(32, true, true, 1.3), 1e-10);
        assertEquals(22.4, NighttimeCycle.sight(32, true, false, 1.3), 1e-10);
    }
    @Test void urgentNeedsFinishBeforeResumingSleepAndRegroupingPrecedesSleep() {
        var brain = mind(false); var night = routine(true, true, true, false, false, false);
        brain.restoreNeeds(0.95, 0.1, 0);
        assertEquals(BehaviorState.FORAGE, brain.step(quiet(true), 10, night));
        for (int i = 0; i < 20; i++) assertEquals(BehaviorState.FORAGE, brain.step(quiet(true), 10, night));
        brain.restoreNeeds(0.1, 0.95, 0);
        var water = new WildlifeMind.Observation(0, false, false, false, false, false, false, true, true, true, 1);
        assertEquals(BehaviorState.DRINK, brain.step(water, 10, night));
        for (int i = 0; i < 10; i++) assertEquals(BehaviorState.DRINK, brain.step(water, 10, night));
        brain.restoreNeeds(0.1, 0.1, 0);
        assertEquals(BehaviorState.REGROUP, brain.step(quiet(true), 10,
                new WildlifeMind.Routine(true, true, true, true, false, false, false, 200, 2, true)));
        assertEquals(BehaviorState.SLEEP, brain.step(quiet(true), 10, night));
    }
}
