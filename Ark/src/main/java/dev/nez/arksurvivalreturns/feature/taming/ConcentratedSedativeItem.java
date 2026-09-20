package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.Config;

/** A concentrated dose: same consumption, melee and ammunition routes as the baseline, stronger effect. */
public final class ConcentratedSedativeItem extends SedativeItem {
    public ConcentratedSedativeItem(Properties properties) {
        super(properties);
    }

    @Override public double potency() {
        return Config.CONCENTRATED_SEDATIVE_POTENCY.get();
    }
}
