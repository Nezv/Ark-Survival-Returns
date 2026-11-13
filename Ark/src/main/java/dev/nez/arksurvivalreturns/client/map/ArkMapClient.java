package dev.nez.arksurvivalreturns.client.map;

import java.util.HashMap;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.map.TribeMapService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * The Ark map's client half (A01 OPT-14): the tribe's explored chunks as the server sent them, drawn into textures of
 * 32 by 32 chunks. Nothing is computed from the world here; a texture is rewritten only when one of its chunks arrives.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class ArkMapClient {
    /** Chunks per side of one region texture. */
    static final int REGION = 32;
    static final int PIXELS = REGION * 16;
    static final KeyMapping OPEN = new KeyMapping("key.arksurvivalreturns.tribe_map", InputConstants.Type.KEYSYM,
            InputConstants.KEY_N, dev.nez.arksurvivalreturns.client.JournalClient.CATEGORY);
    private static final Map<Long, byte[]> CHUNKS = new HashMap<>();
    private static final Map<Long, Region> REGIONS = new HashMap<>();

    static final class Region {
        final Identifier id;
        final DynamicTexture texture;
        boolean dirty;
        Region(int x, int z) {
            id = ArkSurvivalReturns.id("map/region_" + x + "_" + z);
            texture = new DynamicTexture(() -> "ark map region", PIXELS, PIXELS, true);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }

    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TribeMapService.Tiles.TYPE, (payload, context) -> {
            for (var tile : payload.tiles()) put(tile.chunk(), tile.colours());
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        while (OPEN.consumeClick()) if (minecraft.player != null && minecraft.screen == null) minecraft.setScreen(new ArkMapScreen());
    }

    static void put(long chunk, byte[] colours) {
        if (colours.length != 256) return;
        CHUNKS.put(chunk, colours);
        int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
        int rx = Math.floorDiv(cx, REGION), rz = Math.floorDiv(cz, REGION);
        var region = REGIONS.computeIfAbsent(ChunkPos.pack(rx, rz), ignored -> new Region(rx, rz));
        NativeImage image = region.texture.getPixels();
        if (image == null) return;
        int ox = Math.floorMod(cx, REGION) * 16, oz = Math.floorMod(cz, REGION) * 16;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int packed = colours[z * 16 + x] & 0xFF;
            image.setPixel(ox + x, oz + z, packed == 0 ? 0 : MapColor.getColorFromPackedId(packed) | 0xFF000000);
        }
        region.dirty = true;
    }

    static boolean explored(int chunkX, int chunkZ) { return CHUNKS.containsKey(ChunkPos.pack(chunkX, chunkZ)); }

    static Map<Long, Region> regions() {
        for (var region : REGIONS.values()) if (region.dirty) { region.texture.upload(); region.dirty = false; }
        return REGIONS;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        CHUNKS.clear();
        var textures = Minecraft.getInstance().getTextureManager();
        for (var region : REGIONS.values()) textures.release(region.id);
        REGIONS.clear();
    }

    private ArkMapClient() {}
}
