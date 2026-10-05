package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.client.title.CreatureSceneRenderer;
import dev.nez.arksurvivalreturns.client.title.CreatureSceneState;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import dev.nez.arksurvivalreturns.client.audio.physics.SoundPhysicsMod;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class ArkClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(SoundPhysicsMod::initClient);
        // BetterF3 builds its module lists while it is constructed; by now they exist whatever the load order.
        if (net.neoforged.fml.ModList.get().isLoaded("betterf3")) event.enqueueWork(() -> {
            try {
                dev.nez.arksurvivalreturns.client.betterf3.ArkModule.register();
            } catch (LinkageError | RuntimeException e) {
                ArkSurvivalReturns.LOGGER.warn("This BetterF3 version does not fit the Ark module; the debug screen shows without it", e);
            }
        });
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        ModContent.CREATURES.forEach((species, type) -> event.registerEntityRenderer(type.get(), context ->
                new CreatureRenderer(context, species)));
        event.registerEntityRenderer(ModContent.GUARDIAN_DRAGON.get(), context ->
                new CreatureRenderer(context, dev.nez.arksurvivalreturns.feature.creature.Species.DRAGON));
        event.registerEntityRenderer(ModContent.GUARDIAN_GIGANOTOSAURUS.get(), context ->
                new CreatureRenderer(context, dev.nez.arksurvivalreturns.feature.creature.Species.GIGANOTOSAURUS));
    }

    /** Menu creatures (the title scene) reach the screen as pictures-in-picture. */
    @SubscribeEvent public static void pictures(RegisterPictureInPictureRenderersEvent event) {
        event.register(CreatureSceneState.class, CreatureSceneRenderer::new);
    }
    private ArkClient() {}
}
