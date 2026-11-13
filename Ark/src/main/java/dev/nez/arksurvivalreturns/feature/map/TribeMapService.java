package dev.nez.arksurvivalreturns.feature.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.tech.TechService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Ark map's server half (A01 OPT-14). Around every player in the Overworld the chunks already loaded are coloured
 * once, a few a second, nearest first, into the tribe's explored map; each new chunk goes to every online member, and a
 * member who joins receives the whole map in pieces. Nothing is loaded or generated for the map.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TribeMapService {
    /** Chunks around a player looked at for the map. */
    private static final int RADIUS = 5;
    /** New chunks one player colours per second. */
    private static final int PER_SECOND = 6;
    /** Chunks a payload carries; the join backlog sends one payload a tick. */
    private static final int PER_PAYLOAD = 32;
    private static final Map<UUID, ArrayDeque<Tile>> BACKLOG = new HashMap<>();

    public record Tile(long chunk, byte[] colours) {}

    /** Explored chunks for the client's map, a handful at a time. */
    public record Tiles(List<Tile> tiles) implements CustomPacketPayload {
        public static final Type<Tiles> TYPE = new Type<>(ArkSurvivalReturns.id("map_tiles"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Tile> TILE = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Tile::chunk, ByteBufCodecs.BYTE_ARRAY, Tile::colours, Tile::new);
        public static final StreamCodec<RegistryFriendlyByteBuf, Tiles> STREAM_CODEC =
                TILE.apply(ByteBufCodecs.list(PER_PAYLOAD)).map(Tiles::new, Tiles::tiles);
        @Override public Type<Tiles> type() { return TYPE; }
    }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Tiles.TYPE, Tiles.STREAM_CODEC);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var backlog = BACKLOG.get(player.getUUID());
        if (backlog != null && !backlog.isEmpty()) {
            var batch = new ArrayList<Tile>(PER_PAYLOAD);
            while (batch.size() < PER_PAYLOAD && !backlog.isEmpty()) batch.add(backlog.poll());
            PacketDistributor.sendToPlayer(player, new Tiles(batch));
        }
        if (player.tickCount % 20 != 0 || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) return;
        record(player);
    }

    /** Colours up to {@link #PER_SECOND} unexplored loaded chunks around the player. Public for the GameTests. */
    public static int record(ServerPlayer player) {
        var level = player.level();
        UUID tribe = TechService.tribeOf(player);
        var data = TribeMapData.get(level);
        int centreX = player.getBlockX() >> 4, centreZ = player.getBlockZ() >> 4, done = 0;
        var fresh = new ArrayList<Tile>();
        for (int ring = 0; ring <= RADIUS && done < PER_SECOND; ring++) {
            for (int dx = -ring; dx <= ring && done < PER_SECOND; dx++) for (int dz = -ring; dz <= ring && done < PER_SECOND; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                long key = ChunkPos.pack(centreX + dx, centreZ + dz);
                if (data.has(tribe, key)) continue;
                var chunk = level.getChunkSource().getChunkNow(centreX + dx, centreZ + dz);
                if (chunk == null) continue;
                byte[] colours = colours(level, chunk);
                data.put(tribe, key, colours);
                fresh.add(new Tile(key, colours));
                done++;
            }
        }
        if (!fresh.isEmpty()) {
            for (var member : level.getServer().getPlayerList().getPlayers())
                if (TechService.tribeOf(member).equals(tribe)) PacketDistributor.sendToPlayer(member, new Tiles(fresh));
        }
        return done;
    }

    /** One packed map colour per column, shaded by the slope from the north as vanilla maps are. */
    public static byte[] colours(ServerLevel level, LevelChunk chunk) {
        byte[] out = new byte[TribeMapData.COLUMNS];
        int[] heights = new int[TribeMapData.COLUMNS];
        var pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            MapColor colour = MapColor.NONE;
            int depth = 0;
            while (y > level.getMinY()) {
                pos.set(minX + x, --y, minZ + z);
                var state = chunk.getBlockState(pos);
                colour = state.getMapColor(level, pos);
                if (colour == MapColor.NONE) continue;
                if (!state.getFluidState().isEmpty()) {
                    // Water: its own colour, darker the deeper it is.
                    int bottom = y;
                    while (bottom > level.getMinY() && !chunk.getBlockState(pos.setY(bottom - 1)).getFluidState().isEmpty() && depth < 12) { bottom--; depth++; }
                }
                break;
            }
            heights[z * 16 + x] = y;
            MapColor.Brightness brightness;
            if (depth > 0) brightness = depth > 7 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
            else {
                int north = z > 0 ? heights[(z - 1) * 16 + x] : y;
                brightness = y > north ? MapColor.Brightness.HIGH : y < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
            }
            out[z * 16 + x] = colour.getPackedId(brightness);
        }
        return out;
    }

    /** A joining member receives the tribe's whole map, a payload a tick. */
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) queueAll(player);
    }

    public static void queueAll(ServerPlayer player) {
        var queue = new ArrayDeque<Tile>();
        TribeMapData.get(player.level()).chunks(TechService.tribeOf(player)).forEach((key, colours) -> queue.add(new Tile(key, colours)));
        BACKLOG.put(player.getUUID(), queue);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { BACKLOG.remove(event.getEntity().getUUID()); }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { BACKLOG.clear(); }

    private TribeMapService() {}
}
