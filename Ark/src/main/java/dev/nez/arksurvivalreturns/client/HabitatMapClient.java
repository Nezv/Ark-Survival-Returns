package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.flying.HabitatPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid=ArkSurvivalReturns.MOD_ID,value=Dist.CLIENT)
public final class HabitatMapClient {
    private static HabitatPayload snapshot;
    private static dev.nez.arksurvivalreturns.feature.land.LandHabitatPayload landSnapshot;
    public static dev.nez.arksurvivalreturns.feature.land.LandHabitatPayload landSnapshot() { return landSnapshot; }
    public static HabitatPayload snapshot() { return snapshot; }
    @SubscribeEvent public static void register(RegisterClientPayloadHandlersEvent event) { event.register(HabitatPayload.TYPE,(p,c) -> snapshot=p); event.register(dev.nez.arksurvivalreturns.feature.land.LandHabitatPayload.TYPE,(p,c) -> landSnapshot=p); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { snapshot=null; landSnapshot=null; }
    private HabitatMapClient() {}
}
