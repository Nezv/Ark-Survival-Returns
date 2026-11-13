package dev.nez.arksurvivalreturns.feature.testground;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The Specimen Post's screen: the server opens it, the screen sends the settings and an action back. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class SpecimenPayload {
    /** Server to client: open the screen of the post at this position. */
    public record Open(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ArkSurvivalReturns.id("specimen_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBlockPos(p.pos), buf -> new Open(buf.readBlockPos()));
        @Override public Type<Open> type() { return TYPE; }
    }

    /** Client to server: the post's settings as the screen shows them, and what to do. */
    public record Command(BlockPos pos, String species, int variant, int level, int mode, int hunger, boolean respawn, int action)
            implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(ArkSurvivalReturns.id("specimen_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Command> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos); buf.writeUtf(p.species, 64); buf.writeVarInt(p.variant); buf.writeVarInt(p.level);
            buf.writeVarInt(p.mode); buf.writeVarInt(p.hunger); buf.writeBoolean(p.respawn); buf.writeVarInt(p.action);
        }, buf -> new Command(buf.readBlockPos(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<Command> type() { return TYPE; }
    }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Open.TYPE, Open.STREAM_CODEC);
        registrar.playToServer(Command.TYPE, Command.STREAM_CODEC, (packet, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !TestGround.mayUse(player)) return;
            if (player.position().distanceToSqr(Vec3.atCenterOf(packet.pos())) > 64 * 64) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof SpecimenPostBlockEntity post)) return;
            Species species = null;
            for (Species s : Species.values()) if (s.id.equals(packet.species())) species = s;
            var modes = SpecimenPostBlockEntity.Mode.values();
            var actions = SpecimenPostBlockEntity.Action.values();
            if (species == null || packet.mode() < 0 || packet.mode() >= modes.length || packet.action() < 0 || packet.action() >= actions.length) return;
            var action = actions[packet.action()];
            // Only Spawn and Apply change the body. Any other button acts on the specimen that is out: a species picked
            // in the list and not yet spawned must not replace it on the way to a Kill or a Tame.
            boolean body = action == SpecimenPostBlockEntity.Action.SPAWN || action == SpecimenPostBlockEntity.Action.APPLY || !post.hasSpecimen();
            post.configure(body ? species : post.species(), body ? packet.variant() : post.variant(), body ? packet.level() : post.creatureLevel(),
                    modes[packet.mode()], packet.hunger(), packet.respawn(), action != SpecimenPostBlockEntity.Action.SPAWN);
            post.act(action, player);
        });
    }

    private SpecimenPayload() {}
}
