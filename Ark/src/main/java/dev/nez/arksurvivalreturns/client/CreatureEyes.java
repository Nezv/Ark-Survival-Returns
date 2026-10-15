package dev.nez.arksurvivalreturns.client;

import java.util.Map;
import java.util.WeakHashMap;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.base.BoneSnapshots;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * The designed eyes (tools/build_creature_eyes.py, DESIGNS) of the predators, the sea animals and the flyers,
 * brought to life: lids that blink, and close in sleep, torpor and death; a brow that drops and tilts into a
 * frown; a pupil that narrows on prey and widens in the dark; and the calm eyes turning to their hunting
 * colour, behind a blink, for a hunt or a fight. One per creature, eased every frame towards the look of its
 * synced state; the renderer sets the bones from it. How wide a species' lids lie open, what shape its pupil
 * is and whether it has hunting eyes at all is read off its model ({@link #layout}), so a new design needs no
 * code here.
 */
public final class CreatureEyes {
    /** How far a lid reaches past the opening, in eye heights (build_creature_eyes.LID_TUCK), and how much of it covers a shut eye. */
    static final float UPPER_TUCK = .25f, LOWER_TUCK = .15f, UPPER_SHUT = .70f, LOWER_SHUT = .44f;
    /** Seconds a blink takes: shutting, held shut, opening. */
    private static final float SHUT = .07f, HELD = .03f, OPEN = .12f, BLINK = SHUT + HELD + OPEN;
    private static final String[] FRAME = bones("ark_eye_"), UPPER = bones("ark_eye_lid_upper_"), LOWER = bones("ark_eye_lid_lower_"),
            BROW = bones("ark_eye_brow_"), PUPIL = bones("ark_eye_pupil_"), CALM = bones("ark_eyeball_calm_"),
            HUNT = bones("ark_eyeball_hunt_");
    private static final Map<BakedGeoModel, Layout> LAYOUTS = new WeakHashMap<>();

    /**
     * A look: how shut the lids are (0 is the species' own lids, 1 a shut eye, below 0 wider than its own, to -1
     * for no lid at all), the brow's drop (eye heights) and tilt (degrees, front end down), and the pupil's
     * width (a scale). The same numbers as tools/preview_creature_eyes.py STATES.
     */
    public enum Look {
        CALM(0f, 0f, 0f, 1f), ALERT(-.27f, .06f, 6f, .7f), HUNT(.28f, .12f, 8f, .45f), DROWSY(.5f, .04f, 0f, 1f),
        CLOSED(1f, .06f, 0f, 1f), FLEE(-.67f, -.10f, -4f, 1.3f), WINCE(.8f, .14f, 10f, .8f);

        final float closure, drop, tilt, slit;

        Look(float closure, float drop, float tilt, float slit) {
            this.closure = closure;
            this.drop = drop;
            this.tilt = tilt;
            this.slit = slit;
        }
    }

    /** What a model's eye bones say about its design. */
    private record Layout(float height, float restUpper, float restLower, boolean roundPupil, boolean hunt) {}

    private float closure, drop, tilt, slit = 1f;
    private float shownUpper, shownLower, shownDrop;
    private boolean red;
    private float clock = Float.NaN, blinkStart = -10f, nextBlink = Float.NaN;
    private long seed;

    public CreatureEyes(long seed) { this.seed = seed ^ 0x5DEECE66DL; }

    /** The look of a creature's synced state, then a frame of easing towards it. */
    void update(CreatureEntity creature, float partialTick) {
        BehaviorState state = creature.behavior();
        boolean shut = !creature.isAlive() || creature.deathTime > 0 || TorporService.restricted(creature) || state == BehaviorState.SLEEP;
        boolean fight = state.combat() || state == BehaviorState.THREATEN || creature.isAggressive();
        boolean hunting = !shut && (fight || creature.hunting() && !state.sleeping());
        Look look = shut ? Look.CLOSED : creature.hurtTime > 0 ? Look.WINCE : state == BehaviorState.FLEE ? Look.FLEE
                : hunting ? Look.HUNT : state == BehaviorState.REST ? Look.DROWSY
                : state == BehaviorState.ALERT || state == BehaviorState.INVESTIGATE || state == BehaviorState.SEARCH ? Look.ALERT
                : Look.CALM;
        float slitScale = 1f;
        if (look == Look.CALM || look == Look.DROWSY) {
            // A calm pupil opens in the dark and closes in bright light.
            int light = creature.level().getMaxLocalRawBrightness(BlockPos.containing(creature.getEyePosition(partialTick)));
            slitScale = Mth.lerp((15 - light) / 15f, .85f, 1.45f);
        }
        update((creature.tickCount + partialTick) / 20f, look, hunting, slitScale);
    }

    /** One frame at a time in seconds: eases towards the look, blinks now and then, turns red only while shut. */
    public void update(float now, Look look, boolean redLook, float slitScale) {
        float dt = Float.isNaN(clock) ? 1f : Mth.clamp(now - clock, 0f, .25f);
        clock = now;
        boolean open = look != Look.CLOSED;
        if (Float.isNaN(nextBlink)) nextBlink = now + .5f + 4f * random();
        // The colour changes behind a blink: one starts as soon as none is under way.
        if (red != redLook && open && now - blinkStart > BLINK) blinkStart = now;
        if (open && now >= nextBlink && now - blinkStart > BLINK) {
            blinkStart = now;
            nextBlink = now + (random() < .15f ? .3f : 2.5f + 4.5f * random());
        }
        float shutBy = blink(now - blinkStart);
        if (red != redLook && (!open || shutBy > .85f)) red = redLook;
        float lids = 1f - (float) Math.exp(-16f * dt), brow = 1f - (float) Math.exp(-8f * dt), iris = 1f - (float) Math.exp(-4f * dt);
        closure += (look.closure - closure) * lids;
        drop += (look.drop - drop) * brow;
        tilt += (look.tilt - tilt) * brow;
        slit += (look.slit * slitScale - slit) * iris;
        shownUpper = closure + shutBy * (1f - closure);
        shownLower = closure + shutBy * .85f * (1f - closure);
        shownDrop = drop + .03f * shutBy;
    }

    boolean red() { return red; }

    /** How shut the upper lid is this frame, 0 to 1, as the bones show it. */
    float shut() { return shownUpper; }

    /** How shut a blink started that long ago holds the eye, 0 to 1. */
    private static float blink(float since) {
        if (since < 0f || since >= BLINK) return 0f;
        if (since < SHUT) return since / SHUT;
        if (since < SHUT + HELD) return 1f;
        return 1f - (since - SHUT - HELD) / OPEN;
    }

    private float random() {
        seed = seed * 0x5DEECE66DL + 0xBL & (1L << 48) - 1;
        return (int) (seed >>> 24) / (float) (1 << 24);
    }

    /** The share of the eye a lid covers at a closure: its own rest, towards a shut eye, or less (build_creature_eyes.lid_cover). */
    static float cover(float closure, float rest, float shut) {
        return closure >= 0f ? rest + closure * (shut - rest) : rest * (1f + closure);
    }

    /** Sets the eye bones of one pass. */
    public void apply(BakedGeoModel model, BoneSnapshots snapshots) {
        Layout layout = LAYOUTS.computeIfAbsent(model, CreatureEyes::layout);
        float upperScale = (cover(shownUpper, layout.restUpper, UPPER_SHUT) + UPPER_TUCK) / (layout.restUpper + UPPER_TUCK);
        float lowerScale = (cover(shownLower, layout.restLower, LOWER_SHUT) + LOWER_TUCK) / (layout.restLower + LOWER_TUCK);
        float browY = -shownDrop * layout.height, browTurn = tilt * Mth.DEG_TO_RAD;
        // A slit narrows across; a round pupil opens and closes all round, over a smaller range.
        float across = layout.roundPupil ? 1f + (slit - 1f) * .5f : slit, along = layout.roundPupil ? across : 1f;
        boolean hunting = red && layout.hunt;
        for (int side = 0; side < 2; side++) {
            snapshots.ifPresent(UPPER[side], bone -> bone.setScaleY(upperScale));
            snapshots.ifPresent(LOWER[side], bone -> bone.setScaleY(lowerScale));
            snapshots.ifPresent(BROW[side], bone -> bone.setTranslateY(browY).setRotX(browTurn));
            snapshots.ifPresent(PUPIL[side], bone -> bone.setScaleZ(across).setScaleY(along));
            snapshots.ifPresent(CALM[side], bone -> bone.skipRender(hunting));
            snapshots.ifPresent(HUNT[side], bone -> bone.skipRender(!hunting));
        }
    }

    /** Whether a model carries designed eyes. */
    public static boolean designed(BakedGeoModel model) { return model.getBone(FRAME[0]).isPresent(); }

    private static Layout layout(BakedGeoModel model) {
        float height = size(model, CALM[0], 1);
        float upper = size(model, UPPER[0], 1), lower = size(model, LOWER[0], 1);
        float tall = size(model, PUPIL[0], 1), wide = size(model, PUPIL[0], 2);
        // A lid is built (rest + tuck) eye heights long: its own length says how far open it lies.
        return new Layout(height, height > 0f && upper > 0f ? Math.max(0f, upper / height - UPPER_TUCK) : 0f,
                height > 0f && lower > 0f ? Math.max(0f, lower / height - LOWER_TUCK) : 0f, tall > 0f && tall < 1.5f * wide,
                model.getBone(HUNT[0]).isPresent());
    }

    /** The first cube of a bone, along one axis (0 x, 1 y, 2 z) in model units; 0 for a bone that is not there. */
    private static float size(BakedGeoModel model, String bone, int axis) {
        return model.getBone(bone).filter(candidate -> candidate instanceof CuboidGeoBone cuboid && cuboid.cubes.length > 0)
                .map(candidate -> {
                    var size = ((CuboidGeoBone) candidate).cubes[0].size();
                    return (float) (axis == 0 ? size.x() : axis == 1 ? size.y() : size.z());
                }).orElse(0f);
    }

    private static String[] bones(String prefix) { return new String[]{prefix + "l", prefix + "r"}; }
}
