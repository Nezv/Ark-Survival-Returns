package dev.nez.arksurvivalreturns.feature.station;

import java.util.Set;
import java.util.stream.Collectors;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class WorkstationEvents {
    @SubscribeEvent public static void reload(AddServerReloadListenersEvent event) {
        event.addListener(ArkSurvivalReturns.id("workstations"), new WorkstationCatalog());
    }
    @SubscribeEvent public static void packets(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(WorkstationPayload.Snapshot.TYPE, WorkstationPayload.Snapshot.STREAM_CODEC);
        registrar.playToClient(WorkstationPayload.Result.TYPE, WorkstationPayload.Result.STREAM_CODEC);
        registrar.playToServer(WorkstationPayload.Craft.TYPE, WorkstationPayload.Craft.STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) WorkstationCrafting.craft(player, packet);
        });
    }
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> {
            if (player.connection == null || !player.connection.hasChannel(WorkstationPayload.Snapshot.TYPE)) return;
            PacketDistributor.sendToPlayer(player, new WorkstationPayload.Snapshot("", "", true));
            for (var station : WorkstationCatalog.all().values()) {
                String json = WorkstationCodecs.STATION.encodeStart(JsonOps.INSTANCE, station).getOrThrow().toString();
                PacketDistributor.sendToPlayer(player, new WorkstationPayload.Snapshot(station.station(), json, false));
            }
        });
    }
    private static final class Removed {
        static final Set<Identifier> IDS = load();
        private static Set<Identifier> load() {
            try (var stream = WorkstationEvents.class.getResourceAsStream("/data/arksurvivalreturns/workstation_grid_removals.json")) {
                if (stream == null) throw new IllegalStateException("Missing generated workstation removal list");
                var array = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
                return array.asList().stream().map(e -> Identifier.parse(e.getAsString())).collect(Collectors.toUnmodifiableSet());
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
    }
    @SubscribeEvent public static void recipes(ModifyRecipeJsonsEvent event) {
        int before = event.getRecipeJsons().size();
        event.getRecipeJsons().keySet().removeAll(Removed.IDS);
        ArkSurvivalReturns.LOGGER.info("Phase A removed {} original grid recipes", before - event.getRecipeJsons().size());
    }
    private WorkstationEvents() {}
}
