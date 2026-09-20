package dev.nez.arksurvivalreturns;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client preferences only; declaring the spec is safe on a dedicated server. */
public final class NighttimeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue EYE_GLOW;
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;
    static {
        var builder = new ModConfigSpec.Builder();
        EYE_GLOW = builder.comment("Nighttime carnivore eye brightness. Zero disables the visual; does not change server behavior.")
                .defineInRange("nightEyeGlowStrength", 1.0, 0.0, 1.0);
        DEBUG_LOG = builder.comment("Log the first night-eye draw per species to the client log, for diagnosing missing glow.")
                .define("nightEyeDebugLog", false);
        SPEC = builder.build();
    }
    private NighttimeClientConfig() {}
}
