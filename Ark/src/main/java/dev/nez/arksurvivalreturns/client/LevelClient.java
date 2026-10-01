package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Permanent progression occupies the vanilla experience bar's position above the hotbar. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class LevelClient {
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, (graphics, delta) -> renderLevel(graphics));
    }

    /** Called by the experience renderer; the contextual mount jump renderer keeps its normal bar. */
    public static void renderBar(GuiGraphicsExtractor graphics) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.options.hideGui) return;
        var progress = ArkLevels.get(mc.player);
        boolean capped = progress.level() >= Config.PLAYER_LEVEL_CAP.get();
        float ratio = capped ? 1F : Math.clamp((float) progress.xp() / ArkLevels.xpForNextLevel(progress.level()), 0F, 1F);
        int x = (graphics.guiWidth() - 182) / 2;
        int y = graphics.guiHeight() - 29;
        graphics.fill(x, y, x + 182, y + 5, 0xFF17221A);
        graphics.fill(x + 1, y + 1, x + 1 + (int) (180 * ratio), y + 4, 0xFF80FF20);
    }

    private static void renderLevel(GuiGraphicsExtractor graphics) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.options.hideGui || !mc.gameMode.hasExperience()) return;
        String text = Integer.toString(ArkLevels.get(mc.player).level());
        graphics.text(mc.font, text, (graphics.guiWidth() - mc.font.width(text)) / 2, graphics.guiHeight() - 35, 0xFF80FF20);
    }
    private LevelClient() {}
}
