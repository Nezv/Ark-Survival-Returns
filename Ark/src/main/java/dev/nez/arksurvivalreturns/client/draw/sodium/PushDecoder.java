package dev.nez.arksurvivalreturns.client.draw.sodium;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nez.arksurvivalreturns.client.draw.VertexRecorder;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Stands in for a vertex buffer when {@link BulkSink} is checked: reads the entity vertices pushed at it back
 * into a {@link VertexRecorder}. A normal comes back as the middle of the step its byte stands for, so packing
 * it again gives the same byte.
 */
public final class PushDecoder implements VertexConsumer, VertexBufferWriter {
    private final VertexRecorder recorder;

    public PushDecoder(VertexRecorder recorder) { this.recorder = recorder; }

    @Override public void push(MemoryStack stack, long pointer, int count, VertexFormat format) {
        if (format != EntityVertex.FORMAT) throw new IllegalArgumentException("Not the entity format: " + format);
        for (int vertex = 0; vertex < count; vertex++, pointer += EntityVertex.STRIDE) {
            recorder.addVertex(MemoryUtil.memGetFloat(pointer), MemoryUtil.memGetFloat(pointer + 4), MemoryUtil.memGetFloat(pointer + 8),
                    ColorARGB.fromABGR(MemoryUtil.memGetInt(pointer + 12)), MemoryUtil.memGetFloat(pointer + 16), MemoryUtil.memGetFloat(pointer + 20),
                    MemoryUtil.memGetInt(pointer + 24), MemoryUtil.memGetInt(pointer + 28),
                    component(MemoryUtil.memGetByte(pointer + 32)), component(MemoryUtil.memGetByte(pointer + 33)), component(MemoryUtil.memGetByte(pointer + 34)));
        }
    }

    private static float component(byte packed) {
        return packed == 0 ? 0f : (packed + (packed > 0 ? 0.5f : -0.5f)) / 127f;
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
