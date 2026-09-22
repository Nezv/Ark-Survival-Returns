package dev.nez.arksurvivalreturns.feature.guardian;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** `/arkguardian` diagnostics and operator recovery for the First Guardian encounter. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class GuardianCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("arkguardian");
        root.then(Commands.literal("status").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            GuardianService.status(player).forEach(line -> player.sendSystemMessage(line, false));
            return 1;
        }));
        root.then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!GuardianService.forceReset(player)) {
                        context.getSource().sendFailure(Component.translatable("guardian.arksurvivalreturns.status_none"));
                        return 0;
                    }
                    context.getSource().sendSuccess(
                            () -> Component.translatable("guardian.arksurvivalreturns.command.reset"), true);
                    return 1;
                }));
        root.then(Commands.literal("clear").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!GuardianService.clear(player)) {
                        context.getSource().sendFailure(Component.translatable("guardian.arksurvivalreturns.status_none"));
                        return 0;
                    }
                    context.getSource().sendSuccess(
                            () -> Component.translatable("guardian.arksurvivalreturns.command.clear"), true);
                    return 1;
                }));
        root.then(Commands.literal("grant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    GuardianService.grantSchematic(target);
                    context.getSource().sendSuccess(
                            () -> Component.translatable("guardian.arksurvivalreturns.command.grant",
                                    target.getDisplayName()), true);
                    return 1;
                })));
        event.getDispatcher().register(root);
    }

    private GuardianCommands() {}
}
