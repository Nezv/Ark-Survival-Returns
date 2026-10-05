package dev.nez.arksurvivalreturns;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client preferences only; declaring the spec is safe on a dedicated server. */
public final class NighttimeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue EYE_GLOW;
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;
    public static final ModConfigSpec.BooleanValue ARK_UI;
    public static final ModConfigSpec.BooleanValue MIRROR_TURN_CLIPS;
    public static final ModConfigSpec.BooleanValue CREATURE_FAST_DRAWING;
    public static final ModConfigSpec.BooleanValue CREATURE_HIDDEN_FACES;
    static {
        var builder = new ModConfigSpec.Builder();
        EYE_GLOW = builder.comment("Brightness of the red eyes of hunting big carnivores and raptors. Zero disables the visual; does not change server behavior.")
                .defineInRange("nightEyeGlowStrength", 1.0, 0.0, 1.0);
        DEBUG_LOG = builder.comment("Log the first hunting-eye draw per species to the client log, for diagnosing missing glow.")
                .define("nightEyeDebugLog", false);
        ARK_UI = builder.comment("Force the built-in Ark UI resource pack (carved-stone inventory, chests, buttons, hotbar, tooltips). "
                        + "Set false and restart to use vanilla or your own GUI pack.")
                .define("arkUiPack", true);
        MIRROR_TURN_CLIPS = builder.comment("Swap the left and right turn and banking clips if creatures step the wrong way "
                        + "while turning in place.")
                .define("mirrorTurnClips", false);
        CREATURE_FAST_DRAWING = builder.comment("Ark writes the cubes of its creatures itself, in bulk through Sodium where it is installed; "
                        + "GeckoLib still animates them. Set false to let GeckoLib draw them as before, to compare the picture or the frame rate.")
                .define("creatureFastDrawing", true);
        CREATURE_HIDDEN_FACES = builder.comment("With creatureFastDrawing: leave out the sides of a creature's box that the box itself hides "
                        + "(closed, opaque boxes in the world view only). Set false to draw every face.")
                .define("creatureHiddenFaces", true);
        SPEC = builder.build();
    }
    private NighttimeClientConfig() {}
}
