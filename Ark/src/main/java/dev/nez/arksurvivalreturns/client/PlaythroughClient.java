package dev.nez.arksurvivalreturns.client;

import java.io.File;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.playthrough.Playthrough;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** The playthrough's client side (F43): takes the pictures the run asks for and closes the game when a launched run ends. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class PlaythroughClient {
    private static int closing = -1;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var run = Playthrough.current();
        if (run == null) return;
        var mc = Minecraft.getInstance();
        String name = Playthrough.pendingShot();
        if (name != null) {
            var folder = new File(run.folder().toFile(), "screenshots");
            folder.mkdirs();
            Screenshot.grab(folder, name + ".png", mc.getMainRenderTarget(), 1, message -> {});
            Playthrough.shotTaken();
        }
        if (run.finished() && Playthrough.launched() && closing < 0) closing = 40;
        if (closing > 0 && --closing == 0) mc.stop();
    }

    private PlaythroughClient() {}
}
