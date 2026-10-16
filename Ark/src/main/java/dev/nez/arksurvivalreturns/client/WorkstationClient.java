package dev.nez.arksurvivalreturns.client;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.inventory.InventoryContent;
import dev.nez.arksurvivalreturns.feature.inventory.InventoryPayload;
import dev.nez.arksurvivalreturns.feature.station.WorkstationCodecs;
import dev.nez.arksurvivalreturns.feature.station.WorkstationDefinition;
import dev.nez.arksurvivalreturns.feature.station.WorkstationPayload;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class WorkstationClient {
    private static final Map<String, WorkstationDefinition> STATIONS = new LinkedHashMap<>();
    public static WorkstationDefinition get(String station) { return STATIONS.get(station); }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.WORKSTATION_MENU.get(), WorkstationScreen::new);
        event.register(InventoryContent.MENU.get(), ArkInventoryScreen::new);
    }
    /** The inventory key opens the Ark inventory (survival and adventure); it runs before the Curios fork's own redirect. */
    @SubscribeEvent(priority = EventPriority.HIGH) public static void inventory(ScreenEvent.Opening event) {
        var player = Minecraft.getInstance().player;
        if (!(event.getNewScreen() instanceof InventoryScreen) || player == null || player.hasInfiniteMaterials() || player.isSpectator()) return;
        event.setCanceled(true);
        ClientPacketDistributor.sendToServer(new InventoryPayload.Open());
    }
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(WorkstationPayload.Result.TYPE, (packet, context) -> {
            if (Minecraft.getInstance().screen instanceof WorkstationScreen screen) screen.crafted(packet);
            if (Minecraft.getInstance().screen instanceof ArkInventoryScreen screen) screen.crafted(packet);
        });
        event.register(WorkstationPayload.Snapshot.TYPE, (packet, context) -> {
            if (packet.reset()) STATIONS.clear();
            else {
                var definition = WorkstationCodecs.STATION.parse(JsonOps.INSTANCE, JsonParser.parseString(packet.json())).getOrThrow();
                if (!definition.station().equals(packet.station())) return;
                STATIONS.put(packet.station(), definition);
            }
            if (Minecraft.getInstance().screen instanceof WorkstationScreen screen) screen.refresh();
            if (Minecraft.getInstance().screen instanceof ArkInventoryScreen screen) screen.refresh();
        });
    }
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { STATIONS.clear(); }
    private WorkstationClient() {}
}
