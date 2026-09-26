package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/**
 * Client-only construction. FancyMenu reads its layouts once every mod is constructed and skips element types
 * it does not know, so the Ark title-screen element has to be registered here rather than in client setup.
 */
@Mod(value = ArkSurvivalReturns.MOD_ID, dist = Dist.CLIENT)
public final class ArkClientMod {
    public ArkClientMod() {
        if (!ModList.get().isLoaded("fancymenu")) return;
        try {
            dev.nez.arksurvivalreturns.client.title.fancymenu.FancyMenuElements.register();
        } catch (LinkageError e) {
            ArkSurvivalReturns.LOGGER.warn("This FancyMenu version does not fit the Ark creature element; the title scene shows without it", e);
        }
    }
}
