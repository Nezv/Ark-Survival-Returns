package dev.nez.arksurvivalreturns.feature.recorder;

import java.io.IOException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /arkrecord}: {@code arm [delay] [seconds]} records the next world join, {@code start [delay]
 * [seconds]} records now, {@code stop} ends and saves, {@code mark [note]} flags a moment and
 * {@code status} says what is running. Arming, starting and stopping need operator rights.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class RecorderCommand {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arkrecord")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("arm").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> arm(context.getSource(), SessionRecorder.DELAY_SECONDS, SessionRecorder.RECORD_SECONDS))
                        .then(Commands.argument("delay", IntegerArgumentType.integer(0, 3600))
                                .executes(context -> arm(context.getSource(), IntegerArgumentType.getInteger(context, "delay"),
                                        SessionRecorder.RECORD_SECONDS))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(context -> arm(context.getSource(), IntegerArgumentType.getInteger(context, "delay"),
                                                IntegerArgumentType.getInteger(context, "seconds"))))))
                .then(Commands.literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> start(context.getSource(), 0, SessionRecorder.RECORD_SECONDS))
                        .then(Commands.argument("delay", IntegerArgumentType.integer(0, 3600))
                                .executes(context -> start(context.getSource(), IntegerArgumentType.getInteger(context, "delay"),
                                        SessionRecorder.RECORD_SECONDS))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(context -> start(context.getSource(), IntegerArgumentType.getInteger(context, "delay"),
                                                IntegerArgumentType.getInteger(context, "seconds"))))))
                .then(Commands.literal("stop").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    if (SessionRecorder.stop("command")) return 1;
                    context.getSource().sendFailure(Component.literal("No session is running."));
                    return 0;
                }))
                .then(Commands.literal("mark")
                        .executes(context -> mark(context.getSource(), "command"))
                        .then(Commands.argument("note", StringArgumentType.greedyString())
                                .executes(context -> mark(context.getSource(), StringArgumentType.getString(context, "note"))))));
    }

    private static int status(CommandSourceStack source) {
        String armed = SessionRecorder.armed(source.getServer()) ? "; the next world join is armed" : "";
        source.sendSuccess(() -> Component.literal("Session recorder: " + SessionRecorder.status() + armed), false);
        return 1;
    }

    private static int arm(CommandSourceStack source, int delay, int seconds) {
        try {
            SessionRecorder.arm(source.getServer(), delay, seconds);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write the arm file: " + e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("The next world join records " + seconds + " s, starting " + delay
                + " s after you can move. Leave and rejoin the world."), false);
        return 1;
    }

    private static int start(CommandSourceStack source, int delay, int seconds) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player: the recording follows you."));
            return 0;
        }
        if (SessionRecorder.start(source.getServer(), player, delay, seconds, 0, "command") == null) {
            source.sendFailure(Component.literal("A session is already running: " + SessionRecorder.status()));
            return 0;
        }
        return 1;
    }

    private static int mark(CommandSourceStack source, String note) {
        if (SessionRecorder.mark(source.getPlayer(), note)) return 1;
        source.sendFailure(Component.literal("Nothing is being recorded."));
        return 0;
    }

    private RecorderCommand() {}
}
