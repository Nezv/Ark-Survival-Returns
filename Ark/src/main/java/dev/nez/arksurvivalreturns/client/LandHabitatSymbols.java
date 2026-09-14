package dev.nez.arksurvivalreturns.client;

import java.util.List;

/** Pixels correspond exactly to docs/assets/land-{herbivore,carnivore}-habitat.svg. */
public final class LandHabitatSymbols {
    public static final List<String> HERBIVORE=List.of("000111110","001111110","011112110","011121110","011211100","012111000","002110000","020000000","200000000");
    public static final List<String> CARNIVORE=List.of("001111110","011222210","012222210","001222110","001222100","001221000","001210000","001100000","001000000");
    private LandHabitatSymbols() {}
}
