package dev.nez.arksurvivalreturns.feature.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

/** Saved origin and width keep every player/chunk in agreement, including across /setworldspawn. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class ProgressionData extends SavedData {
    public static final Codec<ProgressionData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("origin_x").forGetter(d -> d.originX),
            Codec.INT.fieldOf("origin_z").forGetter(d -> d.originZ),
            Codec.intRange(96, 1024).fieldOf("band_width").forGetter(d -> d.bandWidth),
            Codec.BOOL.optionalFieldOf("initialized", true).forGetter(d -> d.initialized)
    ).apply(i, ProgressionData::new));
    public static final SavedDataType<ProgressionData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("progression"), () -> new ProgressionData(0, 0, 256, false), CODEC);
    /** Immutable copy of the saved layout, safe to read from world-generation worker threads. */
    public record Snapshot(int originX, int originZ, int bandWidth) {}
    private static volatile Snapshot snapshot;
    private int originX, originZ, bandWidth;
    private boolean initialized;
    public ProgressionData(int x, int z, int width, boolean initialized) {
        originX = x; originZ = z; bandWidth = width; this.initialized = initialized;
    }
    /**
     * The saved layout, locked to the world spawn the first time it is read after the spawn exists. A new
     * world loads its overworld before choosing the spawn; reads before that stay unlocked, and danger
     * fails closed (-1) instead of centring every band on (0, 0).
     */
    public static ProgressionData get(ServerLevel world) {
        var data = world.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        if (!data.initialized) {
            var overworld = world.getServer().getWorldData().overworldData();
            if (!overworld.isInitialized()) {
                snapshot = null;
                return data;
            }
            BlockPos spawn = overworld.getRespawnData().pos();
            data.originX = spawn.getX(); data.originZ = spawn.getZ();
            data.bandWidth = Config.BAND_WIDTH.get(); data.initialized = true; data.setDirty();
        }
        publish(data);
        return data;
    }
    private static void publish(ProgressionData data) {
        snapshot = new Snapshot(data.originX, data.originZ, data.bandWidth);
    }
    /** Published layout copy, or null before any level published one. */
    public static @Nullable Snapshot snapshot() {
        return snapshot;
    }
    /**
     * Danger at a position. On the server thread this reads (and republishes) the saved layout;
     * on a world-generation worker thread it uses the published immutable snapshot and fails
     * closed (-1) when the world has not published one yet, so generation never touches storage.
     */
    public static int dangerAt(ServerLevel level, BlockPos pos) {
        if (level.getServer().isSameThread()) {
            var data = get(level);
            return data.initialized ? data.levelAt(pos) : -1;
        }
        var data = snapshot;
        return data == null ? -1 : DangerBands.level(pos.getX(), pos.getZ(), data.originX, data.originZ, data.bandWidth);
    }
    public int levelAt(BlockPos pos) { return DangerBands.level(pos.getX(), pos.getZ(), originX, originZ, bandWidth); }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int bandWidth() { return bandWidth; }
    public boolean initialized() { return initialized; }
    @SubscribeEvent public static void start(ServerStartedEvent event) { get(event.getServer().overworld()); }
    /** The next world (singleplayer) must never generate with this world's layout. */
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { snapshot = null; }
    @SubscribeEvent public static void levelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) get(level);
    }
}
