package dev.nez.arksurvivalreturns.client;

import java.util.HashMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.feature.map.DangerMapView;
import net.minecraft.client.Minecraft;
import xaero.map.MapProcessor;

/** Read-only snapshot of Xaero's loaded/cached terrain, using its own discovered-height sentinel. */
public final class XaeroExploration implements DangerMapView.Exploration {
    private final MapProcessor processor;
    private final int layer;
    private final Map<Long, Boolean> known = new HashMap<>();
    private XaeroExploration(MapProcessor processor) { this.processor = processor; layer = processor.getCurrentCaveLayer(); }
    public static DangerMapView.Exploration current(String dimension) {
        var screen = Minecraft.getInstance().screen;
        if (!DangerMapClient.isMap(screen)) return (a,b,c,d) -> false;
        try {
            // Avoid importing GuiMap's XaeroLib superclass into the optional compile dependency.
            var processor = (MapProcessor)screen.getClass().getMethod("getMapProcessor").invoke(screen);
            if (!processor.getMapWorld().getCurrentDimension().getDimId().identifier().toString().equals(dimension)) return (a,b,c,d) -> false;
            return new XaeroExploration(processor);
        } catch (ReflectiveOperationException | LinkageError e) {
            return (a,b,c,d) -> false; // Fail closed on an unsupported map API; never color unknown terrain.
        }
    }
    @Override public boolean contains(int minX, int minZ, int maxX, int maxZ) {
        int x0 = minX >> 4, z0 = minZ >> 4, x1 = maxX >> 4, z1 = maxZ >> 4;
        if ((long)(x1 - x0 + 1) * (z1 - z0 + 1) > 256) return false;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            long key = ((long)x << 32) ^ (z & 0xFFFFFFFFL);
            Boolean result = known.get(key);
            if (result == null) {
                if (known.size() >= 32768) return false;
                var chunk = processor.getMapChunk(layer, x >> 2, z >> 2);
                // getMapChunk(...)->getLeafMapRegion(..., false): no disk/world loading or creation.
                result = chunk != null && chunk.getLeafTexture() != null
                        && chunk.getLeafTexture().getHeight((x & 3) << 4, (z & 3) << 4) != Short.MAX_VALUE;
                known.put(key, result);
            }
            if (!result) return false;
        }
        return true;
    }
}
