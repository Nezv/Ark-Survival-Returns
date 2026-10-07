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
 * own arithmetic so the numbers agree to the last digits and not merely to the eye. It works in two steps:
 * {@link #pose} reads the bones where GeckoLib's drawing would, inside RenderPassInfo.renderPosed, because they
 * carry their animated state only there, and keeps them relative to the model; {@link #write} places them by
 * the entity's pose and writes the cubes, in that pass or in a later one. One instance, render thread only;
 * nothing is allocated while it writes.
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

    /** A creature's bones as one pass of the animation left them, relative to the model: kept to be drawn again. */
    public static final class Posed {
        static final int HIDDEN = 1, CHILDREN_HIDDEN = 2;
        CreatureMesh mesh;
        float[] pose = new float[0], normal = new float[0];
        byte[] flags = new byte[0];
        /** The frame and the creature's age the bones were read at; the caller's to keep. */
        public long frame = Long.MIN_VALUE;
        public float age = Float.NaN;

        public boolean of(CreatureMesh mesh) { return this.mesh == mesh; }
    }

    private final float[] matrix = new float[12], normalMatrix = new float[9];
    private final float[] corners = new float[24], faceNormals = new float[18];
    /** Faces handed to the sink, faces left out, and cubes left out as too small, since the counters were last read. */
    public long facesWritten, facesSkipped, cubesTooSmall;

    /** Reads the animated bones into the keep. Inside RenderPassInfo.renderPosed only. */
    public void pose(CreatureMesh mesh, Posed out) {
        GeoBone[] bones = mesh.bones;
        int count = bones.length, index = 0;
        if (out.mesh != mesh || out.flags.length != count) {
            out.mesh = mesh;
            out.pose = new float[count * 12];
            out.normal = new float[count * 9];
            out.flags = new byte[count];
        }
        float[] ps = out.pose, ns = out.normal;
        int[] parents = mesh.parent, subtreeEnd = mesh.subtreeEnd;
        float[] pivots = mesh.pivot, rotations = mesh.baseRotation;
        while (index < count) {
            BoneSnapshot snapshot = bones[index].frameSnapshot;
            int parent = parents[index], to = index * 12, nTo = index * 9;
            float m00 = 1f, m01 = 0f, m02 = 0f, m10 = 0f, m11 = 1f, m12 = 0f, m20 = 0f, m21 = 0f, m22 = 1f, m30 = 0f, m31 = 0f, m32 = 0f;
            float n00 = 1f, n01 = 0f, n02 = 0f, n10 = 0f, n11 = 1f, n12 = 0f, n20 = 0f, n21 = 0f, n22 = 1f;
            if (parent >= 0) {
                int from = parent * 12, nFrom = parent * 9;
                m00 = ps[from]; m01 = ps[from + 1]; m02 = ps[from + 2]; m10 = ps[from + 3]; m11 = ps[from + 4]; m12 = ps[from + 5];
                m20 = ps[from + 6]; m21 = ps[from + 7]; m22 = ps[from + 8]; m30 = ps[from + 9]; m31 = ps[from + 10]; m32 = ps[from + 11];
                n00 = ns[nFrom]; n01 = ns[nFrom + 1]; n02 = ns[nFrom + 2]; n10 = ns[nFrom + 3]; n11 = ns[nFrom + 4]; n12 = ns[nFrom + 5];
                n20 = ns[nFrom + 6]; n21 = ns[nFrom + 7]; n22 = ns[nFrom + 8];
            }
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
            out.flags[index] = (byte) ((hidden ? Posed.HIDDEN : 0) | (childrenHidden ? Posed.CHILDREN_HIDDEN : 0));
            index = childrenHidden ? subtreeEnd[index] : index + 1;
        }
    }

    /**
     * Places the kept bones by the entity's pose and writes their cubes.
     *
     * @param pose the entity's pose, an affine matrix
     * @param skip {@link #ALL}, {@link #FAR_SIDES} or {@link #EMPTY_FACES}; the last two need the coverage
     * @param windingNormals the normal of a face from the way its corners wind, as Iris recomputes it for
     *                       every quad of a level pass, instead of the one GeckoLib passes
     * @param smallest zero, or the square of pixels per radian over the size in pixels below which a cube is
     *                 left out; the eye must be the origin of the pose's space then
     */
    public void write(CreatureMesh mesh, Posed posed, Matrix4fc pose, Matrix3fc normal, QuadSink sink, int skip, TextureCoverage coverage,
                      boolean windingNormals, float smallest) {
        float r00 = pose.m00(), r01 = pose.m01(), r02 = pose.m02(), r10 = pose.m10(), r11 = pose.m11(), r12 = pose.m12(),
                r20 = pose.m20(), r21 = pose.m21(), r22 = pose.m22(), r30 = pose.m30(), r31 = pose.m31(), r32 = pose.m32();
        float s00 = normal.m00(), s01 = normal.m01(), s02 = normal.m02(), s10 = normal.m10(), s11 = normal.m11(), s12 = normal.m12(),
                s20 = normal.m20(), s21 = normal.m21(), s22 = normal.m22();
        float[] ps = posed.pose, ns = posed.normal, m = matrix, n = normalMatrix;
        byte[] flags = posed.flags;
        int[] subtreeEnd = mesh.subtreeEnd, cubeEnd = mesh.cubeEnd;
        int count = mesh.bones.length, index = 0;
        while (index < count) {
            int flag = flags[index], first = index == 0 ? 0 : cubeEnd[index - 1], end = cubeEnd[index];
            if ((flag & Posed.HIDDEN) == 0 && first < end) {
                int at = index * 12, nAt = index * 9;
                float x = ps[at], y = ps[at + 1], z = ps[at + 2];
                m[0] = r00 * x + r10 * y + r20 * z; m[1] = r01 * x + r11 * y + r21 * z; m[2] = r02 * x + r12 * y + r22 * z;
                x = ps[at + 3]; y = ps[at + 4]; z = ps[at + 5];
                m[3] = r00 * x + r10 * y + r20 * z; m[4] = r01 * x + r11 * y + r21 * z; m[5] = r02 * x + r12 * y + r22 * z;
                x = ps[at + 6]; y = ps[at + 7]; z = ps[at + 8];
                m[6] = r00 * x + r10 * y + r20 * z; m[7] = r01 * x + r11 * y + r21 * z; m[8] = r02 * x + r12 * y + r22 * z;
                x = ps[at + 9]; y = ps[at + 10]; z = ps[at + 11];
                m[9] = r00 * x + r10 * y + r20 * z + r30; m[10] = r01 * x + r11 * y + r21 * z + r31; m[11] = r02 * x + r12 * y + r22 * z + r32;
                x = ns[nAt]; y = ns[nAt + 1]; z = ns[nAt + 2];
                n[0] = s00 * x + s10 * y + s20 * z; n[1] = s01 * x + s11 * y + s21 * z; n[2] = s02 * x + s12 * y + s22 * z;
                x = ns[nAt + 3]; y = ns[nAt + 4]; z = ns[nAt + 5];
                n[3] = s00 * x + s10 * y + s20 * z; n[4] = s01 * x + s11 * y + s21 * z; n[5] = s02 * x + s12 * y + s22 * z;
                x = ns[nAt + 6]; y = ns[nAt + 7]; z = ns[nAt + 8];
                n[6] = s00 * x + s10 * y + s20 * z; n[7] = s01 * x + s11 * y + s21 * z; n[8] = s02 * x + s12 * y + s22 * z;
                cubes(mesh, first, end, sink, skip, coverage, windingNormals, smallest);
            }
            index = (flag & Posed.CHILDREN_HIDDEN) != 0 ? subtreeEnd[index] : index + 1;
        }
    }

    private void cubes(CreatureMesh mesh, int first, int end, QuadSink sink, int skip, TextureCoverage coverage, boolean windingNormals,
                       float smallest) {
        float[] ps = matrix, ns = normalMatrix, box = mesh.box, local = mesh.normal, corner = corners, out = faceNormals, sizes = mesh.sizeSquared;
        byte[] present = mesh.present, flags = mesh.flags, sides = mesh.side, winding = mesh.winding, slotFace = mesh.slotFace;
        float m00 = ps[0], m01 = ps[1], m02 = ps[2], m10 = ps[3], m11 = ps[4], m12 = ps[5], m20 = ps[6], m21 = ps[7], m22 = ps[8],
                m30 = ps[9], m31 = ps[10], m32 = ps[11];
        float n00 = ns[0], n01 = ns[1], n02 = ns[2], n10 = ns[3], n11 = ns[4], n12 = ns[5], n20 = ns[6], n21 = ns[7], n22 = ns[8];
        // How much the bone stretches a length at most, squared, times the limit: a cube whose diagonal times this is
        // less than its distance from the eye, both squared, is smaller than the limit on the screen.
        float reach = smallest <= 0f ? 0f : smallest * Math.max(m00 * m00 + m01 * m01 + m02 * m02,
                Math.max(m10 * m10 + m11 * m11 + m12 * m12, m20 * m20 + m21 * m21 + m22 * m22));
        long small = 0;
        long written = 0, skipped = 0;
        for (int cube = first; cube < end; cube++) {
            int mask = present[cube];
            if (mask == 0) continue;
            int all = mask, flag = flags[cube], b = cube * CreatureMesh.BOX, f = cube * CreatureMesh.FACES;
            float x = box[b], y = box[b + 1], z = box[b + 2];
            float ox = m00 * x + m10 * y + m20 * z + m30, oy = m01 * x + m11 * y + m21 * z + m31, oz = m02 * x + m12 * y + m22 * z + m32;
            if (reach > 0f && sizes[cube] * reach < ox * ox + oy * oy + oz * oz) {
                small++;
                skipped += Integer.bitCount(mask);
                continue;
            }
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
            sink.cube(corner, out, mesh, cube, mask);
        }
        facesWritten += written;
        facesSkipped += skipped;
        cubesTooSmall += small;
    }
}
