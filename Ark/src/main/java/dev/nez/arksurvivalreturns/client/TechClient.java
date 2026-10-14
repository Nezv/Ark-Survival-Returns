package dev.nez.arksurvivalreturns.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.tech.TechPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** A normal Controls entry: remapping mods can discover it without a custom integration. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class TechClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(ArkSurvivalReturns.id("progression"));
    public static final KeyMapping OPEN_TREE = new KeyMapping("key.arksurvivalreturns.tech_tree",
            InputConstants.Type.KEYSYM, InputConstants.KEY_P, CATEGORY);

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_TREE);
    }
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TechPayload.TYPE, (payload, context) -> {
            if (Minecraft.getInstance().screen instanceof TechScreen screen) screen.accept(payload);
        });
    }
    /** No advancement tab is loaded (C04): the pause menu's Advancements button and the L key open the Chronicle. */
    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof AdvancementsScreen) event.setNewScreen(new TechScreen());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        while (OPEN_TREE.consumeClick()) {
            if (mc.player != null && mc.level != null && mc.screen == null) mc.setScreen(new TechScreen());
        }
    }
    private TechClient() {}
}
