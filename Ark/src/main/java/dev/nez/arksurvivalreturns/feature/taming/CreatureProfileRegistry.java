package dev.nez.arksurvivalreturns.feature.taming;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The complete registry-id-to-profile mapping. Every registered creature has an explicit entry; the
 * static initializer fails fast if one is missing, so a new species cannot fall through to a generic
 * profile.
 *
 * <p>Food choices use items this mod and vanilla already provide: the mod berries, leaves, carrot,
 * apple, wheat, raw land meat and raw fish. No new food system was added. Rotten flesh, cooked meat,
 * pufferfish and narcoberry are deliberately outside every diet; narcoberry is a sedative only.
 */
public final class CreatureProfileRegistry {
    private static final List<TagKey<Item>> SMALL_PLANTS = List.of(TamingTags.SMALL_PLANT_FOOD);
    private static final List<TagKey<Item>> PLANTS = List.of(TamingTags.PLANT_FOOD);
    private static final List<TagKey<Item>> MEAT = List.of(TamingTags.RAW_MEAT);
    private static final List<TagKey<Item>> FISH = List.of(TamingTags.FISH);
    private static final List<TagKey<Item>> MEAT_AND_FISH = List.of(TamingTags.RAW_MEAT, TamingTags.FISH);
    private static final List<TagKey<Item>> PLANTS_AND_MEAT = List.of(TamingTags.PLANT_FOOD, TamingTags.RAW_MEAT);

    private static final Map<Species, CreatureTamingProfile> PROFILES = new EnumMap<>(Species.class);

    static {
        // ------------------------------------------------- hunger-based aerial feeding (fish)
        add(Species.PTERANODON, TamingMethod.AERIAL, 180, FISH, () -> Items.COD, 1.0);
        add(Species.ARGENTAVIS, TamingMethod.AERIAL, 180, FISH, () -> Items.SALMON, 0.9);
        add(Species.ARCHAEOPTERYX, TamingMethod.AERIAL, 180, FISH, () -> Items.COD, 1.4);
        add(Species.QUETZAL, TamingMethod.AERIAL, 180, FISH, () -> Items.SALMON, 0.85);
        add(Species.DRAGON, TamingMethod.AERIAL, 180, FISH, () -> Items.SALMON, 0.7);

        // ------------------------------------------- passive: ordinary nonpredatory animals (60 s)
        add(Species.UNICORN, TamingMethod.PASSIVE, 60, PLANTS, () -> Items.APPLE, 1.0);
        add(Species.MEGALOCERUS, TamingMethod.PASSIVE, 60, PLANTS, () -> Items.WHEAT, 1.0);

        // --------------------------------------------------------- passive: small herbivores (90 s)
        add(Species.LYSTROSAURUS, TamingMethod.PASSIVE, 90, SMALL_PLANTS, berry("amarberry"), 1.25);
        add(Species.PEGOMASTAX, TamingMethod.PASSIVE, 90, SMALL_PLANTS, berry("tintoberry"), 1.25);

        // --------------------------------------------- passive: calm medium herbivores (150 s each)
        add(Species.PARASAUR, TamingMethod.PASSIVE, 150, PLANTS, berry("azulberry"), 1.0);
        add(Species.MAMMOTH, TamingMethod.PASSIVE, 150, PLANTS, () -> Items.WHEAT, 0.9);

        // ------------------------------------------------------- knockout: large herbivores (240 s)
        add(Species.TRICERATOPS, TamingMethod.KNOCKOUT, 240, PLANTS, () -> Items.CARROT, 0.95);
        add(Species.ANKYLOSAURUS, TamingMethod.KNOCKOUT, 240, PLANTS, () -> Items.CARROT, 0.85);
        add(Species.THERIZINOSAURUS, TamingMethod.KNOCKOUT, 240, PLANTS, () -> Items.APPLE, 0.9);
        add(Species.BRONTOSAURUS, TamingMethod.KNOCKOUT, 240, PLANTS, () -> Items.WHEAT, 0.7);
        add(Species.PARACERATHERIUM, TamingMethod.KNOCKOUT, 240, PLANTS, () -> Items.APPLE, 0.8);
        add(Species.TITANOSAUR, TamingMethod.KNOCKOUT, 240, PLANTS, () -> Items.WHEAT, 0.5);

        // -------------------------------- knockout: small terrestrial carnivores (120 s each)
        add(Species.DILOPHOSAUR, TamingMethod.KNOCKOUT, 120, MEAT, () -> Items.RABBIT, 1.0);
        add(Species.DIREWOLF, TamingMethod.KNOCKOUT, 120, MEAT, () -> Items.MUTTON, 1.0);
        add(Species.SABERTOOTH, TamingMethod.KNOCKOUT, 120, MEAT, () -> Items.BEEF, 1.0);
        add(Species.RAVAGER, TamingMethod.KNOCKOUT, 120, MEAT, () -> Items.PORKCHOP, 1.0);
        add(Species.TERRORBIRD, TamingMethod.KNOCKOUT, 120, MEAT, () -> Items.CHICKEN, 1.0);

        // ------------------------------ knockout: medium terrestrial carnivores (210 s each)
        add(Species.VELOCIRAPTOR, TamingMethod.KNOCKOUT, 210, MEAT, () -> Items.MUTTON, 1.0);
        add(Species.ALLOSAURUS, TamingMethod.KNOCKOUT, 210, MEAT, () -> Items.BEEF, 1.0);
        add(Species.CARNOTAURUS, TamingMethod.KNOCKOUT, 210, MEAT, () -> Items.PORKCHOP, 1.0);
        add(Species.CERATOSAURUS, TamingMethod.KNOCKOUT, 210, MEAT, () -> Items.CHICKEN, 1.0);
        add(Species.TITANOBOA, TamingMethod.KNOCKOUT, 210, MEAT, () -> Items.CHICKEN, 1.0);
        add(Species.MEGAPITHECUS, TamingMethod.KNOCKOUT, 210, PLANTS_AND_MEAT, () -> Items.APPLE, 0.85);

        // ------------------------------- knockout: shoreline predators, meat and fish (210 s each)
        add(Species.KAPROSUCHUS, TamingMethod.KNOCKOUT, 210, MEAT_AND_FISH, () -> Items.RABBIT, 0.85);
        add(Species.SARCO, TamingMethod.KNOCKOUT, 210, MEAT_AND_FISH, () -> Items.PORKCHOP, 1.0);
        add(Species.DEINOSUCHUS, TamingMethod.KNOCKOUT, 210, MEAT_AND_FISH, () -> Items.MUTTON, 0.85);

        // -------------------------------------------- knockout: large terrestrial carnivores (360 s)
        add(Species.TYRANNOSAURUS, TamingMethod.KNOCKOUT, 360, MEAT, () -> Items.BEEF, 0.8);
        add(Species.GIGANOTOSAURUS, TamingMethod.KNOCKOUT, 360, MEAT, () -> Items.BEEF, 0.7);
        add(Species.ACROCANTHOSAURUS, TamingMethod.KNOCKOUT, 360, MEAT, () -> Items.BEEF, 0.75);
        add(Species.SPINOSAURUS, TamingMethod.KNOCKOUT, 360, MEAT_AND_FISH, () -> Items.SALMON, 0.8);

        // ---------------------------- explicit per-species aquatic decisions; never the aerial rule
        add(Species.CNIDARIA, TamingMethod.KNOCKOUT, 90, FISH, () -> Items.SALMON, 1.5);
        add(Species.PLESIOSAUR, TamingMethod.KNOCKOUT, 210, MEAT_AND_FISH, () -> Items.SALMON, 1.0);
        add(Species.MEGALODON, TamingMethod.KNOCKOUT, 240, MEAT_AND_FISH, () -> Items.COD, 0.9);
        add(Species.LIOPLEURODON, TamingMethod.KNOCKOUT, 240, MEAT_AND_FISH, () -> Items.SALMON, 0.9);
        add(Species.MOSASAURUS, TamingMethod.KNOCKOUT, 360, MEAT_AND_FISH, () -> Items.SALMON, 0.75);
        add(Species.TUSOTEUTHIS, TamingMethod.KNOCKOUT, 360, MEAT_AND_FISH, () -> Items.COD, 0.75);

        for (Species species : Species.values())
            if (!PROFILES.containsKey(species))
                throw new IllegalStateException("Missing taming profile for registered creature: " + species.id);
    }

    private static Supplier<Item> berry(String id) {
        // Resolved lazily: the item registry is not populated while this class initializes.
        return () -> ModContent.BERRIES.get(id).get();
    }

    private static void add(Species species, TamingMethod method, int seconds,
            List<TagKey<Item>> accepted, Supplier<Item> favourite, double resistance) {
        PROFILES.put(species, new CreatureTamingProfile(species, CreatureSize.of(species.height), method,
                accepted, List.of(favourite), seconds, resistance, ""));
    }

    /** The profile of a registered creature. Never null: the static initializer enforces completeness. */
    public static CreatureTamingProfile of(Species species) {
        return PROFILES.get(species);
    }

    public static CreatureRideProfile ride(Species species) {
        return CreatureSeats.of(species);
    }

    /** Static audit used by the debug command and the tests. */
    public static List<String> validate() {
        var problems = new ArrayList<String>();
        for (Species species : Species.values()) {
            var profile = PROFILES.get(species);
            if (profile == null) {
                problems.add(species.id + ": missing taming profile");
                continue;
            }
            if (profile.accepted().isEmpty()) problems.add(species.id + ": no accepted food tag");
            if (profile.preferred().isEmpty()) problems.add(species.id + ": no preferred food");
            if (profile.targetSeconds() <= 0) problems.add(species.id + ": invalid target duration");
            if (profile.method() == TamingMethod.AERIAL && species.realm() != Species.Realm.AIR)
                problems.add(species.id + ": aerial feeding outside the flying realm");
            if (species.realm() == Species.Realm.AIR && profile.method() != TamingMethod.AERIAL)
                problems.add(species.id + ": flying creature must use aerial feeding");
            if (ride(species) == null) problems.add(species.id + ": missing ride profile");
            if (CreatureTorporClips.of(species) == null) problems.add(species.id + ": missing torpor clip table");
            if (profile.sedativeResistance() <= 0) problems.add(species.id + ": invalid sedative resistance");
        }
        return problems;
    }

    /**
     * Resolves the diets against the live item registry. Runs on the server, where the favourite has to be
     * one of the accepted foods and the sedative must not be edible.
     */
    public static List<String> validateItems() {
        var problems = new ArrayList<String>();
        for (Species species : Species.values()) {
            var profile = PROFILES.get(species);
            if (profile == null) continue;
            for (var favourite : profile.preferred()) {
                var stack = new ItemStack(favourite.get());
                if (!profile.accepts(stack))
                    problems.add(species.id + ": preferred " + stack.getItem() + " is outside the accepted diet");
            }
            if (profile.accepts(new ItemStack(ModContent.BERRIES.get("narcoberry").get())))
                problems.add(species.id + ": narcoberry is a sedative and must not be taming food");
        }
        return problems;
    }

    private CreatureProfileRegistry() {}
}
