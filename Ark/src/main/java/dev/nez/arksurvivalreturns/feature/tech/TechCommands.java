package dev.nez.arksurvivalreturns.feature.tech;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** `/arktech` diagnostics plus operator grant and reset. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TechCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("arktech");
        root.then(Commands.literal("status").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            TechService.status(player).forEach(line -> player.sendSystemMessage(line, false));
            return 1;
        }));
        root.then(Commands.literal("unlock").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("node", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> {
                            TechTree tree = TechTree.current();
                            if (tree != null) {
                                for (TechNode node : tree.nodes()) builder.suggest(node.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String node = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "node");
                            if (!TechService.unlock(player, node)) {
                                context.getSource().sendFailure(
                                        Component.translatable("tech.arksurvivalreturns.command.unknown", node));
                                return 0;
                            }
                            context.getSource().sendSuccess(
                                    () -> Component.translatable("tech.arksurvivalreturns.command.unlock", node), true);
                            return 1;
                        })));
        root.then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    boolean reset = TechService.reset(player);
                    context.getSource().sendSuccess(
                            () -> Component.translatable(reset
                                    ? "tech.arksurvivalreturns.command.reset"
                                    : "tech.arksurvivalreturns.command.reset_empty"), true);
                    return reset ? 1 : 0;
                }));
        event.getDispatcher().register(root);
    }

    private TechCommands() {}
}
