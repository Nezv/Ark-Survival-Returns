package dev.nez.arksurvivalreturns.feature.recovery;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** `/arkrecover` inspection and unplaced-cache claims. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class RecoveryCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("arkrecover");
        root.then(Commands.literal("list").executes(context ->
                RecoveryService.list(context.getSource().getPlayerOrException(), context.getSource())));
        root.then(Commands.literal("claim")
                .then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(context ->
                        RecoveryService.claim(context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "index")))));
        root.then(Commands.literal("clear").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player()).executes(context ->
                        RecoveryService.clear(EntityArgument.getPlayer(context, "player"), context.getSource()))));
        event.getDispatcher().register(root);
    }

    private RecoveryCommands() {}
}
