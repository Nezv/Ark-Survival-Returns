package dev.nez.arksurvivalreturns.feature.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

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
    private int originX, originZ, bandWidth;
    private boolean initialized;
    public ProgressionData(int x, int z, int width, boolean initialized) {
        originX = x; originZ = z; bandWidth = width; this.initialized = initialized;
    }
    public static ProgressionData get(ServerLevel world) {
        var data = world.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        if (!data.initialized) {
            BlockPos spawn = world.getServer().getWorldData().overworldData().getRespawnData().pos();
            data.originX = spawn.getX(); data.originZ = spawn.getZ();
            data.bandWidth = Config.BAND_WIDTH.get(); data.initialized = true; data.setDirty();
        }
        return data;
    }
    public int levelAt(BlockPos pos) { return DangerBands.level(pos.getX(), pos.getZ(), originX, originZ, bandWidth); }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public int bandWidth() { return bandWidth; }
    @SubscribeEvent public static void start(ServerStartedEvent event) { get(event.getServer().overworld()); }
}
