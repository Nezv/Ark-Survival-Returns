package dev.nez.arksurvivalreturns.feature.camp;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/**
 * Consumes a disposable sleeping prop (the Mattress) once its sleeper wakes up.
 *
 * <p>{@code ServerPlayer#stopSleepInBed} fires {@link PlayerWakeUpEvent} before it clears the sleeping
 * position, so the bed is still known here. Any wake counts as a use: the morning skip, a monster, or
 * leaving the bed by hand.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class SleepEvents {
    @SubscribeEvent
    public static void wake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getSleepingPos().ifPresent(pos -> {
            if (player.level() instanceof ServerLevel level
                    && level.getBlockState(pos).getBlock() instanceof AbstractSleepingBlock bed && bed.isDisposable()) {
                bed.disposeAfterSleep(level, pos);
            }
        });
    }

    private SleepEvents() {}
}
