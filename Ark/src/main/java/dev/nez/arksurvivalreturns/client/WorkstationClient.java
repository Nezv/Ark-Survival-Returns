package dev.nez.arksurvivalreturns.client;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.station.WorkstationCodecs;
import dev.nez.arksurvivalreturns.feature.station.WorkstationDefinition;
import dev.nez.arksurvivalreturns.feature.station.WorkstationPayload;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class WorkstationClient {
    private static final Map<String, WorkstationDefinition> STATIONS = new LinkedHashMap<>();
    public static WorkstationDefinition get(String station) { return STATIONS.get(station); }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(ModContent.WORKSTATION_MENU.get(), WorkstationScreen::new); }
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(WorkstationPayload.Result.TYPE, (packet, context) -> {
            if (Minecraft.getInstance().screen instanceof WorkstationScreen screen) screen.crafted(packet);
        });
        event.register(WorkstationPayload.Snapshot.TYPE, (packet, context) -> {
            if (packet.reset()) STATIONS.clear();
            else {
                var definition = WorkstationCodecs.STATION.parse(JsonOps.INSTANCE, JsonParser.parseString(packet.json())).getOrThrow();
                if (!definition.station().equals(packet.station())) return;
                STATIONS.put(packet.station(), definition);
            }
            if (Minecraft.getInstance().screen instanceof WorkstationScreen screen) screen.refresh();
        });
    }
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { STATIONS.clear(); }
    private WorkstationClient() {}
}
