package dev.nez.arksurvivalreturns.feature.land;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.nez.arksurvivalreturns.feature.behavior.*;
import static org.junit.jupiter.api.Assertions.*;

class LandModelTest {
    @Test void waterPenaltyHasExactExclusionAndNoMinimumWeightLeak() {
        assertEquals(1,LandFamily.waterWeight(16,16,48));
        assertEquals(.5,LandFamily.waterWeight(32,16,48));
        assertEquals(0,LandFamily.waterWeight(48,16,48));
        assertEquals(0,LandFamily.waterWeight(49,16,48));
        assertEquals(0,LandFamily.waterWeight(Double.NaN,16,48));
        assertEquals(1,LandFamily.waterWeight(47,80,48));
    }
    @Test void memberCallbacksNeverMultiplyHungerAndUnloadedTimeIsNotReplayed() {
        var group=new GroupNeeds(.5);group.advance(100,1,0,6);double once=group.hunger();
        for(int i=0;i<6;i++)assertFalse(group.advance(100,1,0,6));
        assertEquals(once,group.hunger());
        group.advance(100000,1,0,6);assertEquals(20/24000.0,group.hunger()-once,1e-10);
    }
    @Test void grazingCreditIsProportionalToActualParticipantsAndMealIsDeduplicated() {
        var one=new GroupNeeds(.8);var all=new GroupNeeds(.8);
        one.advance(0,1,1,4);all.advance(0,1,4,4);
        assertEquals(.8+20/24000.0-20/400.0/4,one.hunger(),1e-10);
        assertTrue(all.hunger()<one.hunger());
        var victim=UUID.randomUUID();assertTrue(one.feed(victim,20));
        assertFalse(one.feed(victim,20));assertEquals(.05,one.hunger());assertTrue(one.feeding());
        assertTrue(all.hunger()>.05,"Other groups received food");
    }
    @Test void sharedIntentPreservesIndividualDangerAndDoesNotConsumeFoodTwice() {
        var mind=new WildlifeMind(false,false,false);
        var group=new WildlifeMind.GroupRoutine(.8,BehaviorState.FORAGE);
        var calm=new WildlifeMind.Observation(0,false,false,false,false,false,false,false,true,false,1);
        assertEquals(BehaviorState.FORAGE,mind.step(calm,10,WildlifeMind.Routine.LEGACY,group));
        assertEquals(.8,mind.hunger());
        var danger=new WildlifeMind.Observation(1,true,false,false,true,false,false,false,true,false,.2);
        assertEquals(BehaviorState.FLEE,mind.step(danger,10,WildlifeMind.Routine.LEGACY,group));
        assertEquals(.8,mind.hunger());
    }
    @Test void allHerbivoreOrdinaryRangesAreSmallerThanCarnivoreRanges() {
        for(var f:new LandFamily[]{LandFamily.BIG_HERBIVORE,LandFamily.SMALL_HERBIVORE,LandFamily.TITANOSAUR}) {
            assertTrue(f.roam<LandFamily.SMALL_CARNIVORE.roam);assertTrue(f.roam<LandFamily.BIG_CARNIVORE.roam);
        }
    }
}
