package dev.nez.arksurvivalreturns.client;

import java.util.List;
import dev.nez.arksurvivalreturns.feature.map.DangerMapPayload;
import dev.nez.arksurvivalreturns.feature.map.DangerMapView;
import dev.nez.arksurvivalreturns.feature.spawn.DangerBands;
import io.github.billstark001.xaerobridge.api.MapOverlayContext;
import io.github.billstark001.xaerobridge.api.XaeroWorldMapBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Optional integration. The bridge itself is loaded only on clients that installed it. */
public final class XaeroDangerOverlay {
    private static final int[] COLORS = {0xFF4BAE66, 0xFFA8CA53, 0xFFE4C653, 0xFFE98646, 0xFF994EB3};
    private record View(int width, int height, double x, double z, double scale, DangerMapPayload profile) {}
    private static View cachedView;
    private static List<DangerMapView.Cell> cachedCells = List.of();
    private static boolean overworld;
    private static boolean enabled;
    private static long refreshedAt;
    private static DangerMapView.Exploration exploration;
    public static void register() {
        XaeroWorldMapBridge.registerMapOverlay("arksurvivalreturns:danger", 100, XaeroDangerOverlay::render);
        NeoForge.EVENT_BUS.addListener(XaeroDangerOverlay::legend);
        NeoForge.EVENT_BUS.addListener(XaeroDangerOverlay::init);
    }
    public static void init(ScreenEvent.Init.Post event) {
        if (!DangerMapClient.isMap(event.getScreen())) return;
        cachedView = null;
        exploration = null;
        event.addListener(net.minecraft.client.gui.components.Button.builder(toggleText(), button -> {
            enabled = !enabled; cachedView = null; button.setMessage(toggleText());
        }).bounds(8, 25, 146, 20).build());
    }
    private static Component toggleText() { return Component.translatable("map.arksurvivalreturns.filter_" + (enabled ? "on" : "off")); }
    private static void render(MapOverlayContext context) {
        overworld = context.dimension().equals("minecraft:overworld");
        if (!enabled || !DangerMapClient.unlocked() || !overworld) return;
        var profile = DangerMapClient.profile();
        // Quantized camera key plus a minimum rebuild interval keep pan/zoom from rasterizing every frame.
        var view = new View(context.width(), context.height(), Math.floor(context.cameraX()), Math.floor(context.cameraZ()),
                Math.round(context.pixelsPerBlock() * 1000.0) / 1000.0, profile);
        if (exploration == null) exploration = XaeroExploration.current(context.dimension());
        long now = System.nanoTime();
        boolean moved = !view.equals(cachedView);
        boolean stale = now - refreshedAt > 250_000_000L;
        if ((moved && now - refreshedAt >= 100_000_000L) || stale) {
            cachedCells = DangerMapView.cells(view.width, view.height, view.x, view.z, view.scale,
                    profile.originX(), profile.originZ(), profile.bandWidth(), exploration);
            cachedView = view;
            refreshedAt = now;
        }
        // Xaero submits a framebuffer blit; keep GUI batching from sorting the tint behind it.
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.nextStratum();
        for (var cell : cachedCells) context.canvas().fill(cell.left(), cell.top(), cell.right(), cell.bottom(),
                (COLORS[cell.danger() - 1] & 0xFFFFFF) | 0x48000000);
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.nextStratum();
    }
    public static void legend(ScreenEvent.Render.Post event) {
        if (!enabled || !DangerMapClient.isMap(event.getScreen()) || !DangerMapClient.unlocked() || cachedView == null) return;
        var graphics = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int x = 8, y = 48;
        graphics.fill(x - 4, y - 4, x + 154, y + (overworld ? 82 : 24), 0xD0182028);
        graphics.text(font, Component.translatable("map.arksurvivalreturns." + (overworld ? "legend" : "unrated")), x, y, 0xFFFFFFFF);
        if (!overworld) return;
        for (int i = 0; i < 5; i++) {
            graphics.fill(x, y + 14 + i * 11, x + 8, y + 22 + i * 11, COLORS[i]);
            graphics.text(font, Component.translatable("map.arksurvivalreturns.rank_" + (i + 1)), x + 12, y + 14 + i * 11, 0xFFFFFFFF);
        }
        var view = cachedView;
        double wx = view.x + (event.getMouseX() - view.width / 2.0) / view.scale;
        double wz = view.z + (event.getMouseY() - view.height / 2.0) / view.scale;
        if (Double.isFinite(wx) && Double.isFinite(wz) && Math.abs(wx) <= 30_000_000 && Math.abs(wz) <= 30_000_000
                && exploration.contains((int)Math.floor(wx), (int)Math.floor(wz), (int)Math.floor(wx), (int)Math.floor(wz))) {
            int rank = DangerBands.level((int)Math.floor(wx), (int)Math.floor(wz), view.profile.originX(), view.profile.originZ(), view.profile.bandWidth());
            graphics.text(font, Component.translatable("map.arksurvivalreturns.cursor", rank), x, y + 71, 0xFFFFFFFF);
        }
    }
    private XaeroDangerOverlay() {}
}
