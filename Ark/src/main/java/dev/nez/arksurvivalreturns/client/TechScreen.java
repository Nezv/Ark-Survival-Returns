package dev.nez.arksurvivalreturns.client;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import dev.ftb.mods.ftbquests.client.FTBQuestsClient;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.tech.TechPayload;
import dev.nez.arksurvivalreturns.feature.tech.TechView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** An item-led parchment map. There is deliberately no client-side completion action. */
public final class TechScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final Identifier PAPER = texture("parchment");
    private static int nextRequest;
    private final int request = ++nextRequest;
    private TechView view = TechView.empty();
    private Map<String, TechView.Node> byId = Map.of();
    private boolean received, dragging, draggingBar;
    private int ticks;
    private double pan;

    public TechScreen() { super(Component.translatable("tech.gui.title")); }
    private static Identifier texture(String name) { return ArkSurvivalReturns.id("textures/gui/tech/" + name + ".png"); }
    private int top() { return 51; }
    private int bottom() { return height - 32; }
    private double scale() { return Math.min(1.25, Math.max(.25, (bottom() - top()) / 268.0)); }
    private double visibleWidth() { return Math.max(1, width - 32) / scale(); }
    private int worldWidth() { return view.ages().stream().mapToInt(TechView.Age::right).max().orElse(0); }
    private double maxPan() { return Math.max(0, worldWidth() - visibleWidth()); }
    private void pan(double value) { pan = Math.clamp(value, 0, maxPan()); }
    private double worldX(double screen) { return (screen - 16) / scale() + pan; }
    private double worldY(double screen) { return (screen - top()) / scale(); }
    private boolean inside(double x, double y) { return x >= 16 && x < width - 16 && y >= top() && y < bottom(); }

    @Override protected void init() { pan(pan); request(); }
    @Override public boolean isPauseScreen() { return false; }
    private void request() { ClientPacketDistributor.sendToServer(new TechPayload.Request(request)); }
    @Override public void tick() { if (++ticks % 20 == 0) request(); }

    public void accept(TechPayload packet) {
        if (packet.request() != request) return; // Ignore replies to a closed/replaced screen.
        TechView next = GSON.fromJson(packet.document(), TechView.class);
        if (next == null || next.nodes() == null || next.ages() == null || next.nodes().size() > 64) return;
        view = next;
        byId = view.nodes().stream().collect(Collectors.toUnmodifiableMap(TechView.Node::id, Function.identity()));
        received = true;
        pan(pan);
    }

    private TechView.Node hovered(double x, double y) {
        if (!inside(x, y)) return null;
        double wx = worldX(x), wy = worldY(y);
        return view.nodes().stream().filter(n -> Math.hypot(wx - n.x(), wy - n.y()) <= 25).findFirst().orElse(null);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xF027291F);
        g.text(font, title, 16, 12, 0xFFE0C58F, false);
        if (received) g.text(font, Component.translatable(view.ftbLinked() ? "tech.gui.shared" : "tech.gui.local"),
                width - 126, 12, 0xFFBAA985, false);
        for (int i = 0; i < view.ages().size(); i++) {
            var age = view.ages().get(i);
            int x = 16 + i * 92;
            g.text(font, age.title(), x, 33, 0xFFDACAAB, false);
            if (pan + visibleWidth() / 2 >= age.left() && pan + visibleWidth() / 2 <= age.right())
                g.horizontalLine(x, x + Math.min(83, font.width(age.title())), 45, 0xFFB99550);
        }
        String close = "\u00d7";
        g.text(font, close, width - 22, 12, 0xFFE0C58F, false);
        TechView.Node hover = hovered(mouseX, mouseY);
        g.enableScissor(16, top(), width - 16, bottom());
        g.pose().pushMatrix();
        g.pose().translate(16f, top());
        g.pose().scale((float) scale(), (float) scale());
        g.pose().translate((float) -pan, 0);
        for (var age : view.ages()) {
            g.fill(age.left(), 0, age.right(), 268, age.color());
            // Small repeated fiber tiles retain crisp details at any GUI scale.
            for (int x = age.left(); x < age.right(); x += 128)
                for (int y = 0; y < 268; y += 128)
                    g.blit(RenderPipelines.GUI_TEXTURED, PAPER, x, y, 0f, 0f,
                            Math.min(128, age.right() - x), Math.min(128, 268 - y), 128, 128, 0x45FFFFFF);
            g.text(font, age.title().toUpperCase(java.util.Locale.ROOT), age.left() + 24, 17, 0xFF4E5039, false);
            g.verticalLine(age.left(), 6, 261, 0x557F7151);
        }
        for (var n : view.nodes()) {
            if (n.state() == TechView.State.HIDDEN) continue;
            for (String dependency : n.requires()) {
                var a = byId.get(dependency);
                if (a == null || a.state() == TechView.State.HIDDEN) continue;
                if (n.kind().equals("side")) continue; // Extras never become a fourth progression lane.
                int color = n.state() == TechView.State.COMPLETE ? 0xFF8C823E : 0x997C7658;
                int mid = n.requires().size() >= 3 ? n.x() - 44 : a.x() + 55;
                g.horizontalLine(a.x() + 23, mid, a.y(), color);
                g.verticalLine(mid, Math.min(a.y(), n.y()), Math.max(a.y(), n.y()), color);
                g.horizontalLine(mid, n.x() - 23, n.y(), color);
            }
        }
        for (var n : view.nodes()) {
            if (n.x() + 30 < pan || n.x() - 30 > pan + visibleWidth()) continue;
            if (n.state() == TechView.State.HIDDEN) {
                g.text(font, "???", n.x() - font.width("???") / 2, n.y() - 4, 0xFF827956, false);
                continue;
            }
            String state = switch (n.state()) {
                case COMPLETE -> "complete";
                case AVAILABLE -> "ready";
                default -> "locked";
            };
            blit(g, texture("node_" + state), n.x() - 26, n.y() - 26, 52, -1);
            if (!n.icon().isEmpty()) {
                Identifier id = Identifier.tryParse(n.icon());
                if (id != null) blit(g, id, n.x() - 20, n.y() - 20, 40,
                        n.state() == TechView.State.LOCKED ? 0x88999988 : -1);
            }
            if (hover == n) blit(g, texture("node_hover"), n.x() - 28, n.y() - 28, 56, -1);
        }
        g.pose().popMatrix();
        g.disableScissor();
        if (view.nodes().isEmpty()) g.text(font, Component.translatable(received ? "tech.gui.unavailable" : "tech.gui.loading"),
                28, top() + 40, 0xFFE0C58F, false);
        int rail = Math.max(1, width - 48), thumb = (int) Math.max(24, rail * Math.min(1, visibleWidth() / Math.max(1, worldWidth())));
        int tx = 24 + (int) ((rail - thumb) * (maxPan() > 0 ? pan / maxPan() : 0));
        g.horizontalLine(24, width - 24, height - 22, 0xFF666347);
        g.fill(tx, height - 24, tx + thumb, height - 20, 0xFFC6A15E);
        g.text(font, Component.translatable("tech.gui.pan"), 16, height - 12, 0xFFBAA985, false);
        g.text(font, Component.translatable("tech.gui.quests"), width - 76, height - 12, 0xFFE0C58F, false);
        if (hover != null) tooltip(g, hover, mouseX, mouseY);
    }

    private static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, int size, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size, 64, 64, 64, 64, color);
    }

    private void tooltip(GuiGraphicsExtractor g, TechView.Node n, int mx, int my) {
        int w = Math.min(196, width - 24);
        var titleLines = font.split(Component.literal(n.title()), w - 18);
        var taskLines = font.split(Component.literal(n.task()), w - 18);
        boolean hidden = n.state() == TechView.State.HIDDEN;
        int h = 18 + (titleLines.size() + taskLines.size()) * 10 + (hidden ? 0 : 15);
        int x = Math.clamp(mx + 12, 8, Math.max(8, width - w - 8));
        int y = Math.clamp(my + 12, 8, Math.max(8, height - h - 8));
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x55413927);
        g.fill(x + 2, y, x + w - 2, y + h, 0xFFF0E2C5);
        g.fill(x, y + 2, x + w, y + h - 2, 0xFFF0E2C5);
        g.horizontalLine(x + 5, x + w - 5, y + 2, 0xFFAA8C54);
        int cursor = y + 9;
        for (var line : titleLines) { g.text(font, line, x + 9, cursor, 0xFF473C28, false); cursor += 10; }
        cursor += 3;
        for (var line : taskLines) { g.text(font, line, x + 9, cursor, 0xFF64553B, false); cursor += 10; }
        if (!hidden) g.text(font, Component.translatable("tech.gui." + n.state().name().toLowerCase(java.util.Locale.ROOT)),
                x + 9, cursor + 4, n.state() == TechView.State.COMPLETE ? 0xFF66733E : 0xFF8E6839, false);
    }

    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (!inside(x, y)) return false;
        pan(pan - (sx != 0 ? sx : sy) * 38 / scale());
        return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (event.x() >= width - 30 && event.y() < 28) { onClose(); return true; }
        if (event.y() >= 28 && event.y() < 48) {
            int tab = (int) ((event.x() - 16) / 92);
            if (event.x() >= 16 && tab >= 0 && tab < view.ages().size()) { pan(view.ages().get(tab).left()); return true; }
        }
        if (event.y() >= height - 16 && event.x() >= width - 80) { FTBQuestsClient.openGui(); return true; }
        if (event.y() >= height - 28 && event.y() < height - 16) { draggingBar = true; moveBar(event.x()); return true; }
        dragging = inside(event.x(), event.y());
        return dragging || super.mouseClicked(event, doubleClick);
    }
    private void moveBar(double x) { pan((x - 24) / Math.max(1, width - 48) * worldWidth() - visibleWidth() / 2); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingBar) { moveBar(event.x()); return true; }
        if (dragging) { pan(pan - dx / scale()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false; draggingBar = false;
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (TechClient.OPEN_TREE.matches(event)) { onClose(); return true; }
        if (event.key() == 262 || event.key() == 263) { pan(pan + (event.key() == 262 ? 70 : -70)); return true; }
        if (event.key() == 268) { pan(0); return true; }
        if (event.key() == 269) { pan(maxPan()); return true; }
        return super.keyPressed(event);
    }
}
