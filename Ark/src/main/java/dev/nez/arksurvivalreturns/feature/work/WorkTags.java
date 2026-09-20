package dev.nez.arksurvivalreturns.feature.work;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags the work jobs target, so a data pack can retune what each worker harvests. */
public final class WorkTags {
    /** Triceratops forage: grass, ferns and berry bushes. */
    public static final TagKey<Block> FORAGE = tag("work/forage");
    /** Ankylosaurus minerals: natural stone and ores. */
    public static final TagKey<Block> MINERAL = tag("work/mineral");
    /** NeoForge common ore tag, used only to decide the bonus drop. */
    public static final TagKey<Block> ORE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ArkSurvivalReturns.id(path));
    }

    private WorkTags() {}
}
