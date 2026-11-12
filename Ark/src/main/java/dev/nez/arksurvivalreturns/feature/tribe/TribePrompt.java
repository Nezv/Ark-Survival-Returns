package dev.nez.arksurvivalreturns.feature.tribe;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Two players are a case the game handles from the first minute (BUG-07): a tribe (an FTB Teams party) is what
 * shares tames, research and the level bonus, and nothing used to say so. When a player joins while another is
 * online and neither is in a party, both get a line with a button that forms one ({@code /arktribe form}).
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TribePrompt {
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined) || !Config.TRIBE_AUTO_PROMPT.get() || inParty(joined)) return;
        var server = joined.level().getServer();
        if (server == null) return;
        for (var other : server.getPlayerList().getPlayers()) {
            if (other == joined || inParty(other)) continue;
            prompt(joined, other);
            prompt(other, joined);
        }
    }

    /** True when the player is in a party, not just their own solo team. */
    public static boolean inParty(ServerPlayer player) {
        return TribeService.team(player).map(team -> team.isPartyTeam()).orElse(false);
    }

    private static void prompt(ServerPlayer to, ServerPlayer partner) {
        String name = partner.getGameProfile().name();
        Component button = Component.translatable("tribe.arksurvivalreturns.prompt_button", partner.getDisplayName())
                .withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/arktribe form " + name))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("tribe.arksurvivalreturns.prompt_hover", partner.getDisplayName()))));
        to.sendSystemMessage(Component.translatable("tribe.arksurvivalreturns.prompt", partner.getDisplayName(), button));
    }

    private TribePrompt() {}
}
