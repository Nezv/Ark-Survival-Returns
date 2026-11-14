package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.taming.TamingAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The sedated player's own screen: a heavy dark overlay, the draining torpor bar, suppressed input and the
 * lying pose, mirroring {@link DownedClient}. The server enforces all of it anyway
 * ({@code PlayerUnconsciousHandler}); this removes the walking-into-a-wall feel of being dragged back.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class TorporClient {
    /** True while the own player's synced torpor state restricts them and they are not downed (the downed HUD wins). */
    private static boolean unconscious() {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.hasData(TamingAttachments.TORPOR)) return false;
        return player.getData(TamingAttachments.TORPOR).restricted();
    }

    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ArkSurvivalReturns.id("unconscious"), (graphics, delta) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        if (!unconscious()) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;
        var state = player.getData(TamingAttachments.TORPOR);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // Darkened sight: the world stays faintly visible through the sedation.
        graphics.fill(0, 0, width, height, 0xB4000000);
        int barWidth = Math.min(220, width - 60);
        int x = (width - barWidth) / 2;
        int y = height / 2 + 24;
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 9, 0xAA000000);
        float ratio = (float) state.ratio();
        graphics.fill(x, y, x + (int) (barWidth * ratio), y + 8, 0xFF7A4FBF);
        var font = minecraft.font;
        String title = I18n.get("hud.unconscious.arksurvivalreturns.title");
        String hint = I18n.get("hud.unconscious.arksurvivalreturns.hint");
        graphics.text(font, title, (width - font.width(title)) / 2, y - 16, 0xFFC9A9FF);
        graphics.text(font, hint, (width - font.width(hint)) / 2, y + 12, 0xFFBBBBBB);
    }

    /** Sedated, the own client neither walks nor jumps; no server drag-back is ever needed. */
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        if (unconscious()) event.getInput().keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
    }

    /** Lying pose on the own client and horizontal drift stopped, like the downed player's client tick. */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean downed = DownedClient.downed();
        if (!unconscious()) {
            if (!downed && player.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING) player.setForcedPose(null);
            return;
        }
        player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        var v = player.getDeltaMovement();
        player.setDeltaMovement(0, Math.min(v.y, 0), 0);
    }

    private TorporClient() {}
}
