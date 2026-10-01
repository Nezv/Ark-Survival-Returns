package dev.nez.arksurvivalreturns.feature.levels;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class LevelCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arklevel")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> report(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(c -> {
                                    var player = EntityArgument.getPlayer(c, "player");
                                    int level = IntegerArgumentType.getInteger(c, "level");
                                    if (level > Config.PLAYER_LEVEL_CAP.get()) {
                                        c.getSource().sendFailure(Component.translatable("levels.arksurvivalreturns.cap", Config.PLAYER_LEVEL_CAP.get()));
                                        return 0;
                                    }
                                    ArkLevels.setLevel(player, level);
                                    return report(c.getSource(), player);
                                }))))
                .then(Commands.literal("addxp").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(c -> {
                                    var player = EntityArgument.getPlayer(c, "player");
                                    ArkLevels.addXp(player, IntegerArgumentType.getInteger(c, "amount"), ArkLevels.Source.COMMAND);
                                    return report(c.getSource(), player);
                                })))));
    }
    private static int report(CommandSourceStack source, ServerPlayer player) {
        var progress = ArkLevels.get(player);
        source.sendSuccess(() -> Component.translatable("levels.arksurvivalreturns.status", player.getDisplayName(),
                progress.level(), progress.xp(), ArkLevels.xpForNextLevel(progress.level())), false);
        return 1;
    }
    private LevelCommands() {}
}
