package dev.nez.arksurvivalreturns.client.draw;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoLocator;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * A GeckoLib model flattened for {@link CubeWriter}: the bones in the order GeckoLib draws them (a bone's
 * cubes, then its children) and every cube as a box that is already turned by its own rotation, so a frame
 * only has to move it by its bone. GeckoLib bakes mirroring, inflation and per-face UVs into its quads; they
 * are read from there, not rebuilt.
 */
public final class CreatureMesh {
    /** Floats per cube in {@link #box}: the corner with the lowest coordinates, then the three edges, in bone space. */
    static final int BOX = 12;
    static final int FACES = 6;
    /** {@link #flags}: every side is there and has an area, so the box hides its own far sides. */
    static final int CLOSED = 1;
    /** {@link #flags}: GeckoLib turns a negative x, y or z of this flat cube's normals around (fixInvertedFlatCube). */
    static final int FIX_X = 2, FIX_Y = 4, FIX_Z = 8;

    private static final Map<BakedGeoModel, CreatureMesh> CACHE = new WeakHashMap<>();
    private static final CreatureMesh UNSUPPORTED = new CreatureMesh();

    final GeoBone[] bones;
    final GeoLocator[] locators;
    final int[] depth, parent, subtreeEnd, cubeEnd;
    final float[] pivot, baseRotation;
    final int maxDepth, cubes;
    final float[] box;
    /** Per cube: the square of its diagonal, in the bone's space. */
    final float[] sizeSquared;
    /** Per face: the normal GeckoLib gives the quad, turned by the cube's rotation. */
    final float[] normal;
    /** Per face: u and v of its four vertices. */
    public final float[] uv;
    /** Per face: which corner of the box each of its four vertices is, as an offset into the writer's corners. */
    public final byte[] corner;
    /** Per face: the middle of its texture coordinates, summed and quartered the way Iris does it. */
    public final float[] mid;
    /**
     * Per face, for the tangent a shader pack is given (Iris, NormalHelper.computeTangent): how v changes from the
     * first vertex to the third and to the second, and one over the area the first three cover in the texture.
     */
    public final float[] tangent;
    /**
     * Per face: which way the texture's v runs against that tangent and the normal the corners give, 1 or -1. It
     * stays the same however the bone is moved, turned, stretched or mirrored. 0 where it is not known beforehand.
     */
    public final byte[] handed;
    /** Per cube: the faces GeckoLib baked a quad for. */
    final byte[] present;
    final byte[] flags;
    /** Per face: the axis it is perpendicular to, twice, plus one on the high side; -1 where that is unclear. */
    final byte[] side;
    /** Per face: which way its vertices wind along that axis. */
    final byte[] winding;
    /** Per cube and side (the axis twice, plus one on the high side): the bit of the face that lies there; closed cubes only. */
    final byte[] slotFace;
    private final Map<Identifier, TextureCoverage> coverage = new ConcurrentHashMap<>();

    /** The flattened model, or null when it holds something this writer does not know: GeckoLib draws it then. */
    public static CreatureMesh of(BakedGeoModel model) {
        CreatureMesh mesh = CACHE.get(model);
        if (mesh == null) {
            mesh = compile(model);
            CACHE.put(model, mesh);
        }
        return mesh == UNSUPPORTED ? null : mesh;
    }

    public static CreatureMesh compile(BakedGeoModel model) {
        Builder builder = new Builder();
        for (GeoBone bone : model.topLevelBones()) builder.bone(bone, 0, -1);
        return builder.supported ? new CreatureMesh(builder) : UNSUPPORTED;
    }

    public int cubeCount() { return cubes; }

    public int boneCount() { return bones.length; }

    boolean closed(int cube) { return (flags[cube] & CLOSED) != 0; }

    /** What the texture holds under every face; filled in the background, {@link TextureCoverage#ready} until then false. */
    TextureCoverage coverage(Identifier texture) {
        TextureCoverage found = coverage.get(texture);
        if (found == null) {
            found = new TextureCoverage(this);
            coverage.put(texture, found);
            found.load(texture);
        }
        return found;
    }

    private CreatureMesh() {
        bones = new GeoBone[0];
        locators = new GeoLocator[0];
        depth = parent = subtreeEnd = cubeEnd = new int[0];
        pivot = baseRotation = box = sizeSquared = normal = uv = mid = tangent = new float[0];
        corner = present = flags = side = winding = slotFace = handed = new byte[0];
        maxDepth = cubes = 0;
    }

    private CreatureMesh(Builder builder) {
        bones = builder.bones.toArray(GeoBone[]::new);
        locators = builder.locators.toArray(GeoLocator[]::new);
        depth = builder.depth.toIntArray();
        parent = builder.parent.toIntArray();
        subtreeEnd = builder.subtreeEnd.toIntArray();
        cubeEnd = builder.cubeEnd.toIntArray();
        pivot = builder.pivot.toFloatArray();
        baseRotation = builder.baseRotation.toFloatArray();
        box = builder.box.toFloatArray();
        sizeSquared = builder.sizeSquared.toFloatArray();
        normal = builder.normal.toFloatArray();
        uv = builder.uv.toFloatArray();
        corner = builder.corner.toByteArray();
        mid = builder.mid.toFloatArray();
        tangent = builder.tangent.toFloatArray();
        handed = builder.handed.toByteArray();
        present = builder.present.toByteArray();
        flags = builder.flags.toByteArray();
        side = builder.side.toByteArray();
        winding = builder.winding.toByteArray();
        cubes = present.length;
        slotFace = new byte[cubes * FACES];
        for (int cube = 0; cube < cubes; cube++) {
            if ((flags[cube] & CLOSED) == 0) continue;
            for (int face = 0; face < FACES; face++) slotFace[cube * FACES + side[cube * FACES + face]] = (byte) (1 << face);
        }
        int deepest = 0;
        for (int value : depth) deepest = Math.max(deepest, value);
        maxDepth = deepest;
    }

    private static final class Builder {
        final List<GeoBone> bones = new ArrayList<>();
        final List<GeoLocator> locators = new ArrayList<>();
        final IntArrayList depth = new IntArrayList(), parent = new IntArrayList(), subtreeEnd = new IntArrayList(), cubeEnd = new IntArrayList();
        final FloatArrayList sizeSquared = new FloatArrayList();
        final FloatArrayList pivot = new FloatArrayList(), baseRotation = new FloatArrayList();
        final FloatArrayList box = new FloatArrayList(), normal = new FloatArrayList(), uv = new FloatArrayList();
        final ByteArrayList corner = new ByteArrayList(), present = new ByteArrayList(), flags = new ByteArrayList();
        final ByteArrayList side = new ByteArrayList(), winding = new ByteArrayList();
        final FloatArrayList mid = new FloatArrayList(), tangent = new FloatArrayList();
        final ByteArrayList handed = new ByteArrayList();
        boolean supported = true;

        void bone(GeoBone bone, int level, int above) {
            int index = bones.size();
            bones.add(bone);
            depth.add(level);
            parent.add(above);
            subtreeEnd.add(0);
            pivot.add(bone.pivotX() / 16f);
            pivot.add(bone.pivotY() / 16f);
            pivot.add(bone.pivotZ() / 16f);
            baseRotation.add(bone.baseRotX());
            baseRotation.add(bone.baseRotY());
            baseRotation.add(bone.baseRotZ());
            for (GeoLocator locator : bone.locators()) locators.add(locator);
            if (bone instanceof CuboidGeoBone cuboid) {
                for (GeoCube cube : cuboid.cubes) cube(cube);
            } else {
                supported = false;
            }
            cubeEnd.add(present.size());
            for (GeoBone child : bone.children()) bone(child, level + 1, index);
            subtreeEnd.set(index, bones.size());
        }

        void cube(GeoCube cube) {
            GeoQuad[] quads = cube.quads();
            if (quads.length != FACES) {
                supported = false;
                return;
            }
            float[] low = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] high = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            int mask = 0;
            for (int face = 0; face < FACES; face++) {
                if (quads[face] == null) continue;
                mask |= 1 << face;
                if (quads[face].vertices().length != 4) {
                    supported = false;
                    return;
                }
                for (GeoVertex vertex : quads[face].vertices()) {
                    float[] position = {vertex.posX(), vertex.posY(), vertex.posZ()};
                    for (int axis = 0; axis < 3; axis++) {
                        low[axis] = Math.min(low[axis], position[axis]);
                        high[axis] = Math.max(high[axis], position[axis]);
                    }
                }
            }
            if (mask == 0) low[0] = low[1] = low[2] = high[0] = high[1] = high[2] = 0f;
            // GeoCube.render on a matrix that is not yet anywhere: to the cube's pivot, Z, Y, X, and back, with the
            // calls and the floats GeckoLib uses, so the turn carries the same rounding as its own.
            Quaternionf aboutZ = new Quaternionf().rotationXYZ(0f, 0f, (float) cube.rotation().z()),
                    aboutY = new Quaternionf().rotationXYZ(0f, (float) cube.rotation().y(), 0f),
                    aboutX = new Quaternionf().rotationXYZ((float) cube.rotation().x(), 0f, 0f);
            Matrix4f placed = new Matrix4f().translate((float) (cube.pivot().x() / 16.0), (float) (cube.pivot().y() / 16.0), (float) (cube.pivot().z() / 16.0))
                    .rotate(aboutZ).rotate(aboutY).rotate(aboutX)
                    .translate((float) (-cube.pivot().x() / 16.0), (float) (-cube.pivot().y() / 16.0), (float) (-cube.pivot().z() / 16.0));
            Matrix3f rotation = new Matrix3f().rotate(aboutZ).rotate(aboutY).rotate(aboutX);
            Vector3d origin = new Vector3d(
                    (double) placed.m00() * low[0] + (double) placed.m10() * low[1] + (double) placed.m20() * low[2] + placed.m30(),
                    (double) placed.m01() * low[0] + (double) placed.m11() * low[1] + (double) placed.m21() * low[2] + placed.m31(),
                    (double) placed.m02() * low[0] + (double) placed.m12() * low[1] + (double) placed.m22() * low[2] + placed.m32());
            box.add((float) origin.x);
            box.add((float) origin.y);
            box.add((float) origin.z);
            double diagonal = 0;
            for (int axis = 0; axis < 3; axis++) diagonal += ((double) high[axis] - low[axis]) * ((double) high[axis] - low[axis]);
            sizeSquared.add((float) diagonal);
            for (int axis = 0; axis < 3; axis++) {
                double length = (double) high[axis] - low[axis];
                Vector3d edge = axis == 0 ? new Vector3d(placed.m00(), placed.m01(), placed.m02()).mul(length)
                        : axis == 1 ? new Vector3d(placed.m10(), placed.m11(), placed.m12()).mul(length)
                        : new Vector3d(placed.m20(), placed.m21(), placed.m22()).mul(length);
                box.add((float) edge.x);
                box.add((float) edge.y);
                box.add((float) edge.z);
            }
            boolean[] sides = new boolean[FACES];
            boolean closed = mask == 0b111111 && high[0] > low[0] && high[1] > low[1] && high[2] > low[2];
            for (int face = 0; face < FACES; face++) {
                GeoQuad quad = quads[face];
                if (quad == null) {
                    for (int i = 0; i < 3; i++) normal.add(0f);
                    for (int i = 0; i < 8; i++) uv.add(0f);
                    for (int i = 0; i < 4; i++) corner.add((byte) 0);
                    for (int i = 0; i < 2; i++) mid.add(0f);
                    for (int i = 0; i < 3; i++) tangent.add(0f);
                    handed.add((byte) 0);
                    side.add((byte) -1);
                    winding.add((byte) 0);
                    continue;
                }
                Vector3d turned = new Vector3d(
                        (double) rotation.m00() * quad.normalX() + (double) rotation.m10() * quad.normalY() + (double) rotation.m20() * quad.normalZ(),
                        (double) rotation.m01() * quad.normalX() + (double) rotation.m11() * quad.normalY() + (double) rotation.m21() * quad.normalZ(),
                        (double) rotation.m02() * quad.normalX() + (double) rotation.m12() * quad.normalY() + (double) rotation.m22() * quad.normalZ());
                normal.add((float) turned.x);
                normal.add((float) turned.y);
                normal.add((float) turned.z);
                GeoVertex[] vertices = quad.vertices();
                int[] bits = new int[4];
                for (int i = 0; i < 4; i++) {
                    float[] position = {vertices[i].posX(), vertices[i].posY(), vertices[i].posZ()};
                    for (int axis = 0; axis < 3; axis++) {
                        if (position[axis] == high[axis] && high[axis] > low[axis]) bits[i] |= 1 << axis;
                        else if (position[axis] != low[axis]) supported = false;
                    }
                    uv.add(vertices[i].texU());
                    uv.add(vertices[i].texV());
                    corner.add((byte) (bits[i] * 3));
                }
                // The axis the face is flat along: the one its four corners agree on while the box is flat or they
                // differ along the other two.
                int found = -1;
                for (int axis = 0; axis < 3; axis++) {
                    int bit = 1 << axis;
                    boolean agree = (bits[0] & bit) == (bits[1] & bit) && (bits[1] & bit) == (bits[2] & bit) && (bits[2] & bit) == (bits[3] & bit);
                    if (agree && (found < 0 || high[axis] == low[axis])) found = axis;
                }
                texture(vertices, found >= 0);
                if (found < 0) {
                    side.add((byte) -1);
                    winding.add((byte) 0);
                    closed = false;
                    continue;
                }
                int slot = found * 2 + ((bits[0] >> found) & 1);
                if (sides[slot]) closed = false;
                sides[slot] = true;
                side.add((byte) slot);
                // The way Iris takes a quad's normal from its corners: (v2 - v0) x (v3 - v1).
                double[] a = difference(vertices[2], vertices[0]), b = difference(vertices[3], vertices[1]);
                double[] cross = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
                winding.add((byte) Math.signum(cross[found]));
            }
            present.add((byte) mask);
            int flag = closed ? CLOSED : 0;
            boolean flatX = cube.size().x() == 0.0, flatY = cube.size().y() == 0.0, flatZ = cube.size().z() == 0.0;
            if (flatY || flatZ) flag |= FIX_X;
            if (flatX || flatZ) flag |= FIX_Y;
            if (flatX || flatY) flag |= FIX_Z;
            if (flatX || flatY || flatZ) flag &= ~CLOSED;
            flags.add((byte) flag);
        }

        /**
         * What Iris works out for every quad it is handed, as far as the model alone decides it. The floats are
         * taken in its order, so the middle and the tangent come out as its own to the last digit.
         *
         * @param windingNormal the writer gives this face the normal its corners wind to, as Iris takes it
         */
        private void texture(GeoVertex[] vertices, boolean windingNormal) {
            float u0 = vertices[0].texU(), v0 = vertices[0].texV(), u1 = vertices[1].texU(), v1 = vertices[1].texV();
            float u2 = vertices[2].texU(), v2 = vertices[2].texV(), u3 = vertices[3].texU(), v3 = vertices[3].texV();
            mid.add((u0 + u1 + u2 + u3) * 0.25f);
            mid.add((v0 + v1 + v2 + v3) * 0.25f);
            float deltaU1 = u1 - u0, deltaV2 = v2 - v0, deltaU2 = u2 - u0, deltaV1 = v1 - v0;
            float area = deltaU1 * deltaV2 - deltaU2 * deltaV1, f = (double) area == 0.0 ? 1.0f : 1.0f / area;
            tangent.add(deltaV2);
            tangent.add(deltaV1);
            tangent.add(f);
            // The sign of bitangent . (tangent x normal), with the normal (v2 - v0) x (v3 - v1): all three lie in
            // or across the face, so no movement of the bone changes it.
            double[] first = difference(vertices[1], vertices[0]), second = difference(vertices[2], vertices[0]);
            double[] a = difference(vertices[2], vertices[0]), b = difference(vertices[3], vertices[1]);
            double[] n = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
            double[] t = new double[3], bi = new double[3];
            for (int k = 0; k < 3; k++) {
                t[k] = (double) f * ((double) deltaV2 * first[k] - (double) deltaV1 * second[k]);
                bi[k] = (double) f * (-(double) deltaU2 * first[k] + (double) deltaU1 * second[k]);
            }
            double dot = bi[0] * (t[1] * n[2] - t[2] * n[1]) + bi[1] * (t[2] * n[0] - t[0] * n[2]) + bi[2] * (t[0] * n[1] - t[1] * n[0]);
            double size = Math.sqrt(bi[0] * bi[0] + bi[1] * bi[1] + bi[2] * bi[2]) * Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2])
                    * Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            // A face without an area, in the model or in the texture, has no side to tell: asked at run time.
            handed.add((byte) (!windingNormal || !(Math.abs(dot) > 1e-3 * size) ? 0 : dot < 0.0 ? -1 : 1));
        }

        private static double[] difference(GeoVertex a, GeoVertex b) {
            return new double[]{(double) a.posX() - b.posX(), (double) a.posY() - b.posY(), (double) a.posZ() - b.posZ()};
        }
    }
}
