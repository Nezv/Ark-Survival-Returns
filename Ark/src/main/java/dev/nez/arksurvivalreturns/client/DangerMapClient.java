package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.map.DangerMapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class DangerMapClient {
    private static DangerMapPayload profile;
    private static long lastMessage;
    public static DangerMapPayload profile() { return profile; }
    public static boolean unlocked() { return profile != null && profile.unlocked(); }
    @SubscribeEvent public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(DangerMapPayload.TYPE, (payload, context) -> {
            profile = payload.unlocked() && payload.bandWidth() >= 96 && payload.bandWidth() <= 1024 ? payload : null;
            if (!unlocked() && isMap(Minecraft.getInstance().gui.screen())) Minecraft.getInstance().gui.setScreen(null);
        });
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("xaero_world_map_bridge")) event.enqueueWork(() -> { XaeroDangerOverlay.register(); XaeroHabitatOverlay.register(); XaeroLandHabitatOverlay.register(); });
    }
    public static boolean isMap(Screen screen) {
        for (Class<?> type = screen == null ? null : screen.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("xaero.map.gui.GuiMap")) return true;
        return false;
    }
    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        if (!isMap(event.getNewScreen()) || unlocked()) return;
        event.setCanceled(true);
        var player = Minecraft.getInstance().player;
        long now = System.nanoTime();
        if (player != null && now - lastMessage > 2_000_000_000L) {
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.translatable("map.arksurvivalreturns.locked"));
            lastMessage = now;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        profile = null; lastMessage = 0;
    }
    private DangerMapClient() {}
}
