package dev.nez.arksurvivalreturns.feature.taming;

import java.util.EnumMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.feature.creature.Species;

/**
 * Imported torpor clip names and their authored lengths, read from the runtime animation files by
 * {@code tools/build_taming_manifests.py}. Generated file: do not edit by hand. A null clip means
 * the source library has no such clip, and the animation bridge falls back to the sleep pose.
 */
public final class CreatureTorporClips {
    private static final Map<Species, Clips> CLIPS = build();

    private static Map<Species, Clips> build() {
        var map = new EnumMap<Species, Clips>(Species.class);
        map.put(Species.PTERANODON, new Clips("Ptero-Torpid-In", 33, "Ptero-Torpid-Idle", "Ptero-Torpid-Eat", 133, "Ptero-Torpid-Out-Tamed", 60, "Ptero-Torpid-Out-Wild", 33));
        map.put(Species.VELOCIRAPTOR, new Clips("Raptor-Torpid-In", 40, "Raptor-Torpid", "Raptor-Torpid-Eat", 67, "Raptor-Torpid-Out-Tamed", 67, "Raptor-Torpid-Out-Wild", 40));
        map.put(Species.ARGENTAVIS, new Clips("Argentavis-Torpid-In", 33, "Argentavis-Torpid-Idle", "Argentavis-Torpid-Eat", 67, "Argentavis-Torpid-Out-Tamed", 53, "Argentavis-Torpid-Out-Wild", 33));
        map.put(Species.TRICERATOPS, new Clips("Trike-Torpid-In", 53, "Trike-Torpid", "Trike-Torpid-Eat", 67, "Trike-Torpid-Out-Tamed", 67, "Trike-Torpid-Out-Wild", 40));
        map.put(Species.THERIZINOSAURUS, new Clips("Therizinosaurus-Torpid-In", 40, "Therizinosaurus-Torpid-Idle", "Therizinosaurus-Torpid-Eat", 67, "Therizinosaurus-Torpid-Out-Tamed", 73, "Therizinosaurus-Torpid-Out-Wild", 40));
        map.put(Species.BRONTOSAURUS, new Clips("Sauropod-Torpid-In", 73, "Sauropod-Torpid", "Sauropod-Torpid-Eat", 67, "Sauropod-Torpid-Out-Tamed", 100, "Sauropod-Torpid-Out-Wild", 53));
        map.put(Species.TYRANNOSAURUS, new Clips("Rex-Torpid-In", 40, "Rex-Torpid", "Rex-Torpid-Eat", 67, "Rex-Torpid-Out-Tamed", 67, "Rex-Torpid-Out-Wild", 40));
        map.put(Species.GIGANOTOSAURUS, new Clips("Giganotosaurus-Torpid-In", 40, "Giganotosaurus-Torpid-Idle", "Giganotosaurus-Torpid-Eat", 67, "Giganotosaurus-Torpid-Out-Tamed", 73, "Giganotosaurus-Torpid-Out-Wild", 40));
        map.put(Species.TITANOSAUR, new Clips("Titanosaur-Torpid-In", 67, "Titanosaur-Torpid-Idle", "Titanosaur-Torpid-Eat", 67, "Titanosaur-Torpid-Out-Tamed", 73, "Titanosaur-Torpid-Out-Wild", 27));
        map.put(Species.SPINOSAURUS, new Clips("Spino-Torpid-In", 40, "Spino-Torpid-Idle", "Spino-Torpid-Eat", 67, "Spino-Torpid-Out-Tamed", 73, "Spino-Torpid-Out-Wild", 40));
        map.put(Species.PARASAUR, new Clips("Para-Torpid-In", 40, "Para-Torpid", "Para-Torpid-Eat", 67, "Para-Torpid-Out-Tamed", 60, "Para-Torpid-Out-Wild", 40));
        map.put(Species.CERATOSAURUS, new Clips(null, 0, null, null, 0, null, 0, null, 0));
        map.put(Species.DILOPHOSAUR, new Clips("Dilo-Torpid-In", 40, "Dilo-Torpid", "Dilo-Torpid-Eat", 67, "Dilo-Torpid-Out-Tamed", 67, "Dilo-Torpid-Out-Wild", 40));
        map.put(Species.ACROCANTHOSAURUS, new Clips("Acro_Torp_In", 167, "Acro_Torp_Loop", "Acro_Torp_Eat", 83, "Acro_Torp_Out", 118, null, 0));
        map.put(Species.ALLOSAURUS, new Clips("Allosaurus-Torpid-In", 39, "Allosaurus-Torpid-Idle", "Allosaurus-Torpid-Eat", 66, "Allosaurus-Torpid-Out-Tamed", 66, "Allosaurus-Torpid-Out-Wild", 39));
        map.put(Species.ANKYLOSAURUS, new Clips("Ankylo-Torpid-In", 40, "Ankylo-Torpid", "Ankylo-Torpid-Eat", 67, "Ankylo-Torpid-Out-Tamed", 50, "Ankylo-Torpid-Out-Wild", 40));
        map.put(Species.CARNOTAURUS, new Clips("Carno-Torpid-In", 40, "Carno-Torpid-Idle", "Carno-Torpid-Eat", 67, "Carno-Torpid-Out-Tamed", 73, "Carno-Torpid-Out-Wild", 40));
        map.put(Species.PEGOMASTAX, new Clips("Pegomastax-Torpid-In", 40, "Pegomastax-Torpid-Idle", "Pegomastax-Torpid-Eat", 67, "Pegomastax-Torpid-Out-Tamed", 73, "Pegomastax-Torpid-Out-Wild", 40));
        map.put(Species.LYSTROSAURUS, new Clips("Lystrosaurus-Torpid-In", 40, "Lystrosaurus-Torpid-Idle", "Lystrosaurus-Torpid-Eat", 67, "Lystrosaurus-Torpid-Out-Tamed", 40, "Lystrosaurus-Torpid-Out-Wild", 40));
        map.put(Species.CNIDARIA, new Clips(null, 0, null, null, 0, null, 0, null, 0));
        map.put(Species.PLESIOSAUR, new Clips("Plesiosaur-Torpid-In", 20, "Plesiosaur-Torpid-Idle", "Plesiosaur-Torpid-Eat", 133, "Plesiosaur-Torpid-Out-Tamed", 67, "Plesiosaur-Torpid-Out-Wild", 40));
        map.put(Species.MEGALODON, new Clips("Megalodon-Torpid-In", 20, "Megalodon-Torpid", "Megalodon-Torpid-Eat", 67, "Megalodon-Torpid-Out-Tamed", 40, "Megalodon-Torpid-Out-Wild", 40));
        map.put(Species.LIOPLEURODON, new Clips("Liopleurodon-Torpid-In", 20, "Liopleurodon-Torpid-Idle", "Liopleurodon-Torpid-Eat", 133, "Liopleurodon-Torpid-Out-Tamed", 67, "Liopleurodon-Torpid-Out-Wild", 40));
        map.put(Species.MOSASAURUS, new Clips("Mosasaurus-Torpid-In", 20, "Mosasaurus-Torpid-Idle", "Mosasaurus-Torpid-Eat", 67, "Mosasaurus-Torpid-Out-Tamed", 40, "Mosasaurus-Torpid-Out-Wild", 40));
        map.put(Species.TUSOTEUTHIS, new Clips("Tusoteuthis-Torpid-In", 20, "Tusoteuthis-Torpid-Idle", null, 0, "Tusoteuthis-Torpid-Out-Tamed", 33, "Tusoteuthis-Torpid-Out-Wild", 20));
        map.put(Species.KAPROSUCHUS, new Clips("Kaprosuchus-Torpid-In", 20, "Kaprosuchus-Torpid-Idle", "Kaprosuchus-Torpid-Eat", 67, "Kaprosuchus-Torpid-Out-Tamed", 40, "Kaprosuchus-Torpid-Out-Wild", 20));
        map.put(Species.SARCO, new Clips("Sarco-Ground-Torpid-In", 40, "Sarco-Ground-Torpid-Idle", "Sarco-Ground-Torpid-Eat", 133, "Sarco-Ground-Torpid-Out-Tamed", 40, "Sarco-Ground-Torpid-Out-Wild", 40));
        map.put(Species.DEINOSUCHUS, new Clips("Deinosuchus_Torp_In", 83, null, null, 0, null, 0, null, 0));
        map.put(Species.TITANOBOA, new Clips(null, 0, null, null, 0, null, 0, null, 0));
        map.put(Species.MEGALOCERUS, new Clips("Stag-Torpid-In", 40, "Stag-Torpid-Idle", "Stag-Torpid-Eat", 67, "Stag-Torpid-Out-Tamed", 60, "Stag-Torpid-Out-Wild", 40));
        map.put(Species.UNICORN, new Clips("Equus-Torpid-In", 50, "Equus-Torpid-Idle", "Equus-Torpid-Eat", 83, "Equus-Torpid-Out-Tamed", 75, "Equus-Torpid-Out-Wild", 50));
        map.put(Species.MAMMOTH, new Clips("Mammoth-Torpid-In", 41, "Mammoth-Torpid", "Mammoth-Torpid-Eat", 67, "Mammoth-Torpid-Out-Tamed", 54, "Mammoth-Torpid-Out-Wild", 41));
        map.put(Species.DIREWOLF, new Clips("Direwolf-Torpid-In", 57, "Direwolf-Torpid-Idle", "Direwolf-Torpid-Eat", 67, "Direwolf-Torpid-Out-Tamed", 60, "Direwolf-Torpid-Out-Wild", 40));
        map.put(Species.SABERTOOTH, new Clips("Saber-Torpid-In", 35, "Saber-Torpid-Idle", "Saber-Torpid-Eat", 67, "Saber-Torpid-Out-Tamed", 53, "Saber-Torpid-Out-Wild", 40));
        map.put(Species.MEGAPITHECUS, new Clips(null, 0, null, null, 0, null, 0, null, 0));
        map.put(Species.PARACERATHERIUM, new Clips("Paraceratherium-Torpid-In", 40, "Paraceratherium-Torpid-Idle", "Paraceratherium-Torpid-Eat", 67, "Paraceratherium-Torpid-Out-Tamed", 53, "Paraceratherium-Torpid-Out-Wild", 40));
        map.put(Species.TERRORBIRD, new Clips("TerrorBird-Torpid-In", 40, "TerrorBird-Torpid-Idle", "TerrorBird-Torpid-Eat", 133, "TerrorBird-Torpid-Out-Tamed", 53, "TerrorBird-Torpid-Out-Wild", 40));
        map.put(Species.RAVAGER, new Clips("CaveWolf-Torpid-In", 47, "CaveWolf-Torpid-Idle", "CaveWolf-Torpid-Eat", 67, "CaveWolf-Torpid-Out-Tamed", 67, "CaveWolf-Torpid-Out-Wild", 40));
        map.put(Species.ARCHAEOPTERYX, new Clips("Archaeopteryx-Torpid-In", 66, "Archaeopteryx-Torpid-Idle", "Archaeopteryx-Torpid-Eat", 53, "Archaeopteryx-Torpid-Out-Tamed", 47, "Archaeopteryx-Torpid-Out-Wild", 28));
        map.put(Species.QUETZAL, new Clips("Quetzalcoatlus-Torpid-In", 33, "Quetzalcoatlus-Torpid-Idle", "Quetzalcoatlus-Torpid-Eat", 133, "Quetzalcoatlus-Torpid-Out-Tamed", 60, "Quetzalcoatlus-Torpid-Out-Wild", 33));
        map.put(Species.DRAGON, new Clips(null, 0, null, null, 0, null, 0, null, 0));
        return Map.copyOf(map);
    }

    public static Clips of(Species species) { return CLIPS.get(species); }

    /**
     * @param in       collapse clip, drawn while the entity is falling into unconsciousness
     * @param inTicks  authored length of {@code in}, in ticks
     * @param loop     unconscious idle, held while torpor is above the wake threshold
     * @param eat      feeding clip played for a single meal
     * @param eatTicks authored length of {@code eat}, in ticks
     * @param outTamed wake clip after a successful tame
     * @param outWild  wake clip after a failed attempt
     */
    public record Clips(String in, int inTicks, String loop, String eat, int eatTicks,
                        String outTamed, int outTamedTicks, String outWild, int outWildTicks) {}
    private CreatureTorporClips() {}
}
