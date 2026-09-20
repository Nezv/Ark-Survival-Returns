package dev.nez.arksurvivalreturns.feature.companion;

import java.util.Locale;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingAttachments;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Companion order storage, transitions and owner lookup for a tamed creature. */
public final class CompanionService {
    public static CompanionState of(CreatureEntity creature) {
        return creature.getData(TamingAttachments.COMPANION);
    }

    /** Applies an order server-side; STAY and WANDER anchor at the position where it was given. */
    public static void setOrder(CreatureEntity creature, CompanionOrder order) {
        if (creature.level().isClientSide()) return;
        BlockPos anchor = order == CompanionOrder.FOLLOW ? null : creature.blockPosition();
        of(creature).setOrder(order, anchor);
        creature.syncData(TamingAttachments.COMPANION);
        creature.companionHold();
    }

    /** Cycles the order and reports it on the action bar. Server-side only. */
    public static void orderCommand(CreatureEntity creature, Player player) {
        CompanionOrder next = of(creature).order().next();
        setOrder(creature, next);
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            server.sendSystemMessage(Component.translatable(
                    "companion.arksurvivalreturns.order." + next.name().toLowerCase(Locale.ROOT)), true);
        }
    }

    /** The owner while it is loaded in the same dimension, or null. */
    public static @Nullable Player owner(CreatureEntity creature) {
        if (!(creature.level() instanceof ServerLevel world)) return null;
        var owner = TamingService.of(creature).owner();
        if (owner == null) return null;
        Player player = world.getPlayerByUUID(owner);
        return player != null ? player
                : (world.getEntity(owner) instanceof Player loaded ? loaded : null);
    }

    private CompanionService() {}
}
