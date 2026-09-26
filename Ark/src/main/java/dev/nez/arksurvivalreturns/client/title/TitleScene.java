package dev.nez.arksurvivalreturns.client.title;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A creature standing in a menu scene (the FancyMenu "Ark Creature" element). GeckoLib poses its idle clip with
 * no world loaded, {@link Gaze} turns its neck and head, and the {@link StormClock} the scene's shaders share
 * lights it up and rolls the thunder in after each strike.
 *
 * <p>Placement uses scene units, the same as tools/title_scene/*.glsl: x from the centre of the element in
 * element heights, y up from its bottom. Stretched over the screen, the creature stays on the painted path at
 * any resolution.
 */
public final class TitleScene {
    /** Depth is flattened so long creatures fit the picture's depth range; the view is orthographic, so it never shows. */
    private static final float DEPTH_SQUASH = 0.25f;
    /** Neck and head bones of the imported ARK skeletons, and each one's share of a head turn. */
    private static final String[] GAZE_BONES = {"Cnt_Neck_001_JNT_SKL", "Cnt_Neck_002_JNT_SKL", "Cnt_Neck_003_JNT_SKL",
            "Cnt_Neck_004_JNT_SKL", "Cnt_Head_JNT_SKL"};
    private static final float[] GAZE_SHARE = {0.14f, 0.2f, 0.22f, 0.1f, 0.34f};
    private static final int FLASH_COLOR = 0xFFD6E0FF;
    private static final Identifier THUNDER = Identifier.fromNamespaceAndPath("minecraft", "entity.lightning_bolt.thunder");
    private static final Map<String, TitleCreatureRenderer> RENDERERS = new HashMap<>();
    private static final Map<String, ModelBounds> BOUNDS = new HashMap<>();

    /** What the element asks for this frame (its FancyMenu properties). */
    public record Settings(String species, String variant, String idleClip, float sceneX, float sceneGround,
                           float sceneHeight, float bodyYaw, float cameraPitch, boolean lookAround, float lookRange,
                           int tint, float lightningBoost, boolean thunder, float thunderVolume, float parallax) {}

    private final Gaze gaze = new Gaze(System.nanoTime());
    private final RandomSource random = RandomSource.create();
    private TitleCreature creature;
    private String creatureKey = "";
    private TitleCreatureRenderer renderer;
    private ModelBounds bounds = ModelBounds.UNIT;
    private Settings settings;
    private float lookYaw, lookPitch;
    private int color = 0xFFFFFFFF;
    private long lastStrikeSlot = Long.MIN_VALUE;
    private long thunderAt = Long.MIN_VALUE;
    private float thunderVolume;
    private boolean failed;

    /**
     * Queues this frame's creature. The box is the element area in GUI units; mouse coordinates give the
     * same parallax the shaders apply to the ground layer.
     */
    public void extract(GuiGraphicsExtractor graphics, Settings settings, int boxX, int boxY, int boxWidth, int boxHeight,
                        int mouseX, int mouseY, float partialTick, boolean editor) {
        if (failed || boxWidth <= 0 || boxHeight <= 0) return;
        try {
            prepare(settings, editor);
            float perBlock = settings.sceneHeight() * boxHeight / bounds.height();
            if (!(perBlock > 0)) return;
            float nx = 0f, ny = 0f;
            if (mouseX > 0 || mouseY > 0) {
                nx = Mth.clamp((mouseX - boxX) / (float) boxWidth, 0f, 1f) - 0.5f;
                ny = Mth.clamp((boxY + boxHeight - mouseY) / (float) boxHeight, 0f, 1f) - 0.5f;
            }
            float centerX = boxX + boxWidth / 2f + settings.sceneX() * boxHeight - nx * settings.parallax() * boxHeight;
            float feetY = boxY + boxHeight - settings.sceneGround() * boxHeight + ny * settings.parallax() * 0.35f * boxHeight;
            float halfWidth = bounds.radius() * perBlock * 1.05f;
            int x0 = Mth.floor(centerX - halfWidth), x1 = Mth.ceil(centerX + halfWidth);
            int y0 = Mth.floor(feetY - bounds.height() * perBlock * 1.3f), y1 = Mth.ceil(feetY + bounds.height() * perBlock * 0.1f);
            graphics.submitPictureInPictureRenderState(new CreatureSceneState(this, x0, y0, x1, y1, perBlock,
                    centerX - (x0 + x1) / 2f, feetY - y1, partialTick, graphics.peekScissorStack()));
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    private void prepare(Settings settings, boolean editor) {
        this.settings = settings;
        Species species = species(settings.species());
        String clip = settings.idleClip().isBlank() ? species.idle : settings.idleClip();
        String key = species.id + '|' + settings.variant() + '|' + clip;
        if (!key.equals(creatureKey)) {
            creatureKey = key;
            creature = new TitleCreature(clip);
            renderer = RENDERERS.computeIfAbsent(species.id + '|' + settings.variant(),
                    k -> new TitleCreatureRenderer(new TitleCreatureModel(species.id, settings.variant())));
            bounds = BOUNDS.computeIfAbsent(species.id, TitleScene::loadBounds);
        }
        double now = System.nanoTime() / 1e9;
        long wall = System.currentTimeMillis();
        long slot = Math.floorDiv(wall, StormClock.SLOT_SECONDS * 1000L);
        StormClock.Strike strike = StormClock.strike(slot);
        if (strike != null && wall >= strike.startMillis() && slot != lastStrikeSlot) {
            lastStrikeSlot = slot;
            // Only strikes seen as they happen: not one that was over before the menu opened.
            if (wall - strike.startMillis() < 1000) {
                if (settings.lookAround() && random.nextFloat() < 0.65f) gaze.startle(now, settings.lookRange());
                if (settings.thunder() && !editor) {
                    thunderAt = strike.startMillis() + (long) (strike.thunderDelaySeconds() * 1000);
                    thunderVolume = settings.thunderVolume() * (0.55f + 0.45f * strike.strength());
                }
            }
        }
        if (thunderAt != Long.MIN_VALUE && wall >= thunderAt) {
            thunderAt = Long.MIN_VALUE;
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(THUNDER, SoundSource.WEATHER,
                    thunderVolume, 0.8f + random.nextFloat() * 0.2f, random, false, 0, SoundInstance.Attenuation.NONE,
                    0, 0, 0, true));
        }
        double[] look = settings.lookAround() ? gaze.at(now, settings.lookRange()) : new double[2];
        lookYaw = (float) Math.toRadians(look[0]);
        lookPitch = (float) Math.toRadians(look[1]);
        float flash = Math.min(1f, StormClock.flash(wall) * settings.lightningBoost());
        color = lerpColor(settings.tint(), FLASH_COLOR, flash);
    }

    /** Called by {@link CreatureSceneRenderer} with the texture's origin at the bottom centre. */
    void render(CreatureSceneState state, PoseStack pose) {
        if (failed || renderer == null) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            float scale = state.scale();
            pose.translate(state.offsetX() / scale, state.offsetY() / scale, 0f);
            pose.scale(1f, 1f, DEPTH_SQUASH);
            pose.mulPose(Axis.ZP.rotation(Mth.PI));                     // GUI y runs down; the model's up is +Y
            pose.mulPose(Axis.XP.rotationDegrees(-settings.cameraPitch()));
            // The ARK skeletons face +Z, away from the viewer here: 180 turns them round, body_yaw 90 faces right.
            pose.mulPose(Axis.YP.rotationDegrees(180f + settings.bodyYaw()));
            pose.translate(-bounds.centerX(), 0f, -bounds.centerZ());
            FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
            renderer.color(color);   // renderers are shared per skin; each scene brings its own light
            renderer.performRenderPass(creature, null, pose, dispatcher.getSubmitNodeStorage(), new CameraRenderState(),
                    LightCoordsUtil.FULL_BRIGHT, state.partialTick(), this::lookAround);
            dispatcher.renderAllFeatures();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    /** Spread the head turn along the neck so it bends instead of swivelling at the skull. */
    private void lookAround(RenderPassInfo<GeoRenderState> pass, BoneSnapshots snapshots) {
        for (int i = 0; i < GAZE_BONES.length; i++) {
            float share = GAZE_SHARE[i];
            snapshots.ifPresent(GAZE_BONES[i], bone -> bone.setRotY(bone.getRotY() + lookYaw * share)
                    .setRotX(bone.getRotX() - lookPitch * share));
        }
    }

    private void fail(Throwable e) {
        failed = true;
        ArkSurvivalReturns.LOGGER.error("Menu creature disabled: it failed to render", e);
    }

    static Species species(String id) {
        for (Species species : Species.values()) if (species.id.equals(id)) return species;
        return Species.TYRANNOSAURUS;
    }

    private static ModelBounds loadBounds(String species) {
        Identifier location = ArkSurvivalReturns.id("geckolib/models/entity/" + species + ".geo.json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) return ModelBounds.UNIT;
        try (Reader reader = resource.get().openAsReader()) {
            return ModelBounds.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            ArkSurvivalReturns.LOGGER.warn("Could not read {} for the menu creature", location, e);
            return ModelBounds.UNIT;
        }
    }

    static int lerpColor(int from, int to, float t) {
        int a = Math.round(Mth.lerp(t, from >>> 24, to >>> 24));
        int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
