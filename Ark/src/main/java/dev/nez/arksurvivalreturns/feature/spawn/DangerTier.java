package dev.nez.arksurvivalreturns.feature.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The five area danger ranks of the repeating zone map, with their default wild level ranges. Danger comes
 * only from the area (ProgressionData / DangerBands): biomes decide where a species can live, never how
 * dangerous it is.
 */
public enum DangerTier {
    EASY("easy", 1, 12), MODERATE("moderate", 8, 28), HARD("hard", 20, 50),
    SEVERE("severe", 32, 64), EXTREME("extreme", 40, 80);
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
