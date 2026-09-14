package dev.nez.arksurvivalreturns.feature.land;

import java.util.*;
import dev.nez.arksurvivalreturns.*;
import dev.nez.arksurvivalreturns.feature.map.MapUnlockData;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class LandHabitatSync {
    private static final Map<UUID,LandHabitatPayload> last = new HashMap<>();
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) { event.registrar("1").playToClient(LandHabitatPayload.TYPE,LandHabitatPayload.STREAM_CODEC); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer(); if (server.getTickCount()%100 != 0) return;
        var players = server.getPlayerList().getPlayers(); if (players.isEmpty()) return;
        int start = Math.floorMod(server.getTickCount()/100*8,players.size());
        for (int i=0;i<Math.min(8,players.size());i++) send(players.get((start+i)%players.size()),false);
    }
    public static LandHabitatPayload snapshot(ServerPlayer player) {
        var world = player.level(); var data = LandHabitatData.get(world);
        if (LandHabitats.enabled(world)) for (var h : data.near(player.blockPosition(),96)) {
            if (SpawnRules.loaded(world,new AABB(h.center)) && Math.abs(h.center.getY()-player.getY())<=64) data.discover(h,player.getUUID());
        }
        boolean unlocked = MapUnlockData.get(world).hasAccess(player.getUUID(),Config.MAP_REQUIRES_UNLOCK.get());
        var markers = unlocked && LandHabitats.enabled(world) ? data.discovered(player.getUUID(),player.blockPosition(),LandHabitatPayload.MAX_MARKERS).stream()
                .map(h -> new LandHabitatPayload.Marker(h.id,h.species,h.center,h.valid,!h.members.isEmpty())).toList()
                : List.<LandHabitatPayload.Marker>of();
        return new LandHabitatPayload(world.dimension().identifier().toString(),markers);
    }
    public static void send(ServerPlayer player,boolean force) {
        var packet = snapshot(player);
        if (force || !packet.equals(last.get(player.getUUID()))) { PacketDistributor.sendToPlayer(player,packet); last.put(player.getUUID(),packet); }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) send(p,true); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) send(p,true); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { if (event.getEntity() instanceof ServerPlayer p) send(p,true); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { last.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { last.clear(); }
    private LandHabitatSync() {}
}
