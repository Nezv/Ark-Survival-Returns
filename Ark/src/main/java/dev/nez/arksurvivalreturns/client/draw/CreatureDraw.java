package dev.nez.arksurvivalreturns.client.draw;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.TreeMap;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoLocator;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.RenderPassInfo;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.NighttimeClientConfig;
import dev.nez.arksurvivalreturns.client.draw.iris.IrisPasses;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Draws Ark's creatures with {@link CubeWriter} instead of GeckoLib's own geometry code. GeckoLib still loads
 * the model, runs the animation and poses the bones; this takes the vertex writing: in bulk through Sodium
 * where it is installed, and without the sides of a box that the box itself hides. The bones of a creature are
 * kept once read, so a second pass of the same frame does not ask GeckoLib for them again, and with distance a
 * creature's limbs are read every second to fourth frame and its smallest boxes left out. The client settings
 * (creatureFastDrawing, creatureHiddenFaces, creatureAnimationDistance, creatureSmallestBox) switch all of it;
 * where a case is not this writer's, GeckoLib draws.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class CreatureDraw {
    /**
     * -Darksurvivalreturns.creatureDraw fixes the client settings for a comparison: geckolib (GeckoLib draws),
     * allfaces (this writer, every face, no distance detail), nolod (hidden faces left out) or ark (the defaults).
     */
    private static final String FORCED = System.getProperty("arksurvivalreturns.creatureDraw", "");
    /** -Darksurvivalreturns.creatureDraw.verify=true: every eighth creature is also drawn the old way and compared. */
    private static final boolean VERIFY = Boolean.getBoolean("arksurvivalreturns.creatureDraw.verify");
    private static final double DEFAULT_ANIMATION_DISTANCE = 64.0, DEFAULT_SMALLEST_BOX = 1.0;
    private static final int FRESH = 0, SAME_FRAME = 1, HELD = 2;
    private static final CubeWriter WRITER = new CubeWriter();
    private static final QuadSink PLAIN = new ConsumerSink();
    /** The bones of every creature drawn lately, by GeckoLib's id of it (the entity's). */
    private static final Long2ObjectOpenHashMap<CubeWriter.Posed> POSES = new Long2ObjectOpenHashMap<>();
    private static final CubeWriter.Posed UNKEPT = new CubeWriter.Posed();
    /** What a pass was asked to report bone or locator positions to; GeckoLib keeps no public way to ask. */
    private static final MethodHandle BONE_LISTENERS = listeners("bonePositionListeners"), LOCATOR_LISTENERS = listeners("locatorPositionListeners");
    private static QuadSink bulk;
    private static boolean sodium = ModList.get().isLoaded("sodium"), iris = ModList.get().isLoaded("iris");
    private static long frame;
    /** Of the world view: pixels a radian covers, and how much narrower the view is than the field-of-view setting. */
    private static float pixelsPerRadian, zoom = 1f;
    private static long bulkDraws, plainDraws, geckoLibDraws, farSideDraws, freshPoses, sameFramePoses, heldPoses;
    private static Probe probe;

    public static boolean enabled() {
        return FORCED.isEmpty() ? NighttimeClientConfig.CREATURE_FAST_DRAWING.get() : !FORCED.equals("geckolib");
    }

    private static boolean skipsFaces() {
        return FORCED.isEmpty() ? NighttimeClientConfig.CREATURE_HIDDEN_FACES.get() : FORCED.equals("ark") || FORCED.equals("nolod");
    }

    private static double animationDistance() {
        return FORCED.isEmpty() ? NighttimeClientConfig.CREATURE_ANIMATION_DISTANCE.get() : FORCED.equals("ark") ? DEFAULT_ANIMATION_DISTANCE : 0.0;
    }

    private static double smallestBox() {
        return FORCED.isEmpty() ? NighttimeClientConfig.CREATURE_SMALLEST_BOX.get() : FORCED.equals("ark") ? DEFAULT_SMALLEST_BOX : 0.0;
    }

    @SubscribeEvent public static void frameStart(RenderFrameEvent.Pre event) {
        frame++;
        // Creatures that left the view or the world: their bones are dropped after a while.
        if ((frame & 1023) == 0) POSES.values().removeIf(posed -> frame - posed.frame > 1024);
    }

    /**
     * Submits the pass's model for the render type the way GeoRenderer.submitRenderTasks does, drawn by this
     * writer. False when it is not this writer's to draw (switched off, the missing model, a model with
     * something other than cubes); the caller lets GeckoLib do it then.
     *
     * @param emptyFacesDrawNothing the pass blends without writing depth, so a face under transparent texels
     *                              only changes nothing (the eyes layer)
     */
    public static boolean submit(RenderPassInfo<?> pass, OrderedSubmitNodeCollector collector, RenderType type, Identifier texture,
                                 int light, int overlay, int color, boolean emptyFacesDrawNothing) {
        if (!enabled()) return false;
        BakedGeoModel model = pass.model();
        CreatureMesh mesh = model.isMissingno() ? null : CreatureMesh.of(model);
        if (mesh == null) return false;
        TextureCoverage coverage = skipsFaces() ? mesh.coverage(texture) : null;
        collector.submitCustomGeometry(pass.poseStack(), type,
                new Task(pass, mesh, coverage, texture, type.hasBlending(), emptyFacesDrawNothing, light, overlay, color));
        return true;
    }

    /** Counters since the client started and, when verifying, the comparison with GeckoLib's drawing. */
    public static JsonObject report() {
        JsonObject report = new JsonObject();
        report.addProperty("mode", !enabled() ? "geckolib" : !skipsFaces() ? "allfaces" : animationDistance() > 0 || smallestBox() > 0 ? "ark" : "nolod");
        report.addProperty("draws_bulk", bulkDraws);
        report.addProperty("draws_vertex_by_vertex", plainDraws);
        report.addProperty("draws_left_to_geckolib", geckoLibDraws);
        report.addProperty("draws_without_far_sides", farSideDraws);
        report.addProperty("faces_written", WRITER.facesWritten);
        report.addProperty("faces_left_out", WRITER.facesSkipped);
        report.addProperty("cubes_too_small", WRITER.cubesTooSmall);
        report.addProperty("poses_read", freshPoses);
        report.addProperty("poses_of_the_same_frame_drawn_again", sameFramePoses);
        report.addProperty("poses_held_for_distance", heldPoses);
        report.addProperty("pose_keeping", BONE_LISTENERS != null && LOCATOR_LISTENERS != null);
        if (probe != null) report.add("verify", probe.report());
        return report;
    }

    private static final class Task implements SubmitNodeCollector.CustomGeometryRenderer, Runnable {
        private final RenderPassInfo<?> pass;
        private final CreatureMesh mesh;
        private final TextureCoverage coverage;
        private final Identifier texture;
        private final boolean blending, emptyFacesDrawNothing;
        private final int light, overlay, color;
        private PoseStack.Pose pose;
        private VertexConsumer consumer;
        private boolean world, shadow, pack;

        Task(RenderPassInfo<?> pass, CreatureMesh mesh, TextureCoverage coverage, Identifier texture, boolean blending,
             boolean emptyFacesDrawNothing, int light, int overlay, int color) {
            this.pass = pass;
            this.mesh = mesh;
            this.coverage = coverage;
            this.texture = texture;
            this.blending = blending;
            this.emptyFacesDrawNothing = emptyFacesDrawNothing;
            this.light = light;
            this.overlay = overlay;
            this.color = color;
        }

        @Override public void render(PoseStack.Pose pose, VertexConsumer consumer) {
            this.pose = pose;
            this.consumer = consumer;
            shadow = iris && irisShadow();
            pack = iris && irisShaderPack();
            world = !shadow && worldPass();
            if (world) view(pass.cameraState());
            CubeWriter.Posed kept = affine(pose.pose()) ? kept() : null;
            // The bones carry their animated state, and the hidden eye set, only inside renderPosed.
            if (kept == null) pass.renderPosed(this);
            else draw(kept, kept.frame == frame ? SAME_FRAME : HELD);
            this.pose = null;
            this.consumer = null;
        }

        /** Inside renderPosed: reads the bones and draws them. */
        @Override public void run() {
            if (!affine(pose.pose()) || listening(mesh)) {
                geckoLibDraws++;
                geckoLib(pass, pose, consumer, light, overlay, color);
                return;
            }
            Long id = pass.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
            CubeWriter.Posed posed = UNKEPT;
            if (id != null) {
                posed = POSES.get(id.longValue());
                if (posed == null) POSES.put(id.longValue(), posed = new CubeWriter.Posed());
            }
            WRITER.pose(mesh, posed);
            posed.frame = frame;
            posed.age = age();
            freshPoses++;
            draw(posed, FRESH);
        }

        /**
         * The bones read for this creature earlier, when they still serve: in this very frame at the same age
         * (another pass over it: the eyes layer, a shader pack's shadow map), or, for a creature far away in
         * the world view, up to three frames ago. Null when GeckoLib has to be asked.
         */
        private CubeWriter.Posed kept() {
            if (BONE_LISTENERS == null || LOCATOR_LISTENERS == null || listeners(pass)) return null;
            Long id = pass.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
            CubeWriter.Posed posed = id == null ? null : POSES.get(id.longValue());
            if (posed == null || !posed.of(mesh)) return null;
            if (posed.frame == frame) {
                if (posed.age != age()) return null;
                sameFramePoses++;
                return posed;
            }
            double full = animationDistance();
            if (full <= 0.0 || !(world || shadow)) return null;
            Matrix4f matrix = pose.pose();
            float size = pass.renderState() instanceof EntityRenderState state ? Math.max(state.boundingBoxWidth, state.boundingBoxHeight) : 0f;
            double distance = (Math.sqrt(matrix.m30() * matrix.m30() + matrix.m31() * matrix.m31() + matrix.m32() * matrix.m32()) - size) * zoom;
            int every = distance < full ? 1 : distance < full * 2 ? 2 : distance < full * 4 ? 3 : 4;
            // Staggered by the creature's id, so the far ones do not all read their bones in the same frame.
            if (every == 1 || frame - posed.frame >= every || Math.floorMod(frame + id.longValue(), every) == 0) return null;
            heldPoses++;
            return posed;
        }

        private float age() {
            return pass.renderState() instanceof EntityRenderState state ? state.ageInTicks : Float.NaN;
        }

        private void draw(CubeWriter.Posed posed, int kind) {
            int skip = CubeWriter.ALL;
            float smallest = 0f;
            // Faces are left out only on the way into a vertex buffer of the picture. Anything else that takes a
            // creature's vertices (an outline, Physics Mod building a ragdoll from them) gets every face.
            boolean buffer = consumer.getClass() == BufferBuilder.class;
            if (buffer && coverage != null && coverage.ready) {
                if (emptyFacesDrawNothing) {
                    // A shader pack may write more than colour for a transparent texel; all faces there.
                    if (!pack) skip = CubeWriter.EMPTY_FACES;
                } else if (!blending && world) {
                    skip = CubeWriter.FAR_SIDES;
                    farSideDraws++;
                }
            }
            // The eyes are small and meant to be seen from afar: no box of theirs is too small.
            double limit = smallestBox();
            if (buffer && world && !blending && !emptyFacesDrawNothing && limit > 0.0 && pixelsPerRadian > 0f) {
                float perPixel = (float) (pixelsPerRadian / limit);
                smallest = perPixel * perPixel;
            }
            QuadSink sink = bulk(consumer, color, overlay, light);
            if (sink == null) {
                sink = PLAIN;
                sink.begin(consumer, color, overlay, light);
                plainDraws++;
            } else {
                bulkDraws++;
            }
            try {
                // Iris replaces the normal of every quad drawn into the level vertex by vertex with the one its corners
                // give; its bulk serializer keeps what it is handed, so the same normal is handed over.
                WRITER.write(mesh, posed, pose.pose(), pose.normal(), sink, skip, coverage, pack && (world || shadow), smallest);
            } finally {
                sink.end();
            }
            if (VERIFY) {
                if (probe == null) probe = new Probe();
                probe.check(texture.getPath(), kind, pass, mesh, posed, pose, light, overlay, color);
            }
        }
    }

    /** GeckoLib's own drawing, as its submitRenderTasks does it inside renderPosed. */
    static void geckoLib(RenderPassInfo<?> pass, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        PoseStack stack = pass.poseStack();
        stack.pushPose();
        stack.last().set(pose);
        pass.model().render(pass, consumer, light, overlay, color);
        stack.popPose();
    }

    private static QuadSink bulk(VertexConsumer consumer, int color, int overlay, int light) {
        if (!sodium) return null;
        try {
            // By name: nothing of Sodium's is loaded, or looked at by the verifier, unless Sodium is installed.
            if (bulk == null) bulk = (QuadSink) Class.forName("dev.nez.arksurvivalreturns.client.draw.sodium.BulkSink").getDeclaredConstructor().newInstance();
            return bulk.begin(consumer, color, overlay, light) ? bulk : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            sodium = false;
            ArkSurvivalReturns.LOGGER.warn("Sodium's vertex writer is not usable; creatures are written vertex by vertex", e);
            return null;
        }
    }

    static QuadSink bulkSink() {
        return sodium ? bulk : null;
    }

    private static boolean irisShadow() {
        try {
            return IrisPasses.shadow();
        } catch (LinkageError | RuntimeException e) {
            return irisGone(e);
        }
    }

    private static boolean irisShaderPack() {
        try {
            return IrisPasses.shaderPack();
        } catch (LinkageError | RuntimeException e) {
            return irisGone(e);
        }
    }

    private static boolean irisGone(Throwable e) {
        iris = false;
        ArkSurvivalReturns.LOGGER.warn("Iris does not answer; creatures are drawn as without it", e);
        return false;
    }

    /**
     * Whether the eye is the origin of the space creatures are posed in: a perspective picture whose view
     * matrix only turns. True for the world as the player sees it; false for a creature in a menu
     * (orthographic) and for anything drawn through a view that also moves.
     */
    private static boolean worldPass() {
        if (RenderSystem.getProjectionType() != ProjectionType.PERSPECTIVE) return false;
        Matrix4fc view = RenderSystem.getModelViewMatrix();
        if (!affine(view) || Math.abs(view.m30()) + Math.abs(view.m31()) + Math.abs(view.m32()) > 1e-4f) return false;
        float xx = view.m00() * view.m00() + view.m01() * view.m01() + view.m02() * view.m02();
        float yy = view.m10() * view.m10() + view.m11() * view.m11() + view.m12() * view.m12();
        float zz = view.m20() * view.m20() + view.m21() * view.m21() + view.m22() * view.m22();
        float xy = view.m00() * view.m10() + view.m01() * view.m11() + view.m02() * view.m12();
        float xz = view.m00() * view.m20() + view.m01() * view.m21() + view.m02() * view.m22();
        float yz = view.m10() * view.m20() + view.m11() * view.m21() + view.m12() * view.m22();
        return Math.abs(xx - 1f) < 1e-3f && Math.abs(yy - 1f) < 1e-3f && Math.abs(zz - 1f) < 1e-3f
                && Math.abs(xy) < 1e-3f && Math.abs(xz) < 1e-3f && Math.abs(yz) < 1e-3f;
    }

    /** How large things show in the world view: from its projection, the picture's height and the field-of-view setting. */
    private static void view(CameraRenderState camera) {
        pixelsPerRadian = 0f;
        zoom = 1f;
        if (camera == null) return;
        Matrix4f projection = camera.projectionMatrix;
        // A perspective projection: its second row's scale is one over the tangent of half the angle seen.
        if (projection.m23() != -1f || projection.m33() != 0f || !(projection.m11() > 0f)) return;
        Minecraft minecraft = Minecraft.getInstance();
        pixelsPerRadian = projection.m11() * minecraft.getMainRenderTarget().height * 0.5f;
        double setting = Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5);
        // Above one when the view is wider than the setting, below when it is narrowed (a spyglass): distances count by it.
        if (setting > 0.0) zoom = (float) (1.0 / (projection.m11() * setting));
    }

    private static boolean affine(Matrix4fc matrix) {
        return matrix.m03() == 0f && matrix.m13() == 0f && matrix.m23() == 0f && matrix.m33() == 1f;
    }

    /** Something asked GeckoLib for the place of a bone or a locator in this pass; only its own drawing answers. */
    private static boolean listening(CreatureMesh mesh) {
        for (GeoBone bone : mesh.bones) if (bone.positionListeners != null) return true;
        for (GeoLocator locator : mesh.locators) if (locator.positionListeners != null) return true;
        return false;
    }

    /** The same question before renderPosed has handed the listeners to the bones. */
    private static boolean listeners(RenderPassInfo<?> pass) {
        try {
            return !((Map<?, ?>) BONE_LISTENERS.invoke(pass)).isEmpty() || !((Map<?, ?>) LOCATOR_LISTENERS.invoke(pass)).isEmpty();
        } catch (Throwable e) {
            return true;
        }
    }

    private static MethodHandle listeners(String field) {
        try {
            return MethodHandles.privateLookupIn(RenderPassInfo.class, MethodHandles.lookup()).findGetter(RenderPassInfo.class, field, Map.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ArkSurvivalReturns.LOGGER.warn("This GeckoLib keeps its position listeners elsewhere; creatures have their bones read in every pass", e);
            return null;
        }
    }

    /** Draws a creature a second time both ways into memory and keeps the largest difference per texture. */
    private static final class Probe {
        private final VertexRecorder geckoLib = new VertexRecorder(), plain = new VertexRecorder(), pushed = new VertexRecorder();
        private final CubeWriter writer = new CubeWriter();
        private final ConsumerSink sink = new ConsumerSink();
        private final Map<String, VertexRecorder.Difference> plainDifference = new TreeMap<>(), bulkDifference = new TreeMap<>(),
                keptDifference = new TreeMap<>();
        private VertexConsumer decoder;
        private int calls, compared, held;

        void check(String label, int kind, RenderPassInfo<?> pass, CreatureMesh mesh, CubeWriter.Posed posed, PoseStack.Pose pose,
                   int light, int overlay, int color) {
            if (calls++ % 8 != 0) return;
            if (kind == HELD) {
                // Bones of an earlier frame, by choice: nothing of this frame to compare them with.
                held++;
                return;
            }
            compared++;
            geckoLib.clear();
            plain.clear();
            if (kind == FRESH) CreatureDraw.geckoLib(pass, pose, geckoLib, light, overlay, color);
            else pass.renderPosed(() -> CreatureDraw.geckoLib(pass, pose, geckoLib, light, overlay, color));
            sink.begin(plain, color, overlay, light);
            writer.write(mesh, posed, pose.pose(), pose.normal(), sink, CubeWriter.ALL, null, false, 0f);
            sink.end();
            (kind == FRESH ? plainDifference : keptDifference).computeIfAbsent(label, key -> new VertexRecorder.Difference()).add(geckoLib, plain);
            QuadSink bulk = bulkSink();
            if (bulk == null || kind != FRESH) return;
            try {
                if (decoder == null) decoder = (VertexConsumer) Class.forName("dev.nez.arksurvivalreturns.client.draw.sodium.PushDecoder")
                        .getDeclaredConstructor(VertexRecorder.class).newInstance(pushed);
            } catch (ReflectiveOperationException | LinkageError e) {
                return;
            }
            pushed.clear();
            if (!bulk.begin(decoder, color, overlay, light)) return;
            writer.write(mesh, posed, pose.pose(), pose.normal(), bulk, CubeWriter.ALL, null, false, 0f);
            bulk.end();
            bulkDifference.computeIfAbsent(label, key -> new VertexRecorder.Difference()).add(geckoLib, pushed);
        }

        JsonObject report() {
            JsonObject report = new JsonObject();
            report.addProperty("creatures_compared", compared);
            report.addProperty("creatures_with_held_bones_not_compared", held);
            report.add("vertex_by_vertex", rows(plainDifference));
            report.add("bulk", rows(bulkDifference));
            report.add("bones_of_the_same_frame_drawn_again", rows(keptDifference));
            return report;
        }

        private static JsonObject rows(Map<String, VertexRecorder.Difference> differences) {
            JsonObject rows = new JsonObject();
            differences.forEach((label, difference) -> {
                JsonObject row = new JsonObject();
                row.addProperty("vertices", difference.vertices);
                row.addProperty("position", difference.position);
                row.addProperty("position_scaled", difference.positionScaled);
                row.addProperty("uv", difference.uv);
                row.addProperty("normal", difference.normal);
                row.addProperty("normal_bytes_off_by_more_than_one", difference.normalBytes);
                row.addProperty("colour_overlay_light_or_count", difference.other);
                rows.add(label, row);
            });
            return rows;
        }
    }

    private CreatureDraw() {}
}
