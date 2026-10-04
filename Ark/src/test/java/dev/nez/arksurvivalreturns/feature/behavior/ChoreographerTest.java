package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.nez.arksurvivalreturns.feature.behavior.BehaviorAction.*;
import static dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.*;

class ChoreographerTest {
    private static final BehaviorProfile REX = new BehaviorProfile("tyrannosaurus", true, false, false, false, false, 13.5,
            "Rex-Roar", BehaviorClips.of("tyrannosaurus"));
    private static final BehaviorProfile PARA = new BehaviorProfile("parasaur", false, true, true, false, true, 4,
            "Para-Roar-Alert", BehaviorClips.of("parasaur"));
    private static final BehaviorProfile SABER = new BehaviorProfile("sabertooth", true, false, false, true, false, 1.6,
            "Saber-Startled", BehaviorClips.of("sabertooth"));
    private static final BehaviorProfile STAG = new BehaviorProfile("megalocerus", false, true, true, false, true, 2.2,
            "Stag-Startled", BehaviorClips.of("megalocerus"));

    /** Plays the choreography in think-sized steps and returns the distinct actions in order. */
    private static List<BehaviorAction> play(Choreographer choreo, int thinks, boolean travelling) {
        var seen = new ArrayList<BehaviorAction>();
        seen.add(choreo.action());
        for (int i = 0; i < thinks; i++) {
            choreo.advance(10, travelling);
            if (seen.getLast() != choreo.action()) seen.add(choreo.action());
        }
        return seen;
    }

    @Test void aBigCarnivoreNoticesAndRoarsOnceBeforeItCharges() {
        var rex = new Choreographer(REX, 42);
        rex.reset(SEARCH);
        rex.enter(SEARCH, ALERT, false);
        assertEquals(NOTICE, rex.action());
        assertTrue(rex.holding(), "noticing holds the body");
        rex.enter(ALERT, THREATEN, false);
        assertEquals(ROAR, rex.action(), "the warning display is the roar");
        assertEquals(Cue.WARN, rex.takeCue());
        assertNull(rex.takeCue(), "a cue is delivered once");
        int roar = rex.remaining();
        assertTrue(roar > 60 && roar < 120, "roar lasts about its 4.4 s clip: " + roar);
        rex.enter(THREATEN, HUNT, false);
        rex.settle(true);
        assertEquals(CHASE, rex.action(), "no second roar in the same encounter");
        assertFalse(rex.holding());
    }

    @Test void aStoppedHunterAndACorneredAnimalFaceTheOtherInsteadOfRunningOnTheSpot() {
        var rex = new Choreographer(REX, 42);
        rex.reset(HUNT);
        rex.settle(true);
        assertEquals(CHASE, rex.action());
        rex.settle(false);
        assertEquals(Motion.FACE, rex.action().motion(), "a hunter with no way on stands facing its prey: " + rex.action());
        assertFalse(rex.holding(), "a stopped hunter is free to strike or to set off again");
        rex.settle(true);
        assertEquals(CHASE, rex.action());
        var para = new Choreographer(PARA, 1);
        para.reset(FLEE);
        para.settle(true);
        assertEquals(BOLT, para.action());
        para.settle(false);
        assertEquals(NOTICE, para.action(), "a cornered animal watches the threat");
        para.enter(FLEE, FLEE, false);
        para.settle(true);
        assertEquals(BOLT, para.action());
    }

    @Test void aCalmCarnivoreGoingStraightToHuntStillLooksThenRoarsThenRuns() {
        var rex = new Choreographer(REX, 7);
        rex.reset(SEARCH);
        rex.enter(SEARCH, HUNT, false);
        var sequence = play(rex, 30, true);
        assertEquals(List.of(NOTICE, ROAR, CHASE), sequence);
    }

    @Test void stalkersCreepInsteadOfRoaring() {
        var saber = new Choreographer(SABER, 3);
        saber.reset(SEARCH);
        saber.enter(SEARCH, HUNT, false);
        var sequence = play(saber, 20, true);
        assertEquals(List.of(NOTICE, STALK, CHASE), sequence);
        assertFalse(sequence.contains(ROAR));
    }

    @Test void preyStartlesBeforeItBoltsAndAHitShortensTheStartle() {
        var calm = new Choreographer(PARA, 1);
        calm.reset(FORAGE);
        calm.enter(FORAGE, FLEE, false);
        assertEquals(STARTLE, calm.action());
        assertEquals(Cue.STARTLE, calm.takeCue());
        int normal = calm.remaining();
        assertEquals(List.of(STARTLE, BOLT), play(calm, 10, true));
        var hit = new Choreographer(PARA, 1);
        hit.reset(FORAGE);
        hit.enter(FORAGE, FLEE, true);
        assertTrue(hit.remaining() <= 12 && hit.remaining() < normal, "reflex startle is short: " + hit.remaining());
    }

    @Test void sleepersWakeBeforeTheyReactAndSettleBeforeTheySleep() {
        var herbivore = new Choreographer(STAG, 5);
        herbivore.reset(BehaviorState.SLEEP);
        herbivore.enter(BehaviorState.SLEEP, FLEE, false);
        assertEquals(WAKE, herbivore.action());
        assertEquals(List.of(WAKE, STARTLE, BOLT), play(herbivore, 12, true));
        herbivore.enter(FLEE, ROAM, false);
        assertEquals(LOOK, herbivore.action(), "after an alarm it looks back before settling");
        herbivore.reset(ROAM);
        herbivore.enter(ROAM, BehaviorState.SLEEP, false);
        assertEquals(LOOK, herbivore.action(), "a last look around before sleep");
        var settled = play(herbivore, 20, false);
        assertEquals(BehaviorAction.SLEEP, settled.getLast());
    }

    @Test void routineBoutsHoldTheirActionAndNeverCutAReactionShort() {
        var stag = new Choreographer(STAG, 11);
        stag.reset(ROAM);
        assertTrue(stag.perform(GRAZE, 200));
        assertEquals(GRAZE, stag.action());
        assertTrue(stag.holding(), "a grazing bout holds the body in place");
        assertFalse(stag.perform(LOOK, 40), "a bout in progress is not replaced");
        stag.advance(190, false);
        assertEquals(GRAZE, stag.action());
        stag.advance(10, false);
        assertFalse(stag.holding());
        assertEquals(IDLE, stag.action(), "the bout over, the body stands until the next one");
        assertTrue(stag.perform(POOP, 30));
        assertEquals(Cue.POOP, stag.takeCue(), "a bout with a clip plays it once");
        assertNull(stag.takeCue());
        stag.release();
        assertFalse(stag.holding(), "a released bout ends at once, for a herd that moves off");
        stag.enter(ROAM, FLEE, false);
        assertFalse(stag.perform(GRAZE, 100), "the routine never cuts a startle short");
        assertEquals(STARTLE, stag.action());
    }

    @Test void individualsDifferButTheSameAnimalIsReproducible() {
        var a = new Choreographer(PARA, 1001);
        var b = new Choreographer(PARA, 1001);
        var c = new Choreographer(PARA, 2002);
        for (var choreo : List.of(a, b, c)) { choreo.reset(ROAM); choreo.enter(ROAM, FLEE, false); }
        assertEquals(a.remaining(), b.remaining());
        var durations = new java.util.HashSet<Integer>();
        for (long seed = 0; seed < 40; seed++) {
            var herd = new Choreographer(PARA, seed * 7919);
            herd.reset(ROAM);
            herd.enter(ROAM, FLEE, false);
            durations.add(herd.remaining());
        }
        assertTrue(durations.size() > 5, "herd mates startle for different lengths: " + durations);
    }
}
