package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.mass.MassPayload;
import dev.nez.arksurvivalreturns.feature.mass.MassRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Carried-load gauge: a compact value and bar at the top-left, colored by the warning band. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class MassClient {
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 5;
    private static float mass;
    private static float capacity;
    private static int band;
    private static boolean visible;

    @SubscribeEvent public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(MassPayload.TYPE, (payload, context) -> {
            mass = payload.mass();
            capacity = payload.capacity();
            band = payload.band();
            visible = payload.visible();
        });
    }

    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ArkSurvivalReturns.id("mass_load"), (graphics, delta) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        if (!visible || capacity <= 0f || Minecraft.getInstance().options.hideGui) return;
        var font = Minecraft.getInstance().font;
        MassRules.Band current = MassRules.Band.values()[Math.clamp(band, 0, MassRules.Band.values().length - 1)];
        int color = switch (current) {
            case NORMAL -> 0xFF55FF55;
            case WARN -> 0xFFFFD24A;
            case OVERLOAD -> 0xFFFF8C1A;
            case HEAVY -> 0xFFFF4040;
        };
        String text = I18n.get("hud.arksurvivalreturns.mass", Math.round(mass), Math.round(capacity));
        graphics.text(font, text, 6, 6, color);
        int x = 6;
        int y = 18;
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xAA000000);
        float ratio = mass / capacity;
        graphics.fill(x, y, x + (int) (BAR_WIDTH * Math.clamp(ratio, 0f, 1f)), y + BAR_HEIGHT, color);
        // Load past capacity is drawn as a red overlay, so deliberately hauling cargo stays visible.
        if (ratio > 1f) {
            float over = Math.clamp(ratio - 1f, 0f, 0.25f) / 0.25f;
            graphics.fill(x, y, x + (int) (BAR_WIDTH * over), y + BAR_HEIGHT, 0xFFFF2020);
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        visible = false;
        mass = 0f;
        capacity = 0f;
        band = 0;
    }

    private MassClient() {}
}
