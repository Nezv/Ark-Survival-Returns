package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.feature.behavior.*;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.land.LandFamily;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;

/** Exercise the actual registered expansion entities and shared decision profiles. */
final class CreatureExpansionGameTests {
    static void run(GameTestHelper h) {
        var species = new Species[]{Species.SPINOSAURUS, Species.PARASAUR, Species.CERATOSAURUS,
                Species.DILOPHOSAUR, Species.ACROCANTHOSAURUS, Species.ALLOSAURUS, Species.ANKYLOSAURUS,
                Species.CARNOTAURUS, Species.PEGOMASTAX, Species.LYSTROSAURUS};
        for (var s : species) {
            var entity=ModContent.CREATURES.get(s).get().create(h.getLevel(),EntitySpawnReason.COMMAND);
            h.assertTrue(entity!=null && entity.species()==s && !s.flyer(), "Registration mismatch: "+s);
            h.assertTrue(ModContent.EGGS.get(s).get()!=null, "Missing spawn egg: "+s);
            h.assertTrue(s.minGroup==s.family().minGroup && s.maxGroup==s.family().maxGroup, "Group profile mismatch: "+s);
            h.assertTrue(Math.abs(entity.getBbHeight()-s.height)<.001, "Hitbox mismatch: "+s);
            h.assertTrue(s.strideCycleSeconds(false)>0 && s.strideCycleSeconds(true)>0, "Invalid cadence: "+s);
            var mind=entity.wildlife().mind(); mind.restoreNeeds(.8,.1,0);
            var observation=new WildlifeMind.Observation(1,true,true,true,false,false,false,false,false,true,1);
            for (int i=0;i<8;i++) mind.step(observation,10);
            if (s.timid()) {
                h.assertTrue(mind.state()==BehaviorState.FLEE && !s.defensiveHerd(), "Timid herd did not flee: "+s);
                h.assertTrue(s.family()==LandFamily.SMALL_HERBIVORE, "Timid family mismatch: "+s);
            } else if (s.predator) h.assertTrue(mind.state()==BehaviorState.HUNT, "Predator did not hunt: "+s);
            else h.assertTrue(s.defensiveHerd() && mind.state()==BehaviorState.DEFEND, "Ankylo did not defend");
            if (!s.solitary()) {
                var second=ModContent.CREATURES.get(s).get().create(h.getLevel(),EntitySpawnReason.COMMAND);
                var difficulty=h.getLevel().getCurrentDifficultyAt(entity.blockPosition());
                var group=entity.finalizeSpawn(h.getLevel(),difficulty,EntitySpawnReason.NATURAL,null);
                second.finalizeSpawn(h.getLevel(),difficulty,EntitySpawnReason.NATURAL,group);
                h.assertTrue(entity.packId().equals(second.packId()), "Spawn group split: "+s);
            }
        }
        h.assertTrue(Species.ALLOSAURUS.family()==Species.VELOCIRAPTOR.family(), "Allosaurus lost pack profile");
        h.assertTrue(Species.ANKYLOSAURUS.family()==Species.TRICERATOPS.family(), "Ankylo lost herd profile");
        h.succeed();
    }
}
