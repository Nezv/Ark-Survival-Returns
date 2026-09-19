package dev.nez.arksurvivalreturns.feature.taming;

import java.util.EnumMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.feature.creature.Species;

/**
 * Seat transforms measured from each runtime GeckoLib model by
 * {@code tools/build_taming_manifests.py}. Generated file: do not edit by hand; rerun the tool
 * after any model change. Coordinates are entity-local blocks, taken from the real bone pivot of
 * the chosen back bone with the same 180 degree Y correction the renderer applies.
 */
public final class CreatureSeats {
    private static final Map<Species, CreatureRideProfile> PROFILES = build();

    private static Map<Species, CreatureRideProfile> build() {
        var map = new EnumMap<Species, CreatureRideProfile>(Species.class);
        map.put(Species.PTERANODON, new CreatureRideProfile(CreatureRideProfile.MovementMode.FLIGHT,
                -0.000, 0.482, -0.076, 0.0,
                0.000, 0.000, -1.700, 1.700, 0.000, 0.000,
                "c_back4", "STANDING"));
        map.put(Species.VELOCIRAPTOR, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.000, 2.337, -0.375, 0.0,
                0.000, 0.000, -1.650, 1.650, 0.000, 0.000,
                "Cnt_Spine_003_JNT_SKL", "STANDING"));
        map.put(Species.ARGENTAVIS, new CreatureRideProfile(CreatureRideProfile.MovementMode.FLIGHT,
                -0.000, 2.176, -0.529, 0.0,
                0.000, 0.000, -2.000, 2.000, 0.000, 0.000,
                "c_Spine_Top", "STANDING"));
        map.put(Species.TRICERATOPS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                -0.000, 2.692, -0.639, 0.0,
                0.000, 0.000, -3.300, 3.300, 0.000, 0.000,
                "c_back4", "STANDING"));
        map.put(Species.THERIZINOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                -0.000, 4.469, -0.308, 0.0,
                0.000, 0.000, -2.600, 2.600, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.BRONTOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                -0.062, 7.218, -3.000, 0.0,
                0.000, 0.000, -4.800, 4.800, 0.000, 0.000,
                "c_neck1", "STANDING"));
        map.put(Species.TYRANNOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.008, 11.571, -0.989, 0.0,
                0.000, 0.000, -5.000, 5.000, 0.000, 0.000,
                "Cnt_Spine_002_JNT_SKL", "STANDING"));
        map.put(Species.GIGANOTOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                -0.000, 14.750, -1.565, 0.0,
                0.000, 0.000, -6.050, 6.050, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.TITANOSAUR, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                -0.001, 33.565, -14.570, 0.0,
                0.000, 0.000, -15.800, 15.800, 0.000, 0.000,
                "c_neck1", "STANDING"));
        map.put(Species.SPINOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                -0.000, 10.768, -1.964, 0.0,
                0.000, 0.000, -5.000, 5.000, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.PARASAUR, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.000, 2.811, -1.308, 0.0,
                0.000, 0.000, -2.100, 2.100, 0.000, 0.000,
                "c_back4", "STANDING"));
        map.put(Species.CERATOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                2.586, 0.534, 4.187, 90.0,
                0.000, 0.000, -2.600, 2.600, 0.000, 0.000,
                "Chest_M", "STANDING"));
        map.put(Species.DILOPHOSAUR, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.000, 1.628, -0.287, 0.0,
                0.000, 0.000, -1.450, 1.450, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.ACROCANTHOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                4.202, 1.985, 9.524, 90.0,
                0.000, 0.000, -5.600, 5.600, 0.000, 0.000,
                "Spine2_M", "STANDING"));
        map.put(Species.ALLOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                -0.000, 5.566, -1.150, 0.0,
                0.000, 0.000, -2.600, 2.600, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.ANKYLOSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.000, 3.800, 0.139, 0.0,
                0.000, 0.000, -2.900, 2.900, 0.000, 0.000,
                "c_back2", "STANDING"));
        map.put(Species.CARNOTAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.002, 4.582, -0.937, 0.0,
                0.000, 0.000, -2.600, 2.600, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.PEGOMASTAX, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                -0.000, 1.100, -0.466, 0.0,
                0.000, 0.000, -1.300, 1.300, 0.000, 0.000,
                "c_back4", "STANDING"));
        map.put(Species.LYSTROSAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.000, 0.800, 0.035, 0.0,
                0.000, 0.000, -1.400, 1.400, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.CNIDARIA, new CreatureRideProfile(CreatureRideProfile.MovementMode.SWIM,
                -0.000, 0.400, 0.069, 90.0,
                0.000, 0.000, -0.975, 0.975, 0.000, 0.000,
                "bodyTop7", "STANDING"));
        map.put(Species.PLESIOSAUR, new CreatureRideProfile(CreatureRideProfile.MovementMode.SWIM,
                1.086, 0.998, 0.680, 90.0,
                0.000, 0.000, -1.650, 1.650, 0.000, 0.000,
                "c_back2", "STANDING"));
        map.put(Species.MEGALODON, new CreatureRideProfile(CreatureRideProfile.MovementMode.SWIM,
                -0.000, 2.800, 0.098, -90.0,
                0.000, 0.000, -1.600, 1.600, 0.000, 0.000,
                "c_back1", "STANDING"));
        map.put(Species.LIOPLEURODON, new CreatureRideProfile(CreatureRideProfile.MovementMode.SWIM,
                0.467, 1.342, 0.292, 90.0,
                0.000, 0.000, -1.450, 1.450, 0.000, 0.000,
                "c_back2", "STANDING"));
        map.put(Species.MOSASAURUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.SWIM,
                -1.453, 4.800, 0.183, 90.0,
                0.000, 0.000, -2.100, 2.100, 0.000, 0.000,
                "c_back1", "STANDING"));
        map.put(Species.TUSOTEUTHIS, new CreatureRideProfile(CreatureRideProfile.MovementMode.SWIM,
                0.000, 3.300, 0.015, 0.0,
                0.000, 0.000, -1.800, 1.800, 0.000, 0.000,
                "c_main", "STANDING"));
        map.put(Species.KAPROSUCHUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.790, 0.400, 1.491, 90.0,
                0.000, 0.000, -1.275, 1.275, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.SARCO, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                1.765, 0.400, 1.612, 90.0,
                0.000, 0.000, -1.400, 1.400, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.DEINOSUCHUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                2.837, 1.051, 1.163, 90.0,
                0.000, 0.000, -1.700, 1.700, 0.000, 0.000,
                "Back1", "STANDING"));
        map.put(Species.TITANOBOA, new CreatureRideProfile(CreatureRideProfile.MovementMode.SWIM,
                -0.000, 1.000, 0.271, -90.0,
                0.000, 0.000, -1.200, 1.200, 0.000, 0.000,
                "c_back1", "STANDING"));
        map.put(Species.MEGALOCERUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.462, 0.400, 0.879, 90.0,
                0.000, 0.000, -1.300, 1.300, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.UNICORN, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.630, 0.400, 1.319, 90.0,
                0.000, 0.000, -1.275, 1.275, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.MAMMOTH, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                1.195, 0.400, 2.436, 90.0,
                0.000, 0.000, -1.750, 1.750, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.DIREWOLF, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.603, 0.400, -0.554, 90.0,
                0.000, 0.000, -1.200, 1.200, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.SABERTOOTH, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.667, 0.400, 0.967, 90.0,
                0.000, 0.000, -1.200, 1.200, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.MEGAPITHECUS, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.487, 0.400, 1.601, 90.0,
                0.000, 0.000, -1.650, 1.650, 0.000, 0.000,
                "c_back2", "STANDING"));
        map.put(Species.PARACERATHERIUM, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                2.259, 0.400, 3.071, 90.0,
                0.000, 0.000, -1.800, 1.800, 0.000, 0.000,
                "c_back4", "STANDING"));
        map.put(Species.TERRORBIRD, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.327, 0.400, 1.130, 90.0,
                0.000, 0.000, -1.250, 1.250, 0.000, 0.000,
                "c_back3", "STANDING"));
        map.put(Species.RAVAGER, new CreatureRideProfile(CreatureRideProfile.MovementMode.GROUND,
                0.844, 0.400, 0.880, 90.0,
                0.000, 0.000, -1.250, 1.250, 0.000, 0.000,
                "Spine_Top", "STANDING"));
        map.put(Species.ARCHAEOPTERYX, new CreatureRideProfile(CreatureRideProfile.MovementMode.FLIGHT,
                0.118, 0.400, 0.333, 90.0,
                0.000, 0.000, -1.000, 1.000, 0.000, 0.000,
                "Spine_Top", "STANDING"));
        map.put(Species.QUETZAL, new CreatureRideProfile(CreatureRideProfile.MovementMode.FLIGHT,
                2.485, 0.400, 0.724, 90.0,
                0.000, 0.000, -2.000, 2.000, 0.000, 0.000,
                "c_back4", "STANDING"));
        map.put(Species.DRAGON, new CreatureRideProfile(CreatureRideProfile.MovementMode.FLIGHT,
                2.083, 0.400, 2.440, 90.0,
                0.000, 0.000, -1.900, 1.900, 0.000, 0.000,
                "c_neck1", "STANDING"));
        return Map.copyOf(map);
    }

    public static CreatureRideProfile of(Species species) { return PROFILES.get(species); }
    private CreatureSeats() {}
}
