package dev.nez.arksurvivalreturns.client.accessory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.jspecify.annotations.Nullable;

/**
 * Loads assets/arksurvivalreturns/worn/*.json (written by tools/build_accessories.py) and bakes each into
 * a {@link WornModel}: the vanilla humanoid skeleton with the accessory's bones hung on its parts. Cubes use
 * the ModelPart box UV layout, so the same file renders here and in the Python previews.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class WornModels {
    public record Entry(WornModel model, Identifier texture, @Nullable Identifier glow) {}

    private static final Map<String, PartPose> PARTS = new LinkedHashMap<>();
    private static final Map<String, float[]> CENTRES = new HashMap<>();
    private static volatile Map<String, Entry> models = Map.of();

    static {
        PARTS.put("head", PartPose.offset(0, 0, 0));
        PARTS.put("body", PartPose.offset(0, 0, 0));
        PARTS.put("right_arm", PartPose.offset(-5, 2, 0));
        PARTS.put("left_arm", PartPose.offset(5, 2, 0));
        PARTS.put("right_leg", PartPose.offset(-1.9f, 12, 0));
        PARTS.put("left_leg", PartPose.offset(1.9f, 12, 0));
        CENTRES.put("head", new float[]{0, -4, 0});
        CENTRES.put("body", new float[]{0, 6, 0});
        CENTRES.put("right_arm", new float[]{-1, 4, 0});
        CENTRES.put("left_arm", new float[]{1, 4, 0});
        CENTRES.put("right_leg", new float[]{0, 6, 0});
        CENTRES.put("left_leg", new float[]{0, 6, 0});
    }

    public static @Nullable Entry get(String id) {
        return models.get(id);
    }

    @SubscribeEvent static void reload(AddClientReloadListenersEvent event) {
        event.addListener(ArkSurvivalReturns.id("worn_accessories"), new SimplePreparableReloadListener<Map<String, JsonObject>>() {
            @Override
            protected Map<String, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
                Map<String, JsonObject> files = new HashMap<>();
                manager.listResources("worn", id -> id.getNamespace().equals(ArkSurvivalReturns.MOD_ID)
                        && id.getPath().endsWith(".json")).forEach((id, resource) -> {
                    try (Reader reader = resource.openAsReader()) {
                        String name = id.getPath().substring("worn/".length(), id.getPath().length() - ".json".length());
                        files.put(name, JsonParser.parseReader(reader).getAsJsonObject());
                    } catch (Exception broken) {
                        ArkSurvivalReturns.LOGGER.warn("Unreadable worn accessory model {}", id, broken);
                    }
                });
                return files;
            }

            @Override
            protected void apply(Map<String, JsonObject> files, ResourceManager manager, ProfilerFiller profiler) {
                Map<String, Entry> baked = new HashMap<>();
                files.forEach((name, json) -> {
                    try {
                        Identifier texture = ArkSurvivalReturns.id("textures/entity/accessory/" + name + ".png");
                        Identifier glow = json.has("glow") && json.get("glow").getAsBoolean()
                                ? ArkSurvivalReturns.id("textures/entity/accessory/" + name + "_glow.png") : null;
                        baked.put(name, new Entry(bake(json), texture, glow));
                    } catch (RuntimeException broken) {
                        ArkSurvivalReturns.LOGGER.warn("Could not bake worn accessory model {}", name, broken);
                    }
                });
                models = Map.copyOf(baked);
            }
        });
    }

    static WornModel bake(JsonObject json) {
        JsonArray size = json.getAsJsonArray("texture_size");
        float texW = size.get(0).getAsFloat(), texH = size.get(1).getAsFloat();
        Map<String, Map<String, Map<String, ModelPart>>> byPart = new LinkedHashMap<>();
        List<WornModel.Bone> bones = new ArrayList<>();
        List<WornModel.Fit> fits = new ArrayList<>();
        int unique = 0;
        for (JsonElement element : json.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String humanoid = bone.get("part").getAsString();
            String fit = bone.has("fit") ? bone.get("fit").getAsString() : "none";
            float[] pivot = floats(bone.getAsJsonArray("pivot"));
            float[] rest = radians(floats(bone.getAsJsonArray("rotation")));
            float[] open = bone.has("open") ? radians(floats(bone.getAsJsonArray("open"))) : rest;
            float[] centre = fit.equals("none") ? new float[3] : CENTRES.get(humanoid);
            List<ModelPart.Cube> cubes = new ArrayList<>();
            for (JsonElement c : bone.getAsJsonArray("cubes")) cubes.add(cube(c.getAsJsonObject(), texW, texH));
            ModelPart part = new ModelPart(cubes, Map.of());
            part.setInitialPose(PartPose.offsetAndRotation(pivot[0] - centre[0], pivot[1] - centre[1], pivot[2] - centre[2],
                    rest[0], rest[1], rest[2]));
            part.resetPose();
            String name = bone.get("name").getAsString() + "_" + unique++;
            byPart.computeIfAbsent(humanoid, k -> new LinkedHashMap<>()).computeIfAbsent(fit, k -> new LinkedHashMap<>()).put(name, part);
            bones.add(new WornModel.Bone(part, humanoid, text(bone, "side"), text(bone, "arms"), text(bone, "anim"), rest, open));
        }
        Map<String, ModelPart> rootChildren = new LinkedHashMap<>();
        for (var entry : PARTS.entrySet()) {
            String humanoid = entry.getKey();
            Map<String, ModelPart> children = new LinkedHashMap<>();
            byPart.getOrDefault(humanoid, Map.of()).forEach((fit, members) -> {
                ModelPart wrapper = new ModelPart(List.of(), members);
                float[] centre = fit.equals("none") ? new float[3] : CENTRES.get(humanoid);
                wrapper.setInitialPose(PartPose.offset(centre[0], centre[1], centre[2]));
                wrapper.resetPose();
                children.put("fit_" + fit, wrapper);
                if (!fit.equals("none")) fits.add(new WornModel.Fit(wrapper, humanoid, fit));
            });
            if (humanoid.equals("head")) children.put("hat", new ModelPart(List.of(), Map.of()));
            ModelPart part = new ModelPart(List.of(), children);
            part.setInitialPose(entry.getValue());
            part.resetPose();
            rootChildren.put(humanoid, part);
        }
        return new WornModel(new ModelPart(List.of(), rootChildren), bones, fits);
    }

    private static ModelPart.Cube cube(JsonObject c, float texW, float texH) {
        float[] origin = floats(c.getAsJsonArray("origin"));
        float[] size = floats(c.getAsJsonArray("size"));
        float[] grow = c.has("grow") ? floats(c.getAsJsonArray("grow")) : new float[3];
        JsonArray uv = c.getAsJsonArray("uv");
        Set<Direction> faces = EnumSet.allOf(Direction.class);
        if (c.has("faces")) {
            faces = EnumSet.noneOf(Direction.class);
            for (JsonElement face : c.getAsJsonArray("faces")) faces.add(Direction.valueOf(face.getAsString().toUpperCase(Locale.ROOT)));
        }
        boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
        return new ModelPart.Cube(uv.get(0).getAsInt(), uv.get(1).getAsInt(), origin[0], origin[1], origin[2],
                size[0], size[1], size[2], grow[0], grow[1], grow[2], mirror, texW, texH, faces);
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsFloat();
        return out;
    }

    private static float[] radians(float[] degrees) {
        return new float[]{degrees[0] * Mth.DEG_TO_RAD, degrees[1] * Mth.DEG_TO_RAD, degrees[2] * Mth.DEG_TO_RAD};
    }

    private static @Nullable String text(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : null;
    }

    private WornModels() {}
}
