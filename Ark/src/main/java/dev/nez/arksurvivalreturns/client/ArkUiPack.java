package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.NighttimeClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Registers the built-in "Ark UI" resource pack (resourcepacks/ark_ui, built by tools/build_ui_pack.py).
 * Resource packs from mods start disabled unless forced, so the pack is forced on while the client
 * option is true; turning the option off removes it after a restart.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class ArkUiPack {
    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !enabled()) return;
        event.addPackFinders(ArkSurvivalReturns.id("resourcepacks/ark_ui"), PackType.CLIENT_RESOURCES,
                Component.literal("Ark UI"), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static boolean enabled() {
        try {
            return NighttimeClientConfig.ARK_UI.get();
        } catch (IllegalStateException notLoaded) {
            return NighttimeClientConfig.ARK_UI.getDefault();
        }
    }

    private ArkUiPack() {}
}
