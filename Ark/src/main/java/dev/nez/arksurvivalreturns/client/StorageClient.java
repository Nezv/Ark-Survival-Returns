package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client screen wiring for the storage crate. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class StorageClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.STORAGE_CRATE_MENU.get(), StorageCrateScreen::new);
    }

    private StorageClient() {}
}
