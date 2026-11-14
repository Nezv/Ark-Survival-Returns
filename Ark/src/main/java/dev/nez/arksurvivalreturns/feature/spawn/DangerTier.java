package dev.nez.arksurvivalreturns.feature.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The three area danger zones of the repeating zone map, with their default wild level ranges: the first zone is
 * as hard as the last one used to be (levels 32-80 then), the third is meant to be unplayable on foot. Danger comes
 * only from the area (ProgressionData / DangerBands): biomes decide where a species can live, never how
 * dangerous it is.
 */
public enum DangerTier {
    EASY("easy", 30, 70), DANGEROUS("dangerous", 70, 120), DEADLY("deadly", 120, 200);
    public final String id;
    public final int minLevel, maxLevel;
    DangerTier(String id, int min, int max) {
        this.id = id; minLevel = min; maxLevel = max;
    }
    public int dangerLevel() { return ordinal() + 1; }
    public static DangerTier at(ServerLevel world, BlockPos pos) {
        return values()[ProgressionData.get(world).levelAt(pos) - 1];
    }
}
