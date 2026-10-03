package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.Config;

/** Narcotics, the ground dose: same consumption, melee and ammunition routes as the baseline, stronger effect. */
public final class NarcoticsItem extends SedativeItem {
    public NarcoticsItem(Properties properties) {
        super(properties);
    }

    @Override public double potency() {
        return Config.NARCOTICS_POTENCY.get();
    }
}
