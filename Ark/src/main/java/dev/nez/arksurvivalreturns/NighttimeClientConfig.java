package dev.nez.arksurvivalreturns;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client preferences only; declaring the spec is safe on a dedicated server. */
public final class NighttimeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue EYE_GLOW;
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;
    public static final ModConfigSpec.BooleanValue ARK_UI;
    public static final ModConfigSpec.BooleanValue MIRROR_TURN_CLIPS;
    static {
        var builder = new ModConfigSpec.Builder();
        EYE_GLOW = builder.comment("Nighttime carnivore eye brightness. Zero disables the visual; does not change server behavior.")
                .defineInRange("nightEyeGlowStrength", 1.0, 0.0, 1.0);
        DEBUG_LOG = builder.comment("Log the first night-eye draw per species to the client log, for diagnosing missing glow.")
                .define("nightEyeDebugLog", false);
        ARK_UI = builder.comment("Force the built-in Ark UI resource pack (carved-stone inventory, chests, buttons, hotbar, tooltips). "
                        + "Set false and restart to use vanilla or your own GUI pack.")
                .define("arkUiPack", true);
        MIRROR_TURN_CLIPS = builder.comment("Swap the left and right turn and banking clips if creatures step the wrong way "
                        + "while turning in place.")
                .define("mirrorTurnClips", false);
        SPEC = builder.build();
    }
    private NighttimeClientConfig() {}
}
