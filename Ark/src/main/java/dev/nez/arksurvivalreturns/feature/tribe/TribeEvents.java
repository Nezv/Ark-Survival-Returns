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
        var data = event.getEventData();
        if (data.player() == null) return;
        TribePermissions.get(data.player().level()).clear(data.playerId());
    }

    private TribeEvents() {}
}
