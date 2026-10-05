package dev.nez.arksurvivalreturns.feature.drop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The Overworld day the last supply drop was sent on, so the two-day rhythm survives a restart. */
public final class SupplyDropData extends SavedData {
    public static final long UNSET = -1;
    public static final Codec<SupplyDropData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("last_day", UNSET).forGetter(data -> data.lastDay)
    ).apply(instance, SupplyDropData::new));
    public static final SavedDataType<SupplyDropData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("supply_drops"), SupplyDropData::new, CODEC);

    private long lastDay;

    public SupplyDropData() { this(UNSET); }

    public SupplyDropData(long lastDay) { this.lastDay = lastDay; }

    public static SupplyDropData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public long lastDay() { return lastDay; }

    public void setLastDay(long day) {
        if (day == lastDay) return;
        lastDay = day;
        setDirty();
    }
}
