package dev.nez.arksurvivalreturns.client.map;

import dev.nez.arksurvivalreturns.client.DangerMapClient;
import dev.nez.arksurvivalreturns.feature.map.DangerMapView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * The tribe's map (A01 OPT-14): the land any member has explored, the players in view, and the danger zones over it.
 * Drag to pan, scroll to zoom, D toggles the zones, the map key or Escape closes it.
 */
public final class ArkMapScreen extends Screen {
    private static final double[] ZOOMS = {0.25, 0.5, 1, 2, 4};
    private static final int[] DANGER = {0x6AB04C, 0xE1A23B, 0xC0392B};
    private int zoom = 2;
    private double centreX, centreZ;
    private boolean zones = true;

    public ArkMapScreen() {
        super(Component.translatable("map.arksurvivalreturns.title"));
        var player = Minecraft.getInstance().player;
        if (player != null) { centreX = player.getX(); centreZ = player.getZ(); }
    }

    @Override public boolean isPauseScreen() { return false; }

    private double scale() { return ZOOMS[zoom]; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF1B1A16);
        double scale = scale();
        int pixels = ArkMapClient.PIXELS;
        int drawn = (int) Math.ceil(pixels * scale);
        for (var entry : ArkMapClient.regions().entrySet()) {
            int rx = ChunkPos.getX(entry.getKey()), rz = ChunkPos.getZ(entry.getKey());
            int x = (int) Math.floor(width / 2.0 + (rx * pixels - centreX) * scale);
            int y = (int) Math.floor(height / 2.0 + (rz * pixels - centreZ) * scale);
            if (x > width || y > height || x + drawn < 0 || y + drawn < 0) continue;
            g.blit(RenderPipelines.GUI_TEXTURED, entry.getValue().id, x, y, 0f, 0f, drawn, drawn, pixels, pixels, pixels, pixels);
        }
        if (zones && DangerMapClient.profile() != null) {
            var profile = DangerMapClient.profile();
            for (var cell : DangerMapView.cells(width, height, centreX, centreZ, scale, profile.originX(), profile.originZ(), profile.bandWidth(),
                    (minX, minZ, maxX, maxZ) -> ArkMapClient.explored(minX >> 4, minZ >> 4) || ArkMapClient.explored(maxX >> 4, maxZ >> 4)))
                g.fill(cell.left(), cell.top(), cell.right(), cell.bottom(), (DANGER[cell.danger() - 1] & 0xFFFFFF) | 0x40000000);
        }
        var level = Minecraft.getInstance().level;
        var me = Minecraft.getInstance().player;
        if (level != null) for (var player : level.players()) {
            int x = (int) (width / 2.0 + (player.getX() - centreX) * scale), y = (int) (height / 2.0 + (player.getZ() - centreZ) * scale);
            int colour = player == me ? 0xFFFFFFFF : 0xFF7FD3FF;
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
            g.fill(x - 1, y - 1, x + 2, y + 2, colour);
            if (player != me) g.text(font, player.getName(), x + 4, y - 4, colour, true);
        }
        g.text(font, title, 8, 8, 0xFFE8DCC0, true);
        g.text(font, Component.translatable("map.arksurvivalreturns.hint"), 8, height - 14, 0xFFB8AC90, true);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int before = zoom;
        zoom = Math.clamp(zoom + (scrollY > 0 ? 1 : scrollY < 0 ? -1 : 0), 0, ZOOMS.length - 1);
        if (zoom != before) {
            // Keep the point under the cursor where it is.
            double worldX = centreX + (x - width / 2.0) / ZOOMS[before], worldZ = centreZ + (y - height / 2.0) / ZOOMS[before];
            centreX = worldX - (x - width / 2.0) / scale();
            centreZ = worldZ - (y - height / 2.0) / scale();
        }
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        centreX -= dx / scale();
        centreZ -= dy / scale();
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (ArkMapClient.OPEN.matches(event)) { onClose(); return true; }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_D) { zones = !zones; return true; }
        return super.keyPressed(event);
    }
}
