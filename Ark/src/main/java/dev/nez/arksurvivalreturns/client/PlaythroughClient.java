package dev.nez.arksurvivalreturns.client;

import java.io.File;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.playthrough.Playthrough;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The playthrough's client side (F43): does what the run asks of the client (pictures, the camera, the screens only the
 * client opens), says which screen is open, and closes the game when a launched run ends.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class PlaythroughClient {
    private static int closing = -1;
    /** The use key as the run last wanted it: kept down every tick while held (a screen or a mouse grab resets keys). */
    private static boolean holdingUse;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var run = Playthrough.current();
        if (run == null) return;
        var mc = Minecraft.getInstance();
        if (Playthrough.launched()) mc.getToastManager().clear();
        String request = Playthrough.pendingRequest();
        if (request != null && mc.player != null) {
            int colon = request.indexOf(':');
            String verb = request.substring(0, colon), what = request.substring(colon + 1);
            try {
                switch (verb) {
                    case "shot" -> {
                        var folder = new File(run.folder().toFile(), "screenshots");
                        folder.mkdirs();
                        Screenshot.grab(folder, what + ".png", mc.getMainRenderTarget(), 1, message -> {});
                    }
                    case "camera" -> mc.options.setCameraType(what.equals("third") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
                    case "screen" -> mc.setScreen(switch (what) {
                        case "tech" -> new TechScreen();
                        case "inventory" -> new InventoryScreen(mc.player);
                        default -> null;
                    });
                    default -> {}
                }
            } catch (RuntimeException e) {
                ArkSurvivalReturns.LOGGER.error("Playthrough: client request {} failed", request, e);
            }
            Playthrough.requestDone(mc.screen == null ? "" : mc.screen.getClass().getSimpleName());
        }
        boolean hold = Playthrough.useHeld() && mc.player != null;
        if (hold != holdingUse) {
            holdingUse = hold;
            if (hold && mc.screen != null) mc.setScreen(null);
            mc.options.keyUse.setDown(hold);
        } else if (hold) mc.options.keyUse.setDown(true);
        if (run.finished() && Playthrough.launched() && closing < 0) closing = 40;
        if (closing > 0 && --closing == 0) mc.stop();
    }

    private PlaythroughClient() {}
}
