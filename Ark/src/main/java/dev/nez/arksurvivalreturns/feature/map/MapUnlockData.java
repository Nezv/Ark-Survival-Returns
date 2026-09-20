package dev.nez.arksurvivalreturns.feature.map;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-scoped, per-player entitlement. A rank-5-origin tame or /arkmap grants it. */
public final class MapUnlockData extends SavedData {
    public static final Codec<MapUnlockData> CODEC = UUIDUtil.CODEC.listOf().xmap(MapUnlockData::new,
            data -> data.unlocked.stream().sorted().toList()).fieldOf("unlocked_players").codec();
    public static final SavedDataType<MapUnlockData> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("map_unlocks"), () -> new MapUnlockData(List.of()), CODEC);
    private final Set<UUID> unlocked;
    public MapUnlockData(List<UUID> players) { unlocked = new HashSet<>(players); }
    public static MapUnlockData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    public boolean isUnlocked(UUID player) { return unlocked.contains(player); }
    public boolean hasAccess(UUID player, boolean requiresUnlock) { return !requiresUnlock || isUnlocked(player); }
    public boolean setUnlocked(UUID player, boolean value) {
        boolean changed = value ? unlocked.add(player) : unlocked.remove(player);
        if (changed) setDirty();
        return changed;
    }
}
