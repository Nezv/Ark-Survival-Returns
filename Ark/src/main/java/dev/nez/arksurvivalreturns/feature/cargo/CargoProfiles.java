package dev.nez.arksurvivalreturns.feature.cargo;

import java.util.EnumMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.world.item.ItemStack;

/**
 * Species cargo roles: which harness opens the species capacity and how much it hauls.
 *
 * <p>Capacity is a movement budget, not an inventory limit. Without the required harness a creature
 * uses the bare allowance, and any creature can still be overloaded manually; the harness only
 * raises the denominator the penalty curve and the automation ceiling are measured against.
 */
public final class CargoProfiles {
    public enum Harness { NONE, PACK, REINFORCED }

    public record Profile(Harness harness, double capacity) {}

    private static final Profile DEFAULT = new Profile(Harness.PACK, 150.0);
    private static final Map<Species, Profile> PROFILES = new EnumMap<>(Species.class);

    static {
        // Light hands: scouts and small companions.
        put(Harness.PACK, 100, Species.PTERANODON, Species.ARCHAEOPTERYX, Species.PEGOMASTAX,
                Species.DILOPHOSAUR, Species.CNIDARIA);
        // Basic pack animals.
        put(Harness.PACK, 150, Species.VELOCIRAPTOR, Species.LYSTROSAURUS, Species.TERRORBIRD,
                Species.TITANOBOA, Species.MEGAPITHECUS);
        put(Harness.PACK, 200, Species.CERATOSAURUS, Species.ALLOSAURUS, Species.CARNOTAURUS,
                Species.KAPROSUCHUS, Species.DIREWOLF, Species.SABERTOOTH, Species.MEGALOCERUS,
                Species.UNICORN, Species.RAVAGER);
        put(Harness.PACK, 250, Species.PARASAUR, Species.SARCO);
        put(Harness.PACK, 300, Species.THERIZINOSAURUS);
        put(Harness.PACK, 350, Species.TRICERATOPS);
        put(Harness.PACK, 400, Species.ANKYLOSAURUS, Species.ARGENTAVIS);
        // Heavy work animals; the reinforced harness is the primitive logistics project.
        put(Harness.REINFORCED, 300, Species.PLESIOSAUR, Species.MEGALODON, Species.TUSOTEUTHIS,
                Species.LIOPLEURODON);
        put(Harness.REINFORCED, 350, Species.TYRANNOSAURUS, Species.GIGANOTOSAURUS,
                Species.ACROCANTHOSAURUS, Species.DEINOSUCHUS);
        put(Harness.REINFORCED, 400, Species.SPINOSAURUS, Species.MOSASAURUS);
        put(Harness.REINFORCED, 500, Species.DRAGON);
        put(Harness.REINFORCED, 700, Species.QUETZAL);
        put(Harness.REINFORCED, 800, Species.MAMMOTH);
        put(Harness.REINFORCED, 900, Species.BRONTOSAURUS, Species.PARACERATHERIUM);
        put(Harness.REINFORCED, 1400, Species.TITANOSAUR);
    }

    public static Profile of(Species species) {
        return PROFILES.getOrDefault(species, DEFAULT);
    }

    /** The harness item tier, or NONE for anything that is not a rig. */
    public static Harness tier(ItemStack stack) {
        if (stack.is(ModContent.REINFORCED_HARNESS.get())) return Harness.REINFORCED;
        if (stack.is(ModContent.PACK_HARNESS.get())) return Harness.PACK;
        return Harness.NONE;
    }

    /** Effective capacity: the configured species value once the required harness is fitted, else bare. */
    public static double capacity(Species species, Harness equipped) {
        Profile profile = of(species);
        if (equipped.ordinal() >= profile.harness().ordinal() && equipped != Harness.NONE) {
            return Config.CARGO_CAPACITY.get(species).get();
        }
        return Config.MASS_BARE_CAPACITY.get();
    }

    private static void put(Harness harness, double capacity, Species... species) {
        for (Species value : species) PROFILES.put(value, new Profile(harness, capacity));
    }

    private CargoProfiles() {}
}
