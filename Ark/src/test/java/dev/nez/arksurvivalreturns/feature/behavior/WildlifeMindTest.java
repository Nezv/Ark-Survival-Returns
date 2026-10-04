package dev.nez.arksurvivalreturns.feature.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WildlifeMindTest {
    private static WildlifeMind.Observation sight(boolean visible, boolean prey, boolean intruding, double health) {
        return new WildlifeMind.Observation(visible ? 1 : 0, visible, prey, intruding, false, false, false, false, false, false, health);
    }
    @Test void unsafeShelterPreventsScheduledSleepAndLegacyFatigueRest() {
        for (boolean cycle : new boolean[]{false, true}) {
            var mind = new WildlifeMind(true, false, false);
            mind.restoreNeeds(.1, .1, .95);
            var quiet = sight(false, false, false, 1);
            var exposed = new WildlifeMind.Routine(cycle, false, true, false, false, false, false, 100, 1);
            for (int i = 0; i < 30; i++) assertFalse(mind.step(quiet, 10, exposed, null).sleeping());
            var sheltered = new WildlifeMind.Routine(cycle, false, true, true, false, false, false, 100, 1);
            for (int i = 0; i < 12; i++) mind.step(quiet, 10, sheltered, null);
            assertTrue(mind.state().sleeping());
            assertFalse(mind.step(quiet, 10, exposed, null).sleeping(), "Canopy loss must end existing sleep/rest");
        }
    }
    @Test void hungryPredatorWarnsBeforeHuntingThenStopsAnEndlessChase() {
        var mind = new WildlifeMind(true, false, false);
        var prey = sight(true, true, false, 1);
        assertEquals(BehaviorState.ALERT, mind.step(prey, 10));
        assertEquals(BehaviorState.THREATEN, mind.step(prey, 10));
        for (int i = 0; i < 4; i++) mind.step(prey, 10);
        assertEquals(BehaviorState.HUNT, mind.state());
        boolean gaveUp = false;
        for (int i = 0; i < 40; i++) if (mind.step(prey, 10) == BehaviorState.RETURN_HOME) gaveUp = true;
        assertTrue(gaveUp);
    }
    @Test void hearingInvestigatesWithoutAttackingAndMemoryExpires() {
        var mind = new WildlifeMind(true, false, false);
        var sound = new WildlifeMind.Observation(0.65, false, true, true, false, false, false, false, false, false, 1);
        for (int i = 0; i < 30; i++) assertFalse(mind.step(sound, 10).combat());
        assertEquals(BehaviorState.INVESTIGATE, mind.state());
        for (int i = 0; i < 20; i++) mind.step(sight(false, false, false, 1), 10);
        assertFalse(mind.remembers());
        assertEquals(BehaviorState.ROAM, mind.state());
    }
    @Test void injuredAndTimidAnimalsFleeAndSatiatedPredatorsDoNotHunt() {
        var injured = new WildlifeMind(true, false, false);
        for (int i = 0; i < 4; i++) injured.step(sight(true, true, true, 0.2), 10);
        assertEquals(BehaviorState.FLEE, injured.state());
        var timid = new WildlifeMind(false, true, false);
        for (int i = 0; i < 4; i++) timid.step(sight(true, false, true, 1), 10);
        assertEquals(BehaviorState.FLEE, timid.state());
        var fed = new WildlifeMind(true, false, false); fed.ate();
        for (int i = 0; i < 30; i++) assertFalse(fed.step(sight(true, true, false, 1), 10).combat());
    }
    @Test void defensiveAnimalDoesNotAttackADistantObserverButDefendsAfterWarning() {
        var herbivore = new WildlifeMind(false, false, false);
        for (int i = 0; i < 30; i++) assertFalse(herbivore.step(sight(true, false, false, 1), 10).combat());
        for (int i = 0; i < 10; i++) herbivore.step(sight(true, false, true, 1), 10);
        assertEquals(BehaviorState.DEFEND, herbivore.state());
    }
    @Test void needsDriveSustainedRoutinesAndNocturnalSchedule() {
        var animal = new WildlifeMind(false, false, false);
        var ground = new WildlifeMind.Observation(0, false, false, false, false, false, false, false, true, false, 1);
        assertEquals(BehaviorState.FORAGE, animal.step(ground, 10));
        double hunger = animal.hunger();
        for (int i = 0; i < 10; i++) animal.step(ground, 10);
        assertTrue(animal.hunger() < hunger);
        var night = new WildlifeMind.Observation(0, false, false, false, false, false, false, false, false, true, 1);
        var diurnal = new WildlifeMind(true, false, false); diurnal.restoreNeeds(0.1, 0.1, 0.3);
        var nocturnal = new WildlifeMind(true, false, true); nocturnal.restoreNeeds(0.1, 0.1, 0.3);
        assertEquals(BehaviorState.REST, diurnal.step(night, 10));
        assertEquals(BehaviorState.ROAM, nocturnal.step(night, 10));
        animal.restoreNeeds(0.1, 0.9, 0.1);
        var water = new WildlifeMind.Observation(0, false, false, false, false, false, false, true, false, false, 1);
        assertEquals(BehaviorState.DRINK, animal.step(water, 10));
        assertTrue(animal.thirst() < 0.9);
    }
    @Test void malformedSavedNeedsAreClamped() {
        var mind = new WildlifeMind(false, false, false);
        mind.restoreNeeds(Double.NaN, -20, 100);
        assertTrue(Double.isFinite(mind.hunger()));
        assertEquals(0, mind.thirst()); assertEquals(1, mind.fatigue());
    }
    @Test void anAnimalWithNoWaterInReachMakesDoAndStopsSearching() {
        var animal = new WildlifeMind(false, false, false); animal.restoreNeeds(0.1, 0.9, 0.1);
        var dry = new WildlifeMind.Observation(0, false, false, false, false, false, false, false, false, false, 1);
        assertEquals(BehaviorState.SEEK_WATER, animal.step(dry, 10));
        animal.makeDo();
        assertNotEquals(BehaviorState.SEEK_WATER, animal.step(dry, 10));
        assertTrue(animal.thirst() >= 0.5 && animal.thirst() < 0.6, "thirst " + animal.thirst());
        animal.restoreNeeds(0.1, 0.2, 0.1); animal.makeDo();
        assertEquals(0.2, animal.thirst(), 1.0E-9, "making do never adds thirst");
    }
    @Test void arrivalEndsRecoveryWithoutWaitingOrRestartingIt() {
        var mind = new WildlifeMind(false, false, false); mind.restoreNeeds(0.1, 0.1, 0.1);
        mind.abandonChase();
        assertEquals(BehaviorState.RETURN_HOME, mind.step(sight(false, false, false, 1), 10));
        mind.arrivedHome();
        assertEquals(0, mind.recovery());
        for (int i = 0; i < 30; i++) assertEquals(BehaviorState.ROAM, mind.step(sight(false, false, false, 1), 10));
    }
    @Test void homeRecoveryDoesNotSuppressAHitOrAVisibleCloseIntruder() {
        var hit = new WildlifeMind(false, false, false); hit.abandonChase();
        var attack = new WildlifeMind.Observation(1, true, false, false, true, false, true, false, false, false, 1);
        assertEquals(BehaviorState.DEFEND, hit.step(attack, 10));
        assertEquals(BehaviorState.DEFEND, hit.step(attack, 10), "Being outside home range erased retaliation");
        var intruder = new WildlifeMind(false, false, false); intruder.abandonChase();
        var close = new WildlifeMind.Observation(1, true, false, true, false, false, true, false, false, false, 1);
        for (int i = 0; i < 10; i++) assertNotEquals(BehaviorState.RETURN_HOME, intruder.step(close, 10));
        assertEquals(BehaviorState.DEFEND, intruder.state());
    }
    @Test void timidAnimalWatchesADistantThreatAndBoltsOnlyFromAPressingOne() {
        var mind = new WildlifeMind(false, true, false);
        var distant = new WildlifeMind.Observation(1, true, false, false, false, false, false, false, false, false, 1, false);
        for (int i = 0; i < 40; i++) assertNotEquals(BehaviorState.FLEE, mind.step(distant, 10), "Fled from a threat that keeps its distance");
        assertEquals(BehaviorState.ALERT, mind.state());
        var pressing = new WildlifeMind.Observation(1, true, false, false, false, false, false, false, false, false, 1, true);
        assertEquals(BehaviorState.FLEE, mind.step(pressing, 10));
        // Once running it keeps running while it remembers why, even if the threat has fallen back.
        assertEquals(BehaviorState.FLEE, mind.step(distant, 10));
    }
    @Test void aFlightEndsInAWatchfulPauseInsteadOfAWalkBackToTheThreat() {
        var mind = new WildlifeMind(false, true, false);
        var threat = new WildlifeMind.Observation(1, true, false, false, false, false, false, false, false, false, 1, true);
        for (int i = 0; i < 4; i++) mind.step(threat, 10);
        assertEquals(BehaviorState.FLEE, mind.state());
        var quiet = sight(false, false, false, 1);
        int watching = 0, changes = 0;
        var last = mind.state();
        for (int i = 0; i < 40; i++) {
            var state = mind.step(quiet, 10);
            assertNotEquals(BehaviorState.INVESTIGATE, state, "A timid animal walked toward what scared it");
            if (state == BehaviorState.ALERT) watching += 10;
            if (state != last) { changes++; last = state; }
        }
        assertTrue(watching >= WildlifeMind.WARY_TICKS, "The animal resumed its routine after " + watching + " ticks of watching");
        assertEquals(BehaviorState.ROAM, mind.state());
        assertEquals(2, changes, "Flee, watch, roam: no flicker between states");
    }
    private static WildlifeMind.Routine hour(DailySchedule.Activity activity) {
        return new WildlifeMind.Routine(true, false, false, true, false, false, false, 0, 1, false, activity);
    }
    @Test void theHourSendsAGrazerToFeedAndLieUpWithoutWaitingForTheNeed() {
        var mind = new WildlifeMind(false, false, false);
        mind.restoreNeeds(.05, .1, .05);
        var onGrass = new WildlifeMind.Observation(0, false, false, false, false, false, false, false, true, false, 1);
        assertEquals(BehaviorState.ROAM, mind.step(onGrass, 10, hour(DailySchedule.Activity.ROAM), null), "fed and rested, nothing to do");
        assertEquals(BehaviorState.FORAGE, mind.step(onGrass, 10, hour(DailySchedule.Activity.FEED), null), "feeding hours on grazing ground");
        for (int i = 0; i < 60; i++) assertEquals(BehaviorState.FORAGE, mind.step(onGrass, 10, hour(DailySchedule.Activity.FEED), null));
        assertEquals(BehaviorState.REST, mind.step(onGrass, 10, hour(DailySchedule.Activity.REST), null), "the midday rest");
        for (int i = 0; i < 60; i++) assertEquals(BehaviorState.REST, mind.step(onGrass, 10, hour(DailySchedule.Activity.REST), null));
        assertEquals(BehaviorState.FORAGE, mind.step(onGrass, 10, hour(DailySchedule.Activity.FEED), null), "up again when the feeding hours return");
        var offGrass = sight(false, false, false, 1);
        assertEquals(BehaviorState.ROAM, mind.step(offGrass, 10, hour(DailySchedule.Activity.FEED), null), "no grazing ground: it moves on");
        var hunter = new WildlifeMind(true, false, false);
        hunter.restoreNeeds(.05, .1, .05);
        assertEquals(BehaviorState.ROAM, hunter.step(onGrass, 10, hour(DailySchedule.Activity.FEED), null), "hunters do not graze");
        assertEquals(BehaviorState.REST, hunter.step(onGrass, 10, hour(DailySchedule.Activity.REST), null), "but they lie up");
        var exposed = new WildlifeMind.Routine(true, false, false, false, false, false, false, 0, 1, false, DailySchedule.Activity.REST);
        assertEquals(BehaviorState.ROAM, hunter.step(onGrass, 10, exposed, null), "never where it is not safe to");
    }
    @Test void wateringTimeTakesALittleThirstToTheBankAndADrinkLasts() {
        var mind = new WildlifeMind(false, false, false);
        mind.restoreNeeds(.05, .3, .05);
        var dryLand = sight(false, false, false, 1);
        var atWater = new WildlifeMind.Observation(0, false, false, false, false, false, false, true, false, false, 1);
        assertEquals(BehaviorState.ROAM, mind.step(dryLand, 10, hour(DailySchedule.Activity.ROAM), null), "a little thirst waits for the hour");
        assertEquals(BehaviorState.SEEK_WATER, mind.step(dryLand, 10, hour(DailySchedule.Activity.DRINK), null));
        assertEquals(BehaviorState.DRINK, mind.step(atWater, 10, hour(DailySchedule.Activity.DRINK), null));
        int drinking = 10;
        while (mind.step(atWater, 10, hour(DailySchedule.Activity.DRINK), null) == BehaviorState.DRINK && drinking < 2000) drinking += 10;
        assertTrue(drinking >= WildlifeMind.DRINK_TICKS, "the drink lasted " + drinking + " ticks");
        assertTrue(mind.thirst() < 0.05);
        // A range with no water in reach is not searched again at every watering hour.
        var dry = new WildlifeMind(false, false, false);
        dry.restoreNeeds(.05, .4, .05);
        assertEquals(BehaviorState.SEEK_WATER, dry.step(dryLand, 10, hour(DailySchedule.Activity.DRINK), null));
        dry.makeDo();
        assertEquals(BehaviorState.ROAM, dry.step(dryLand, 10, hour(DailySchedule.Activity.DRINK), null));
        dry.restoreNeeds(.05, .7, .05);
        assertEquals(BehaviorState.SEEK_WATER, dry.step(dryLand, 10, hour(DailySchedule.Activity.DRINK), null), "real thirst still searches");
    }
    @Test void anInvestigationLastsLongEnoughToBeOne() {
        var mind = new WildlifeMind(true, false, false);
        var sound = new WildlifeMind.Observation(0.65, false, false, false, false, false, false, false, false, false, 1);
        mind.step(sound, 10); mind.step(sound, 10);
        assertEquals(BehaviorState.INVESTIGATE, mind.state());
        var quiet = sight(false, false, false, 1);
        int ticks = 0;
        while (mind.step(quiet, 10) == BehaviorState.INVESTIGATE && ticks < 400) ticks += 10;
        assertTrue(ticks + 20 >= WildlifeMind.INVESTIGATE_TICKS, "Investigated for " + (ticks + 20) + " ticks");
    }
}
