package dev.nez.arksurvivalreturns.client.draw;

import java.util.Arrays;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * A vertex consumer that keeps what it is given, to compare two ways of drawing the same creature number by
 * number. Only the whole-vertex call is recorded: it is the one GeckoLib and {@link ConsumerSink} make.
 */
public final class VertexRecorder implements VertexConsumer {
    /** Floats per vertex: x, y, z, u, v, the normal. */
    public static final int FLOATS = 8;
    /** Ints per vertex: colour (ARGB), overlay, light. */
    public static final int INTS = 3;
    public float[] floats = new float[FLOATS * 4096];
    public int[] ints = new int[INTS * 4096];
    public int vertices;

    public void clear() { vertices = 0; }

    @Override public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        if (vertices * FLOATS == floats.length) {
            floats = Arrays.copyOf(floats, floats.length * 2);
            ints = Arrays.copyOf(ints, ints.length * 2);
        }
        int f = vertices * FLOATS, i = vertices * INTS;
        floats[f] = x; floats[f + 1] = y; floats[f + 2] = z; floats[f + 3] = u; floats[f + 4] = v;
        floats[f + 5] = nx; floats[f + 6] = ny; floats[f + 7] = nz;
        ints[i] = color; ints[i + 1] = overlay; ints[i + 2] = light;
        vertices++;
    }

    /** The byte a vertex buffer keeps of a normal's component (BufferBuilder.normalIntValue). */
    public static int packed(float component) {
        return (byte) ((int) (Math.clamp(component, -1f, 1f) * 127f) & 0xFF);
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer setColor(int color) { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer setUv(float u, float v) { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer setUv1(int u, int v) { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer setUv2(int u, int v) { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer setNormal(float x, float y, float z) { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer setLineWidth(float width) { throw new UnsupportedOperationException(); }

    /** The largest differences between two recordings of the same vertices in the same order. */
    public static final class Difference {
        public int vertices;
        public double position, uv, normal;
        /** The position difference against the largest coordinate of the creature it was found in: a float keeps seven digits of that. */
        public double positionScaled;
        /** Vertices whose colour, overlay or light differ; a different number of vertices counts them all. */
        public int other;
        /** Normals whose stored bytes differ by more than one step. */
        public int normalBytes;

        public void add(VertexRecorder expected, VertexRecorder actual) {
            if (expected.vertices != actual.vertices) {
                other += Math.max(expected.vertices, actual.vertices);
                return;
            }
            vertices += expected.vertices;
            float[] a = expected.floats, b = actual.floats;
            double reach = 1.0, moved = 0.0;
            for (int vertex = 0; vertex < expected.vertices; vertex++) {
                int f = vertex * FLOATS, i = vertex * INTS;
                for (int k = 0; k < 3; k++) {
                    moved = Math.max(moved, apart(a[f + k], b[f + k]));
                    if (Float.isFinite(a[f + k])) reach = Math.max(reach, Math.abs(a[f + k]));
                }
                for (int k = 3; k < 5; k++) uv = Math.max(uv, apart(a[f + k], b[f + k]));
                // A bone scaled unevenly leaves GeckoLib's normal longer than one; measured against its length then.
                double length = Math.max(1.0, Math.sqrt((double) a[f + 5] * a[f + 5] + (double) a[f + 6] * a[f + 6] + (double) a[f + 7] * a[f + 7]));
                for (int k = 5; k < 8; k++) {
                    normal = Math.max(normal, Double.isFinite(length) ? apart(a[f + k], b[f + k]) / length : 0.0);
                    if (Math.abs(packed(a[f + k]) - packed(b[f + k])) > 1) normalBytes++;
                }
                if (expected.ints[i] != actual.ints[i] || expected.ints[i + 1] != actual.ints[i + 1] || expected.ints[i + 2] != actual.ints[i + 2]) other++;
            }
            position = Math.max(position, moved);
            positionScaled = Math.max(positionScaled, moved / reach);
        }

        /** The distance between two values; none between two that are not numbers alike (a bone scaled to nothing). */
        private static double apart(float a, float b) {
            if (Float.compare(a, b) == 0) return 0.0;
            double distance = Math.abs((double) a - b);
            return Double.isNaN(distance) ? Double.POSITIVE_INFINITY : distance;
        }

        public boolean within(double scaledPositionLimit, double uvLimit, double normalLimit) {
            return other == 0 && positionScaled <= scaledPositionLimit && uv <= uvLimit && normal <= normalLimit;
        }

        @Override public String toString() {
            return String.format(java.util.Locale.ROOT, "%d vertices, position %.2e (%.2e of the largest coordinate), uv %.2e, normal %.2e, other %d, normal bytes %d",
                    vertices, position, positionScaled, uv, normal, other, normalBytes);
        }
    }
}
