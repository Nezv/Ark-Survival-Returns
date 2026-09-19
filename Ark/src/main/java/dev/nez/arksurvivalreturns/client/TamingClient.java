package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client wiring for the taming feature: the saddle screen renderer and the tranquilizer arrow. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class TamingClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.CREATURE_MOUNT_MENU.get(), CreatureMountScreen::new);
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.TRANQUILIZER_ARROW.get(), SedativeArrowRenderer::new);
    }

    private TamingClient() {}
}
