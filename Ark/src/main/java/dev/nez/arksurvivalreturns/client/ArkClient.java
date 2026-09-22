package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import dev.nez.arksurvivalreturns.client.audio.physics.SoundPhysicsMod;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class ArkClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(SoundPhysicsMod::initClient);
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        ModContent.CREATURES.forEach((species, type) -> event.registerEntityRenderer(type.get(), context ->
                new CreatureRenderer(context, species)));
        event.registerEntityRenderer(ModContent.GUARDIAN_GIGANOTOSAURUS.get(), context ->
                new CreatureRenderer(context, dev.nez.arksurvivalreturns.feature.creature.Species.GIGANOTOSAURUS));
    }
    private ArkClient() {}
}
