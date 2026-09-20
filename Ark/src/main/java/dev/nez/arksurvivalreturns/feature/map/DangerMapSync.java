package dev.nez.arksurvivalreturns.feature.map;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.spawn.ProgressionData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DangerMapSync {
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(DangerMapPayload.TYPE, DangerMapPayload.STREAM_CODEC);
    }
    public static void send(ServerPlayer player) {
        boolean unlocked = MapUnlockData.get(player.level()).hasAccess(player.getUUID(), Config.MAP_REQUIRES_UNLOCK.get());
        var profile = ProgressionData.get(player.level());
        // A locked client receives no real origin or region scale.
        PacketDistributor.sendToPlayer(player, new DangerMapPayload(unlocked,
                unlocked ? profile.originX() : 0, unlocked ? profile.originZ() : 0,
                unlocked ? profile.bandWidth() : 0));
    }
    /** Called when a completed tame originates from the difficulty-5 band. */
    public static void setUnlocked(ServerPlayer player, boolean value) {
        MapUnlockData.get(player.level()).setUnlocked(player.getUUID(), value);
        send(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) send(p);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) send(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) send(p);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("arkmap").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (String action : new String[]{"unlock", "lock", "status"}) {
            root.then(Commands.literal(action).executes(c -> execute(c.getSource().getPlayerOrException(), action, c.getSource()))
                    .then(Commands.argument("player", EntityArgument.player()).executes(c ->
                            execute(EntityArgument.getPlayer(c, "player"), action, c.getSource()))));
        }
        event.getDispatcher().register(root);
    }
    private static int execute(ServerPlayer player, String action, net.minecraft.commands.CommandSourceStack source) {
        if (!action.equals("status")) setUnlocked(player, action.equals("unlock"));
        boolean unlocked = MapUnlockData.get(player.level()).hasAccess(player.getUUID(), Config.MAP_REQUIRES_UNLOCK.get());
        source.sendSuccess(() -> Component.translatable("map.arksurvivalreturns.status", player.getDisplayName(),
                Component.translatable("map.arksurvivalreturns." + (unlocked ? "unlocked" : "locked_short"))), true);
        return unlocked ? 1 : 0;
    }
    private DangerMapSync() {}
}
