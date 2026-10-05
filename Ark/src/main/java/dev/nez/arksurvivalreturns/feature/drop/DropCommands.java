package dev.nez.arksurvivalreturns.feature.drop;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /arkdrop [white|green|blue|purple]}: an operator calls a supply drop down near themselves, out of turn. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DropCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("arkdrop").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> send(context, null));
        for (SupplyTier tier : SupplyTier.values()) root.then(Commands.literal(tier.id).executes(context -> send(context, tier)));
        event.getDispatcher().register(root);
    }

    private static int send(CommandContext<CommandSourceStack> context, SupplyTier tier) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SupplyTier sent = tier != null ? tier : SupplyTier.roll(player.level().getRandom());
        if (SupplyDrops.send(player.level(), player, sent).isEmpty()) {
            context.getSource().sendFailure(Component.translatable("drop.arksurvivalreturns.no_place"));
            return 0;
        }
        return 1;
    }

    private DropCommands() {}
}
