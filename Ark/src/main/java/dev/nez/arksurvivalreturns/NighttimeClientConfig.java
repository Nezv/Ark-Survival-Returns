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
    public static final ModConfigSpec.DoubleValue CREATURE_ANIMATION_DISTANCE;
    public static final ModConfigSpec.DoubleValue CREATURE_SMALLEST_BOX;
    public static final ModConfigSpec.BooleanValue CREATURE_SHADER_VERTICES;
    public static final ModConfigSpec.BooleanValue CREATURE_FAST_ANIMATION;
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
        CREATURE_ANIMATION_DISTANCE = builder.comment("With creatureFastDrawing: a creature farther away than this many blocks has its limbs "
                        + "moved every second frame, every third beyond twice the distance and every fourth beyond four times; where it stands "
                        + "and faces stays smooth, and a spyglass brings it as close as it shows it. 0 moves every limb in every frame.")
                .defineInRange("creatureAnimationDistance", 64.0, 0.0, 1024.0);
        CREATURE_SMALLEST_BOX = builder.comment("With creatureFastDrawing: a box of a creature that would be smaller than this many pixels "
                        + "across is not drawn. 0 draws every box.")
                .defineInRange("creatureSmallestBox", 1.0, 0.0, 16.0);
        CREATURE_SHADER_VERTICES = builder.comment("With creatureFastDrawing, Sodium and a shader pack: Ark writes the vertices of its creatures "
                        + "in the wider format Iris gives a shader pack, with the tangent and the middle of the texture kept per face of the "
                        + "model. Set false to have Iris convert every vertex as before.")
                .define("creatureShaderVertices", true);
        CREATURE_FAST_ANIMATION = builder.comment("The keys of an animation are read directly where they are plain linear or catmull-rom keys, "
                        + "as all of Ark's are, instead of through GeckoLib's easing objects; the values are the same. Set false for GeckoLib's own reading.")
                .define("creatureFastAnimation", true);
        SPEC = builder.build();
    }
    private NighttimeClientConfig() {}
}
