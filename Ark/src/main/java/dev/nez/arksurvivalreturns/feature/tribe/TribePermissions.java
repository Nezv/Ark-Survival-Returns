package dev.nez.arksurvivalreturns.feature.tribe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-player action flags shared inside one FTB Teams party.
 *
 * <p>Membership itself is owned by FTB Teams; this store only records deviations from the
 * configured defaults. A player with no entry uses the defaults, and leaving or changing a
 * team clears the player's entry so flags never follow someone into another tribe.
 */
public final class TribePermissions extends SavedData {
    public static final int SCHEMA_VERSION = 1;
    public static final Codec<TribePermissions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", SCHEMA_VERSION).forGetter(data -> data.schemaVersion),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("players", Map.of()).forGetter(data -> data.flags)
    ).apply(instance, TribePermissions::new));
    public static final SavedDataType<TribePermissions> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("tribe_permissions"), TribePermissions::new, CODEC);

    private final int schemaVersion;
    private final Map<UUID, Integer> flags;

    public TribePermissions() {
        this(SCHEMA_VERSION, Map.of());
    }

    public TribePermissions(int schemaVersion, Map<UUID, Integer> flags) {
        this.schemaVersion = schemaVersion;
        this.flags = new HashMap<>(flags);
    }

    public static TribePermissions get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** True when the permission is granted to this player under the supplied default mask. */
    public boolean allows(UUID player, TribePermission permission, int defaultMask) {
        return TribePermission.allows(flags.getOrDefault(player, defaultMask), permission);
    }

    /** Resolved mask for display; absent entries fall back to the configured defaults. */
    public int mask(UUID player, int defaultMask) {
        return flags.getOrDefault(player, defaultMask);
    }

    /** True when the player has an explicit deviation from the defaults. */
    public boolean customized(UUID player) {
        return flags.containsKey(player);
    }

    /** Applies one flag. Returning to the default mask removes the stored entry. */
    public boolean set(UUID player, TribePermission permission, boolean enabled, int defaultMask) {
        int current = flags.getOrDefault(player, defaultMask);
        int updated = enabled ? current | permission.mask() : current & ~permission.mask();
        if (updated == current) return false;
        if (updated == defaultMask) flags.remove(player);
        else flags.put(player, updated);
        setDirty();
        return true;
    }

    /** Drops a player's custom flags, for example after a team change. */
    public boolean clear(UUID player) {
        if (flags.remove(player) == null) return false;
        setDirty();
        return true;
    }

    public int size() {
        return flags.size();
    }
}
