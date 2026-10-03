package dev.nez.arksurvivalreturns.feature.levels;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.guardian.GuardianGiganotosaurusEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class LevelEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void killed(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        ServerPlayer player = credit(event.getSource().getEntity());
        if (player == null) player = credit(event.getEntity().getKillCredit());
        if (player == null) return;
        if (event.getEntity() instanceof dev.nez.arksurvivalreturns.feature.guardian.GuardianDragonEntity
                || event.getEntity() instanceof GuardianGiganotosaurusEntity
                || event.getEntity() instanceof EnderDragon || event.getEntity() instanceof WitherBoss) {
            ArkLevels.addXp(player, Config.PLAYER_XP_BOSS.get(), ArkLevels.Source.BOSS);
        } else if (event.getEntity() instanceof CreatureEntity creature) {
            ArkLevels.addXp(player, (long) Config.PLAYER_XP_KILL.get() * creature.creatureLevel(), ArkLevels.Source.KILL);
        }
    }

    private static @Nullable ServerPlayer credit(@Nullable Entity attacker) {
        if (attacker instanceof ServerPlayer player) return player;
        if (attacker instanceof CreatureEntity tame && TamingService.of(tame).tamed()) {
            var owner = TamingService.of(tame).owner();
            if (owner != null) return tame.level().getServer().getPlayerList().getPlayer(owner);
        }
        return null;
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { sync(event); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { sync(event); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event); }
    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ArkLevels.get(player);
            player.syncData(LevelAttachments.PROGRESS);
        }
    }
    private LevelEvents() {}
}
