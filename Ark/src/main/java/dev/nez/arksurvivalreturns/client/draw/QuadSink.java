package dev.nez.arksurvivalreturns.client.draw;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Where {@link CubeWriter} puts its cubes: a vertex consumer, one vertex at a time or in bulk. */
public abstract class QuadSink {
    /** Starts a creature. False when this sink cannot write to the consumer; another one must be used then. */
    public abstract boolean begin(VertexConsumer consumer, int color, int overlay, int light);

    /**
     * One cube: its eight corners (x, y, z each; the corner's index has bit 0 on the high x side, bit 1 on y,
     * bit 2 on z), the normal of each of its six faces, and the faces to write, a bit each. The mesh says of
     * the cube's faces (its first is the cube's number times six) which corners the four vertices are and
     * what they carry of the texture.
     */
    public abstract void cube(float[] corners, float[] normals, CreatureMesh mesh, int cube, int mask);

    /** Ends the creature begun last. */
    public abstract void end();
}
