package dev.nez.arksurvivalreturns.client;

import java.util.Locale;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.recovery.DownedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Bleed-out HUD for the downed player: vignette, countdown bar and rescue hint. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class DownedClient {
    private static boolean downed;
    private static int ticksLeft;
    private static int totalTicks;

    @SubscribeEvent public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(DownedPayload.TYPE, (payload, context) -> {
            downed = payload.downed();
            ticksLeft = payload.ticksLeft();
            totalTicks = payload.totalTicks();
        });
    }

    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ArkSurvivalReturns.id("downed"), (graphics, delta) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        if (!downed) return;
        var font = Minecraft.getInstance().font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // Vignette bands read as blood loss without hiding the world.
        graphics.fill(0, 0, width, 22, 0x66AA0000);
        graphics.fill(0, height - 22, width, height, 0x66AA0000);

        int barWidth = Math.min(220, width - 60);
        int x = (width - barWidth) / 2;
        int y = height - 58;
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 9, 0xAA000000);
        float ratio = totalTicks <= 0 ? 0f : Math.clamp(ticksLeft / (float) totalTicks, 0f, 1f);
        graphics.fill(x, y, x + (int) (barWidth * ratio), y + 8, 0xFFFF3030);

        String title = I18n.get("hud.downed.arksurvivalreturns.title");
        String hint = I18n.get("hud.downed.arksurvivalreturns.hint");
        String seconds = I18n.get("hud.downed.arksurvivalreturns.seconds",
                String.format(Locale.ROOT, "%.0f", ticksLeft / 20.0));
        graphics.text(font, title, (width - font.width(title)) / 2, y - 16, 0xFFFF5555);
        graphics.text(font, hint, (width - font.width(hint)) / 2, y + 12, 0xFFFFFFFF);
        graphics.text(font, seconds, (width - font.width(seconds)) / 2, y + 24, 0xFFFFAAAA);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        downed = false;
        ticksLeft = 0;
        totalTicks = 0;
    }

    private DownedClient() {}
}
