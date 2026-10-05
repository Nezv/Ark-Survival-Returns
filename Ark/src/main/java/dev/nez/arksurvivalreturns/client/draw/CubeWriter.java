package dev.nez.arksurvivalreturns.client.draw;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.GeoBone;
import org.joml.Math;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;

/**
 * Walks the animated bones of a {@link CreatureMesh} and hands every cube to a {@link QuadSink} as eight
 * corners and the normals of its faces. It does what GeckoLib's own drawing does (BakedGeoModel.render down to
 * GeoQuad.render, RenderUtil.prepMatrixForBone, PoseStack.Pose for the normals) with its own matrices: the
 * animation's translation, the pivot, Z then Y then X, the scale, hidden bones and hidden children, in JOML's
 * own arithmetic so the numbers agree to the last digits and not merely to the eye. It must
 * run where GeckoLib's would, inside RenderPassInfo.renderPosed, because the bones carry their animated state
 * only there. One instance, render thread only; nothing is allocated while it writes.
 */
public final class CubeWriter {
    /** Every face GeckoLib would draw. */
    public static final int ALL = 0;
    /**
     * Leaves out the sides of a box that face away from the eye, for boxes that are closed, opaque under the
     * texture and clear of the eye: those sides lie behind the box's own near sides. The eye must be the origin
     * of the pose's space, within {@link #EYE_MARGIN}.
     */
    public static final int FAR_SIDES = 1;
    /** Leaves out faces whose texels are all transparent, for a pass in which such a face changes nothing. */
    public static final int EMPTY_FACES = 2;
    /**
     * How far, in blocks, the eye may be from the origin (view bobbing moves it) and how far the near plane's
     * corners reach from it; a side counts as facing away, or towards, only beyond this.
     */
    static final float EYE_MARGIN = 0.5f;
    private static final float MARGIN_SQUARED = EYE_MARGIN * EYE_MARGIN;

    private float[] poses = new float[0], normals = new float[0];
    private final float[] corners = new float[24], faceNormals = new float[18];
    /** Faces handed to the sink and faces left out since the counters were last read. */
    public long facesWritten, facesSkipped;

    /**
     * @param pose the entity's pose, an affine matrix
     * @param skip {@link #ALL}, {@link #FAR_SIDES} or {@link #EMPTY_FACES}; the last two need the coverage
     * @param windingNormals the normal of a face from the way its corners wind, as Iris recomputes it for
     *                       every quad of a level pass, instead of the one GeckoLib passes
     */
    public void write(CreatureMesh mesh, Matrix4fc pose, Matrix3fc normal, QuadSink sink, int skip, TextureCoverage coverage,
                      boolean windingNormals) {
        int levels = mesh.maxDepth + 2;
        if (poses.length < levels * 12) {
            poses = new float[levels * 12];
            normals = new float[levels * 9];
        }
        float[] ps = poses, ns = normals;
        ps[0] = pose.m00(); ps[1] = pose.m01(); ps[2] = pose.m02();
        ps[3] = pose.m10(); ps[4] = pose.m11(); ps[5] = pose.m12();
        ps[6] = pose.m20(); ps[7] = pose.m21(); ps[8] = pose.m22();
        ps[9] = pose.m30(); ps[10] = pose.m31(); ps[11] = pose.m32();
        ns[0] = normal.m00(); ns[1] = normal.m01(); ns[2] = normal.m02();
        ns[3] = normal.m10(); ns[4] = normal.m11(); ns[5] = normal.m12();
        ns[6] = normal.m20(); ns[7] = normal.m21(); ns[8] = normal.m22();

        GeoBone[] bones = mesh.bones;
        int[] depth = mesh.depth, subtreeEnd = mesh.subtreeEnd, cubeEnd = mesh.cubeEnd;
        float[] pivots = mesh.pivot, rotations = mesh.baseRotation;
        int count = bones.length, index = 0;
        while (index < count) {
            BoneSnapshot snapshot = bones[index].frameSnapshot;
            int to = (depth[index] + 1) * 12, from = to - 12, nTo = (depth[index] + 1) * 9, nFrom = nTo - 9;
            float m00 = ps[from], m01 = ps[from + 1], m02 = ps[from + 2], m10 = ps[from + 3], m11 = ps[from + 4], m12 = ps[from + 5],
                    m20 = ps[from + 6], m21 = ps[from + 7], m22 = ps[from + 8], m30 = ps[from + 9], m31 = ps[from + 10], m32 = ps[from + 11];
            float n00 = ns[nFrom], n01 = ns[nFrom + 1], n02 = ns[nFrom + 2], n10 = ns[nFrom + 3], n11 = ns[nFrom + 4], n12 = ns[nFrom + 5],
                    n20 = ns[nFrom + 6], n21 = ns[nFrom + 7], n22 = ns[nFrom + 8];
            float pivotX = pivots[index * 3], pivotY = pivots[index * 3 + 1], pivotZ = pivots[index * 3 + 2];
            float rotX = rotations[index * 3], rotY = rotations[index * 3 + 1], rotZ = rotations[index * 3 + 2];
            boolean hidden = false, childrenHidden = false;
            if (snapshot != null) {
                if (snapshot.hasTranslation()) {
                    float x = -snapshot.getTranslateX() / 16f, y = snapshot.getTranslateY() / 16f, z = snapshot.getTranslateZ() / 16f;
                    m30 = Math.fma(m00, x, Math.fma(m10, y, Math.fma(m20, z, m30)));
                    m31 = Math.fma(m01, x, Math.fma(m11, y, Math.fma(m21, z, m31)));
                    m32 = Math.fma(m02, x, Math.fma(m12, y, Math.fma(m22, z, m32)));
                }
                rotX += snapshot.getRotX();
                rotY += snapshot.getRotY();
                rotZ += snapshot.getRotZ();
                hidden = snapshot.isHidden();
                childrenHidden = snapshot.areChildrenHidden();
            }
            m30 = Math.fma(m00, pivotX, Math.fma(m10, pivotY, Math.fma(m20, pivotZ, m30)));
            m31 = Math.fma(m01, pivotX, Math.fma(m11, pivotY, Math.fma(m21, pivotZ, m31)));
            m32 = Math.fma(m02, pivotX, Math.fma(m12, pivotY, Math.fma(m22, pivotZ, m32)));
            // A turn about one axis the way JOML makes it from the quaternion of half the angle: "turn" for the
            // cosine, "lean" for the sine and "keep", next to one, for the axis itself. The same numbers as GeckoLib's.
            if (rotZ != 0f) {
                float sin = Math.sin(rotZ * 0.5f), cos = Math.cosFromSin(sin, rotZ * 0.5f);
                float w2 = cos * cos, q2 = sin * sin, qw = sin * cos, lean = qw + qw, turn = w2 - q2, keep = q2 + w2, t;
                t = m00 * turn + m10 * lean; m10 = m00 * -lean + m10 * turn; m00 = t; m20 *= keep;
                t = m01 * turn + m11 * lean; m11 = m01 * -lean + m11 * turn; m01 = t; m21 *= keep;
                t = m02 * turn + m12 * lean; m12 = m02 * -lean + m12 * turn; m02 = t; m22 *= keep;
                t = n00 * turn + n10 * lean; n10 = n00 * -lean + n10 * turn; n00 = t; n20 *= keep;
                t = n01 * turn + n11 * lean; n11 = n01 * -lean + n11 * turn; n01 = t; n21 *= keep;
                t = n02 * turn + n12 * lean; n12 = n02 * -lean + n12 * turn; n02 = t; n22 *= keep;
            }
            if (rotY != 0f) {
                float sin = Math.sin(rotY * 0.5f), cos = Math.cosFromSin(sin, rotY * 0.5f);
                float w2 = cos * cos, q2 = sin * sin, qw = sin * cos, lean = qw + qw, turn = w2 - q2, keep = q2 + w2, t;
                t = m00 * turn + m20 * -lean; m20 = m00 * lean + m20 * turn; m00 = t; m10 *= keep;
                t = m01 * turn + m21 * -lean; m21 = m01 * lean + m21 * turn; m01 = t; m11 *= keep;
                t = m02 * turn + m22 * -lean; m22 = m02 * lean + m22 * turn; m02 = t; m12 *= keep;
                t = n00 * turn + n20 * -lean; n20 = n00 * lean + n20 * turn; n00 = t; n10 *= keep;
                t = n01 * turn + n21 * -lean; n21 = n01 * lean + n21 * turn; n01 = t; n11 *= keep;
                t = n02 * turn + n22 * -lean; n22 = n02 * lean + n22 * turn; n02 = t; n12 *= keep;
            }
            if (rotX != 0f) {
                float sin = Math.sin(rotX * 0.5f), cos = Math.cosFromSin(sin, rotX * 0.5f);
                float w2 = cos * cos, q2 = sin * sin, qw = sin * cos, lean = qw + qw, turn = w2 - q2, keep = w2 + q2, t;
                t = m10 * turn + m20 * lean; m20 = m10 * -lean + m20 * turn; m10 = t; m00 *= keep;
                t = m11 * turn + m21 * lean; m21 = m11 * -lean + m21 * turn; m11 = t; m01 *= keep;
                t = m12 * turn + m22 * lean; m22 = m12 * -lean + m22 * turn; m12 = t; m02 *= keep;
                t = n10 * turn + n20 * lean; n20 = n10 * -lean + n20 * turn; n10 = t; n00 *= keep;
                t = n11 * turn + n21 * lean; n21 = n11 * -lean + n21 * turn; n11 = t; n01 *= keep;
                t = n12 * turn + n22 * lean; n22 = n12 * -lean + n22 * turn; n12 = t; n02 *= keep;
            }
            if (snapshot != null && snapshot.hasScale()) {
                float x = snapshot.getScaleX(), y = snapshot.getScaleY(), z = snapshot.getScaleZ();
                m00 *= x; m01 *= x; m02 *= x;
                m10 *= y; m11 *= y; m12 *= y;
                m20 *= z; m21 *= z; m22 *= z;
                // PoseStack.Pose.scale: an even scale keeps the normals but for its signs, an uneven one divides them.
                if (Math.abs(x) == Math.abs(y) && Math.abs(y) == Math.abs(z)) {
                    if (x < 0f || y < 0f || z < 0f) {
                        x = Math.signum(x);
                        y = Math.signum(y);
                        z = Math.signum(z);
                        n00 *= x; n01 *= x; n02 *= x;
                        n10 *= y; n11 *= y; n12 *= y;
                        n20 *= z; n21 *= z; n22 *= z;
                    }
                } else {
                    x = 1f / x;
                    y = 1f / y;
                    z = 1f / z;
                    n00 *= x; n01 *= x; n02 *= x;
                    n10 *= y; n11 *= y; n12 *= y;
                    n20 *= z; n21 *= z; n22 *= z;
                }
            }
            m30 = Math.fma(m00, -pivotX, Math.fma(m10, -pivotY, Math.fma(m20, -pivotZ, m30)));
            m31 = Math.fma(m01, -pivotX, Math.fma(m11, -pivotY, Math.fma(m21, -pivotZ, m31)));
            m32 = Math.fma(m02, -pivotX, Math.fma(m12, -pivotY, Math.fma(m22, -pivotZ, m32)));
            ps[to] = m00; ps[to + 1] = m01; ps[to + 2] = m02; ps[to + 3] = m10; ps[to + 4] = m11; ps[to + 5] = m12;
            ps[to + 6] = m20; ps[to + 7] = m21; ps[to + 8] = m22; ps[to + 9] = m30; ps[to + 10] = m31; ps[to + 11] = m32;
            ns[nTo] = n00; ns[nTo + 1] = n01; ns[nTo + 2] = n02; ns[nTo + 3] = n10; ns[nTo + 4] = n11; ns[nTo + 5] = n12;
            ns[nTo + 6] = n20; ns[nTo + 7] = n21; ns[nTo + 8] = n22;
            int first = index == 0 ? 0 : cubeEnd[index - 1], end = cubeEnd[index];
            if (!hidden && first < end) cubes(mesh, first, end, to, nTo, sink, skip, coverage, windingNormals);
            index = childrenHidden ? subtreeEnd[index] : index + 1;
        }
    }

    private void cubes(CreatureMesh mesh, int first, int end, int at, int normalAt, QuadSink sink, int skip, TextureCoverage coverage,
                       boolean windingNormals) {
        float[] ps = poses, ns = normals, box = mesh.box, local = mesh.normal, corner = corners, out = faceNormals;
        byte[] present = mesh.present, flags = mesh.flags, sides = mesh.side, winding = mesh.winding, slotFace = mesh.slotFace;
        float m00 = ps[at], m01 = ps[at + 1], m02 = ps[at + 2], m10 = ps[at + 3], m11 = ps[at + 4], m12 = ps[at + 5],
                m20 = ps[at + 6], m21 = ps[at + 7], m22 = ps[at + 8], m30 = ps[at + 9], m31 = ps[at + 10], m32 = ps[at + 11];
        float n00 = ns[normalAt], n01 = ns[normalAt + 1], n02 = ns[normalAt + 2], n10 = ns[normalAt + 3], n11 = ns[normalAt + 4],
                n12 = ns[normalAt + 5], n20 = ns[normalAt + 6], n21 = ns[normalAt + 7], n22 = ns[normalAt + 8];
        long written = 0, skipped = 0;
        for (int cube = first; cube < end; cube++) {
            int mask = present[cube];
            if (mask == 0) continue;
            int all = mask, flag = flags[cube], b = cube * CreatureMesh.BOX, f = cube * CreatureMesh.FACES;
            float x = box[b], y = box[b + 1], z = box[b + 2];
            float ox = m00 * x + m10 * y + m20 * z + m30, oy = m01 * x + m11 * y + m21 * z + m31, oz = m02 * x + m12 * y + m22 * z + m32;
            x = box[b + 3]; y = box[b + 4]; z = box[b + 5];
            float ax = m00 * x + m10 * y + m20 * z, ay = m01 * x + m11 * y + m21 * z, az = m02 * x + m12 * y + m22 * z;
            x = box[b + 6]; y = box[b + 7]; z = box[b + 8];
            float bx = m00 * x + m10 * y + m20 * z, by = m01 * x + m11 * y + m21 * z, bz = m02 * x + m12 * y + m22 * z;
            x = box[b + 9]; y = box[b + 10]; z = box[b + 11];
            float cx = m00 * x + m10 * y + m20 * z, cy = m01 * x + m11 * y + m21 * z, cz = m02 * x + m12 * y + m22 * z;

            boolean farSides = skip == FAR_SIDES && (flag & CreatureMesh.CLOSED) != 0 && (coverage.opaqueCubes[cube >> 6] >>> cube & 1L) != 0;
            // The plane of each pair of sides: across the y and z edges for the x pair, and so on.
            float ux = 0f, uy = 0f, uz = 0f, vx = 0f, vy = 0f, vz = 0f, wx = 0f, wy = 0f, wz = 0f;
            if (farSides || windingNormals) {
                ux = by * cz - bz * cy; uy = bz * cx - bx * cz; uz = bx * cy - by * cx;
                vx = cy * az - cz * ay; vy = cz * ax - cx * az; vz = cx * ay - cy * ax;
                wx = ay * bz - az * by; wy = az * bx - ax * bz; wz = ax * by - ay * bx;
            }
            if (farSides) {
                float volume = ax * ux + ay * uy + az * uz, sign = volume < 0f ? -1f : 1f;
                volume *= sign;
                // How far the eye is in front of the low side of each pair, times the length of that pair's plane
                // vector; in front of the high side it is the opposite, less the box's thickness.
                float lowX = sign * (ux * ox + uy * oy + uz * oz), highX = -lowX - volume, limitX = MARGIN_SQUARED * (ux * ux + uy * uy + uz * uz);
                float lowY = sign * (vx * ox + vy * oy + vz * oz), highY = -lowY - volume, limitY = MARGIN_SQUARED * (vx * vx + vy * vy + vz * vz);
                float lowZ = sign * (wx * ox + wy * oy + wz * oz), highZ = -lowZ - volume, limitZ = MARGIN_SQUARED * (wx * wx + wy * wy + wz * wz);
                // Only a box the eye is clear of hides its far sides; one the eye is inside or against shows them.
                boolean clear = lowX > 0f && lowX * lowX > limitX || highX > 0f && highX * highX > limitX
                        || lowY > 0f && lowY * lowY > limitY || highY > 0f && highY * highY > limitY
                        || lowZ > 0f && lowZ * lowZ > limitZ || highZ > 0f && highZ * highZ > limitZ;
                if (clear) {
                    if (lowX < 0f && lowX * lowX > limitX) mask &= ~slotFace[f];
                    if (highX < 0f && highX * highX > limitX) mask &= ~slotFace[f + 1];
                    if (lowY < 0f && lowY * lowY > limitY) mask &= ~slotFace[f + 2];
                    if (highY < 0f && highY * highY > limitY) mask &= ~slotFace[f + 3];
                    if (lowZ < 0f && lowZ * lowZ > limitZ) mask &= ~slotFace[f + 4];
                    if (highZ < 0f && highZ * highZ > limitZ) mask &= ~slotFace[f + 5];
                }
            } else if (skip == EMPTY_FACES) {
                long[] empty = coverage.emptyFaces;
                int shift = f & 63;
                long bits = empty[f >> 6] >>> shift;
                if (shift > 58) bits |= empty[(f >> 6) + 1] << 64 - shift;
                mask &= ~(int) bits;
            }
            written += Integer.bitCount(mask);
            skipped += Integer.bitCount(all) - Integer.bitCount(mask);
            if (mask == 0) continue;

            corner[0] = ox; corner[1] = oy; corner[2] = oz;
            corner[3] = ox + ax; corner[4] = oy + ay; corner[5] = oz + az;
            corner[6] = ox + bx; corner[7] = oy + by; corner[8] = oz + bz;
            corner[9] = corner[3] + bx; corner[10] = corner[4] + by; corner[11] = corner[5] + bz;
            corner[12] = ox + cx; corner[13] = oy + cy; corner[14] = oz + cz;
            corner[15] = corner[3] + cx; corner[16] = corner[4] + cy; corner[17] = corner[5] + cz;
            corner[18] = corner[6] + cx; corner[19] = corner[7] + cy; corner[20] = corner[8] + cz;
            corner[21] = corner[9] + cx; corner[22] = corner[10] + cy; corner[23] = corner[11] + cz;

            if (windingNormals) {
                float lengthX = Math.sqrt(ux * ux + uy * uy + uz * uz), lengthY = Math.sqrt(vx * vx + vy * vy + vz * vz),
                        lengthZ = Math.sqrt(wx * wx + wy * wy + wz * wz);
                float scaleX = lengthX > 0f ? 1f / lengthX : 0f, scaleY = lengthY > 0f ? 1f / lengthY : 0f, scaleZ = lengthZ > 0f ? 1f / lengthZ : 0f;
                for (int face = 0; face < CreatureMesh.FACES; face++) {
                    if ((mask >> face & 1) == 0) continue;
                    int slot = sides[f + face], o = face * 3;
                    float way = winding[f + face];
                    if (slot < 0) {
                        x = local[(f + face) * 3]; y = local[(f + face) * 3 + 1]; z = local[(f + face) * 3 + 2];
                        out[o] = n00 * x + n10 * y + n20 * z; out[o + 1] = n01 * x + n11 * y + n21 * z; out[o + 2] = n02 * x + n12 * y + n22 * z;
                    } else if (slot < 2) {
                        way *= scaleX;
                        out[o] = ux * way; out[o + 1] = uy * way; out[o + 2] = uz * way;
                    } else if (slot < 4) {
                        way *= scaleY;
                        out[o] = vx * way; out[o + 1] = vy * way; out[o + 2] = vz * way;
                    } else {
                        way *= scaleZ;
                        out[o] = wx * way; out[o + 1] = wy * way; out[o + 2] = wz * way;
                    }
                }
            } else {
                for (int face = 0; face < CreatureMesh.FACES; face++) {
                    if ((mask >> face & 1) == 0) continue;
                    int l = (f + face) * 3, o = face * 3;
                    x = local[l]; y = local[l + 1]; z = local[l + 2];
                    float nx = n00 * x + n10 * y + n20 * z, ny = n01 * x + n11 * y + n21 * z, nz = n02 * x + n12 * y + n22 * z;
                    // RenderUtil.fixInvertedFlatCube, for the cubes without thickness.
                    if ((flag & CreatureMesh.FIX_X) != 0 && nx < 0f) nx = -nx;
                    if ((flag & CreatureMesh.FIX_Y) != 0 && ny < 0f) ny = -ny;
                    if ((flag & CreatureMesh.FIX_Z) != 0 && nz < 0f) nz = -nz;
                    out[o] = nx; out[o + 1] = ny; out[o + 2] = nz;
                }
            }
            sink.cube(corner, out, mesh.uv, f * 8, mesh.corner, f * 4, mask);
        }
        facesWritten += written;
        facesSkipped += skipped;
    }
}
