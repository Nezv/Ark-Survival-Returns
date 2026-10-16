package dev.nez.arksurvivalreturns.feature.inventory;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The inventory key, sent by the client instead of opening the vanilla screen: the server opens the Ark inventory. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class InventoryPayload {
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ArkSurvivalReturns.id("open_inventory"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.unit(new Open());
        @Override public Type<Open> type() { return TYPE; }
    }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Open.TYPE, Open.STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) open(player);
        });
    }

    public static void open(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || player.hasInfiniteMaterials()) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new ArkInventoryMenu(id, inventory),
                Component.translatable("container.inventory")));
    }

    private InventoryPayload() {}
}
