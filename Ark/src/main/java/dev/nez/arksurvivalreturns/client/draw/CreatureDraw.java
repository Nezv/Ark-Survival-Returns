package dev.nez.arksurvivalreturns.client.draw;

import java.util.Map;
import java.util.TreeMap;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoLocator;
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
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Draws Ark's creatures with {@link CubeWriter} instead of GeckoLib's own geometry code. GeckoLib still loads
 * the model, runs the animation and poses the bones; this takes the vertex writing: in bulk through Sodium
 * where it is installed, and without the sides of a box that the box itself hides. The client settings
 * (creatureFastDrawing, creatureHiddenFaces) switch it; where a case is not this writer's, GeckoLib draws.
 */
public final class CreatureDraw {
    /** -Darksurvivalreturns.creatureDraw=geckolib, allfaces or ark: fixes the two client settings for a comparison. */
    private static final String FORCED = System.getProperty("arksurvivalreturns.creatureDraw", "");
    /** -Darksurvivalreturns.creatureDraw.verify=true: every eighth creature is also drawn the old way and compared. */
    private static final boolean VERIFY = Boolean.getBoolean("arksurvivalreturns.creatureDraw.verify");
    private static final CubeWriter WRITER = new CubeWriter();
    private static final QuadSink PLAIN = new ConsumerSink();
    private static QuadSink bulk;
    private static boolean sodium = ModList.get().isLoaded("sodium"), iris = ModList.get().isLoaded("iris");
    private static long bulkDraws, plainDraws, geckoLibDraws, farSideDraws;
    private static Probe probe;

    public static boolean enabled() {
        return FORCED.isEmpty() ? NighttimeClientConfig.CREATURE_FAST_DRAWING.get() : !FORCED.equals("geckolib");
    }

    private static boolean skipsFaces() {
        return FORCED.isEmpty() ? NighttimeClientConfig.CREATURE_HIDDEN_FACES.get() : FORCED.equals("ark");
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
        report.addProperty("mode", !enabled() ? "geckolib" : skipsFaces() ? "ark" : "allfaces");
        report.addProperty("draws_bulk", bulkDraws);
        report.addProperty("draws_vertex_by_vertex", plainDraws);
        report.addProperty("draws_left_to_geckolib", geckoLibDraws);
        report.addProperty("draws_without_far_sides", farSideDraws);
        report.addProperty("faces_written", WRITER.facesWritten);
        report.addProperty("faces_left_out", WRITER.facesSkipped);
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
            // The bones carry their animated state, and the hidden eye set, only inside renderPosed.
            pass.renderPosed(this);
            this.pose = null;
            this.consumer = null;
        }

        @Override public void run() {
            Matrix4f matrix = pose.pose();
            if (!affine(matrix) || listening(mesh)) {
                geckoLibDraws++;
                geckoLib(pass, pose, consumer, light, overlay, color);
                return;
            }
            boolean shadow = iris && irisShadow(), pack = iris && irisShaderPack(), world = !shadow && worldPass();
            int skip = CubeWriter.ALL;
            // Faces are left out only on the way into a vertex buffer of the picture. Anything else that takes a
            // creature's vertices (an outline, Physics Mod building a ragdoll from them) gets every face.
            if (coverage != null && coverage.ready && consumer.getClass() == BufferBuilder.class) {
                if (emptyFacesDrawNothing) {
                    // A shader pack may write more than colour for a transparent texel; all faces there.
                    if (!pack) skip = CubeWriter.EMPTY_FACES;
                } else if (!blending && world) {
                    skip = CubeWriter.FAR_SIDES;
                    farSideDraws++;
                }
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
                WRITER.write(mesh, matrix, pose.normal(), sink, skip, coverage, pack && (world || shadow));
            } finally {
                sink.end();
            }
            if (VERIFY) {
                if (probe == null) probe = new Probe();
                probe.check(texture.getPath(), pass, mesh, pose, light, overlay, color);
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

    private static boolean affine(Matrix4fc matrix) {
        return matrix.m03() == 0f && matrix.m13() == 0f && matrix.m23() == 0f && matrix.m33() == 1f;
    }

    /** Something asked GeckoLib for the place of a bone or a locator in this pass; only its own drawing answers. */
    private static boolean listening(CreatureMesh mesh) {
        for (GeoBone bone : mesh.bones) if (bone.positionListeners != null) return true;
        for (GeoLocator locator : mesh.locators) if (locator.positionListeners != null) return true;
        return false;
    }

    /** Draws a creature a second time both ways into memory and keeps the largest difference per texture. */
    private static final class Probe {
        private final VertexRecorder geckoLib = new VertexRecorder(), plain = new VertexRecorder(), pushed = new VertexRecorder();
        private final CubeWriter writer = new CubeWriter();
        private final ConsumerSink sink = new ConsumerSink();
        private final Map<String, VertexRecorder.Difference> plainDifference = new TreeMap<>(), bulkDifference = new TreeMap<>();
        private VertexConsumer decoder;
        private int calls;

        void check(String label, RenderPassInfo<?> pass, CreatureMesh mesh, PoseStack.Pose pose, int light, int overlay, int color) {
            if (calls++ % 8 != 0) return;
            geckoLib.clear();
            plain.clear();
            CreatureDraw.geckoLib(pass, pose, geckoLib, light, overlay, color);
            sink.begin(plain, color, overlay, light);
            writer.write(mesh, pose.pose(), pose.normal(), sink, CubeWriter.ALL, null, false);
            sink.end();
            plainDifference.computeIfAbsent(label, key -> new VertexRecorder.Difference()).add(geckoLib, plain);
            QuadSink bulk = bulkSink();
            if (bulk == null) return;
            try {
                if (decoder == null) decoder = (VertexConsumer) Class.forName("dev.nez.arksurvivalreturns.client.draw.sodium.PushDecoder")
                        .getDeclaredConstructor(VertexRecorder.class).newInstance(pushed);
            } catch (ReflectiveOperationException | LinkageError e) {
                return;
            }
            pushed.clear();
            if (!bulk.begin(decoder, color, overlay, light)) return;
            writer.write(mesh, pose.pose(), pose.normal(), bulk, CubeWriter.ALL, null, false);
            bulk.end();
            bulkDifference.computeIfAbsent(label, key -> new VertexRecorder.Difference()).add(geckoLib, pushed);
        }

        JsonObject report() {
            JsonObject report = new JsonObject();
            report.addProperty("creatures_compared", (calls + 7) / 8);
            report.add("vertex_by_vertex", rows(plainDifference));
            report.add("bulk", rows(bulkDifference));
            return report;
        }

        private static JsonObject rows(Map<String, VertexRecorder.Difference> differences) {
            JsonObject rows = new JsonObject();
            differences.forEach((label, difference) -> {
                JsonObject row = new JsonObject();
                row.addProperty("vertices", difference.vertices);
                row.addProperty("position", difference.position);
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
