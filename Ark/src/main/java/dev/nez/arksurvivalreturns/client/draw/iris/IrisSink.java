package dev.nez.arksurvivalreturns.client.draw.iris;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nez.arksurvivalreturns.client.draw.CreatureMesh;
import dev.nez.arksurvivalreturns.client.draw.QuadSink;
import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.NormalHelper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The way into a vertex buffer while a shader pack draws the world. Iris gives such a buffer a wider vertex
 * (what is being drawn, the middle of the quad in the texture, a tangent) and, for vertices pushed in the plain
 * entity format, fills the extra fields itself: it reads every quad back and works them out from its corners
 * (ModelToEntityVertexSerializer). This writes the wide vertex at once, so the buffer only copies: the middle,
 * and what the tangent takes from the texture, are kept per face of the model ({@link CreatureMesh}), and the
 * tangent is Iris's own arithmetic on the corners the cube already has. The fields are written with Sodium's
 * own writers: LWJGL's memPut makes a memory segment for every call on this Java, a fifth of the render thread
 * when used here. Loaded only when Sodium and Iris are.
 */
public final class IrisSink extends QuadSink {
    public static final VertexFormat FORMAT = IrisVertexFormats.ENTITY;
    public static final int STRIDE = FORMAT.getVertexSize();
    /** Where the extra fields are in the wide vertex; the plain entity vertex comes first, unchanged. */
    public static final int DRAWN = FORMAT.getOffset(IrisVertexFormats.ENTITY_ID_ELEMENT),
            MID = FORMAT.getOffset(IrisVertexFormats.MID_TEXTURE_ELEMENT), TANGENT = FORMAT.getOffset(IrisVertexFormats.TANGENT_ELEMENT);
    /** Iris leaves room after the three numbers; it is written as nothing, so no stray byte reaches the buffer. */
    private static final boolean SPARE = MID - DRAWN >= 8;
    private static final int CUBE_VERTICES = 24, CAPACITY = 16 * CUBE_VERTICES;
    private static final MethodHandle FORMAT_OF;

    static {
        if (DRAWN != EntityVertex.STRIDE || MID < DRAWN + 6 || TANGENT < MID + 8 || STRIDE < TANGENT + 4)
            throw new IllegalStateException("This Iris lays its entity vertex out another way: " + FORMAT);
        try {
            FORMAT_OF = MethodHandles.privateLookupIn(BufferBuilder.class, MethodHandles.lookup()).findGetter(BufferBuilder.class, "format", VertexFormat.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("A vertex buffer keeps its format elsewhere", e);
        }
    }

    private final long buffer = MemoryUtil.nmemAlloc((long) CAPACITY * STRIDE);
    private VertexBufferWriter writer;
    private long at;
    private int vertices, color, overlay, light;
    private short entity, blockEntity, item;

    /** True for a vertex buffer Iris has widened, and only for that: anything else gets the plain format. */
    @Override public boolean begin(VertexConsumer consumer, int color, int overlay, int light) {
        if (consumer.getClass() != BufferBuilder.class) return false;
        try {
            if ((VertexFormat) FORMAT_OF.invokeExact((BufferBuilder) consumer) != FORMAT) return false;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        VertexBufferWriter writer = VertexBufferWriter.tryOf(consumer);
        return writer != null && begin(writer, color, overlay, light);
    }

    /** The same into anything that takes the wide vertex. */
    public boolean begin(VertexBufferWriter writer, int color, int overlay, int light) {
        this.writer = writer;
        this.color = ColorARGB.toABGR(color);
        this.overlay = overlay;
        this.light = light;
        // What Iris says is being drawn, as its serializer reads it for the vertices of one push.
        entity = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
        blockEntity = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        item = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
        at = buffer;
        vertices = 0;
        return true;
    }

    @Override public void cube(float[] corners, float[] normals, CreatureMesh mesh, int cube, int mask) {
        if (vertices > CAPACITY - CUBE_VERTICES) flush();
        float[] uv = mesh.uv, mids = mesh.mid, tangents = mesh.tangent;
        byte[] corner = mesh.corner, handed = mesh.handed;
        long pointer = at;
        int color = this.color, overlay = this.overlay, light = this.light, added = 0;
        short entity = this.entity, blockEntity = this.blockEntity, item = this.item;
        for (int face = 0; face < 6; face++) {
            if ((mask >> face & 1) == 0) continue;
            int f = cube * 6 + face, c = f * 4, t = f * 8;
            int normal = NormI8.pack(normals[face * 3], normals[face * 3 + 1], normals[face * 3 + 2]);
            int p0 = corner[c], p1 = corner[c + 1], p2 = corner[c + 2];
            float x0 = corners[p0], y0 = corners[p0 + 1], z0 = corners[p0 + 2];
            int tangent, way = handed[f];
            if (way == 0) {
                // A face whose side the model does not tell beforehand: Iris's whole sum, with the normal as it is stored.
                tangent = NormalHelper.computeTangent(null, net.irisshaders.iris.vertices.NormI8.unpackX(normal),
                        net.irisshaders.iris.vertices.NormI8.unpackY(normal), net.irisshaders.iris.vertices.NormI8.unpackZ(normal),
                        x0, y0, z0, uv[t], uv[t + 1], corners[p1], corners[p1 + 1], corners[p1 + 2], uv[t + 2], uv[t + 3],
                        corners[p2], corners[p2 + 1], corners[p2 + 2], uv[t + 4], uv[t + 5]);
            } else {
                // NormalHelper.computeTangent, float for float, with what the texture gives taken from the mesh.
                float edge1x = corners[p1] - x0, edge1y = corners[p1 + 1] - y0, edge1z = corners[p1 + 2] - z0;
                float edge2x = corners[p2] - x0, edge2y = corners[p2 + 1] - y0, edge2z = corners[p2 + 2] - z0;
                float deltaV2 = tangents[f * 3], deltaV1 = tangents[f * 3 + 1], scale = tangents[f * 3 + 2];
                float x = scale * (deltaV2 * edge1x - deltaV1 * edge2x);
                float y = scale * (deltaV2 * edge1y - deltaV1 * edge2y);
                float z = scale * (deltaV2 * edge1z - deltaV1 * edge2z);
                float squared = x * x + y * y + z * z, length = squared == 0f ? 1f : (float) (1.0 / Math.sqrt(squared));
                x *= length;
                y *= length;
                z *= length;
                tangent = x == 0f && y == 0f && z == 0f ? -1
                        : (int) (x * 127f) & 0xFF | ((int) (y * 127f) & 0xFF) << 8 | ((int) (z * 127f) & 0xFF) << 16 | (way > 0 ? 0x7F000000 : 0x81000000);
            }
            float midU = mids[f * 2], midV = mids[f * 2 + 1];
            for (int vertex = 0; vertex < 4; vertex++) {
                int p = corner[c + vertex];
                EntityVertex.write(pointer, corners[p], corners[p + 1], corners[p + 2], color, uv[t + vertex * 2], uv[t + vertex * 2 + 1],
                        overlay, light, normal);
                MemoryIntrinsics.putShort(pointer + DRAWN, entity);
                MemoryIntrinsics.putShort(pointer + DRAWN + 2, blockEntity);
                MemoryIntrinsics.putShort(pointer + DRAWN + 4, item);
                if (SPARE) MemoryIntrinsics.putShort(pointer + DRAWN + 6, (short) 0);
                MemoryIntrinsics.putFloat(pointer + MID, midU);
                MemoryIntrinsics.putFloat(pointer + MID + 4, midV);
                MemoryIntrinsics.putInt(pointer + TANGENT, tangent);
                pointer += STRIDE;
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
        try (MemoryStack stack = MemoryStack.stackPush()) {
            writer.push(stack, buffer, vertices, FORMAT);
        }
        at = buffer;
        vertices = 0;
    }
}
