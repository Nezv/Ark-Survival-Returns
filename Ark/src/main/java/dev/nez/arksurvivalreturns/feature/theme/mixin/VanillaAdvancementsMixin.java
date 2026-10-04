package dev.nez.arksurvivalreturns.feature.theme.mixin;

import java.util.HashMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.feature.theme.ThemePolicy;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The vanilla advancement tabs never load. Filtering the loaded map, not overriding the files, also covers the
 * advancements NeoForge ships its own copy of, which win over a data override.
 */
@Mixin(ServerAdvancementManager.class)
abstract class VanillaAdvancementsMixin {
    @ModifyVariable(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true)
    private Map<Identifier, Advancement> ark$dropVanilla(Map<Identifier, Advancement> loaded) {
        var kept = new HashMap<>(loaded);
        kept.keySet().removeIf(ThemePolicy::removedAdvancement);
        return kept;
    }
}
