package dev.nez.arksurvivalreturns.feature.tribe;

import java.util.Optional;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Tribe access rules for tames: the owner keeps every permission, and members of the owner's
 * FTB Teams party receive the configured defaults unless the tribe changed them.
 *
 * <p>Every decision runs on the server. Clients get the owner shortcut for interaction
 * prediction only; anything else is answered by the authoritative interaction result.
 */
public final class TribeService {
    public static boolean canRide(CreatureEntity creature, Player player) {
        return allowed(creature, player, TribePermission.RIDE);
    }

    public static boolean canAccessCargo(CreatureEntity creature, Player player) {
        return allowed(creature, player, TribePermission.CARGO);
    }

    public static boolean canCommand(CreatureEntity creature, Player player) {
        return allowed(creature, player, TribePermission.COMMANDS);
    }

    public static boolean canBreed(CreatureEntity creature, Player player) {
        return allowed(creature, player, TribePermission.BREEDING);
    }

    /** True when the player shares the owner's party, whether or not this action is permitted. */
    public static boolean isTribeMember(CreatureEntity creature, Player player) {
        UUID owner = TamingService.of(creature).owner();
        if (owner == null || owner.equals(player.getUUID())) return false;
        return sameTribe(owner, player.getUUID());
    }

    private static boolean allowed(CreatureEntity creature, Player player, TribePermission permission) {
        var state = TamingService.of(creature);
        if (!state.tamed() || state.owner() == null) return false;
        if (state.owner().equals(player.getUUID())) return true;
        if (player.level().isClientSide()) return false;
        if (!sameTribe(state.owner(), player.getUUID())) return false;
        return permissions(player.level()).allows(player.getUUID(), permission, defaultMask());
    }

    /** True when both UUIDs resolve to the same FTB Teams team, parties included. */
    public static boolean sameTribe(UUID first, UUID second) {
        if (first.equals(second)) return true;
        if (!FTBTeamsAPI.api().isManagerLoaded()) return false;
        return FTBTeamsAPI.api().getManager().arePlayersInSameTeam(first, second);
    }

    public static Optional<Team> team(UUID player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return Optional.empty();
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(player);
    }

    public static Optional<Team> team(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return Optional.empty();
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
    }

    /** True when the actor owns the target's current team (parties included). */
    public static boolean ownsTeamOf(Player actor, UUID target) {
        if (actor.getUUID().equals(target)) return true;
        return team(target).map(team -> team.getOwner().equals(actor.getUUID())
                && team.getMembers().contains(actor.getUUID())).orElse(false);
    }

    public static int defaultMask() {
        return TribePermission.mask(Config.TRIBE_DEFAULT_RIDE.get(), Config.TRIBE_DEFAULT_CARGO.get(),
                Config.TRIBE_DEFAULT_COMMANDS.get(), Config.TRIBE_DEFAULT_BREEDING.get());
    }

    public static TribePermissions permissions(net.minecraft.world.level.Level level) {
        return TribePermissions.get((ServerLevel) level);
    }

    private TribeService() {}
}
