package dev.nez.arksurvivalreturns.feature.tribe;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator and tribe-owner commands for the Ark permission layer.
 *
 * <p>Membership and invitations stay with FTB Teams (`/ftbteams`); this command only inspects
 * the party and edits the action flags that gate another member's tames.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TribeCommands {
    private static final String[] PERMISSIONS = {"ride", "cargo", "commands", "breeding"};

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("arktribe");
        root.then(Commands.literal("status")
                .executes(context -> status(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> status(context.getSource(), EntityArgument.getPlayer(context, "player")))));
        root.then(Commands.literal("perm")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("permission", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(PERMISSIONS, builder))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"on", "off"}, builder))
                                        .executes(context -> setPermission(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "permission"),
                                                StringArgumentType.getString(context, "value")))))));
        root.then(Commands.literal("reset")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> reset(context.getSource(), EntityArgument.getPlayer(context, "player")))));
        event.getDispatcher().register(root);
    }

    private static int status(CommandSourceStack source, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var actor = source.getPlayerOrException();
        var team = TribeService.team(target);
        if (team.isEmpty() || !team.get().isPartyTeam()) {
            source.sendSuccess(() -> Component.translatable("tribe.arksurvivalreturns.status_solo", target.getDisplayName()), false);
            return 0;
        }
        var resolved = team.get();
        source.sendSuccess(() -> Component.translatable("tribe.arksurvivalreturns.status_party",
                resolved.getShortName(), resolved.getMembers().size()), false);
        if (!actor.getUUID().equals(target.getUUID()) && !TribeService.sameTribe(actor.getUUID(), target.getUUID())) {
            return resolved.getMembers().size();
        }
        int defaultMask = TribeService.defaultMask();
        int mask = TribeService.permissions(actor.level()).mask(target.getUUID(), defaultMask);
        for (TribePermission permission : TribePermission.values()) {
            boolean enabled = TribePermission.allows(mask, permission);
            source.sendSuccess(() -> Component.translatable("tribe.arksurvivalreturns.flag",
                    permission.key(), Component.translatable(enabled ? "tribe.arksurvivalreturns.on" : "tribe.arksurvivalreturns.off")), false);
        }
        return resolved.getMembers().size();
    }

    private static int setPermission(CommandSourceStack source, ServerPlayer target, String permissionName, String value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var actor = source.getPlayerOrException();
        if (!authorized(source, actor, target)) {
            source.sendFailure(Component.translatable("tribe.arksurvivalreturns.permission_denied"));
            return 0;
        }
        var permission = TribePermission.byName(permissionName);
        if (permission == null) {
            source.sendFailure(Component.translatable("tribe.arksurvivalreturns.unknown", permissionName));
            return 0;
        }
        boolean enabled = value.equalsIgnoreCase("on");
        if (!enabled && !value.equalsIgnoreCase("off")) {
            source.sendFailure(Component.translatable("tribe.arksurvivalreturns.unknown", value));
            return 0;
        }
        var permissions = TribeService.permissions(actor.level());
        permissions.set(target.getUUID(), permission, enabled, TribeService.defaultMask());
        source.sendSuccess(() -> Component.translatable("tribe.arksurvivalreturns.permission_set",
                permission.key(), target.getDisplayName(),
                Component.translatable(enabled ? "tribe.arksurvivalreturns.on" : "tribe.arksurvivalreturns.off")), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var actor = source.getPlayerOrException();
        if (!authorized(source, actor, target)) {
            source.sendFailure(Component.translatable("tribe.arksurvivalreturns.permission_denied"));
            return 0;
        }
        TribeService.permissions(actor.level()).clear(target.getUUID());
        source.sendSuccess(() -> Component.translatable("tribe.arksurvivalreturns.permission_cleared", target.getDisplayName()), true);
        return 1;
    }

    /** A gamemaster may always edit; otherwise the actor must own the target's party. */
    private static boolean authorized(CommandSourceStack source, ServerPlayer actor, ServerPlayer target) {
        if (Commands.LEVEL_GAMEMASTERS.check(source.permissions())) return true;
        if (actor.getUUID().equals(target.getUUID())) return true;
        return TribeService.ownsTeamOf(actor, target.getUUID())
                && TribeService.sameTribe(actor.getUUID(), target.getUUID());
    }

    private TribeCommands() {}
}
