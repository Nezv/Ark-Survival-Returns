package dev.nez.arksurvivalreturns.client.draw.iris;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nez.arksurvivalreturns.client.draw.CreatureDraw;
import dev.nez.arksurvivalreturns.client.draw.CreatureMesh;
import dev.nez.arksurvivalreturns.client.draw.CubeWriter;
import dev.nez.arksurvivalreturns.client.draw.TextureCoverage;
import dev.nez.arksurvivalreturns.client.draw.sodium.BulkSink;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The check of {@link IrisSink}: a creature is written the plain way and handed to Iris's own serializer, and
 * written the wide way, and the two are compared byte by byte: the plain vertex, what is being drawn, the
 * middle of the texture, the tangent. Used by the unit test of the writer and, in the client, by
 * -Darksurvivalreturns.creatureDraw.verify.
 */
public final class IrisCheck implements CreatureDraw.ShaderCheck {
    /** What differed, over every creature compared. Nothing but the vertices and quads counts when the two agree. */
    public static final class Difference {
        public int vertices, quads;
        /** Creatures the two ways gave a different number of vertices. */
        public int counts;
        /** Vertices that differ in the plain part, in what is being drawn, in the middle of the texture. */
        public int plain, drawn, middle;
        /** Quads whose tangent is not Iris's byte for byte, and of those the ones more than a step off or facing the other way. */
        public int tangentNotSame, tangentOff;
        /** Quads without an area: their tangent is whatever the rounding left and nothing is drawn of them. */
        public int flat;

        public boolean same() {
            return counts == 0 && plain == 0 && drawn == 0 && middle == 0 && tangentOff == 0;
        }

        @Override public String toString() {
            return vertices + " vertices in " + quads + " quads: counts " + counts + ", plain " + plain + ", drawn " + drawn + ", middle " + middle
                    + ", tangent not the same byte " + tangentNotSame + " (more than a step off " + tangentOff + "), without area " + flat;
        }
    }

    /** Keeps the vertices pushed at it, as the memory they came in. */
    private static final class Capture implements VertexConsumer, VertexBufferWriter {
        final VertexFormat format;
        final int stride;
        long address;
        int capacity, vertices;

        Capture(VertexFormat format, int stride) {
            this.format = format;
            this.stride = stride;
        }

        @Override public void push(MemoryStack stack, long pointer, int count, VertexFormat pushed) {
            if (pushed != format) throw new IllegalArgumentException("Not " + format + ": " + pushed);
            if (vertices + count > capacity) {
                capacity = Math.max(capacity * 2, vertices + count + 4096);
                address = MemoryUtil.nmemRealloc(address, (long) capacity * stride);
            }
            MemoryUtil.memCopy(pointer, address + (long) vertices * stride, (long) count * stride);
            vertices += count;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setColor(int color) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv(float u, float v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setLineWidth(float width) { throw new UnsupportedOperationException(); }
    }

    private final Capture plain = new Capture(EntityVertex.FORMAT, EntityVertex.STRIDE), wide = new Capture(IrisSink.FORMAT, IrisSink.STRIDE);
    private final BulkSink bulk = new BulkSink();
    private final IrisSink sink = new IrisSink();
    private final CubeWriter writer = new CubeWriter();
    private final VertexSerializer serializer;
    private final Difference total = new Difference();
    private long expected;
    private int room;

    /** Against the serializer the game uses between the two formats: Iris's, once Iris has started. */
    public IrisCheck() {
        this(VertexSerializerRegistry.instance().get(EntityVertex.FORMAT, IrisSink.FORMAT));
    }

    public IrisCheck(VertexSerializer serializer) {
        this.serializer = serializer;
    }

    @Override public void check(CreatureMesh mesh, CubeWriter.Posed posed, Matrix4fc pose, Matrix3fc normal, int skip, TextureCoverage coverage,
                                float smallest, int color, int overlay, int light) {
        compare(mesh, posed, pose, normal, skip, coverage, smallest, color, overlay, light, total);
    }

    @Override public JsonObject report() {
        JsonObject report = new JsonObject();
        report.addProperty("serializer", serializer.getClass().getName());
        report.addProperty("vertices", total.vertices);
        report.addProperty("quads", total.quads);
        report.addProperty("creatures_with_another_vertex_count", total.counts);
        report.addProperty("vertices_differing_in_the_plain_part", total.plain);
        report.addProperty("vertices_differing_in_what_is_drawn", total.drawn);
        report.addProperty("vertices_differing_in_the_middle_of_the_texture", total.middle);
        report.addProperty("quads_with_a_tangent_not_the_same_byte", total.tangentNotSame);
        report.addProperty("quads_with_a_tangent_more_than_a_step_off", total.tangentOff);
        report.addProperty("quads_without_area_not_compared", total.flat);
        return report;
    }

    /** Writes the creature both ways, with the normals a shader pack is given, and adds what differs. */
    public void compare(CreatureMesh mesh, CubeWriter.Posed posed, Matrix4fc pose, Matrix3fc normal, int skip, TextureCoverage coverage,
                        float smallest, int color, int overlay, int light, Difference into) {
        plain.vertices = 0;
        wide.vertices = 0;
        bulk.begin(plain, color, overlay, light);
        writer.write(mesh, posed, pose, normal, bulk, skip, coverage, true, smallest);
        bulk.end();
        sink.begin((VertexBufferWriter) wide, color, overlay, light);
        writer.write(mesh, posed, pose, normal, sink, skip, coverage, true, smallest);
        sink.end();
        if (plain.vertices != wide.vertices) {
            into.counts++;
            return;
        }
        int count = plain.vertices, stride = IrisSink.STRIDE;
        if (count == 0) return;
        if (count > room) {
            room = count + 4096;
            expected = MemoryUtil.nmemRealloc(expected, (long) room * stride);
        }
        serializer.serialize(plain.address, expected, count);
        into.vertices += count;
        into.quads += count / 4;
        for (int quad = 0; quad < count / 4; quad++) {
            long a = expected + (long) quad * 4 * stride, b = wide.address + (long) quad * 4 * stride;
            for (int vertex = 0; vertex < 4; vertex++) {
                long from = a + (long) vertex * stride, to = b + (long) vertex * stride;
                boolean same = true;
                for (int at = 0; at < EntityVertex.STRIDE; at += 4) same &= MemoryUtil.memGetInt(from + at) == MemoryUtil.memGetInt(to + at);
                if (!same) into.plain++;
                if (MemoryUtil.memGetInt(from + IrisSink.DRAWN) != MemoryUtil.memGetInt(to + IrisSink.DRAWN)
                        || MemoryUtil.memGetShort(from + IrisSink.DRAWN + 4) != MemoryUtil.memGetShort(to + IrisSink.DRAWN + 4)) into.drawn++;
                if (MemoryUtil.memGetLong(from + IrisSink.MID) != MemoryUtil.memGetLong(to + IrisSink.MID)) into.middle++;
            }
            boolean notSame = false, off = false;
            for (int vertex = 0; vertex < 4; vertex++) {
                int theirs = MemoryUtil.memGetInt(a + (long) vertex * stride + IrisSink.TANGENT), ours = MemoryUtil.memGetInt(b + (long) vertex * stride + IrisSink.TANGENT);
                if (theirs == ours) continue;
                notSame = true;
                for (int shift = 0; shift < 24; shift += 8) off |= Math.abs((byte) (theirs >> shift) - (byte) (ours >> shift)) > 1;
                off |= theirs >>> 24 != ours >>> 24;
            }
            if (!notSame) continue;
            if (flat(a, stride)) {
                into.flat++;
                continue;
            }
            into.tangentNotSame++;
            if (off) into.tangentOff++;
        }
    }

    /** A quad squashed to a line or a point, in the model or by a bone scaled to nothing. */
    private static boolean flat(long quad, int stride) {
        double[] first = new double[3], second = new double[3];
        double longest = 0;
        for (int k = 0; k < 3; k++) {
            first[k] = (double) MemoryUtil.memGetFloat(quad + 2L * stride + k * 4) - MemoryUtil.memGetFloat(quad + k * 4);
            second[k] = (double) MemoryUtil.memGetFloat(quad + 3L * stride + k * 4) - MemoryUtil.memGetFloat(quad + stride + k * 4);
            longest = Math.max(longest, Math.max(Math.abs(first[k]), Math.abs(second[k])));
        }
        double x = first[1] * second[2] - first[2] * second[1], y = first[2] * second[0] - first[0] * second[2], z = first[0] * second[1] - first[1] * second[0];
        return !(Math.sqrt(x * x + y * y + z * z) > 1e-4 * longest * longest);
    }
}
