package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class ArkClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        ModContent.CREATURES.forEach((species, type) -> event.registerEntityRenderer(type.get(), context ->
                new CreatureRenderer(context, species)));
    }
    private ArkClient() {}
}
