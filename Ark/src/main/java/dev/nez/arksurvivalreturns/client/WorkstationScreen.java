package dev.nez.arksurvivalreturns.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.station.*;
import dev.nez.arksurvivalreturns.feature.station.WorkstationGraph.Node;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Drawing and input port of workstation_ui.js, in its original 320 x 200 design units. */
public final class WorkstationScreen extends AbstractContainerScreen<WorkstationMenu> {
    private record Frame(int margin, int top, int bottom, int width, int height) {}
    private record TagDisplay(String name, String item) {}
    private record Box(int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private record Widget(String id, Box box, boolean enabled) {}
    private record Tip(String text, int color) {}
    private record Pixel(int x, int y, int w, int color) {}
    private static final class Fly {
        final String item; final double x, y; double t;
        Fly(String item, double x, double y) { this.item = item; this.x = x; this.y = y; }
    }
    private WorkstationStyle style;
    private Frame frame;
    private Map<String, TagDisplay> tags = Map.of();
    private WorkstationGraph graph;
    private Identifier frameTexture;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<Fly> flies = new ArrayList<>();
    private String note;
    private int noteTicks;
    private List<Pixel> wellPattern = List.of();
    private Node hovered, pressed, dragging;
    private Widget hot, pressedWidget;
    private int amount = 1;
    private double pressX, pressY;
    private boolean pressedWell;
    private long lastTime;
    private double accumulator, scale, panelX, panelY;

    public WorkstationScreen(WorkstationMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Override protected void init() {
        super.init();
        try {
            Gson gson = new Gson();
            try (var reader = minecraft.getResourceManager().getResourceOrThrow(ArkSurvivalReturns.id("workstation/graph_style.json")).openAsReader()) {
                style = gson.fromJson(reader, WorkstationStyle.class);
            }
            try (var reader = minecraft.getResourceManager().getResourceOrThrow(ArkSurvivalReturns.id("workstation/frame.json")).openAsReader()) {
                frame = gson.fromJson(reader, Frame.class);
            }
            try (var reader = minecraft.getResourceManager().getResourceOrThrow(ArkSurvivalReturns.id("workstation/tags.json")).openAsReader()) {
                tags = gson.fromJson(reader, new com.google.gson.reflect.TypeToken<Map<String, TagDisplay>>() {}.getType());
            }
            frameTexture = ArkSurvivalReturns.id("textures/gui/workstation/" + Identifier.parse(menu.station).getPath() + ".png");
            refresh();
        } catch (java.io.IOException e) { throw new IllegalStateException("Missing workstation design assets", e); }
    }
    public void refresh() {
        if (style == null) return;
        var station = WorkstationClient.get(menu.station);
        graph = station == null ? null : new WorkstationGraph(station, style);
        wellPattern = station == null ? List.of() : pattern(station.well());
        pressed = dragging = hovered = null; pressedWidget = hot = null; amount = 1;
        lastTime = 0; accumulator = 0;
        flies.clear(); note = null;
    }
    public void crafted(WorkstationPayload.Result packet) {
        if (graph == null || packet.containerId() != menu.containerId) return;
        Node sel = graph.selected;
        if (sel != null && sel.item.item().equals(packet.item())) {
            for (Node n : graph.nodes) if (n.need > 0) n.pulse = style.motion().pulse();
            sel.pulse = style.motion().pulse();
            if (!packet.apply()) flies.add(new Fly(packet.item(), sel.x, sel.y));
        }
        String verb = graph.station.verb();
        String past = Map.of("Cut", "Cut", "Grind", "Ground", "Saw", "Sawn").getOrDefault(verb, verb + (verb.endsWith("e") ? "d" : "ed"));
        note = packet.apply() ? "Trim applied" : past + " " + (packet.count() > 1 ? packet.count() + " " : "") + name(packet.item());
        noteTicks = 90;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(graphics);
    }
    private void layout() {
        var p = style.panel(); double tall = p.height() + frame.top + frame.bottom;
        scale = Math.min(width * p.widthFraction(), height * p.maxHeightFraction() * p.width() / tall) / p.width();
        double uw = Math.ceil(width / scale), uh = Math.ceil(height / scale);
        panelX = Math.floor((uw - p.width()) / 2); panelY = Math.floor((uh - tall) / 2) + frame.top;
    }
    private double localX(double x) { return x / scale - panelX; }
    private double localY(double y) { return y / scale - panelY; }
    private boolean inWell(double x, double y) {
        var a = graph.area; return x >= a.x() && x < a.x() + a.w() && y >= a.y() && y < a.y() + a.h();
    }
    private Widget widgetAt(double x, double y) { return widgets.stream().filter(w -> w.box.contains(x, y)).findFirst().orElse(null); }
    private int color(String key) { return hex(style.palette().get(key)); }
    private static int hex(String value) { return (int) (Long.parseLong(value.substring(1), 16) | (value.length() == 7 ? 0xff000000L : 0)); }
    private static int opacity(int color, double alpha) { return (color & 0xffffff) | ((int) Math.round((color >>> 24) * alpha) << 24); }
    private static int mix(int a, int b, double t) {
        int out = 0xff000000;
        for (int shift : new int[]{0, 8, 16}) out |= (int) Math.round(((a >> shift) & 255) + (((b >> shift) & 255) - ((a >> shift) & 255)) * t) << shift;
        return out;
    }
    private static double ease(double t) { t = Math.clamp(t, 0, 1); return 1 - (1 - t) * (1 - t) * (1 - t); }
    private void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, String key) { g.fill(x0, y0, x1, y1, color(key)); }
    private void text(GuiGraphicsExtractor g, String value, int x, int y, int color, boolean shadow) { g.text(font, value, x, y, color, shadow); }
    private String fit(String value, int width) {
        if (font.width(value) <= width) return value;
        while (value.length() > 1 && font.width(value + "...") > width) value = value.substring(0, value.length() - 1);
        return value.stripTrailing() + "...";
    }
    private ItemStack icon(String selector) {
        if (selector == null) return ItemStack.EMPTY;
        if (selector.equals("minecraft:water_bottle")) return PotionContents.createItemStack(Items.POTION, Potions.WATER);
        if (selector.startsWith("#")) {
            var display = tags.get(selector);
            if (display != null && display.item != null) return icon(display.item);
            var tag = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, Identifier.parse(selector.substring(1))));
            return tag.isPresent() && tag.get().size() > 0 ? new ItemStack(tag.get().get(0).value()) : ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(selector);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.getValue(id)) : ItemStack.EMPTY;
    }
    private String name(String selector) {
        if (selector.startsWith("#")) {
            var display = tags.get(selector);
            if (display != null) return display.name;
            String base = selector.substring(selector.indexOf(':') + 1); base = base.substring(base.lastIndexOf('/') + 1).replace('_', ' ');
            if (base.endsWith("s")) base = base.substring(0, base.length() - 1);
            return "Any " + Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }
        if (selector.endsWith("_armor_trim_smithing_template")) {
            Identifier id = Identifier.parse(selector);
            return Component.translatable("trim_pattern." + id.getNamespace() + "." + id.getPath().replace("_armor_trim_smithing_template", "")).getString() + " Template";
        }
        ItemStack item = icon(selector); return item.isEmpty() ? selector : item.getHoverName().getString();
    }
    private String nodeId(Node n) { return switch (n.kind) { case "category" -> n.cat.icon(); case "group" -> n.entry.icon(); case "item" -> n.item.item(); default -> n.ingredient; }; }
    private String title(Node n) { return n.kind.equals("category") ? n.cat.title() : n.kind.equals("group") ? n.entry.title() : name(nodeId(n)); }
    private String variantName(Node n) { return n.item.name() == null ? name(n.item.item()) : n.item.name(); }
    private int have(String selector) {
        int count = 0;
        for (ItemStack stack : minecraft.player.getInventory().getNonEquipmentItems()) if (WorkstationInventory.matches(stack, selector)) count += stack.getCount();
        return count;
    }
    private int max(Node n) { return WorkstationInventory.read(minecraft.player.getInventory(), n.item.cost()).maxCrafts(); }
    private boolean enough(Node n) { return have(nodeId(n)) >= n.need * amount; }
    private void shape(GuiGraphicsExtractor g, int radius, int thickness, int color) {
        var texture = ArkSurvivalReturns.id("textures/gui/workstation/shapes/" + radius + "_" + thickness + ".png");
        g.blit(RenderPipelines.GUI_TEXTURED, texture, -radius, -radius, 0f, 0f, radius * 2, radius * 2, radius * 2, radius * 2, color);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (style == null) return;
        layout();
        g.fill(0, 0, width, height, color("backdrop"));
        if (graph == null) { g.text(font, "Loading workstation recipes...", width / 2 - 80, height / 2, color("title"), false); return; }
        graph.level = menu.craftingLevel();
        long now = System.nanoTime(); accumulator += Math.min(250, lastTime == 0 ? 0 : (now - lastTime) / 1000000.0); lastTime = now;
        for (int steps = 0; accumulator >= 1000.0 / 60; steps++) {
            accumulator -= 1000.0 / 60;
            if (steps > 8) { accumulator = 0; break; }
            if (graph.moving()) graph.tick();
            for (Fly fly : flies) fly.t += 1.0 / style.motion().fly();
            flies.removeIf(fly -> fly.t >= 1);
            if (noteTicks > 0 && --noteTicks == 0) note = null;
        }
        double mx = localX(mouseX), my = localY(mouseY);
        hovered = inWell(mx, my) ? graph.nodeAt(mx, my) : null;
        hot = widgetAt(mx, my);
        g.pose().pushMatrix(); g.pose().scale((float) scale, (float) scale); g.pose().translate((float) panelX, (float) panelY);
        drawPanel(g); drawGraph(g); drawBar(g);
        for (Fly fly : flies) {
            double t = ease(fly.t), x = fly.x + (style.panel().width() - 12 - fly.x) * t;
            double y = fly.y + (style.panel().height() - 10 - fly.y) * t - 14 * Math.sin(Math.PI * fly.t);
            g.pose().pushMatrix(); g.pose().translate((float) x - 8, (float) y - 8);
            g.item(icon(fly.item), 0, 0); g.pose().popMatrix();
        }
        g.pose().popMatrix();
        List<Tip> tips = tips();
        if (!tips.isEmpty() && dragging == null) {
            g.pose().pushMatrix(); g.pose().scale((float) scale, (float) scale);
            tooltip(g, tips, (int) (mouseX / scale), (int) (mouseY / scale)); g.pose().popMatrix();
        }
    }
    private void drawPanel(GuiGraphicsExtractor g) {
        int w = (int) style.panel().width(), h = (int) style.panel().height(); var a = graph.area;
        fill(g, 1, 0, w - 1, 1, "outline"); fill(g, 1, h - 1, w - 1, h, "outline");
        fill(g, 0, 1, 1, h - 1, "outline"); fill(g, w - 1, 1, w, h - 1, "outline");
        fill(g, 1, 1, w - 1, h - 1, "bevelLight"); fill(g, 2, 2, w - 1, h - 1, "bevelDark"); fill(g, 2, 2, w - 2, h - 2, "face");
        text(g, graph.station.title(), 8, 4, hex(graph.station.accent()), false);
        String level = menu.levelGate() ? "Level " + menu.displayedLevel() : "Level gates off";
        text(g, level, w - 8 - font.width(level) + 1, 4, color("level"), true);
        fill(g, (int) a.x() - 1, (int) a.y() - 1, (int) (a.x() + a.w()) + 1, (int) (a.y() + a.h()) + 1, "bevelLight");
        fill(g, (int) a.x() - 1, (int) a.y() - 1, (int) (a.x() + a.w()), (int) (a.y() + a.h()), "bevelDark");
        fill(g, (int) a.x(), (int) a.y(), (int) (a.x() + a.w()), (int) (a.y() + a.h()), "well");
        for (Pixel pixel : wellPattern) g.fill((int) a.x() + pixel.x, (int) a.y() + pixel.y, (int) a.x() + pixel.x + pixel.w, (int) a.y() + pixel.y + 1, pixel.color);
        g.blit(RenderPipelines.GUI_TEXTURED, frameTexture, -frame.margin, -frame.top, 0f, 0f, frame.width, frame.height, frame.width, frame.height);
    }
    private void drawGraph(GuiGraphicsExtractor g) {
        var area = graph.area;
        g.enableScissor((int) area.x(), (int) area.y(), (int) (area.x() + area.w()), (int) (area.y() + area.h()));
        for (var link : graph.links) {
            var a = link.a(); var b = link.b(); double t = Math.min(ease(a.grow), ease(b.grow)), alpha;
            int color;
            if (link.kind().equals("chain")) { color = color("link"); alpha = 1; }
            else if (link.kind().equals("child")) { color = hex(a.cat.color()); alpha = .4; }
            else { color = color(enough(b) ? "craftable" : "missing"); alpha = .75; }
            if (hovered != null && (a == hovered || b == hovered)) { if (link.kind().equals("chain")) color = color("linkHot"); alpha = 1; }
            if (!graph.unlocked(a) || !graph.unlocked(b)) alpha *= .5;
            line(g, a.x, a.y, b.x, b.y, opacity(color, alpha * t));
        }
        for (Node node : graph.nodes) drawNode(g, node, node == hovered);
        g.disableScissor();
    }
    private void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, int color) {
        // Pixel stroke, one design unit wide. The graph itself uses no trigonometry.
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        for (int i = 0; i <= steps; i++) { int x = (int) Math.round(x0 + (x1 - x0) * i / steps), y = (int) Math.round(y0 + (y1 - y0) * i / steps); g.fill(x, y, x + 1, y + 1, color); }
    }
    private void drawNode(GuiGraphicsExtractor g, Node n, boolean hot) {
        double s = ease(n.grow); if (s < .03) return;
        int r = (int) n.r; boolean locked = !graph.unlocked(n);
        int ring = locked ? color("faint") : n == graph.selected ? color("select")
                : n.kind.equals("category") || n.kind.equals("group") ? hex(n.cat.color())
                : n.kind.equals("ingredient") ? color(enough(n) ? "craftable" : "missing")
                : max(n) > 0 ? color("craftable") : mix(hex(n.cat.color()), color("nodeFill"), .4);
        Integer emphasis = n == graph.selected ? color("select") : n == graph.open || n == graph.openGroup ? hex(n.cat.color()) : null;
        if (hot && !locked) ring = mix(ring, 0xffffffff, .4);
        if (n.pulse > 0) s *= 1 - .14 * Math.sin(Math.PI * n.pulse / style.motion().pulse());
        g.pose().pushMatrix(); g.pose().translate((float) (n.x + (n.shake > 0 ? Math.sin(n.shake * 1.7) * 1.6 : 0)), (float) n.y);
        g.pose().scale((float) s, (float) s);
        double alpha = Math.min(1, s * 1.5);
        if (emphasis != null) shape(g, r + 3, 1, opacity(emphasis, alpha * .4));
        shape(g, r, 0, opacity(color("nodeFill"), alpha)); shape(g, r, emphasis != null ? 2 : 1, opacity(ring, alpha));
        if (n.kind.equals("group")) shape(g, r - 2, 1, opacity(mix(ring, color("nodeFill"), .45), alpha));
        g.item(icon(nodeId(n)), -8, -8);
        if (n.kind.equals("item") && n.entry.variants().size() > 1) for (int i = 0; i < Math.min(3, n.entry.variants().size()); i++) g.fill(-3 + i * 2, r - 2, -2 + i * 2, r - 1, i == 0 ? ring : color("faint"));
        if (locked) shape(g, r - 1, 0, opacity(color("dim"), alpha));
        if (n.need > 0) { String count = Integer.toString(n.need * amount); text(g, count, 9 - font.width(count), 1, enough(n) ? 0xffffffff : color("missing"), true); }
        g.pose().popMatrix();
    }
    private void button(GuiGraphicsExtractor g, String id, Box b, String label, boolean enabled) {
        boolean hover = enabled && hot != null && hot.id.equals(id), down = hover && pressedWidget != null && pressedWidget.id.equals(id);
        fill(g, b.x, b.y, b.x + b.w, b.y + b.h, "outline");
        fill(g, b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, !enabled ? "buttonOff" : hover ? "buttonHot" : "button");
        if (enabled) {
            fill(g, b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 2, hover ? "buttonHotLight" : "buttonLight");
            fill(g, b.x + 1, b.y + b.h - 2, b.x + b.w - 1, b.y + b.h - 1, "bevelDark");
        }
        text(g, label, b.x + (b.w - font.width(label) + 1) / 2, b.y + (b.h - 8) / 2 + 1 + (down ? 1 : 0), color(enabled ? "buttonText" : "buttonTextOff"), enabled);
        widgets.add(new Widget(id, b, enabled));
    }
    private String blocker(int times) {
        Node sel = graph.selected;
        if (!graph.unlocked(sel)) return "Needs level " + WorkstationDefinition.levelOf(sel.cat, sel.entry, sel.group);
        for (var price : sel.item.cost().entrySet()) {
            int missing = price.getValue() * times - have(price.getKey()); if (missing > 0) return "Missing " + missing + " " + name(price.getKey());
        }
        return WorkstationInventory.read(minecraft.player.getInventory(), sel.item.cost()).plan(times) == null ? "Missing ingredients" : "";
    }
    private void drawBar(GuiGraphicsExtractor g) {
        widgets.clear(); int y = (int) (graph.area.y() + graph.area.h()), right = (int) style.panel().width() - 8;
        fill(g, 8, y + 6, 26, y + 24, "bevelDark"); fill(g, 9, y + 7, 26, y + 24, "bevelLight"); fill(g, 9, y + 7, 25, y + 23, "slotFace");
        Node sel = graph.selected;
        if (sel == null) {
            String hint = graph.openGroup != null ? "Pick a recipe to see its cost" : graph.open != null ? "Pick a group or a recipe" : "Pick a material to see what it makes";
            text(g, fit(hint, (int) style.panel().width() - 40), 32, y + 8, color("muted"), false);
            text(g, fit("Names show on hover; dimmed needs a higher level", (int) style.panel().width() - 40), 32, y + 18, color("faint"), false); return;
        }
        Box craft = new Box(right - 34, y + 7, 34, 16); right -= 40;
        Box max = new Box(right - 20, y + 9, 20, 12); right -= 21;
        Box plus = new Box(right - 11, y + 9, 11, 12); right -= 12;
        Box count = new Box(right - 18, y + 9, 18, 12); right -= 19;
        Box minus = new Box(right - 11, y + 9, 11, 12); right -= 11;
        boolean many = sel.entry.variants().size() > 1, locked = !graph.unlocked(sel);
        g.item(icon(sel.item.item()), 9, y + 7);
        text(g, fit(variantName(sel), right - 6 - 32 - (many ? 30 : 0)), 32, y + 7, color(locked ? "muted" : "title"), false);
        if (many) { button(g, "prev", new Box(right - 27, y + 5, 10, 10), "<", true); button(g, "next", new Box(right - 16, y + 5, 10, 10), ">", true); }
        String sub = locked ? "Needs level " + WorkstationDefinition.levelOf(sel.cat, sel.entry, sel.group) : sel.item.apply() ? "Trims an armour piece"
                : (sel.item.count() > 1 ? "Makes " + sel.item.count() + " · have " : "Have ") + have(sel.item.item()) + (many ? " · " + (sel.variant + 1) + "/" + sel.entry.variants().size() : "");
        text(g, fit(note == null ? sub : note, right - 6 - 32), 32, y + 17, color(note != null ? "craftable" : locked ? "missing" : "muted"), false);
        button(g, "minus", minus, "-", !locked && amount > 1);
        fill(g, count.x, count.y, count.x + count.w, count.y + count.h, "bevelDark");
        fill(g, count.x + 1, count.y + 1, count.x + count.w, count.y + count.h, "bevelLight");
        fill(g, count.x + 1, count.y + 1, count.x + count.w - 1, count.y + count.h - 1, "slotFace");
        text(g, "" + amount, count.x + (count.w - font.width("" + amount) + 1) / 2, count.y + 3, locked ? color("faint") : 0xffffffff, false);
        widgets.add(new Widget("count", count, !locked));
        button(g, "plus", plus, "+", !locked && amount < 99); button(g, "max", max, "Max", !locked && max(sel) > 0);
        button(g, "craft", craft, sel.item.apply() ? "Apply" : graph.station.verb(), blocker(amount).isEmpty());
    }
    private List<Tip> tips() {
        List<Tip> out = new ArrayList<>();
        if (hovered != null) {
            Node n = hovered; boolean locked = !graph.unlocked(n);
            String name = n.kind.equals("item") && n.entry.variants().size() > 1 && n.entry.title() != null ? n.entry.title() : title(n);
            out.add(new Tip(name, 0xffffffff));
            if (n.kind.equals("category") || n.kind.equals("group")) {
                var entries = n.kind.equals("category") ? n.cat.items() : n.entry.items();
                int recipes = entries.stream().mapToInt(e -> e.group() != null ? e.items().size() : 1).sum();
                out.add(new Tip(locked ? "Requires level " + (n.kind.equals("category") ? n.cat.level() : WorkstationDefinition.levelOf(n.cat, null, n.group))
                        : recipes + (recipes == 1 ? " recipe" : " recipes"), locked ? color("missing") : 0xffaaaaaa));
            } else if (n.kind.equals("item")) {
                if (n.entry.variants().size() > 1) out.add(new Tip(variantName(n) + "  " + (n.variant + 1) + "/" + n.entry.variants().size(), 0xffaaaaaa));
                if (locked) out.add(new Tip("Requires level " + WorkstationDefinition.levelOf(n.cat, n.entry, n.group), color("missing")));
                else if (n == graph.selected) out.add(new Tip(n.item.apply() ? "Click again to apply" : "Click again to " + graph.station.verb().toLowerCase(java.util.Locale.ROOT), 0xffaaaaaa));
                if (n.item.count() > 1) out.add(new Tip("Makes " + n.item.count(), 0xffaaaaaa));
                if (n.entry.variants().size() > 1) out.add(new Tip("Scroll for the other variants", 0xffaaaaaa));
            }
            if (n.need > 0) out.add(new Tip("Have " + have(nodeId(n)) + " / " + n.need * amount, enough(n) ? 0xffaaaaaa : color("missing")));
        } else if (hot != null && graph.selected != null) {
            Node sel = graph.selected;
            switch (hot.id) {
                case "craft" -> { out.add(new Tip((sel.item.apply() ? "Apply " : graph.station.verb() + " ") + variantName(sel), 0xffffffff)); String why = blocker(amount); out.add(new Tip(why.isEmpty() ? "Shift: all you can" : why, why.isEmpty() ? 0xffaaaaaa : color("missing"))); }
                case "max" -> { out.add(new Tip("As many as you can make", 0xffffffff)); out.add(new Tip("" + max(sel), 0xffaaaaaa)); }
                case "count" -> { out.add(new Tip("Amount", 0xffffffff)); out.add(new Tip("Scroll or use - and +", 0xffaaaaaa)); }
                case "prev", "next" -> { out.add(new Tip("Other variant", 0xffffffff)); out.add(new Tip((sel.variant + 1) + " of " + sel.entry.variants().size(), 0xffaaaaaa)); }
                default -> {}
            }
        }
        return out;
    }
    private void tooltip(GuiGraphicsExtractor g, List<Tip> lines, int mx, int my) {
        int w = lines.stream().mapToInt(t -> font.width(t.text) - 1).max().orElse(0), h = lines.size() * 10 - 2 + (lines.size() > 1 ? 2 : 0);
        int x = mx + 12, y = my - 12;
        if (x + w + 4 > width / scale) x = Math.max(4, mx - 16 - w);
        y = Math.max(4, Math.min(y, (int) (height / scale) - h - 4));
        fill(g, x - 3, y - 4, x + w + 3, y - 3, "tooltip"); fill(g, x - 3, y + h + 3, x + w + 3, y + h + 4, "tooltip");
        fill(g, x - 3, y - 3, x + w + 3, y + h + 3, "tooltip"); fill(g, x - 4, y - 3, x - 3, y + h + 3, "tooltip"); fill(g, x + w + 3, y - 3, x + w + 4, y + h + 3, "tooltip");
        for (int i = 0; i < h + 4; i++) {
            int c = mix(color("tooltipTop"), color("tooltipBottom"), (i + 1.0) / (h + 5));
            g.fill(x - 3, y - 2 + i, x - 2, y - 1 + i, c); g.fill(x + w + 2, y - 2 + i, x + w + 3, y - 1 + i, c);
        }
        fill(g, x - 3, y - 3, x + w + 3, y - 2, "tooltipTop"); fill(g, x - 3, y + h + 2, x + w + 3, y + h + 3, "tooltipBottom");
        for (int i = 0; i < lines.size(); i++) text(g, lines.get(i).text, x, y + i * 10 + (i != 0 ? 2 : 0), lines.get(i).color, true);
    }
    private List<Pixel> pattern(String kind) {
        List<Pixel> out = new ArrayList<>(); int w = (int) graph.area.w(), h = (int) graph.area.h(), dot = color("wellDot");
        class Random { long seed = 7; double next() { seed = seed * 16807 % 2147483647; return seed / 2147483647.0; } }
        Random random = new Random();
        if (kind.equals("grain")) {
            for (int py = 3; py < h; py += 5) for (int px = 0; px < w; px++) if (random.next() < .55) out.add(new Pixel(px, py + (random.next() < .1 ? 1 : 0), 1, dot));
        } else if (kind.equals("speckle")) {
            for (int i = 0; i < w * h / 26.0; i++) out.add(new Pixel((int) (random.next() * w), (int) (random.next() * h), random.next() < .2 ? 2 : 1, dot));
        } else if (kind.equals("leaves")) {
            for (int i = 0; i < w * h / 180.0; i++) { int x = (int) (random.next() * w), y = (int) (random.next() * h); out.add(new Pixel(x, y, 2, dot)); out.add(new Pixel(x + 1, y + 1, 2, dot)); }
        } else if (kind.equals("sparks")) {
            for (int i = 0; i < w * h / 150.0; i++) { int c = random.next() < .3 ? hex("#3a2410") : dot; out.add(new Pixel((int) (random.next() * w), (int) (random.next() * h), 1, c)); }
        } else for (int x = 6; x < w - 2; x += 12) for (int y = 6; y < h - 2; y += 12) out.add(new Pixel(x, y, 1, dot));
        return out;
    }
    private void craft(boolean shift) {
        Node sel = graph.selected; if (sel == null) return;
        if (!graph.unlocked(sel) || !shift && !blocker(amount).isEmpty()) { sel.shake = style.motion().shake(); return; }
        ClientPacketDistributor.sendToServer(new WorkstationPayload.Craft(menu.station, sel.item.item(), shift ? -1 : amount, sel.key, sel.variant, menu.containerId));
    }
    private void clickWidget(String id, boolean shift) {
        Node sel = graph.selected; if (sel == null) return;
        switch (id) {
            case "minus" -> amount = Math.max(1, amount - (shift ? 10 : 1));
            case "plus" -> amount = Math.min(99, amount + (shift ? 10 : 1));
            case "max" -> amount = Math.max(1, Math.min(99, max(sel)));
            case "craft" -> craft(shift);
            case "prev" -> graph.setVariant(sel, sel.variant - 1);
            case "next" -> graph.setVariant(sel, sel.variant + 1);
            default -> {}
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (graph == null || event.button() != 0) return false;
        double x = localX(event.x()), y = localY(event.y());
        pressX = event.x(); pressY = event.y(); pressedWell = inWell(x, y);
        pressed = pressedWell ? graph.nodeAt(x, y) : null; pressedWidget = widgetAt(x, y);
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (graph == null) return false;
        if (pressed != null && dragging == null && Math.hypot(event.x() - pressX, event.y() - pressY) > 3) { dragging = pressed; graph.grab(dragging); }
        if (dragging != null) graph.moveTo(dragging, localX(event.x()), localY(event.y()));
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (graph == null || event.button() != 0) return false;
        if (dragging != null) { graph.drop(dragging); dragging = null; }
        else {
            double x = localX(event.x()), y = localY(event.y()); boolean shift = (event.modifiers() & 1) != 0;
            if (pressed != null && graph.nodeAt(x, y) == pressed) {
                String result = graph.click(pressed);
                if (result.equals("select")) { amount = 1; note = null; }
                if (result.equals("craft")) craft(shift);
            } else if (pressedWidget != null) {
                Widget next = widgetAt(x, y);
                if (next != null && next.id.equals(pressedWidget.id) && (next.enabled || next.id.equals("craft") && shift && max(graph.selected) > 0)) clickWidget(next.id, shift);
            } else if (pressedWell && inWell(x, y) && graph.nodeAt(x, y) == null) graph.click(null);
        }
        pressed = null; pressedWidget = null; pressedWell = false; return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (graph == null || scrollY == 0) return false;
        Node node = graph.nodeAt(localX(x), localY(y)); int step = scrollY > 0 ? -1 : 1;
        if (node != null && node.kind.equals("item") && node.entry.variants().size() > 1) { graph.setVariant(node, node.variant + step); return true; }
        Widget widget = widgetAt(localX(x), localY(y));
        if (widget != null && graph.selected != null) {
            if (widget.id.equals("count")) { amount = Math.clamp(amount - step, 1, 99); return true; }
            if (widget.id.equals("prev") || widget.id.equals("next")) { graph.setVariant(graph.selected, graph.selected.variant + step); return true; }
        }
        return false;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && graph != null && (graph.selected != null || graph.openGroup != null || graph.open != null)) { graph.click(null); return true; }
        return super.keyPressed(event);
    }
}
