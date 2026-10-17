package dev.nez.arksurvivalreturns.client;

import java.io.File;
import java.util.List;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The showcase's Interface screens, taken from the real client with nobody at the keyboard ({@code tools/ui_screens.py}).
 * Off unless the client was launched with {@code -Darksurvivalreturns.screens=<output folder>}, into a single-player
 * world. Once the player is in control they stand at the world spawn in survival, on a clear morning, wearing keratin
 * and carrying a few Prehistoric tools; after 45 seconds for chunks and shaders the
 * folder gets game.png, inventory.png (the Ark inventory) and options.png. Back on the title screen it gets title.png
 * (FancyMenu's scene with the creature) and new_game.png; then the game closes.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class ScreenTour {
    private static final String OUT = System.getProperty("arksurvivalreturns.screens", "");
    private static final int SETTLE_TICKS = 20 * 45;
    /** A screen open this long has drawn its frames and finished opening; the title scene needs longer for the creature. */
    private static final int SHOW_TICKS = 60, TITLE_TICKS = 200, QUIT_TICKS = 40;
    private static final List<String> GEAR = List.of("clear @a",
            "item replace entity @a armor.head with arksurvivalreturns:keratin_helmet",
            "item replace entity @a armor.chest with arksurvivalreturns:keratin_chestplate",
            "item replace entity @a armor.legs with arksurvivalreturns:keratin_leggings",
            "item replace entity @a armor.feet with arksurvivalreturns:keratin_boots",
            "give @a arksurvivalreturns:stone_hatchet", "give @a arksurvivalreturns:rock_pickaxe",
            "give @a arksurvivalreturns:keratin_spear", "give @a arksurvivalreturns:flint_knife",
            "give @a minecraft:torch 16", "give @a arksurvivalreturns:amarberry 24", "give @a minecraft:oak_log 32");

    private enum Step { WAIT, SETTLE, INVENTORY, OPTIONS, LEAVE, TITLE, NEW_GAME, DONE }

    private static Step step = Step.WAIT;
    private static int ticks;
    private static boolean windowSet;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (OUT.isEmpty()) return;
        var mc = Minecraft.getInstance();
        // A full-screen window that was opened without the focus minimises itself and draws nothing.
        if (!windowSet && mc.getWindow() != null) {
            windowSet = true;
            GLFW.glfwSetWindowAttrib(mc.getWindow().handle(), GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
        }
        ticks++;
        // Recipe and advancement toasts would cover the corner of every picture.
        if (step != Step.WAIT) mc.getToastManager().clear();
        switch (step) {
            case WAIT -> {
                if (mc.player != null && mc.level != null && mc.screen == null && mc.getOverlay() == null && mc.getSingleplayerServer() != null) {
                    begin(mc);
                    go(Step.SETTLE);
                }
            }
            case SETTLE -> {
                if (mc.screen != null && mc.screen.isPauseScreen()) mc.setScreen(null);
                if (ticks >= SETTLE_TICKS) shot(mc, "game", Step.INVENTORY);
            }
            case INVENTORY -> {
                // Opening the vanilla screen goes through WorkstationClient's redirect to the Ark inventory, as the key does.
                if (ticks == 1) mc.setScreen(new InventoryScreen(mc.player));
                if (ticks >= SHOW_TICKS) shot(mc, "inventory", Step.OPTIONS);
            }
            case OPTIONS -> {
                if (ticks == 1) mc.setScreen(new OptionsScreen(new PauseScreen(true), mc.options, true));
                if (ticks >= SHOW_TICKS) shot(mc, "options", Step.LEAVE);
            }
            case LEAVE -> {
                if (ticks == 1) mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                else if (mc.level == null && mc.screen instanceof TitleScreen) go(Step.TITLE);
            }
            case TITLE -> {
                if (ticks >= TITLE_TICKS) shot(mc, "title", Step.NEW_GAME);
            }
            case NEW_GAME -> {
                // The screen opens once the world presets are loaded; the clock starts then.
                if (ticks == 1) CreateWorldScreen.openFresh(mc, () -> mc.setScreen(new TitleScreen()));
                if (!(mc.screen instanceof CreateWorldScreen)) ticks = Math.min(ticks, 2);
                else if (ticks >= SHOW_TICKS) shot(mc, "new_game", Step.DONE);
            }
            case DONE -> {
                if (ticks == QUIT_TICKS) mc.stop();
            }
        }
    }

    private static void begin(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var level = server.overworld();
            var spawn = server.getRespawnData().pos();
            int y = level.getChunk(spawn).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) + 1;
            float heading = FrameBenchmark.landward(level, spawn);
            var source = server.createCommandSourceStack().withSuppressedOutput();
            for (String command : List.of("gamemode survival @a", "effect give @a minecraft:resistance infinite 255 true",
                    "gamerule advance_time false", "gamerule advance_weather false", "weather clear", "time set 2000",
                    "execute in minecraft:overworld run tp @a " + (spawn.getX() + 0.5) + " " + y + " " + (spawn.getZ() + 0.5) + " " + heading + " 8"))
                server.getCommands().performPrefixedCommand(source, command);
            for (String command : GEAR) server.getCommands().performPrefixedCommand(source, command);
        });
        ArkSurvivalReturns.LOGGER.info("Screen tour: started, output {}", OUT);
    }

    /** The picture of what the last frame showed (in the folder's screenshots/), then the next step. */
    private static void shot(Minecraft mc, String name, Step next) {
        Screenshot.grab(new File(OUT), name + ".png", mc.getMainRenderTarget(), 1, message -> {});
        ArkSurvivalReturns.LOGGER.info("Screen tour: {}.png", name);
        go(next);
    }

    private static void go(Step next) {
        step = next;
        ticks = 0;
    }

    private ScreenTour() {}
}
