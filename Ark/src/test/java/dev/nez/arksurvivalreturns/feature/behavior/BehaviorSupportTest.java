package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.EnumMap;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BehaviorSupportTest {
    private static final BehaviorProfile REX = new BehaviorProfile("tyrannosaurus", true, false, false, false, false, 13.5,
            "Rex-Roar", BehaviorClips.of("tyrannosaurus"));
    private static final BehaviorProfile WOLF = new BehaviorProfile("direwolf", true, false, true, true, false, 1.6,
            "Direwolf-Howl", BehaviorClips.of("direwolf"));

    @Test void desyncIsStableBoundedAndDiffersBetweenHerdMates() {
        assertEquals(Desync.unit(99, 3), Desync.unit(99, 3));
        assertNotEquals(Desync.unit(99, 3), Desync.unit(100, 3));
        for (long seed = -500; seed < 500; seed++) {
            double speed = Desync.speedFactor(seed * 31);
            assertTrue(speed >= 0.9 && speed < 1.1);
            double jitter = Desync.headingJitter(seed, 4);
            assertTrue(Math.abs(jitter) <= 35);
            float rate = Desync.animationRate(seed);
            assertTrue(rate >= 0.94f && rate < 1.06f);
            double radius = Desync.formationRadius(seed, 20, 2);
            assertTrue(radius >= 4 && radius <= 16);
        }
        assertTrue(Desync.reactionDelay(5, 0, 40, false) > Desync.reactionDelay(5, 0, 2, false), "alarms ripple outward");
        assertTrue(Desync.reactionDelay(5, 0, 20, true) < Desync.reactionDelay(5, 0, 20, false), "timid animals react first");
    }

    private static final BehaviorProfile STAG = new BehaviorProfile("megalocerus", false, true, true, false, true, 2.2,
            "Stag-Startled", BehaviorClips.of("megalocerus"));

    /** Runs the chain of bouts for a while and returns the share of the time spent on each step. */
    private static EnumMap<CalmRoutine.Step, Double> budget(CalmRoutine.Mode mode, BehaviorProfile profile, long seed) {
        var random = new SplittableRandom(seed);
        var time = new EnumMap<CalmRoutine.Step, Double>(CalmRoutine.Step.class);
        CalmRoutine.Step previous = null;
        double total = 0;
        for (int i = 0; i < 20000; i++) {
            var plan = CalmRoutine.next(mode, previous, profile, random);
            assertFalse(previous != null && previous.travels() && plan.step().travels(), "two walks in a row: " + mode);
            // A walked bout lasts as long as its distance takes at a 1.6 blocks a second walk; a turn about a second.
            double ticks = plan.step().travels() ? plan.distance() / (1.6 * plan.pace()) * 20 : plan.step() == CalmRoutine.Step.TURN ? 20 : plan.ticks();
            assertTrue(ticks > 0, plan.toString());
            time.merge(plan.step(), ticks, Double::sum);
            total += ticks;
            previous = plan.step();
        }
        for (var step : time.keySet()) time.put(step, time.get(step) / total);
        return time;
    }

    private static double moving(EnumMap<CalmRoutine.Step, Double> budget) {
        return budget.getOrDefault(CalmRoutine.Step.WALK, 0.0) + budget.getOrDefault(CalmRoutine.Step.STEP, 0.0);
    }

    @Test void calmRoutineSpendsTimeLikeAnAnimalAndOnlyPlaysClipsTheRigHas() {
        var random = new SplittableRandom(1);
        assertEquals(CalmRoutine.Step.SLEEP, CalmRoutine.sleep(random).step());
        assertEquals(CalmRoutine.Step.WALK, CalmRoutine.homeward(REX, random).step());
        var grazing = budget(CalmRoutine.Mode.GRAZE, STAG, 2);
        assertTrue(grazing.get(CalmRoutine.Step.GRAZE) > 0.6, "a grazer on its feeding ground has its head down: " + grazing);
        assertTrue(moving(grazing) < 0.2, "and barely walks: " + grazing);
        assertTrue(grazing.containsKey(CalmRoutine.Step.LOOK) && grazing.containsKey(CalmRoutine.Step.POOP), grazing.toString());
        assertFalse(grazing.containsKey(CalmRoutine.Step.SNIFF), "the stag has no sniff clip");
        assertFalse(grazing.containsKey(CalmRoutine.Step.LIE), "lying up belongs to the rest hours");
        var loafing = budget(CalmRoutine.Mode.LOAF, REX, 3);
        assertTrue(moving(loafing) < 0.4, "an animal with nothing to do stands more than it walks: " + loafing);
        assertTrue(loafing.get(CalmRoutine.Step.STAND) > 0.3, loafing.toString());
        assertFalse(loafing.containsKey(CalmRoutine.Step.SNIFF), "Rex has no sniff clip");
        assertFalse(loafing.containsKey(CalmRoutine.Step.GRAZE) || loafing.containsKey(CalmRoutine.Step.STEP), "carnivores do not graze");
        var patrol = budget(CalmRoutine.Mode.PATROL, WOLF, 4);
        assertTrue(moving(patrol) > moving(loafing) && moving(patrol) < 0.75, "a round is mostly walking, with stops: " + patrol);
        assertTrue(patrol.getOrDefault(CalmRoutine.Step.SNIFF, 0.0) > 0.03, "wolves scent the air on their rounds: " + patrol);
        var resting = budget(CalmRoutine.Mode.REST, REX, 5);
        assertTrue(resting.get(CalmRoutine.Step.LIE) > 0.7 && moving(resting) == 0, "lying up is lying: " + resting);
        // The bouts of a big animal last longer and cover more ground.
        assertTrue(CalmRoutine.slow(REX) > CalmRoutine.slow(STAG) && CalmRoutine.stride(REX) > CalmRoutine.stride(STAG));
        for (int i = 0; i < 2000; i++) {
            var plan = CalmRoutine.next(CalmRoutine.Mode.LOAF, CalmRoutine.Step.STAND, STAG, random);
            if (plan.step() == CalmRoutine.Step.TURN) assertTrue(Math.abs(plan.turn()) >= 30 && Math.abs(plan.turn()) < 100);
            if (plan.step() == CalmRoutine.Step.WALK) assertTrue(plan.distance() >= 6 && plan.distance() < 18 && plan.pace() == 1);
        }
        assertEquals(BehaviorState.FORAGE, CalmRoutine.state(CalmRoutine.Step.GRAZE));
        assertEquals(BehaviorState.REST, CalmRoutine.state(CalmRoutine.Step.LIE));
        assertEquals(BehaviorAction.TURN, CalmRoutine.action(CalmRoutine.Step.TURN));
        assertEquals(BehaviorAction.WALK, CalmRoutine.action(CalmRoutine.Step.STEP));
    }

    @Test void flightCurvesStayAroundTheNestAndMoveSmoothly() {
        var random = new SplittableRandom(8);
        for (int lap = 0; lap < 200; lap++) {
            var shape = FlightPath.pick(random, 32, 10, lap % 2 == 0);
            double theta = 0, previousY = Double.NaN;
            double[] previous = FlightPath.offset(shape, theta);
            while (theta < Math.PI * 2) {
                double step = FlightPath.step(shape, theta, 0.3);
                assertTrue(step > 0 && step <= 0.5);
                theta += step;
                double[] next = FlightPath.offset(shape, theta);
                double moved = Math.sqrt(Math.pow(next[0] - previous[0], 2) + Math.pow(next[1] - previous[1], 2) + Math.pow(next[2] - previous[2], 2));
                assertTrue(moved < 0.6, "the followed point never jumps: " + moved + " " + shape);
                assertTrue(Math.hypot(next[0], next[2]) <= 32 * 1.25, "lap stays near the roam radius");
                assertTrue(next[1] >= 10 - 6.01 && next[1] <= 10 + 12, "altitude stays in a band: " + next[1]);
                previous = next;
                previousY = next[1];
            }
            assertFalse(Double.isNaN(previousY));
        }
    }

    @Test void everySpeciesClipBookResolvesItsRolesAndGaits() {
        for (var id : new String[]{"tyrannosaurus", "triceratops", "parasaur", "direwolf", "megalocerus", "pteranodon",
                "argentavis", "mosasaurus", "deinosuchus", "titanosaur"}) {
            var book = BehaviorClips.of(id);
            assertEquals(id, book.species());
            for (var role : ClipRole.values()) if (book.has(role)) assertNotNull(book.clip(book.name(role)), id + " " + role);
        }
        var rex = BehaviorClips.of("tyrannosaurus");
        assertTrue(rex.groundSpeed("Rex-Move-Fwd") > 3 && rex.groundSpeed("Rex-Move-Fwd") < rex.groundSpeed("Rex-Charge-Fwd"));
        assertTrue(rex.has(ClipRole.TURN_LEFT) && rex.has(ClipRole.TURN_RIGHT));
        assertTrue(Double.isNaN(BehaviorClips.of("mosasaurus").groundSpeed("Mosasaurus-Swim-Fwd")), "swimming has no stance speed");
        assertTrue(BehaviorClips.of("megalocerus").has(ClipRole.LOOK) && BehaviorClips.of("megalocerus").has(ClipRole.POOP));
        assertTrue(BehaviorClips.of("direwolf").has(ClipRole.SNIFF));
        assertTrue(BehaviorClips.of("deinosuchus").has(ClipRole.SETTLE) && BehaviorClips.of("deinosuchus").has(ClipRole.WAKE));
        assertSame(ClipBook.EMPTY, BehaviorClips.of("unknown"));
        assertEquals(40, ClipBook.EMPTY.ticks("anything", 40));
    }
}
