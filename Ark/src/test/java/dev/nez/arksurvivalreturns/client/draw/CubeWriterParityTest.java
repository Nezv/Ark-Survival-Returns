package dev.nez.arksurvivalreturns.client.draw;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.loading.definition.geometry.Geometry;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nez.arksurvivalreturns.client.draw.iris.IrisCheck;
import dev.nez.arksurvivalreturns.client.draw.sodium.BulkSink;
import dev.nez.arksurvivalreturns.client.draw.sodium.PushDecoder;
import net.irisshaders.iris.vertices.sodium.ModelToEntityVertexSerializer;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The correctness gate of Ark's creature writer: every shipped model, posed at three moments of every one of
 * its animation clips and in a few poses no clip has, is drawn by GeckoLib's own code and by the writer, and
 * the vertices are compared number by number: position, texture coordinate, normal, colour, overlay and light,
 * in the same order. The bulk path is read back from the memory it pushes, and the vertices written in the
 * format Iris gives a shader pack are compared, byte by byte, with what Iris's own serializer makes of the
 * plain ones. The table goes to build/reports/creature-draw-parity.txt.
 */
class CubeWriterParityTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/arksurvivalreturns");
    private static final Path MODELS = ASSETS.resolve("geckolib/models/entity"), CLIPS = ASSETS.resolve("geckolib/animations/entity");
    private static final String[] SKINS = {"ivory", "darken", "emerald", "midnight", "burgundy"};
    private static final String EYES_CALM = "ark_eye_calm", EYES_ALERT = "ark_eye_alert";
    /**
     * A float carries seven digits and a vertex goes through some fifty operations: positions may differ by a few
     * millionths of the creature's largest coordinate, normals by a few millionths of their length.
     */
    private static final double POSITION = 5e-6, NORMAL = 5e-6, WINDING_NORMAL = 2e-3;
    private static final int LIGHT = 0x00B000D0, OVERLAY = 0x000A0003, COLOR = 0xC8FFE0B0;

    private final VertexRecorder geckoLib = new VertexRecorder(), plain = new VertexRecorder(), pushed = new VertexRecorder(),
            winding = new VertexRecorder(), culled = new VertexRecorder();
    private final CubeWriter writer = new CubeWriter();
    private final CubeWriter.Posed posed = new CubeWriter.Posed();
    private final VertexRecorder moved = new VertexRecorder(), small = new VertexRecorder();
    /** Pixels a radian covers on a screen 1080 high with a view of 70 degrees. */
    private static final float PIXELS_PER_RADIAN = (float) (540.0 / Math.tan(Math.toRadians(35.0)));
    private final ConsumerSink sink = new ConsumerSink();
    private final BulkSink bulk = new BulkSink();
    private final PushDecoder decoder = new PushDecoder(pushed);
    private final IrisCheck shaderCheck = new IrisCheck(new ModelToEntityVertexSerializer());

    private static final class Row {
        int cubes, bones, poses, closed, culledFaces, allFaces, windingQuads, faces, facesAsked;
        final VertexRecorder.Difference plain = new VertexRecorder.Difference(), bulk = new VertexRecorder.Difference(),
                kept = new VertexRecorder.Difference();
        final IrisCheck.Difference shader = new IrisCheck.Difference();
        int smallCubes, smallChecked, farCubes;
        double windingNormal, windingSmall;
        String textures = "";
    }

    @Test void everyModelInEveryClipMatchesGeckoLib() throws Exception {
        Map<String, Row> rows = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> list = Files.list(MODELS)) {
            files = list.filter(path -> path.getFileName().toString().endsWith(".geo.json")).sorted().toList();
        }
        assertTrue(files.size() >= 40, "the shipped creature models are there: " + files.size());
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".geo.json", "");
            BakedGeoModel model;
            try (Reader reader = Files.newBufferedReader(file)) {
                model = Geometry.GSON.fromJson(reader, Geometry.class).bake(Identifier.fromNamespaceAndPath("arksurvivalreturns", "entity/" + name));
            }
            CreatureMesh mesh = CreatureMesh.of(model);
            assertNotNull(mesh, name + " holds something the writer does not draw");
            Row row = new Row();
            rows.put(name, row);
            row.cubes = mesh.cubeCount();
            row.bones = mesh.boneCount();
            for (int cube = 0; cube < mesh.cubes; cube++) if (mesh.closed(cube)) row.closed++;
            for (int face = 0; face < mesh.cubes * CreatureMesh.FACES; face++) {
                if ((mesh.present[face / CreatureMesh.FACES] >> face % CreatureMesh.FACES & 1) == 0) continue;
                row.faces++;
                if (mesh.handed[face] == 0) row.facesAsked++;
            }
            TextureCoverage opaque = new TextureCoverage(mesh);
            opaque.read(1024, 1024, (x, y) -> 255);

            Path clipFile = CLIPS.resolve(name + ".animation.json");
            JsonObject clips = Files.isRegularFile(clipFile)
                    ? JsonParser.parseString(Files.readString(clipFile)).getAsJsonObject().getAsJsonObject("animations") : new JsonObject();
            int pose = 0;
            for (Map.Entry<String, JsonElement> clip : clips.entrySet()) {
                JsonObject tracks = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
                double length = clip.getValue().getAsJsonObject().has("animation_length")
                        ? clip.getValue().getAsJsonObject().get("animation_length").getAsDouble() : 1.0;
                if (tracks == null) continue;
                for (double moment : new double[]{0.13, 0.5, 0.87}) {
                    List<BoneSnapshot> snapshots = pose(model, tracks, moment * length);
                    hideEyes(model, snapshots, pose % 2 == 0);
                    compare(name, row, model, mesh, opaque, snapshots, pose++);
                }
            }
            // Poses no clip has: at rest, and bones mirrored, squashed, scaled to nothing, hidden.
            compare(name, row, model, mesh, opaque, List.of(), pose++);
            compare(name, row, model, mesh, opaque, odd(model, 0), pose++);
            compare(name, row, model, mesh, opaque, odd(model, 1), pose++);
            row.poses = pose;
            row.textures = textures(name, mesh);
        }

        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%-18s %5s %5s %5s %10s %10s %10s %10s %9s %10s %8s  %s%n",
                "model", "bones", "cubes", "poses", "vertices", "position", "scaled", "normal", "bulk norm", "winding n", "far side", "closed cubes not opaque, per texture; kept bones moved on (scaled); cubes under a pixel at 90 to 450 blocks"));
        double worstPosition = 0, worstNormal = 0;
        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            Row row = entry.getValue();
            table.append(String.format(Locale.ROOT, "%-18s %5d %5d %5d %10d %10.2e %10.2e %10.2e %9s %10.2e %7.1f%%  %s%n", entry.getKey(), row.bones,
                    row.cubes, row.poses, row.plain.vertices, Math.max(row.plain.position, row.bulk.position),
                    Math.max(row.plain.positionScaled, row.bulk.positionScaled), row.plain.normal,
                    row.bulk.normalBytes == 0 ? "same byte" : row.bulk.normalBytes + " off",
                    row.windingNormal, 100.0 * row.culledFaces / Math.max(1, row.allFaces),
                    row.textures + String.format(Locale.ROOT, "; %.2e; %.0f%%", row.kept.positionScaled, 100.0 * row.smallCubes / Math.max(1, row.farCubes))));
            worstPosition = Math.max(worstPosition, Math.max(row.plain.position, row.bulk.position));
            worstNormal = Math.max(worstNormal, row.plain.normal);
        }
        IrisCheck.Difference shader = new IrisCheck.Difference();
        int faces = 0, facesAsked = 0;
        for (Row row : rows.values()) {
            faces += row.faces;
            facesAsked += row.facesAsked;
            shader.vertices += row.shader.vertices;
            shader.quads += row.shader.quads;
            shader.counts += row.shader.counts;
            shader.plain += row.shader.plain;
            shader.drawn += row.shader.drawn;
            shader.middle += row.shader.middle;
            shader.tangentNotSame += row.shader.tangentNotSame;
            shader.tangentOff += row.shader.tangentOff;
            shader.flat += row.shader.flat;
        }
        table.append(String.format(Locale.ROOT, "in the shader pack's format, against Iris's serializer on the plain vertices: %s; "
                + "%d of the models' %d faces have their tangent's side asked of Iris at run time%n", shader, facesAsked, faces));
        table.append(String.format(Locale.ROOT, "largest deviation from GeckoLib: position %.2e blocks (scaled: against the largest coordinate of the "
                + "creature, a float's seven digits), normal %.2e of its length; texture coordinates, "
                + "colour, overlay, light, count and order identical. bulk: read back from the pushed memory, its normal within one step of the "
                + "byte GeckoLib's would be stored as. winding n: against the normal Iris takes from the corners. far side: faces left out with "
                + "hidden faces on, in these poses.%n", worstPosition, worstNormal));
        Path report = Path.of("build/reports/creature-draw-parity.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, table);
        System.out.print(table);

        int smallChecked = 0;
        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            Row row = entry.getValue();
            String name = entry.getKey();
            assertTrue(row.plain.vertices > 0, name + " drew nothing");
            assertTrue(row.plain.within(POSITION, 0.0, NORMAL), name + " vertex by vertex: " + row.plain);
            assertEquals(row.plain.vertices, row.bulk.vertices, name + " bulk vertex count");
            assertTrue(row.bulk.other == 0 && row.bulk.positionScaled <= POSITION && row.bulk.uv == 0.0 && row.bulk.normalBytes == 0, name + " bulk: " + row.bulk);
            assertTrue(row.windingNormal <= WINDING_NORMAL, name + " winding normals: " + row.windingNormal);
            assertTrue(row.windingSmall <= 4.0, name + " winding normals of small quads, in units of their rounding: " + row.windingSmall);
            assertTrue(row.windingQuads > 0, name + " checked no winding normal");
            assertTrue(row.kept.within(POSITION, 0.0, NORMAL), name + " kept bones: " + row.kept);
            assertTrue(row.shader.same() && row.shader.quads > 0, name + " in the shader pack's format: " + row.shader);
            smallChecked += row.smallChecked;
        }
        assertTrue(smallChecked > 1000, "cubes under a pixel were checked: " + smallChecked);
    }

    private void compare(String name, Row row, BakedGeoModel model, CreatureMesh mesh, TextureCoverage opaque, List<BoneSnapshot> snapshots, int pose) {
        // The pose an entity renderer leaves: its place from the camera, its size, the way it faces, GeckoLib's lift.
        PoseStack stack = new PoseStack();
        float distance = 4f + pose % 5 * 9f;
        stack.translate(distance * 0.6f, -1.6f - pose % 3, -distance);
        float size = 0.6f + pose % 4 * 0.7f;
        stack.scale(size, size, size);
        stack.mulPose(Axis.YP.rotationDegrees(180f - 37f * pose));
        if (pose % 3 == 1) stack.mulPose(Axis.XP.rotationDegrees(-25f));
        stack.translate(0f, 0.01f, 0f);
        stack.mulPose(Axis.YP.rotationDegrees(180f));
        PoseStack.Pose root = stack.last().copy();
        for (BoneSnapshot snapshot : snapshots) snapshot.apply();
        try {
            geckoLib.clear();
            plain.clear();
            pushed.clear();
            winding.clear();
            culled.clear();
            RenderPassInfo<GeoRenderState> pass = new RenderPassInfo<>(null, null, stack, model, null, true) {};
            model.render(pass, geckoLib, LIGHT, OVERLAY, COLOR);
            assertEquals(root.pose(), stack.last().pose(), "GeckoLib left the pose stack as it found it");

            writer.pose(mesh, posed);
            sink.begin(plain, COLOR, OVERLAY, LIGHT);
            writer.write(mesh, posed, root.pose(), root.normal(), sink, CubeWriter.ALL, null, false, 0f);
            sink.end();
            row.plain.add(geckoLib, plain);

            assertTrue(bulk.begin(decoder, COLOR, OVERLAY, LIGHT));
            writer.write(mesh, posed, root.pose(), root.normal(), bulk, CubeWriter.ALL, null, false, 0f);
            bulk.end();
            row.bulk.add(geckoLib, pushed);

            shaderCheck.compare(mesh, posed, root.pose(), root.normal(), CubeWriter.ALL, null, 0f, COLOR, OVERLAY, LIGHT, row.shader);
            shaderCheck.compare(mesh, posed, root.pose(), root.normal(), CubeWriter.FAR_SIDES, opaque, 0f, COLOR, OVERLAY, LIGHT, row.shader);

            sink.begin(winding, COLOR, OVERLAY, LIGHT);
            writer.write(mesh, posed, root.pose(), root.normal(), sink, CubeWriter.ALL, null, true, 0f);
            sink.end();
            windingNormals(name, row);

            long before = writer.facesSkipped;
            sink.begin(culled, COLOR, OVERLAY, LIGHT);
            writer.write(mesh, posed, root.pose(), root.normal(), sink, CubeWriter.FAR_SIDES, opaque, false, 0f);
            sink.end();
            boolean hidden = snapshots.stream().anyMatch(snapshot -> snapshot.isHidden() || snapshot.areChildrenHidden());
            farSides(name, row, mesh, (int) (writer.facesSkipped - before), hidden);

            // Far away, where boxes fall under a pixel: the same bones, placed there.
            PoseStack far = new PoseStack();
            far.translate(30f + pose % 5 * 45f, -8f, -(90f + pose % 7 * 60f));
            far.scale(size, size, size);
            far.mulPose(Axis.YP.rotationDegrees(63f * pose));
            moved.clear();
            small.clear();
            sink.begin(moved, COLOR, OVERLAY, LIGHT);
            writer.write(mesh, posed, far.last().pose(), far.last().normal(), sink, CubeWriter.ALL, null, false, 0f);
            sink.end();
            long tooSmall = writer.cubesTooSmall;
            sink.begin(small, COLOR, OVERLAY, LIGHT);
            writer.write(mesh, posed, far.last().pose(), far.last().normal(), sink, CubeWriter.ALL, null, false, PIXELS_PER_RADIAN * PIXELS_PER_RADIAN);
            sink.end();
            shaderCheck.compare(mesh, posed, far.last().pose(), far.last().normal(), CubeWriter.ALL, null, PIXELS_PER_RADIAN * PIXELS_PER_RADIAN,
                    COLOR, OVERLAY, LIGHT, row.shader);
            boolean even = snapshots.stream().allMatch(snapshot -> snapshot.getScaleX() == snapshot.getScaleY() && snapshot.getScaleY() == snapshot.getScaleZ());
            smallBoxes(name, row, mesh, (int) (writer.cubesTooSmall - tooSmall), hidden, even);

            // The bones kept, the entity moved on: what a later pass or a later frame draws from them is what GeckoLib
            // draws there with the same animation.
            stack.translate(0.37f, 0.11f, -0.52f);
            stack.mulPose(Axis.YP.rotationDegrees(11f));
            geckoLib.clear();
            moved.clear();
            model.render(pass, geckoLib, LIGHT, OVERLAY, COLOR);
            sink.begin(moved, COLOR, OVERLAY, LIGHT);
            writer.write(mesh, posed, stack.last().pose(), stack.last().normal(), sink, CubeWriter.ALL, null, false, 0f);
            sink.end();
            row.kept.add(geckoLib, moved);
        } finally {
            for (BoneSnapshot snapshot : snapshots) snapshot.cleanup();
        }
    }

    /** With winding normals the positions stay and every normal is the one Iris takes from the quad's corners. */
    private void windingNormals(String name, Row row) {
        assertEquals(geckoLib.vertices, winding.vertices, name);
        float[] a = geckoLib.floats, b = winding.floats;
        for (int quad = 0; quad < geckoLib.vertices / 4; quad++) {
            int at = quad * 4 * VertexRecorder.FLOATS;
            double[] first = new double[3], second = new double[3];
            for (int k = 0; k < 3; k++) {
                first[k] = (double) a[at + 2 * VertexRecorder.FLOATS + k] - a[at + k];
                second[k] = (double) a[at + 3 * VertexRecorder.FLOATS + k] - a[at + VertexRecorder.FLOATS + k];
            }
            double[] cross = {first[1] * second[2] - first[2] * second[1], first[2] * second[0] - first[0] * second[2], first[0] * second[1] - first[1] * second[0]};
            double length = Math.sqrt(cross[0] * cross[0] + cross[1] * cross[1] + cross[2] * cross[2]);
            double diagonals = Math.sqrt(first[0] * first[0] + first[1] * first[1] + first[2] * first[2]) * Math.sqrt(second[0] * second[0] + second[1] * second[1] + second[2] * second[2]);
            if (!(length > 1e-9) || length < 1e-3 * diagonals) continue;   // a quad squashed to a line has no normal to compare
            // The corners are floats: a quad that is small next to its distance from the origin has a normal that
            // rounding alone tilts. Those are held to what the rounding allows, the others to the limit.
            double reach = 1.0, edge = Double.MAX_VALUE;
            for (int vertex = 0; vertex < 4; vertex++) {
                double side = 0;
                for (int k = 0; k < 3; k++) {
                    reach = Math.max(reach, Math.abs(a[at + vertex * VertexRecorder.FLOATS + k]));
                    double step = (double) a[at + (vertex + 1) % 4 * VertexRecorder.FLOATS + k] - a[at + vertex * VertexRecorder.FLOATS + k];
                    side += step * step;
                }
                edge = Math.min(edge, Math.sqrt(side));
            }
            double rounding = 8 * reach * Math.ulp(1f) / edge, deviation = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                for (int k = 0; k < 3; k++) deviation = Math.max(deviation, Math.abs(cross[k] / length - b[at + vertex * VertexRecorder.FLOATS + 5 + k]));
            }
            row.windingQuads++;
            if (rounding < WINDING_NORMAL / 4) row.windingNormal = Math.max(row.windingNormal, deviation);
            else row.windingSmall = Math.max(row.windingSmall, deviation / rounding);
        }
    }

    /**
     * Without far sides: what is left is what was drawn before, in order, less whole faces; every face left out
     * belongs to a closed box, has the eye more than the margin behind it and a side of its box with the eye more
     * than the margin in front; and no such face is kept.
     */
    private void farSides(String name, Row row, CreatureMesh mesh, int skipped, boolean hidden) {
        float[] all = plain.floats, kept = culled.floats;
        int quads = plain.vertices / 4, next = 0, left = 0, quad = 0;
        // GeckoLib skips the cubes of a hidden bone and the recording does not say which, so the faces are matched
        // to their cubes only while every bone is shown. A pose that hides bones is checked for order and count.
        for (; hidden && quad < quads; quad++) {
            int at = quad * 4 * VertexRecorder.FLOATS;
            boolean same = next < culled.vertices / 4;
            for (int i = 0; same && i < 4 * VertexRecorder.FLOATS; i++) same = Float.compare(all[at + i], kept[next * 4 * VertexRecorder.FLOATS + i]) == 0;
            if (same) next++;
            else left++;
        }
        for (int cube = 0; cube < mesh.cubes && quad < quads; cube++) {
            int faces = Integer.bitCount(mesh.present[cube] & 0xFF);
            if (faces == 0) continue;
            double[] centre = new double[3];
            for (int i = 0; i < faces * 4; i++) for (int k = 0; k < 3; k++) centre[k] += all[(quad * 4 + i) * VertexRecorder.FLOATS + k] / (faces * 4.0);
            boolean clear = false, nearlyClear = false;
            double[] behind = new double[faces];
            for (int face = 0; face < faces; face++) {
                int at = (quad + face) * 4 * VertexRecorder.FLOATS;
                double[] middle = new double[3], first = new double[3], second = new double[3];
                for (int k = 0; k < 3; k++) {
                    for (int vertex = 0; vertex < 4; vertex++) middle[k] += all[at + vertex * VertexRecorder.FLOATS + k] / 4.0;
                    first[k] = (double) all[at + VertexRecorder.FLOATS + k] - all[at + k];
                    second[k] = (double) all[at + 3 * VertexRecorder.FLOATS + k] - all[at + k];
                }
                double[] out = {first[1] * second[2] - first[2] * second[1], first[2] * second[0] - first[0] * second[2], first[0] * second[1] - first[1] * second[0]};
                double length = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
                double side = 0, eye = 0;
                for (int k = 0; k < 3; k++) {
                    side += out[k] * (middle[k] - centre[k]);
                    eye += out[k] * -middle[k];
                }
                // How far the eye, the origin, is in front of the face, measured outwards from the box.
                behind[face] = length > 0 ? -(side < 0 ? -eye : eye) / length : 0;
                if (-behind[face] > CubeWriter.EYE_MARGIN + 1e-3) clear = true;
                if (-behind[face] > CubeWriter.EYE_MARGIN - 1e-3) nearlyClear = true;
            }
            for (int face = 0; face < faces; face++) {
                int at = (quad + face) * 4 * VertexRecorder.FLOATS;
                boolean same = next < culled.vertices / 4;
                for (int i = 0; same && i < 4 * VertexRecorder.FLOATS; i++) same = Float.compare(all[at + i], kept[next * 4 * VertexRecorder.FLOATS + i]) == 0;
                if (same) {
                    next++;
                    if (mesh.closed(cube) && clear)
                        assertFalse(behind[face] > CubeWriter.EYE_MARGIN + 1e-3, name + ": a far side was drawn, " + behind[face] + " behind");
                } else {
                    left++;
                    assertTrue(mesh.closed(cube), name + ": a face of an open cube was left out");
                    assertTrue(behind[face] > CubeWriter.EYE_MARGIN - 1e-3, name + ": a face only " + behind[face] + " behind the eye was left out");
                    assertTrue(nearlyClear, name + ": a face of a box the eye is not clear of was left out");
                }
            }
            quad += faces;
        }
        assertEquals(culled.vertices / 4, next, name + ": the faces kept are not the faces drawn before, in order");
        assertEquals(skipped, left, name + ": faces left out");
        row.culledFaces += left;
        row.allFaces += quads;
    }

    /**
     * With a limit of one pixel: what is left are the faces drawn before, in order, less whole cubes, and a cube
     * left out is smaller than a pixel across, even measured generously (its whole diagonal, from its farthest corner).
     */
    private void smallBoxes(String name, Row row, CreatureMesh mesh, int skipped, boolean hidden, boolean even) {
        float[] all = moved.floats, kept = small.floats;
        int quads = moved.vertices / 4, next = 0, quad = 0, cubesOut = 0;
        for (; hidden && quad < quads; quad++) {
            int at = quad * 4 * VertexRecorder.FLOATS;
            boolean same = next < small.vertices / 4;
            for (int i = 0; same && i < 4 * VertexRecorder.FLOATS; i++) same = Float.compare(all[at + i], kept[next * 4 * VertexRecorder.FLOATS + i]) == 0;
            if (same) next++;
        }
        for (int cube = 0; cube < mesh.cubes && quad < quads; cube++) {
            int faces = Integer.bitCount(mesh.present[cube] & 0xFF);
            if (faces == 0) continue;
            boolean same = next + faces <= small.vertices / 4;
            for (int i = 0; same && i < faces * 4 * VertexRecorder.FLOATS; i++)
                same = Float.compare(all[quad * 4 * VertexRecorder.FLOATS + i], kept[next * 4 * VertexRecorder.FLOATS + i]) == 0;
            if (same) {
                next += faces;
            } else {
                cubesOut++;
                double diagonal = 0, far = 0;
                for (int i = 0; i < faces * 4; i++) {
                    int a = (quad * 4 + i) * VertexRecorder.FLOATS;
                    far = Math.max(far, Math.sqrt((double) all[a] * all[a] + (double) all[a + 1] * all[a + 1] + (double) all[a + 2] * all[a + 2]));
                    for (int j = 0; j < i; j++) {
                        int b = (quad * 4 + j) * VertexRecorder.FLOATS;
                        diagonal = Math.max(diagonal, Math.sqrt(Math.pow((double) all[a] - all[b], 2) + Math.pow((double) all[a + 1] - all[b + 1], 2)
                                + Math.pow((double) all[a + 2] - all[b + 2], 2)));
                    }
                }
                // A bone stretched unevenly under a turned parent is sheared; its cubes are measured by the bone's
                // longest axis, which may come out a little short there. Checked where no bone is stretched unevenly.
                if (even) {
                    row.smallChecked++;
                    assertTrue(diagonal * PIXELS_PER_RADIAN / far < 1.0 + 1e-3, name + ": a cube " + diagonal * PIXELS_PER_RADIAN / far + " pixels across was left out");
                }
            }
            quad += faces;
        }
        assertEquals(small.vertices / 4, next, name + ": the faces kept are not the faces drawn before, in order");
        if (!hidden) assertEquals(skipped, cubesOut, name + ": cubes left out as too small");
        row.smallCubes += skipped;
        row.farCubes += quads / 6;
    }

    /** The clip's bones at a moment: straight interpolation between its keys, in GeckoLib's units and signs. */
    private List<BoneSnapshot> pose(BakedGeoModel model, JsonObject tracks, double time) {
        List<BoneSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, JsonElement> track : tracks.entrySet()) {
            GeoBone bone = model.getBone(track.getKey()).orElse(null);
            if (bone == null) continue;
            JsonObject channels = track.getValue().getAsJsonObject();
            BoneSnapshot snapshot = BoneSnapshot.create(bone);
            if (channels.has("rotation")) {
                float[] value = at(channels.get("rotation"), time);
                snapshot.setRotation((float) -Math.toRadians(value[0]), (float) -Math.toRadians(value[1]), (float) Math.toRadians(value[2]));
            }
            if (channels.has("position")) {
                float[] value = at(channels.get("position"), time);
                snapshot.setTranslation(value[0], value[1], value[2]);
            }
            if (channels.has("scale")) {
                float[] value = at(channels.get("scale"), time);
                snapshot.setScale(value[0], value[1], value[2]);
            }
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    /** One eye set at a time, as CreatureRenderer.adjustModelBonesForRender shows them. */
    private void hideEyes(BakedGeoModel model, List<BoneSnapshot> snapshots, boolean alert) {
        for (String name : new String[]{EYES_CALM, EYES_ALERT}) {
            GeoBone bone = model.getBone(name).orElse(null);
            if (bone == null) continue;
            boolean hide = name.equals(EYES_CALM) == alert;
            BoneSnapshot snapshot = snapshots.stream().filter(candidate -> candidate.getBone() == bone).findFirst().orElseGet(() -> {
                BoneSnapshot created = BoneSnapshot.create(bone);
                snapshots.add(created);
                return created;
            });
            snapshot.skipRender(hide).skipChildrenRender(hide);
        }
    }

    /** Bones as no clip leaves them: mirrored, squashed, scaled to nothing, hidden with and without their children. */
    private List<BoneSnapshot> odd(BakedGeoModel model, int variant) {
        List<BoneSnapshot> snapshots = new ArrayList<>();
        List<GeoBone> bones = new ArrayList<>(model.boneLookup().get().values());
        bones.sort((a, b) -> a.name().compareTo(b.name()));
        for (int i = 0; i < bones.size(); i++) {
            BoneSnapshot snapshot = BoneSnapshot.create(bones.get(i));
            switch ((i + variant * 3) % 11) {
                case 0 -> snapshot.setScale(-1f, 1f, 1f);
                case 1 -> snapshot.setScale(0.5f, 1.75f, 1.2f).setRotation(0.3f, 0f, -0.2f);
                case 2 -> snapshot.setScale(-1.5f, -1.5f, -1.5f).setTranslation(1f, -2f, 3f);
                case 3 -> snapshot.setRotation(0f, 2.5f, 0f);
                case 4 -> snapshot.setRotation(-1.2f, 0f, 0f).setTranslation(0f, 4f, 0f);
                case 5 -> { if (variant == 1 && i % 5 == 0) snapshot.setScale(0f, 0f, 0f); }
                case 6 -> { if (variant == 1 && i % 7 == 0) snapshot.skipRender(true); }
                case 7 -> { if (variant == 1 && i % 9 == 0) snapshot.skipChildrenRender(true); }
                case 8 -> snapshot.setScale(2f, 2f, 2f);
                default -> { continue; }
            }
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private static float[] at(JsonElement track, double time) {
        if (!track.isJsonObject()) return vector(track);
        JsonObject keys = track.getAsJsonObject();
        if (keys.has("post") || keys.has("vector")) return vector(track);
        String before = null, after = null;
        double beforeTime = Double.NEGATIVE_INFINITY, afterTime = Double.POSITIVE_INFINITY;
        for (String key : keys.keySet()) {
            double moment = Double.parseDouble(key);
            if (moment <= time && moment > beforeTime) { beforeTime = moment; before = key; }
            if (moment >= time && moment < afterTime) { afterTime = moment; after = key; }
        }
        if (before == null) return vector(keys.get(after));
        if (after == null || afterTime == beforeTime) return vector(keys.get(before));
        float[] a = vector(keys.get(before)), b = vector(keys.get(after));
        float share = (float) ((time - beforeTime) / (afterTime - beforeTime));
        return new float[]{a[0] + (b[0] - a[0]) * share, a[1] + (b[1] - a[1]) * share, a[2] + (b[2] - a[2]) * share};
    }

    private static float[] vector(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            return vector(object.has("post") ? object.get("post") : object.get("vector"));
        }
        if (value.isJsonPrimitive()) {
            float single = value.getAsFloat();
            return new float[]{single, single, single};
        }
        return new float[]{value.getAsJsonArray().get(0).getAsFloat(), value.getAsJsonArray().get(1).getAsFloat(), value.getAsJsonArray().get(2).getAsFloat()};
    }

    /** Per texture of the model: how many of its closed cubes are not opaque all over, so keep all their sides. */
    private static String textures(String name, CreatureMesh mesh) throws IOException {
        StringBuilder found = new StringBuilder();
        List<String> files = new ArrayList<>();
        if (Files.isRegularFile(ASSETS.resolve("textures/entity/" + name + ".png"))) files.add(name);
        for (String skin : SKINS) if (Files.isRegularFile(ASSETS.resolve("textures/entity/" + name + "_" + skin + ".png"))) files.add(name + "_" + skin);
        int closed = 0;
        for (int cube = 0; cube < mesh.cubes; cube++) if (mesh.closed(cube)) closed++;
        for (String file : files) {
            BufferedImage image = ImageIO.read(ASSETS.resolve("textures/entity/" + file + ".png").toFile());
            TextureCoverage coverage = new TextureCoverage(mesh);
            coverage.read(image.getWidth(), image.getHeight(), (x, y) -> image.getRGB(x, y) >>> 24);
            int open = 0;
            for (int cube = 0; cube < mesh.cubes; cube++) if (mesh.closed(cube) && (coverage.opaqueCubes[cube >> 6] >>> cube & 1L) == 0) open++;
            if (open > 0) found.append(file).append(' ').append(open).append('/').append(closed).append("  ");
        }
        return found.isEmpty() ? "none of " + files.size() : found.toString().trim();
    }

    /**
     * The eyes layer draws the whole model with a texture that is transparent but for the irises. Without the
     * empty faces, what is left are the faces drawn before, in order, each with a texel that is not transparent,
     * and every face left out has none.
     */
    @Test void theEyesLayerKeepsTheFacesWithSomethingOnThem() throws Exception {
        List<Path> files;
        try (Stream<Path> list = Files.list(ASSETS.resolve("textures/entity"))) {
            files = list.filter(path -> path.getFileName().toString().endsWith("_eyes.png")).sorted().toList();
        }
        assertFalse(files.isEmpty(), "the eye textures are there");
        StringBuilder table = new StringBuilder();
        for (Path file : files) {
            String name = file.getFileName().toString().replace("_eyes.png", "");
            BakedGeoModel model;
            try (Reader reader = Files.newBufferedReader(MODELS.resolve(name + ".geo.json"))) {
                model = Geometry.GSON.fromJson(reader, Geometry.class).bake(Identifier.fromNamespaceAndPath("arksurvivalreturns", "entity/" + name));
            }
            CreatureMesh mesh = CreatureMesh.of(model);
            BufferedImage image = ImageIO.read(file.toFile());
            TextureCoverage coverage = new TextureCoverage(mesh);
            coverage.read(image.getWidth(), image.getHeight(), (x, y) -> image.getRGB(x, y) >>> 24);
            PoseStack stack = new PoseStack();
            stack.translate(1f, -2f, -9f);
            stack.mulPose(Axis.YP.rotationDegrees(33f));
            plain.clear();
            culled.clear();
            writer.pose(mesh, posed);
            sink.begin(plain, COLOR, OVERLAY, LIGHT);
            writer.write(mesh, posed, stack.last().pose(), stack.last().normal(), sink, CubeWriter.ALL, null, false, 0f);
            sink.end();
            sink.begin(culled, COLOR, OVERLAY, LIGHT);
            writer.write(mesh, posed, stack.last().pose(), stack.last().normal(), sink, CubeWriter.EMPTY_FACES, coverage, false, 0f);
            sink.end();
            int next = 0, kept = culled.vertices / 4;
            for (int quad = 0; quad < plain.vertices / 4; quad++) {
                int at = quad * 4 * VertexRecorder.FLOATS;
                boolean same = next < kept;
                for (int i = 0; same && i < 4 * VertexRecorder.FLOATS; i++) same = Float.compare(plain.floats[at + i], culled.floats[next * 4 * VertexRecorder.FLOATS + i]) == 0;
                // The texels under the face, from the texture coordinates of the vertices themselves.
                float u0 = 1f, u1 = 0f, v0 = 1f, v1 = 0f;
                for (int vertex = 0; vertex < 4; vertex++) {
                    u0 = Math.min(u0, plain.floats[at + vertex * VertexRecorder.FLOATS + 3]);
                    u1 = Math.max(u1, plain.floats[at + vertex * VertexRecorder.FLOATS + 3]);
                    v0 = Math.min(v0, plain.floats[at + vertex * VertexRecorder.FLOATS + 4]);
                    v1 = Math.max(v1, plain.floats[at + vertex * VertexRecorder.FLOATS + 4]);
                }
                boolean something = false;
                for (int y = Math.round(v0 * image.getHeight()) - 1; y <= Math.round(v1 * image.getHeight()); y++) {
                    for (int x = Math.round(u0 * image.getWidth()) - 1; x <= Math.round(u1 * image.getWidth()); x++) {
                        boolean inside = x >= Math.round(u0 * image.getWidth()) && x < Math.round(u1 * image.getWidth())
                                && y >= Math.round(v0 * image.getHeight()) && y < Math.round(v1 * image.getHeight());
                        if (inside && x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && image.getRGB(x, y) >>> 24 != 0) something = true;
                    }
                }
                if (same) next++;
                else assertFalse(something, name + ": a face with something on it was left out, quad " + quad);
            }
            assertEquals(kept, next, name + ": the faces kept are not faces drawn before, in order");
            assertTrue(kept > 0 && kept < plain.vertices / 4 / 10, name + ": the irises are a few faces, " + kept + " of " + plain.vertices / 4);
            table.append(String.format(Locale.ROOT, "%-18s eyes layer: %d of %d faces carry a texel%n", name, kept, plain.vertices / 4));
        }
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/creature-draw-eyes.txt"), table);
        System.out.print(table);
    }

    @Test void coverageReadsTheTexelsUnderAFace() throws Exception {
        BakedGeoModel model;
        try (Reader reader = Files.newBufferedReader(MODELS.resolve("tyrannosaurus.geo.json"))) {
            model = Geometry.GSON.fromJson(reader, Geometry.class).bake(Identifier.fromNamespaceAndPath("arksurvivalreturns", "entity/tyrannosaurus"));
        }
        CreatureMesh mesh = CreatureMesh.of(model);
        TextureCoverage opaque = new TextureCoverage(mesh), clear = new TextureCoverage(mesh), holed = new TextureCoverage(mesh);
        assertFalse(opaque.ready);
        opaque.read(512, 528, (x, y) -> 255);
        clear.read(512, 528, (x, y) -> 0);
        // One transparent texel in the middle of the first face of the first cube.
        float[] uv = mesh.uv;
        float u = (uv[0] + uv[2] + uv[4] + uv[6]) / 4f, v = (uv[1] + uv[3] + uv[5] + uv[7]) / 4f;
        int holeX = (int) (u * 512), holeY = (int) (v * 528);
        holed.read(512, 528, (x, y) -> x == holeX && y == holeY ? 0 : 255);
        assertTrue(opaque.ready && clear.ready && holed.ready);
        assertEquals(mesh.cubes, opaque.opaqueCount());
        assertEquals(0, opaque.emptyCount());
        assertEquals(0, clear.opaqueCount());
        assertEquals(mesh.cubes * 6, clear.emptyCount());
        assertEquals(0L, holed.opaqueCubes[0] & 1L, "the cube with the hole is not opaque");
        assertTrue(holed.opaqueCount() >= mesh.cubes - 8 && holed.opaqueCount() < mesh.cubes, "only the cubes sharing that texel: " + holed.opaqueCount());
        assertEquals(0, holed.emptyCount());
    }
}
