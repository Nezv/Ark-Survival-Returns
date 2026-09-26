package dev.nez.arksurvivalreturns.client.title;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Rest-pose extents of a GeckoLib geometry in blocks (16 model units), with GeckoLib's mirrored X. Cube
 * rotations are ignored: the menu scene only needs the height to scale by and the footprint to size its box.
 */
public record ModelBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    static final ModelBounds UNIT = new ModelBounds(-0.5f, 0f, -0.5f, 0.5f, 1f, 0.5f);

    public float height() { return maxY - minY; }
    public float centerX() { return (minX + maxX) / 2; }
    public float centerZ() { return (minZ + maxZ) / 2; }
    /** Half the horizontal diagonal: at any yaw the model stays within this distance of its centre. */
    public float radius() { return (float) Math.hypot(maxX - minX, maxZ - minZ) / 2; }

    public static ModelBounds of(JsonObject geo) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        JsonArray geometries = geo.getAsJsonArray("minecraft:geometry");
        if (geometries == null) return UNIT;
        for (JsonElement geometry : geometries) {
            JsonArray bones = geometry.getAsJsonObject().getAsJsonArray("bones");
            if (bones == null) continue;
            for (JsonElement bone : bones) {
                JsonArray cubes = bone.getAsJsonObject().getAsJsonArray("cubes");
                if (cubes == null) continue;
                for (JsonElement element : cubes) {
                    JsonObject cube = element.getAsJsonObject();
                    JsonArray origin = cube.getAsJsonArray("origin");
                    JsonArray size = cube.getAsJsonArray("size");
                    if (origin == null || size == null) continue;
                    float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
                    for (int axis = 0; axis < 3; axis++) {
                        float o = origin.get(axis).getAsFloat();
                        float s = size.get(axis).getAsFloat();
                        // GeckoLib bakes Bedrock X mirrored: -(origin + size) .. -origin.
                        float lo = axis == 0 ? -(o + s) : o;
                        min[axis] = Math.min(min[axis], lo - inflate);
                        max[axis] = Math.max(max[axis], lo + s + inflate);
                    }
                }
            }
        }
        if (min[0] > max[0]) return UNIT;
        return new ModelBounds(min[0] / 16, min[1] / 16, min[2] / 16, max[0] / 16, max[1] / 16, max[2] / 16);
    }
}
