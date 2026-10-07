package dev.nez.arksurvivalreturns.client.draw.sodium;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nez.arksurvivalreturns.client.draw.CreatureMesh;
import dev.nez.arksurvivalreturns.client.draw.QuadSink;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Sodium's bulk way into a vertex buffer: whole vertices are written to memory in the entity format and
 * pushed sixteen cubes at a time, so the buffer copies them, or converts them to the format a shader pack
 * needs with the serializer Iris registers, without a call per vertex. Loaded only when Sodium is.
 */
public final class BulkSink extends QuadSink {
    private static final int CUBE_VERTICES = 24, CAPACITY = 16 * CUBE_VERTICES;
    private final long buffer = MemoryUtil.nmemAlloc((long) CAPACITY * EntityVertex.STRIDE);
    private VertexBufferWriter writer;
    private long at;
    private int vertices, color, overlay, light;

    @Override public boolean begin(VertexConsumer consumer, int color, int overlay, int light) {
        writer = VertexBufferWriter.tryOf(consumer);
        if (writer == null) return false;
        this.color = ColorARGB.toABGR(color);
        this.overlay = overlay;
        this.light = light;
        at = buffer;
        vertices = 0;
        return true;
    }

    @Override public void cube(float[] corners, float[] normals, CreatureMesh mesh, int cube, int mask) {
        if (vertices > CAPACITY - CUBE_VERTICES) flush();
        float[] uv = mesh.uv;
        byte[] corner = mesh.corner;
        int uvAt = cube * 48, cornerAt = cube * 24;
        long pointer = at;
        int color = this.color, overlay = this.overlay, light = this.light, added = 0;
        for (int face = 0; face < 6; face++) {
            if ((mask >> face & 1) == 0) continue;
            int normal = NormI8.pack(normals[face * 3], normals[face * 3 + 1], normals[face * 3 + 2]);
            int c = cornerAt + face * 4, t = uvAt + face * 8;
            for (int vertex = 0; vertex < 4; vertex++) {
                int p = corner[c + vertex];
                EntityVertex.write(pointer, corners[p], corners[p + 1], corners[p + 2], color, uv[t + vertex * 2], uv[t + vertex * 2 + 1],
                        overlay, light, normal);
                pointer += EntityVertex.STRIDE;
            }
            added += 4;
        }
        at = pointer;
        vertices += added;
    }

    @Override public void end() {
        flush();
        writer = null;
    }

    private void flush() {
        if (vertices == 0) return;
        // The stack is for consumers that copy what they are handed (outlines, crumbling); a buffer does not use it.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            writer.push(stack, buffer, vertices, EntityVertex.FORMAT);
        }
        at = buffer;
        vertices = 0;
    }
}
