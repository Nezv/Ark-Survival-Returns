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

    @Test void ambientRoutineSleepsOnScheduleWalksHomeAndOnlyPlaysClipsTheRigHas() {
        var random = new SplittableRandom(1);
        assertEquals(AmbientRoutine.Step.SLEEP, AmbientRoutine.next(DailySchedule.Phase.SLEEP, REX, random, false).step());
        assertEquals(AmbientRoutine.Step.WALK, AmbientRoutine.next(DailySchedule.Phase.ROAM, REX, random, true).step());
        var seen = new EnumMap<AmbientRoutine.Step, Integer>(AmbientRoutine.Step.class);
        for (int i = 0; i < 5000; i++) {
            var plan = AmbientRoutine.next(DailySchedule.Phase.ROAM, REX, random, false);
            seen.merge(plan.step(), 1, Integer::sum);
            if (plan.step() == AmbientRoutine.Step.TURN) assertTrue(Math.abs(plan.turn()) >= 40 && Math.abs(plan.turn()) < 130);
            if (plan.step() == AmbientRoutine.Step.WALK) assertTrue(plan.distance() >= 4 && plan.distance() < 16);
        }
        assertFalse(seen.containsKey(AmbientRoutine.Step.SNIFF), "Rex has no sniff clip");
        assertFalse(seen.containsKey(AmbientRoutine.Step.GRAZE), "carnivores do not graze");
        assertTrue(seen.getOrDefault(AmbientRoutine.Step.TURN, 0) > 500);
        int sniffs = 0;
        for (int i = 0; i < 2000; i++)
            if (AmbientRoutine.next(DailySchedule.Phase.HUNT, WOLF, random, false).step() == AmbientRoutine.Step.SNIFF) sniffs++;
        assertTrue(sniffs > 100, "wolves sniff on night patrols: " + sniffs);
        assertEquals(BehaviorState.FORAGE, AmbientRoutine.state(AmbientRoutine.Step.GRAZE));
        assertEquals(BehaviorAction.TURN, AmbientRoutine.action(AmbientRoutine.Step.TURN));
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
