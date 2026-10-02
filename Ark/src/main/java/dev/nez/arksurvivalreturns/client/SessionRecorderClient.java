package dev.nez.arksurvivalreturns.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.recorder.RecorderPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client side of the session recorder. The server cannot tell a loading screen from a player who can
 * move, so the client says so once per join; the recorder's clock starts from that moment. Also the
 * marker key, which flags the current moment in a running recording, and for a recording with nobody
 * at the keyboard ({@code -Darksurvivalreturns.record.quit=true}) closing the game once the file is saved,
 * and ({@code -Darksurvivalreturns.record.freshWorld=<folder>}) creating a new world at the title screen.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class SessionRecorderClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(ArkSurvivalReturns.id("debug"));
    private static final KeyMapping MARK = new KeyMapping("key.arksurvivalreturns.session_mark",
            InputConstants.Type.KEYSYM, InputConstants.KEY_F7, CATEGORY);
    private static final boolean QUIT_WHEN_SAVED = Boolean.getBoolean("arksurvivalreturns.record.quit");
    private static final String FRESH_WORLD = System.getProperty("arksurvivalreturns.record.freshWorld", "");
    private static boolean reported, recording, created;
    private static int quitIn = -1;

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(MARK);
    }

    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(RecorderPayloads.Status.TYPE, (payload, context) -> {
            recording = payload.recording();
            // The file is closed and checked by now; a moment for the chat line, then the game ends.
            if (!payload.recording() && QUIT_WHEN_SAVED) quitIn = 60;
        });
    }

    /** True from the recording's first tick until its file is saved, as the server reported it. */
    static boolean recording() { return recording; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (quitIn >= 0 && quitIn-- == 0) mc.stop();
        if (!FRESH_WORLD.isEmpty() && !created && mc.level == null && mc.isGameLoadFinished() && mc.getOverlay() == null
                && mc.screen != null) {
            created = true;
            createWorld(mc);
        }
        var connection = mc.getConnection();
        if (mc.player == null || mc.level == null || connection == null) return;
        while (MARK.consumeClick())
            if (connection.hasChannel(RecorderPayloads.Mark.TYPE)) ClientPacketDistributor.sendToServer(new RecorderPayloads.Mark("key"));
        // In control: no loading or menu screen, no resource-reload overlay, not paused.
        if (reported || mc.screen != null || mc.getOverlay() != null || mc.isPaused()) return;
        if (!connection.hasChannel(RecorderPayloads.Ready.TYPE)) return;
        reported = true;
        ClientPacketDistributor.sendToServer(new RecorderPayloads.Ready(mc.options.renderDistance().get(),
                mc.options.simulationDistance().get(), shaders(), ModList.get().isLoaded("distanthorizons"), mc.getFps(),
                mc.options.guiScale().get(), mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()));
    }

    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { reported = recording = false; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reported = recording = false; }

    /** A new survival world with the game's defaults, as the Create New World screen makes it; the seed is optional. */
    private static void createWorld(Minecraft mc) {
        long seed = WorldOptions.parseSeed(System.getProperty("arksurvivalreturns.record.seed", "")).orElse(WorldOptions.randomSeed());
        var settings = new LevelSettings(FRESH_WORLD, GameType.SURVIVAL, LevelSettings.DifficultySettings.DEFAULT, false,
                WorldDataConfiguration.DEFAULT);
        ArkSurvivalReturns.LOGGER.info("Session recorder: creating the world {} with seed {}", FRESH_WORLD, seed);
        mc.createWorldOpenFlows().createFreshLevel(FRESH_WORLD, settings, new WorldOptions(seed, true, false),
                WorldPresets::createNormalWorldDimensions, mc.screen);
    }

    /** The shader pack Iris is drawing with, read through its public API so Iris stays optional. */
    private static String shaders() {
        if (!ModList.get().isLoaded("iris")) return "none";
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object iris = api.getMethod("getInstance").invoke(null);
            if (!(Boolean) api.getMethod("isShaderPackInUse").invoke(iris)) return "off";
            try {
                Object name = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
                String text = String.valueOf(name);
                return text.substring(0, Math.min(text.length(), 120));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "on";
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "unknown";
        }
    }

    private SessionRecorderClient() {}
}
