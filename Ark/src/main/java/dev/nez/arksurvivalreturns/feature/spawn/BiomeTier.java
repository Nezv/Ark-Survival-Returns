package dev.nez.arksurvivalreturns.feature.spawn;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public enum BiomeTier {
    EASY("easy", 1, 12), MODERATE("moderate", 8, 28), HARD("hard", 20, 50),
    SEVERE("severe", 32, 64), EXTREME("extreme", 40, 80);
    public final String id;
    public final int minLevel, maxLevel;
    public final TagKey<Biome> tag;
    BiomeTier(String id, int min, int max) {
        this.id = id; minLevel = min; maxLevel = max;
        tag = TagKey.create(Registries.BIOME, ArkSurvivalReturns.id("difficulty/" + id));
    }
    /** Highest difficulty wins if an extension pack overlaps tier tags. Unknown biomes are moderate. */
    public static BiomeTier of(Holder<Biome> biome) {
        for (int i = values().length - 1; i >= 0; i--) if (biome.is(values()[i].tag)) return values()[i];
        return MODERATE;
    }
    public int dangerLevel() { return ordinal() + 1; }
    public static BiomeTier at(ServerLevel world, BlockPos pos) {
        return values()[ProgressionData.get(world).levelAt(pos) - 1];
    }
}
