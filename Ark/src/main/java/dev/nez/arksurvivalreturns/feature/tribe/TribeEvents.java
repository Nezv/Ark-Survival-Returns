package dev.nez.arksurvivalreturns.feature.tribe;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Keeps tribe permission flags attached to a team, not to a player's history.
 *
 * <p>FTB Teams owns membership, so a player who leaves or changes a party loses their custom
 * flags and falls back to the configured defaults in the new team.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TribeEvents {
    @SubscribeEvent public static void changedTeam(dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent.PlayerChangedTeam event) {
        // The player may be offline (kicked while away); the flags live in the overworld's saved data either way.
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) TribePermissions.get(server.overworld()).clear(event.getEventData().playerId());
    }

    private TribeEvents() {}
}
