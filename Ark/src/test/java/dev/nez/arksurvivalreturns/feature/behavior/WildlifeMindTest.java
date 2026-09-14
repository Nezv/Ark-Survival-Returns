package dev.nez.arksurvivalreturns.feature.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WildlifeMindTest {
    private static WildlifeMind.Observation sight(boolean visible, boolean prey, boolean intruding, double health) {
        return new WildlifeMind.Observation(visible ? 1 : 0, visible, prey, intruding, false, false, false, false, false, false, health);
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
}
