package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client screen wiring for the homestead kitchen. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class KitchenClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.COOKING_POT_MENU.get(), CookingPotScreen::new);
    }

    private KitchenClient() {}
}
