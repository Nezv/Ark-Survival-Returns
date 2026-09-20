package dev.nez.arksurvivalreturns.feature.creature;

import java.util.EnumMap;
import java.util.Map;

/**
 * Imported melee clip names and their authored lengths, read from the runtime animation files by
 * {@code tools/build_taming_manifests.py}. Generated file: do not edit by hand. The length drives
 * the wind-up before a strike lands and the attack recovery that gates the next one.
 */
public final class CreatureAttackClips {
    private static final Map<Species, Clips> CLIPS = build();

    private static Map<Species, Clips> build() {
        var map = new EnumMap<Species, Clips>(Species.class);
        map.put(Species.PTERANODON, new Clips("Ptero-Ground-Attack", 21));
        map.put(Species.VELOCIRAPTOR, new Clips("Raptor-Attack", 13));
        map.put(Species.ARGENTAVIS, new Clips("Argentavis-Ground-Attack", 27));
        map.put(Species.TRICERATOPS, new Clips("Trike-Attack-Horn", 20));
        map.put(Species.THERIZINOSAURUS, new Clips("Therizinosaurus-Attack-Claw", 20));
        map.put(Species.BRONTOSAURUS, new Clips("Sauropod-Attack-FootStomp", 53));
        map.put(Species.TYRANNOSAURUS, new Clips("Rex-Bite2", 28));
        map.put(Species.GIGANOTOSAURUS, new Clips("Giganotosaurus-Attack-Bite", 20));
        map.put(Species.TITANOSAUR, new Clips("Titanosaur-Attack-Footstomp", 53));
        map.put(Species.SPINOSAURUS, new Clips("Spino-Attack-Bite", 20));
        map.put(Species.PARASAUR, new Clips("Para-Attack-Bite", 20));
        map.put(Species.CERATOSAURUS, new Clips("Cerato_Attack_Bite1", 23));
        map.put(Species.DILOPHOSAUR, new Clips("Dilo-Attack-Bite", 20));
        map.put(Species.ACROCANTHOSAURUS, new Clips("Acro_Attack_Bite", 71));
        map.put(Species.ALLOSAURUS, new Clips("Allosaurus-Attack-Bite", 20));
        map.put(Species.ANKYLOSAURUS, new Clips("Ankylo-Attack-Tail-Sweep", 33));
        map.put(Species.CARNOTAURUS, new Clips("Carno-Attack-Bite", 20));
        map.put(Species.PEGOMASTAX, new Clips("Pegomastax-Attack-Bite", 17));
        map.put(Species.LYSTROSAURUS, new Clips("Lystrosaurus-Attack-Bite", 20));
        map.put(Species.CNIDARIA, new Clips("Cnidaria-Shock", 20));
        map.put(Species.PLESIOSAUR, new Clips("Plesiosaur-Attack-Bite", 20));
        map.put(Species.MEGALODON, new Clips("Megalodon-Attack-Bite", 27));
        map.put(Species.LIOPLEURODON, new Clips("Liopleurodon-Attack-Chomp", 27));
        map.put(Species.MOSASAURUS, new Clips("Mosasaurus-Attack-Bite", 27));
        map.put(Species.TUSOTEUTHIS, new Clips("Tusoteuthis-Attack-Bite", 57));
        map.put(Species.KAPROSUCHUS, new Clips("Kaprosuchus-Attack-Bite", 20));
        map.put(Species.SARCO, new Clips("Sarco-Ground-Attack-Bite", 20));
        map.put(Species.DEINOSUCHUS, new Clips("Deinosuchus_Attack_Bite", 30));
        map.put(Species.TITANOBOA, new Clips("BoaFrill-Attack-Lunge", 33));
        map.put(Species.MEGALOCERUS, new Clips("Stag-Attack-Gore", 20));
        map.put(Species.UNICORN, new Clips("Equus-Attack-Buck", 25));
        map.put(Species.MAMMOTH, new Clips("Mammoth-Attack-Tusk-Jab", 17));
        map.put(Species.DIREWOLF, new Clips("Direwolf-Attack-Bite", 20));
        map.put(Species.SABERTOOTH, new Clips("Saber-Attack-Bite", 20));
        map.put(Species.MEGAPITHECUS, new Clips("Gorilla-Attack-Pound", 27));
        map.put(Species.PARACERATHERIUM, new Clips("Paraceratherium-Attack-Footstomp", 30));
        map.put(Species.TERRORBIRD, new Clips("TerrorBird-Attack-Bite", 20));
        map.put(Species.RAVAGER, new Clips("CaveWolf-Attack-Bite", 23));
        map.put(Species.ARCHAEOPTERYX, new Clips("Archaeopteryx-Attack-Bite", 13));
        map.put(Species.QUETZAL, new Clips("Quetzalcoatlus-Fly-Attack-Bite", 27));
        map.put(Species.DRAGON, new Clips("Dragon-Ground-Attack-Bite", 27));
        return Map.copyOf(map);
    }

    public static Clips of(Species species) { return CLIPS.get(species); }

    /** @param attack authored melee clip; @param attackTicks its length in ticks */
    public record Clips(String attack, int attackTicks) {}
    private CreatureAttackClips() {}
}
