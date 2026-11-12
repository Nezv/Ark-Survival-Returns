package dev.nez.arksurvivalreturns.feature.guardian;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-side wiring for the beacon guardians: the sky beacons are checked once a second. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class GuardianEvents {
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) SkyBeaconStructure.tick(event.getServer());
    }

    private GuardianEvents() {}
}
