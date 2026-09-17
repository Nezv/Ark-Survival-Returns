package dev.nez.arksurvivalreturns.feature.theme;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Refuses the interaction, placement and use routes of the removed mechanics in worlds that
 * already contain them: enchanting, brewing, teleportation, magical infrastructure, sculk
 * gameplay, totems, golems, the Wither build and firework-powered flight.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class MechanicGuard {
    /**
     * Right-clicking a disabled block does nothing. The server decides, so that the client never
     * has to read the server config, which is only loaded where a server has loaded it.
     */
    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !Config.THEME_MECHANICS.get()) return;
        if (!ThemePolicy.disabled(event.getLevel().getBlockState(event.getPos()))) return;
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
    }

    /** Magical consumables, teleportation items and uncraftable remains cannot be used. */
    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() || !Config.THEME_MECHANICS.get()) return;
        if (!ThemePolicy.removed(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
    }

    /** Placement of a disabled mechanic's block, a golem build or a Wither build is refused. */
    @SubscribeEvent
    public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !Config.THEME_MECHANICS.get()) return;
        var state = event.getPlacedBlock();
        if (ThemePolicy.disabled(state)) { event.setCanceled(true); return; }
        var below = event.getLevel().getBlockState(event.getPos().below());
        if (ThemePolicy.isGolemHead(state) && ThemePolicy.golemBase(below)) { event.setCanceled(true); return; }
        if (state.is(Blocks.WITHER_SKELETON_SKULL) || state.is(Blocks.WITHER_SKELETON_WALL_SKULL)) {
            if (ThemePolicy.witherBase(below)) event.setCanceled(true);
        }
    }

    /** Totems of undying that survive in an existing inventory no longer save their holder. */
    @SubscribeEvent
    public static void useTotem(LivingUseTotemEvent event) {
        if (!Config.THEME_MECHANICS.get() || !ThemePolicy.removed(event.getTotem())) return;
        event.setCanceled(true);
    }

    /** Gliding is stopped server-side, so an elytra kept from another world cannot fly. */
    @SubscribeEvent
    public static void glide(PlayerTickEvent.Pre event) {
        if (!Config.THEME_MECHANICS.get() || event.getEntity().level().isClientSide()) return;
        if (event.getEntity().isFallFlying()) event.getEntity().stopFallFlying();
    }

    private MechanicGuard() {}
}
