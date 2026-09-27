package dev.nez.arksurvivalreturns.feature.camp;

import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Grants the one-time starter kit on a player's first join in a world: a single flint knife, so a fresh
 * start begins with nothing else.
 *
 * <p>The flag is
 * world-scoped SavedData, so rejoining or moving between dimensions never duplicates it.
 */
public final class StarterKitService {
    public static void onLogin(ServerPlayer player) {
        if (!Config.CAMP_STARTER_KIT.get()) return;
        if (!StarterKitData.get(player.level()).markGranted(player.getUUID())) return;
        give(player);
        player.sendSystemMessage(Component.translatable("camp.arksurvivalreturns.starter_kit"), false);
    }

    /** Delivers the kit without touching the granted flag; used by the login path and tests. */
    public static void give(ServerPlayer player) {
        for (ItemStack stack : kit()) {
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    public static List<ItemStack> kit() {
        return List.of(new ItemStack(ModContent.FLINT_KNIFE.get()));
    }

    private StarterKitService() {}
}
