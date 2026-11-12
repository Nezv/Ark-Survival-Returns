package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/** Client wiring for the taming feature: the saddle screen renderer and the tranquilizer arrow. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class TamingClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.CREATURE_MOUNT_MENU.get(), CreatureMountScreen::new);
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.TRANQUILIZER_ARROW.get(), SedativeArrowRenderer::new);
    }

    /**
     * The attack key of a rider who drives an Ark mount makes the mount strike (BUG-05); a block in reach is still
     * mined. The server picks the target, so nothing here is trusted.
     */
    @SubscribeEvent public static void mountedAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || !(player.getControlledVehicle() instanceof dev.nez.arksurvivalreturns.feature.creature.CreatureEntity)) return;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new dev.nez.arksurvivalreturns.feature.taming.MountedCombat.Strike());
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private TamingClient() {}
}
