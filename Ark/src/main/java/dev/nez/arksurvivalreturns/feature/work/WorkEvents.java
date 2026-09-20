package dev.nez.arksurvivalreturns.feature.work;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Tracks player-placed blocks so work jobs can respect what survivors build. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class WorkEvents {
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!Config.WORK_RESPECT_PLACED.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Player)) return;
        WorkProtection.get(level).record(event.getPos());
    }

    @SubscribeEvent public static void broken(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        WorkProtection.get(level).forget(event.getPos());
    }

    private WorkEvents() {}
}
