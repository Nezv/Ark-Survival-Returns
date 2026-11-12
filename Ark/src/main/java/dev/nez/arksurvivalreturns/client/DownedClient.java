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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Bleed-out HUD for the downed player: vignette, countdown bar and rescue hint. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class DownedClient {
    private static boolean downed;
    private static int ticksLeft;
    private static int totalTicks;
    /** Ticks Sneak has been held while downed; at {@link #GIVE_UP_TICKS} the player gives up. */
    private static int giveUpTicks;
    private static final int GIVE_UP_TICKS = 60;

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
        String seconds = giveUpTicks > 0
                ? I18n.get("hud.downed.arksurvivalreturns.giving_up", String.format(Locale.ROOT, "%.1f", (GIVE_UP_TICKS - giveUpTicks) / 20.0))
                : I18n.get("hud.downed.arksurvivalreturns.seconds", String.format(Locale.ROOT, "%.0f", ticksLeft / 20.0));
        graphics.text(font, title, (width - font.width(title)) / 2, y - 16, 0xFFFF5555);
        graphics.text(font, hint, (width - font.width(hint)) / 2, y + 12, 0xFFFFFFFF);
        graphics.text(font, seconds, (width - font.width(seconds)) / 2, y + 24, 0xFFFFAAAA);
    }

    /** Downed, the player's own client neither walks nor jumps; the server holds them in place anyway (BUG-08). */
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        if (downed) event.getInput().keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
    }

    /** Lying pose on the own client, horizontal drift stopped, and the hold-Sneak give-up. */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;
        if (!downed) {
            giveUpTicks = 0;
            if (player.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING) player.setForcedPose(null);
            return;
        }
        player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        var v = player.getDeltaMovement();
        player.setDeltaMovement(0, Math.min(v.y, 0), 0);
        if (minecraft.screen == null && minecraft.options.keyShift.isDown()) {
            if (++giveUpTicks == GIVE_UP_TICKS)
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new dev.nez.arksurvivalreturns.feature.recovery.DownedSync.GiveUp());
        } else giveUpTicks = 0;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        giveUpTicks = 0;
        downed = false;
        ticksLeft = 0;
        totalTicks = 0;
    }

    private DownedClient() {}
}
