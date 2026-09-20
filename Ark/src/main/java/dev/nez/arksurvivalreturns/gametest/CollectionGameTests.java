package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeMind;
import dev.nez.arksurvivalreturns.feature.creature.CreatureAttackClips;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.land.LandFamily;
import dev.nez.arksurvivalreturns.feature.land.LandWildlife;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;

/** Registry, realm wiring and cold-habitat policy for the ice, flying, aquatic and swamp collection. */
final class CollectionGameTests {
    private static final Species[] COLLECTION = {Species.CNIDARIA, Species.PLESIOSAUR, Species.MEGALODON,
            Species.LIOPLEURODON, Species.MOSASAURUS, Species.TUSOTEUTHIS, Species.KAPROSUCHUS, Species.SARCO,
            Species.DEINOSUCHUS, Species.TITANOBOA, Species.MEGALOCERUS, Species.UNICORN, Species.MAMMOTH,
            Species.DIREWOLF, Species.SABERTOOTH, Species.MEGAPITHECUS, Species.PARACERATHERIUM, Species.TERRORBIRD,
            Species.RAVAGER, Species.ARCHAEOPTERYX, Species.QUETZAL, Species.DRAGON};

    static void registration(GameTestHelper h) {
        // Realm, group, clip and registry invariants for every registered species, not only the collection.
        for (var species : Species.values()) {
            var entity = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
            h.assertTrue(entity != null && entity.species() == species, "Registration mismatch: " + species);
            h.assertTrue(ModContent.EGGS.get(species).get() != null, "Missing spawn egg: " + species);
            h.assertTrue(Math.abs(entity.getBbHeight() - species.height) < .001, "Hitbox mismatch: " + species);
            h.assertFalse(entity.isLocomoting(), "Fresh creature already reported travel: " + species);
            h.assertTrue(entity.wildlife() != null && entity.wildlife().mind() != null, "Missing routine controller: " + species);
            h.assertTrue(species.strideCycleSeconds(false) > 0 && species.strideCycleSeconds(true) > 0, "Invalid cadence: " + species);
            h.assertTrue(species.runClip() != null && species.foodClip() != null && species.warningClip() != null,
                    "Missing role clip: " + species);
            h.assertTrue(CreatureAttackClips.of(species) != null && CreatureAttackClips.of(species).attackTicks() > 0,
                    "Missing melee timing: " + species);
            h.assertTrue(species.minimumDanger() >= 1 && species.minimumDanger() <= 5, "Danger outside 1-5: " + species);
            h.assertTrue(species.eyeBones().length == 0 || species.eyeBones().length == 2, "Eye bones must be empty or a pair: " + species);
            switch (species.realm()) {
                case AIR -> {
                    h.assertTrue(species.flyer() && species.flyerProfile() != null, "AIR species needs a flight policy: " + species);
                    h.assertTrue(ModContent.NESTS.get(species).get() != null, "Missing nest block: " + species);
                    h.assertTrue(ModContent.NEST_EGGS.get(species).get() != null, "Missing nest egg: " + species);
                    h.assertFalse(species.sleeps(), "AIR species perches instead of sleeping: " + species);
                }
                case WATER -> {
                    h.assertTrue(species.aquatic() && species.family() == LandFamily.AQUATIC, "Water realm mismatch: " + species);
                    h.assertTrue(!species.sleeps() && !species.landHabitat(), "Water realm kept a ground routine: " + species);
                }
                case AMPHIBIOUS -> h.assertTrue(species.swimmer() && species.landHabitat() && species.sleeps(),
                        "Amphibious realm needs a shoreline habitat and a sleep pose: " + species);
                case LAND -> h.assertFalse(species.swimmer(), "Land species has no water clip set: " + species);
            }
            if (species.landHabitat()) {
                h.assertTrue(species.minGroup == species.family().minGroup && species.maxGroup == species.family().maxGroup,
                        "Group profile mismatch: " + species);
                // Rex and Triceratops ship their own sleeping clips; every other land species uses the
                // authored standing pose so a sleep state always has a real clip to play.
                h.assertTrue(species.sleepClip().equals("Ark-Sleep")
                                || species == Species.TYRANNOSAURUS || species == Species.TRICERATOPS,
                        "Missing sleep pose: " + species);
            }
            if (species.swimmer())
                h.assertTrue(species.swimIdle() != null && species.swimWalk() != null && species.swimRun() != null,
                        "Missing water clip set: " + species);
            // Every realm reports status through the shared decision model.
            entity.wildlife().mind().restoreNeeds(.8, .1, 0);
            var observation = new WildlifeMind.Observation(1, true, true, true, false, false, false, false, false, true, 1);
            for (int i = 0; i < 8; i++) entity.wildlife().mind().step(observation, 10);
            if (species.timid()) h.assertTrue(entity.wildlife().mind().state() == BehaviorState.FLEE, "Timid species did not flee: " + species);
            else if (species.predator) h.assertTrue(entity.wildlife().mind().state() == BehaviorState.HUNT, "Predator did not hunt: " + species);
            entity.discard();
        }
        for (var species : COLLECTION)
            h.assertTrue(ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND) != null,
                    "Collection species not registered: " + species);
        // Realm and family splits that the collection is expected to keep.
        h.assertTrue(Species.DEINOSUCHUS.solitary() && Species.KAPROSUCHUS.solitary() && Species.TITANOBOA.solitary(),
                "Swamp solo rule changed");
        h.assertTrue(Species.UNICORN.solitary() && Species.MEGAPITHECUS.solitary(), "Rare solo rule changed");
        h.assertTrue(Species.DIREWOLF.family() == LandFamily.COLD_PREDATOR && Species.DIREWOLF.minGroup == 4,
                "Cold pack profile changed");
        h.assertTrue(Species.SABERTOOTH.family() == LandFamily.COLD_STALKER && Species.SABERTOOTH.maxGroup == 2,
                "Cold stalking pair profile changed");
        h.assertTrue(Species.QUETZAL.solitary() && Species.DRAGON.solitary(), "Apex flyers must roost alone");
        h.assertTrue(Species.ARCHAEOPTERYX.flyer() && !Species.ARCHAEOPTERYX.solitary(), "Glider colony size changed");
        h.assertTrue(Species.DRAGON.apex() && Species.DRAGON.predator, "Apex flyer policy changed");
        h.assertTrue(Species.CNIDARIA.family() == LandFamily.AQUATIC, "Cnidaria is not a water realm");
        h.assertTrue(Species.values().length == 41, "Registered species count changed: " + Species.values().length);
        h.succeed();
    }

    static void cold(GameTestHelper h) {
        var world = h.getLevel();
        for (int x = 16; x < 48; x++) for (int z = 16; z < 48; z++) {
            h.setBlock(x, 0, z, Blocks.STONE);
            h.setBlock(x, 1, z, Blocks.SNOW_BLOCK);
        }
        var snow = h.absolutePos(new BlockPos(30, 1, 30));
        h.assertTrue(Boolean.TRUE.equals(LandWildlife.coldHydration(world, snow)), "Snow block hydration rejected");
        h.setBlock(31, 2, 31, Blocks.SNOW);
        h.assertTrue(Boolean.TRUE.equals(LandWildlife.coldHydration(world, h.absolutePos(new BlockPos(31, 2, 31)))),
                "Snow layer hydration rejected");
        h.setBlock(31, 2, 31, Blocks.AIR);

        // A frozen crossing is useful only when a bounded check confirms water beneath the ice.
        h.setBlock(40, 1, 20, Blocks.WATER);
        h.setBlock(40, 2, 20, Blocks.WATER);
        h.setBlock(40, 3, 20, Blocks.ICE);
        var iceOverWater = h.absolutePos(new BlockPos(40, 3, 20));
        h.assertTrue(Boolean.TRUE.equals(LandWildlife.coldHydration(world, iceOverWater)), "Ice over water rejected");
        for (int y = 1; y <= 3; y++) h.setBlock(41, y, 21, Blocks.ICE);
        h.assertFalse(Boolean.TRUE.equals(LandWildlife.coldHydration(world, h.absolutePos(new BlockPos(41, 3, 21)))),
                "Bare ice accepted as water");

        // Snow browsing is a cold-adapted abstraction, never a warm-species one.
        var browse = h.absolutePos(new BlockPos(32, 2, 32));
        h.assertTrue(LandWildlife.forage(world, Species.MAMMOTH, browse), "Cold browser cannot use snow cover");
        h.assertFalse(LandWildlife.forage(world, Species.PARASAUR, browse), "Warm species browsed snow");
        h.succeed();
    }
    private CollectionGameTests() {}
}
