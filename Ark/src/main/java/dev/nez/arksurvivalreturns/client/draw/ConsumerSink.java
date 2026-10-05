package dev.nez.arksurvivalreturns.client.draw;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Any vertex consumer, a vertex at a time: what GeckoLib calls, and the way without Sodium. */
public final class ConsumerSink extends QuadSink {
    private VertexConsumer consumer;
    private int color, overlay, light;

    @Override public boolean begin(VertexConsumer consumer, int color, int overlay, int light) {
        this.consumer = consumer;
        this.color = color;
        this.overlay = overlay;
        this.light = light;
        return true;
    }

    @Override public void cube(float[] corners, float[] normals, float[] uv, int uvAt, byte[] corner, int cornerAt, int mask) {
        VertexConsumer consumer = this.consumer;
        int color = this.color, overlay = this.overlay, light = this.light;
        for (int face = 0; face < CreatureMesh.FACES; face++) {
            if ((mask >> face & 1) == 0) continue;
            float nx = normals[face * 3], ny = normals[face * 3 + 1], nz = normals[face * 3 + 2];
            int c = cornerAt + face * 4, t = uvAt + face * 8;
            for (int vertex = 0; vertex < 4; vertex++) {
                int p = corner[c + vertex];
                consumer.addVertex(corners[p], corners[p + 1], corners[p + 2], color, uv[t + vertex * 2], uv[t + vertex * 2 + 1],
                        overlay, light, nx, ny, nz);
            }
        }
    }

    @Override public void end() { consumer = null; }
}
