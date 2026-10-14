package dev.nez.arksurvivalreturns.client;

import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.companion.CompanionCommand;
import dev.nez.arksurvivalreturns.feature.companion.CompanionCommandPayload;
import dev.nez.arksurvivalreturns.feature.companion.CompanionOrder;
import dev.nez.arksurvivalreturns.feature.companion.CompanionService;
import dev.nez.arksurvivalreturns.feature.companion.CompanionWhistle;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The command wheel (F22): six carved wedges round a medallion, clockwise from the top Follow, Stay, Wander, Work, Pet
 * and Call (tools/build_command_wheel.py draws them). The medallion shows the tame under the crosshair, turning to
 * the wedge the cursor points at, or the count of the player's own tames within whistle range.
 *
 * <p>Held, the key opens the wheel and releasing it gives the pointed order; tapped, the wheel stays open for a click
 * or a number key 1 to 6. A wedge the tame cannot take is dimmed and says why; an ember gem marks the order it already
 * has. The client only asks: the server checks ownership, tribe permissions and range (CompanionWhistle).
 */
public final class CommandWheelScreen extends Screen {
    private static final CompanionCommand[] WEDGES = CompanionCommand.values();
    /** GUI units, matching the art: the texture's side, the ring's outer edge, the medallion's edge. */
    private static final int WHEEL = 136, OUTER = 64, INNER = 28;
    private static final int DEAD_ZONE = 14;
    private static final long TAP_NANOS = 300_000_000L;
    private static final int TITLE = 0xFFE6E8E1, MUTED = 0xFF8A8F86, OFF = 0xFF555A50, HINT = 0xFFB8C9AD,
            REASON = 0xFFE2763F, FAINT = 0xFF7A7F76;

    private final @Nullable CreatureEntity target;
    private final long openedAt = System.nanoTime();
    private List<CreatureEntity> group = List.of();
    private boolean clickMode;
    private int hovered = -1;
    private int ticks;
    private float scale = 1;

    public CommandWheelScreen(@Nullable CreatureEntity target) {
        super(Component.translatable("key.arksurvivalreturns.command_wheel"));
        this.target = target;
    }

    private static Identifier texture(String name) {
        return ArkSurvivalReturns.id("textures/gui/command_wheel/" + name + ".png");
    }

    @Override protected void init() {
        // Room above for the name and below for the hint; a short window shrinks the ring rather than clip it.
        scale = Math.clamp((height - 96) / (float) (WHEEL + 20), 0.55f, 1f);
        refresh();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void tick() {
        if (target != null && (!target.isAlive() || target.isRemoved())) { onClose(); return; }
        if (++ticks % 10 == 0) refresh();
    }

    private void refresh() {
        var player = minecraft.player;
        if (target != null || player == null) return;
        double range = CompanionWhistle.range();
        group = player.level().getEntitiesOfClass(CreatureEntity.class, player.getBoundingBox().inflate(range),
                creature -> creature.isAlive() && creature.isTamed() && creature.isOwnedBy(player)
                        && !TorporService.restricted(creature) && creature.distanceTo(player) <= range);
    }

    private int centerX() { return width / 2; }
    private int centerY() { return height / 2 + 6; }

    /** Centre of wedge i in radians, screen-wise: the top is -90 degrees and angles grow clockwise. */
    private static double angle(int i) { return Math.toRadians(-90 + 60 * i); }

    // ------------------------------------------------------------------------------------------- state

    /** Why a command cannot be given here, or null when it can. */
    private @Nullable Component unavailable(CompanionCommand command) {
        var player = minecraft.player;
        if (target != null) {
            if (TorporService.restricted(target)) return Component.translatable("wheel.arksurvivalreturns.why.unconscious");
            if (command == CompanionCommand.PET) {
                return player != null && player.distanceTo(target) > CompanionWhistle.PET_REACH + target.getBbWidth() / 2
                        ? Component.translatable("wheel.arksurvivalreturns.why.pet_far") : null;
            }
            if (command == CompanionCommand.WORK && !CompanionWhistle.workCapable(target)) {
                return Component.translatable("companion.arksurvivalreturns.command.cannot_work", target.getDisplayName());
            }
            return null;
        }
        if (group.isEmpty()) return Component.translatable("wheel.arksurvivalreturns.why.none", (int) CompanionWhistle.range());
        if (command == CompanionCommand.PET) return Component.translatable("wheel.arksurvivalreturns.why.pet_group");
        if (command == CompanionCommand.WORK && group.stream().noneMatch(CompanionWhistle::workCapable)) {
            return Component.translatable("companion.arksurvivalreturns.command.group.cannot_work");
        }
        return null;
    }

    /** The order the tame already has, or the one every tame of the group shares. */
    private @Nullable CompanionOrder current() {
        if (target != null) return CompanionService.of(target).order();
        CompanionOrder shared = null;
        for (var creature : group) {
            CompanionOrder order = CompanionService.of(creature).order();
            if (shared != null && order != shared) return null;
            shared = order;
        }
        return shared;
    }

    private void hover(double mouseX, double mouseY) {
        double dx = (mouseX - centerX()) / scale, dy = (mouseY - centerY()) / scale;
        int next = -1;
        if (Math.hypot(dx, dy) >= DEAD_ZONE) {
            double degrees = Math.toDegrees(Math.atan2(dy, dx));
            next = (int) Math.floor(((degrees + 120) % 360 + 360) % 360 / 60) % WEDGES.length;
        }
        if (next != hovered) {
            hovered = next;
            if (next >= 0 && unavailable(WEDGES[next]) == null) click(1.7f, 0.18f);
        }
    }

    private void click(float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }

    private void choose(int index) {
        if (index < 0 || index >= WEDGES.length) return;
        var command = WEDGES[index];
        if (unavailable(command) != null) { click(0.6f, 0.3f); return; }
        ClientPacketDistributor.sendToServer(new CompanionCommandPayload(
                target != null ? target.getId() : CompanionCommandPayload.ALL, command));
        onClose();
    }

    /** The key let go: a pointed order is given; a quick tap leaves the wheel open for a click instead. */
    private void release() {
        if (clickMode) return;
        if (hovered >= 0 && unavailable(WEDGES[hovered]) == null) choose(hovered);
        else if (System.nanoTime() - openedAt < TAP_NANOS) clickMode = true;
        else onClose();
    }

    // ------------------------------------------------------------------------------------------- input

    @Override public boolean keyReleased(KeyEvent event) {
        if (CommandWheelClient.OPEN_WHEEL.matches(event)) { release(); return true; }
        return super.keyReleased(event);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (CommandWheelClient.OPEN_WHEEL.matchesMouse(event)) { release(); return true; }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (CommandWheelClient.OPEN_WHEEL.matchesMouse(event)) return true;
        if (event.button() == 1) { onClose(); return true; }
        if (event.button() == 0) {
            hover(event.x(), event.y());
            if (hovered >= 0) choose(hovered);
            else onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (CommandWheelClient.OPEN_WHEEL.matches(event)) {
            if (clickMode) onClose();
            return true;
        }
        int digit = event.key() - InputConstants.KEY_1;
        if (digit >= 0 && digit < WEDGES.length) { choose(digit); return true; }
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------------------------------------ drawing

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // A light dusk over the world, so the tames stay in view while the wheel is open.
        g.fillGradient(0, 0, width, height, 0x30000000, 0x68000000);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hover(mouseX, mouseY);
        int cx = centerX(), cy = centerY(), half = WHEEL / 2;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        wheel(g, "wheel", -half);
        for (int i = 0; i < WEDGES.length; i++) {
            if (unavailable(WEDGES[i]) != null) wheel(g, "off_" + WEDGES[i].key(), -half);
            else if (i == hovered) wheel(g, "hot_" + WEDGES[i].key(), -half);
        }
        CompanionCommand current = current() == null ? null : CompanionCommand.of(current());
        if (current != null) {
            double a = angle(current.ordinal());
            int gx = (int) Math.round(Math.cos(a) * (OUTER - 1.75)), gy = (int) Math.round(Math.sin(a) * (OUTER - 1.75));
            g.blit(RenderPipelines.GUI_TEXTURED, texture("gem"), gx - 3, gy - 3, 0f, 0f, 6, 6, 12, 12, 12, 12);
        }
        g.pose().popMatrix();
        medallion(g, cx, cy, mouseX, mouseY);
        labels(g, cx, cy);
        header(g, cx, cy - Math.round((half + 6) * scale));
        footer(g, cx, cy + Math.round((half + 4) * scale));
    }

    private static void wheel(GuiGraphicsExtractor g, String name, int at) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture(name), at, at, 0f, 0f, WHEEL, WHEEL, WHEEL * 2, WHEEL * 2, WHEEL * 2, WHEEL * 2);
    }

    private void medallion(GuiGraphicsExtractor g, int cx, int cy, int mouseX, int mouseY) {
        if (target != null) {
            int r = Math.round(20 * scale);
            int size = Math.clamp(Math.round(30f * scale / Math.max(target.getBbWidth(), target.getBbHeight())), 3, 28);
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, cx - r, cy - r, cx + r, cy + r, size, 0.25f,
                    mouseX, mouseY, target);
            return;
        }
        String count = String.valueOf(group.size());
        g.pose().pushMatrix();
        g.pose().translate(cx, cy - 7);
        g.pose().scale(2f, 2f);
        g.text(font, count, -font.width(count) / 2, -4, group.isEmpty() ? OFF : TITLE, false);
        g.pose().popMatrix();
        g.centeredText(font, Component.translatable("wheel.arksurvivalreturns.tames"), cx, cy + 6, MUTED);
    }

    private void labels(GuiGraphicsExtractor g, int cx, int cy) {
        for (int i = 0; i < WEDGES.length; i++) {
            var command = WEDGES[i];
            Component label = Component.translatable("wheel.arksurvivalreturns.order." + command.key());
            double a = angle(i), r = (OUTER + 9) * scale;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r) - 4;
            int w = font.width(label);
            if (Math.abs(Math.cos(a)) < 0.3) x -= w / 2;
            else if (Math.cos(a) < 0) x -= w;
            int colour = unavailable(command) != null ? OFF : i == hovered ? TITLE : MUTED;
            g.text(font, label, x, y, colour, true);
        }
    }

    private void header(GuiGraphicsExtractor g, int cx, int bottom) {
        Component name, status;
        if (target != null) {
            name = target.getDisplayName();
            String state = TorporService.restricted(target) ? "unconscious"
                    : CompanionService.of(target).order().name().toLowerCase(java.util.Locale.ROOT);
            status = Component.translatable("wheel.arksurvivalreturns.status", target.creatureLevel(),
                    Component.translatable("wheel.arksurvivalreturns.state." + state));
        } else {
            name = Component.translatable("wheel.arksurvivalreturns.group");
            status = Component.translatable("wheel.arksurvivalreturns.group.count", group.size(), (int) CompanionWhistle.range());
        }
        g.centeredText(font, name, cx, bottom - 30, TITLE);
        g.centeredText(font, status, cx, bottom - 18, MUTED);
    }

    private void footer(GuiGraphicsExtractor g, int cx, int top) {
        if (hovered >= 0) {
            var command = WEDGES[hovered];
            Component why = unavailable(command);
            Component line = why != null ? why : Component.translatable("wheel.arksurvivalreturns.hint." + command.key());
            g.centeredText(font, line, cx, top + 10, why != null ? REASON : HINT);
        }
        Component help = clickMode ? Component.translatable("wheel.arksurvivalreturns.help.click")
                : Component.translatable("wheel.arksurvivalreturns.help.hold", CommandWheelClient.OPEN_WHEEL.getTranslatedKeyMessage());
        g.centeredText(font, help, cx, top + 23, FAINT);
    }
}
