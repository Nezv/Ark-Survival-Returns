package dev.nez.arksurvivalreturns.feature.tribe;

import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.Nullable;

/**
 * One side in a fight (BUG-06): a player, the members of their tribe (FTB Teams party), and every tame those players
 * own. Tames never turn on their side, and unless the server allows friendly fire ({@code tribe.friendlyFire}) a
 * side's blows do not hurt its own tames, nor a tame's its own side.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TribeAllies {
    /** The player an entity answers to: a player itself, a tame's owner; null for anything wild. */
    public static @Nullable UUID side(@Nullable Entity entity) {
        if (entity instanceof Player player) return player.getUUID();
        if (entity instanceof CreatureEntity creature && TamingService.isTamed(creature)) return TamingService.of(creature).owner();
        return null;
    }

    /** True when both answer to the same player or tribe. */
    public static boolean allied(@Nullable Entity first, @Nullable Entity second) {
        if (first == null || second == null) return false;
        if (first == second) return true;
        UUID a = side(first), b = side(second);
        return a != null && b != null && TribeService.sameTribe(a, b);
    }

    @SubscribeEvent public static void friendlyFire(LivingIncomingDamageEvent event) {
        var target = event.getEntity();
        if (target.level().isClientSide() || Config.TRIBE_FRIENDLY_FIRE.get()) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == target) return;
        boolean tame = target instanceof CreatureEntity hurt && TamingService.isTamed(hurt)
                || attacker instanceof CreatureEntity striking && TamingService.isTamed(striking);
        if (tame && allied(target, attacker)) event.setCanceled(true);
    }

    private TribeAllies() {}
}
