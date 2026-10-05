package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.drop.DropContent;
import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;

/** Client wiring for the supply drops: the falling drop, the landed crate's beam (the beacon's own) and its screen. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class DropClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DropContent.SUPPLY_DROP.get(), SupplyDropRenderer::new);
        event.registerBlockEntityRenderer(DropContent.LOOT_CRATE_BLOCK_ENTITY.get(), context -> new BeaconRenderer<>());
    }

    /** The parachutes belong to no block, so they are loaded on their own. */
    @SubscribeEvent public static void models(ModelEvent.RegisterStandalone event) {
        for (SupplyTier tier : SupplyTier.values())
            event.register(SupplyDropRenderer.PARACHUTES.get(tier), SimpleUnbakedStandaloneModel.simpleModelWrapper(
                    ArkSurvivalReturns.id("block/drop/parachute_" + tier.id)));
    }

    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(DropContent.LOOT_CRATE_MENU.get(), LootCrateScreen::new);
    }

    private DropClient() {}
}
