package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.debug.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-only terminal overlay; the server owns the inspected data. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class DinoDebugClient {
    private static DinoDebugPayload snapshot;
    private static long receivedAt;
    private static boolean matchesTarget;
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(DinoDebugPayload.TYPE, (payload, context) -> {
            snapshot = payload.target().equals(DinoDebugPayload.NO_TARGET) ? null : payload;
            receivedAt = System.nanoTime(); matchesTarget = false;
        });
    }
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ArkSurvivalReturns.id("dino_debug"), (graphics, delta) -> render(graphics));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!DebugSpyglass.using(mc.player) || mc.level == null) { reset(); return; }
        var target = DinoDebugSync.target(mc.player);
        matchesTarget = snapshot != null && target != null && target.getUUID().equals(snapshot.target())
                && mc.level.dimension().identifier().toString().equals(snapshot.dimension())
                && System.nanoTime() - receivedAt < 2_000_000_000L;
        // Never resurrect a previous target's values after turning away or changing worlds.
        if (!matchesTarget) snapshot = null;
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        if (!DebugSpyglass.using(mc.player) || mc.screen != null) return;
        event.setCanceled(true); // Keep the scoped item selected while browsing.
        if (snapshot != null && matchesTarget && event.getScrollDeltaY() != 0)
            ClientPacketDistributor.sendToServer(new DinoDebugPayload.TurnPage(snapshot.target(), event.getScrollDeltaY() < 0 ? 1 : -1));
    }
    private static void render(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (!DebugSpyglass.using(mc.player) || mc.options.hideGui || mc.screen != null || !mc.options.getCameraType().isFirstPerson()) return;
        var font = mc.font;
        boolean valid = snapshot != null && matchesTarget && System.nanoTime() - receivedAt < 2_000_000_000L;
        int width = Math.min(370, g.guiWidth() < 520 ? g.guiWidth() - 16 : (int) (g.guiWidth() * 0.46));
        float scale = Math.min(0.85f, (width - 16f) / 350f);
        int height = 52 + (int) Math.ceil(DinoDebugSnapshot.PAGE_LINES * 11 * scale);
        int x = 8, y = Math.max(8, (g.guiHeight() - height) / 2);
        if (g.guiWidth() < 520) y = Math.max(8, g.guiHeight() - height - 8);
        g.fill(x, y, x + width, y + height, 0xE008170E);
        g.outline(x, y, width, height, 0xFF2EBA62);
        g.fill(x, y, x + width, y + 16, 0xE0154229);
        for (int line = y + 18; line < y + height; line += 3) g.horizontalLine(x + 1, x + width - 2, line, 0x0C58FF89);
        g.text(font, font.plainSubstrByWidth(I18n.get("debug.arksurvivalreturns.title"), width - 16), x + 8, y + 4, 0xFF9DFFB7);
        String name = valid ? snapshot.name() : I18n.get("debug.arksurvivalreturns.searching");
        g.text(font, font.plainSubstrByWidth("> " + name, width - 16), x + 8, y + 21, 0xFF5EFF8A);
        g.enableScissor(x + 6, y + 32, x + width - 6, y + height - 17);
        g.pose().pushMatrix();
        g.pose().translate(x + 8f, y + 34f); g.pose().scale(scale, scale);
        if (valid) {
            for (int i = 0; i < snapshot.lines().size(); i++)
                g.text(font, snapshot.lines().get(i), 0, i * 11, i % 2 == 0 ? 0xFF74F59C : 0xFF47C779, false);
        } else g.text(font, I18n.get("debug.arksurvivalreturns.aim"), 0, 0, 0xFF47C779, false);
        g.pose().popMatrix(); g.disableScissor();
        String footer = valid ? I18n.get("debug.arksurvivalreturns.page", snapshot.page() + 1, snapshot.pages())
                : I18n.get("debug.arksurvivalreturns.range", DinoDebugSync.RANGE);
        g.text(font, font.plainSubstrByWidth(footer, width - 16), x + 8, y + height - 12, 0xFF9DFFB7);
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        g.horizontalLine(cx - 7, cx - 3, cy, 0xFF5EFF8A); g.horizontalLine(cx + 3, cx + 7, cy, 0xFF5EFF8A);
        g.verticalLine(cx, cy - 7, cy - 3, 0xFF5EFF8A); g.verticalLine(cx, cy + 3, cy + 7, 0xFF5EFF8A);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    private static void reset() { snapshot = null; matchesTarget = false; receivedAt = 0; }
    private DinoDebugClient() {}
}
