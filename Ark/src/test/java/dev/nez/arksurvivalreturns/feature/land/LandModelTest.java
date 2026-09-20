package dev.nez.arksurvivalreturns.feature.land;

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
