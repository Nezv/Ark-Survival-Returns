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
 * The designed eyes of a big carnivore (tools/build_creature_eyes.py, DESIGNS) brought to life: lids that blink,
 * and close in sleep, torpor and death; a brow that drops and tilts into a frown; a slit that narrows on prey
 * and widens in the dark; and the yellow eyes turning red, behind a blink, for a hunt or a fight. One per
 * creature, eased every frame towards the look of its synced state; the renderer sets the bones from it.
 */
public final class CreatureEyes {
    /** The lid contract of build_creature_eyes.LID_REST and LID_TUCK: the cover at scale 1, the reach past the opening. */
    static final float UPPER_REST = .30f, UPPER_TUCK = .25f, LOWER_REST = .12f, LOWER_TUCK = .15f;
    /** Seconds a blink takes: shutting, held shut, opening. */
    private static final float SHUT = .07f, HELD = .03f, OPEN = .12f, BLINK = SHUT + HELD + OPEN;
    private static final String[] UPPER = bones("ark_eye_lid_upper_"), LOWER = bones("ark_eye_lid_lower_"),
            BROW = bones("ark_eye_brow_"), PUPIL = bones("ark_eye_pupil_"), CALM = bones("ark_eyeball_calm_"),
            HUNT = bones("ark_eyeball_hunt_");
    private static final Map<BakedGeoModel, Float> HEIGHTS = new WeakHashMap<>();

    /**
     * Lid cover (shares of the eye's height), brow drop (eye heights) and tilt (degrees, front end down), slit
     * width (a scale of the model's). The same numbers as tools/preview_creature_eyes.py STATES.
     */
    public enum Look {
        CALM(.30f, .12f, 0f, 0f, 1f), ALERT(.22f, .10f, .06f, 6f, .7f), HUNT(.40f, .20f, .12f, 8f, .45f),
        DROWSY(.50f, .20f, .04f, 0f, 1f), CLOSED(.70f, .44f, .06f, 0f, 1f), FLEE(.10f, .04f, -.10f, -4f, 1.3f),
        WINCE(.62f, .40f, .14f, 10f, .8f);

        final float upper, lower, drop, tilt, slit;

        Look(float upper, float lower, float drop, float tilt, float slit) {
            this.upper = upper;
            this.lower = lower;
            this.drop = drop;
            this.tilt = tilt;
            this.slit = slit;
        }
    }

    private float upper = UPPER_REST, lower = LOWER_REST, drop, tilt, slit = 1f;
    private float shownUpper = UPPER_REST, shownLower = LOWER_REST, shownDrop;
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
            // A calm slit opens in the dark and closes in bright light.
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
        upper += (look.upper - upper) * lids;
        lower += (look.lower - lower) * lids;
        drop += (look.drop - drop) * brow;
        tilt += (look.tilt - tilt) * brow;
        slit += (look.slit * slitScale - slit) * iris;
        shownUpper = Mth.lerp(shutBy, upper, Look.CLOSED.upper);
        shownLower = Mth.lerp(shutBy * .85f, lower, Look.CLOSED.lower);
        shownDrop = drop + .03f * shutBy;
    }

    boolean red() { return red; }

    /** How much of the eye the lids cover this frame, as the bones show it. */
    float covered() { return shownUpper + shownLower; }

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

    /** Sets the eye bones of one pass; eye heights (for the brow's drop) are read off the model's eyeball. */
    public void apply(BakedGeoModel model, BoneSnapshots snapshots) {
        float height = HEIGHTS.computeIfAbsent(model, CreatureEyes::eyeHeight);
        float upperScale = (shownUpper + UPPER_TUCK) / (UPPER_REST + UPPER_TUCK);
        float lowerScale = (shownLower + LOWER_TUCK) / (LOWER_REST + LOWER_TUCK);
        float browY = -shownDrop * height, browTurn = tilt * Mth.DEG_TO_RAD;
        for (int side = 0; side < 2; side++) {
            snapshots.ifPresent(UPPER[side], bone -> bone.setScaleY(upperScale));
            snapshots.ifPresent(LOWER[side], bone -> bone.setScaleY(lowerScale));
            snapshots.ifPresent(BROW[side], bone -> bone.setTranslateY(browY).setRotX(browTurn));
            snapshots.ifPresent(PUPIL[side], bone -> bone.setScaleZ(slit));
            snapshots.ifPresent(CALM[side], bone -> bone.skipRender(red));
            snapshots.ifPresent(HUNT[side], bone -> bone.skipRender(!red));
        }
    }

    /** Whether a model carries designed eyes. */
    public static boolean designed(BakedGeoModel model) { return model.getBone(UPPER[0]).isPresent(); }

    private static float eyeHeight(BakedGeoModel model) {
        return model.getBone(CALM[0]).filter(bone -> bone instanceof CuboidGeoBone cuboid && cuboid.cubes.length > 0)
                .map(bone -> (float) ((CuboidGeoBone) bone).cubes[0].size().y()).orElse(0f);
    }

    private static String[] bones(String prefix) { return new String[]{prefix + "l", prefix + "r"}; }
}
