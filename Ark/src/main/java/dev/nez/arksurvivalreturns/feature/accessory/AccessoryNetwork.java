package dev.nez.arksurvivalreturns.feature.accessory;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client-to-server accessory actions; the server re-checks everything. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class AccessoryNetwork {
    /** Quetzal Mantle: the wearer jumped again in mid-air. */
    public record AirJump() implements CustomPacketPayload {
        public static final Type<AirJump> TYPE = new Type<>(ArkSurvivalReturns.id("accessory_air_jump"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AirJump> STREAM_CODEC = StreamCodec.unit(new AirJump());
        @Override public Type<AirJump> type() { return TYPE; }
    }

    @SubscribeEvent static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(AirJump.TYPE, AirJump.STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) AccessoryEffects.airJump(player);
        });
    }

    private AccessoryNetwork() {}
}
