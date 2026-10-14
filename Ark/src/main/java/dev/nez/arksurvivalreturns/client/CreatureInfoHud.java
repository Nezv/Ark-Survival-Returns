package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.NighttimeClientConfig;
import dev.nez.arksurvivalreturns.feature.companion.CompanionService;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.jspecify.annotations.Nullable;

/**
 * A small plate under the crosshair for the creature the player looks at within 24 blocks: its name and level, a
 * health bar, and a tame's order (what the command wheel set, F22) or that a creature is unconscious. In the P16
 * palette, edged green for a tame, red for a wild hunter and tan for the rest. The target is picked once a tick; the
 * plate fades for half a second after the look moves off. Client setting creatureInfo.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID, value = Dist.CLIENT)
public final class CreatureInfoHud {
    private static final double RANGE = 24;
    private static final int LINGER = 10, BAR = 72;
    private static final int TITLE = 0xE6E8E1, MUTED = 0x8A8F86, TAME = 0x8FB07F, HUNTER = 0xC2573F, OTHER = 0xB5895A,
            UNCONSCIOUS = 0xE2763F, PANEL = 0x121410, WELL = 0x0B0C0A;
    private static @Nullable CreatureEntity target;
    private static int since = LINGER;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !NighttimeClientConfig.CREATURE_INFO.get()) {
            target = null;
            return;
        }
        CreatureEntity seen = CommandWheelClient.aimedCreature(mc, RANGE, creature -> true);
        if (seen != null) {
            target = seen;
            since = 0;
        } else if (++since >= LINGER) {
            target = null;
        }
        if (target != null && (!target.isAlive() || target.isRemoved() || target.level() != mc.level)) target = null;
    }

    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ArkSurvivalReturns.id("creature_info"), (graphics, delta) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        CreatureEntity creature = target;
        if (creature == null || mc.options.hideGui || mc.screen != null) return;
        float fade = since == 0 ? 1f : 1f - since / (float) LINGER;
        if (fade < 0.08f) return;
        var font = mc.font;
        Component name = creature.getDisplayName();
        Component level = Component.translatable("hud.arksurvivalreturns.creature.level", creature.creatureLevel());
        String health = Math.round(creature.getHealth()) + " / " + Math.round(creature.getMaxHealth());
        Component state = null;
        int stateColour = MUTED;
        if (TorporService.restricted(creature)) {
            state = Component.translatable("hud.arksurvivalreturns.creature.unconscious");
            stateColour = UNCONSCIOUS;
        } else if (creature.isTamed()) {
            state = Component.translatable("hud.arksurvivalreturns.creature.tame", Component.translatable(
                    "wheel.arksurvivalreturns.state." + CompanionService.of(creature).order().name().toLowerCase(java.util.Locale.ROOT)));
            stateColour = TAME;
        }
        int inner = Math.max(font.width(name) + 6 + font.width(level), BAR + 6 + font.width(health));
        if (state != null) inner = Math.max(inner, font.width(state));
        int width = inner + 12, height = state == null ? 28 : 39;
        int x = g.guiWidth() / 2 - width / 2, y = g.guiHeight() / 2 + 18;
        int edge = creature.isTamed() ? TAME : creature.species().predator ? HUNTER : OTHER;

        g.fill(x, y, x + width, y + height, argb(PANEL, 0.78f * fade));
        g.horizontalLine(x, x + width - 1, y, argb(edge, fade));
        int left = x + 6, line = y + 5;
        g.text(font, name, left, line, argb(TITLE, fade), false);
        g.text(font, level, left + font.width(name) + 6, line, argb(MUTED, fade), false);
        line += 12;
        float share = Math.clamp(creature.getHealth() / Math.max(1f, creature.getMaxHealth()), 0f, 1f);
        int bar = share > 0.6f ? TAME : share > 0.3f ? 0xE4C653 : HUNTER;
        g.fill(left, line + 2, left + BAR, line + 5, argb(WELL, fade));
        g.fill(left, line + 2, left + Math.round(BAR * share), line + 5, argb(bar, fade));
        g.text(font, health, left + BAR + 6, line, argb(MUTED, fade), false);
        if (state != null) g.text(font, state, left, line + 11, argb(stateColour, fade), false);
    }

    private static int argb(int rgb, float alpha) {
        return Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24 | rgb & 0xFFFFFF;
    }

    private CreatureInfoHud() {}
}
