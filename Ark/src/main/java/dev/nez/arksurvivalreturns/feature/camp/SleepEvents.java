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
 * <p>{@code ServerPlayer#stopSleepInBed} fires {@link PlayerWakeUpEvent} before it clears the entity's
 * sleeping position or resets the sleep timer, so both are still readable here. Gating on {@code
 * getSleepTimer() > 0} is the "at least one sleep tick elapsed" rule from {@link AbstractSleepingBlock}:
 * it excludes the theoretical case of lying down and waking on the very same tick, while still counting
 * an interrupted sleep (a monster attack, a manual wake, or the morning skip) as a real use.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class SleepEvents {
    @SubscribeEvent
    public static void wake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getSleepTimer() <= 0) return;
        player.getSleepingPos().ifPresent(pos -> {
            if (player.level() instanceof ServerLevel level
                    && level.getBlockState(pos).getBlock() instanceof AbstractSleepingBlock bed && bed.isDisposable()) {
                bed.disposeAfterSleep(level, pos);
            }
        });
    }

    private SleepEvents() {}
}
