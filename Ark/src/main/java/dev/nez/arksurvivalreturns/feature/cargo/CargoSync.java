package dev.nez.arksurvivalreturns.feature.cargo;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server handler for the Load/Unload buttons; access and range are validated on every request. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class CargoSync {
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(CargoTransferPayload.TYPE, CargoTransferPayload.STREAM_CODEC, CargoSync::handle);
    }

    private static void handle(CargoTransferPayload packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.level().getEntity(packet.entityId()) instanceof CreatureEntity creature)) return;
        if (!TamingService.canAccess(creature, player) || !player.isWithinEntityInteractionRange(creature, 4.0)) return;
        int moved = packet.load() ? CargoTransferService.load(player, creature) : CargoTransferService.unload(player, creature);
        player.sendSystemMessage(Component.translatable(
                moved > 0 ? "cargo.arksurvivalreturns.moved" : "cargo.arksurvivalreturns.none", moved), true);
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        MassService.markDirty(creature);
    }

    private CargoSync() {}
}
