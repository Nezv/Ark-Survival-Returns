package dev.nez.arksurvivalreturns.feature.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.nez.arksurvivalreturns.feature.station.WorkstationDefinition.*;

/** One-to-one port of tools/workstation_graph.js. Preserve loop order and double arithmetic. */
public final class WorkstationGraph {
    public static final double[][] DIRS = {{1,0},{.92388,.38268},{.70711,.70711},{.38268,.92388},
            {0,1},{-.38268,.92388},{-.70711,.70711},{-.92388,.38268},
            {-1,0},{-.92388,-.38268},{-.70711,-.70711},{-.38268,-.92388},
            {0,-1},{.38268,-.92388},{.70711,-.70711},{.92388,-.38268}};
    public record Area(double x, double y, double w, double h) {}
    public static Area graphArea(WorkstationStyle style) {
        var p = style.panel();
        return new Area(p.inset(), p.title(), p.width() - 2 * p.inset(), p.height() - p.title() - p.bar() - p.inset());
    }
    public static int maxCrafts(Variant variant, Map<String, Integer> have) {
        int best = -1;
        for (var cost : variant.cost().entrySet()) {
            int times = have.getOrDefault(cost.getKey(), 0) / cost.getValue();
            best = best < 0 ? times : Math.min(best, times);
        }
        return Math.max(0, best);
    }
    public static void pay(Variant variant, int times, Map<String, Integer> have) {
        for (var cost : variant.cost().entrySet()) have.put(cost.getKey(), have.getOrDefault(cost.getKey(), 0) - cost.getValue() * times);
    }
    public static final class Node {
        public final String kind, key;
        public final Category cat;
        public final Entry group, entry;
        public final Node parent;
        public int variant, need, shake, pulse;
        public Variant item;
        public String ingredient;
        public double r, x, y, vx, vy, grow;
        public boolean dying, fixed;
        Node(String kind, String key, Category cat, Entry group, Entry entry, Node parent, double r, double x, double y) {
            this.kind = kind; this.key = key; this.cat = cat; this.group = group; this.entry = entry; this.parent = parent;
            this.r = r; this.x = x; this.y = y;
            item = entry != null && !entry.variants().isEmpty() ? entry.variants().getFirst() : null;
        }
    }
    public record Link(Node a, Node b, String kind) {}
    public final WorkstationDefinition station;
    public final WorkstationStyle style;
    public final Area area;
    public List<Node> nodes = new ArrayList<>();
    public List<Link> links = new ArrayList<>();
    public int level, ticks;
    public Node open, openGroup, selected;
    public double alpha = 1, alphaTarget;

    public WorkstationGraph(WorkstationDefinition station, WorkstationStyle style) {
        this.station = station; this.style = style; area = graphArea(style);
        var cats = station.categories();
        Map<String, Node> byId = new LinkedHashMap<>();
        double cx = area.x + area.w / 2, cy = area.y + area.h / 2;
        for (int i = 0; i < cats.size(); i++) {
            var d = DIRS[(i * 7) % 16]; double reach = 10 + 9 * i;
            Node node = add("category", "c:" + cats.get(i).id(), cats.get(i), null, null, null,
                    cx + d[0] * reach * 1.8, cy + d[1] * reach * .8);
            node.grow = 1; byId.put(cats.get(i).id(), node);
        }
        for (Category cat : cats) if (cat.after() != null && byId.containsKey(cat.after())) link(byId.get(cat.after()), byId.get(cat.id()), "chain");
        settle(600);
    }
    public Node add(String kind, String key, Category cat, Entry group, Entry entry, Node parent, double x, double y) {
        Node node = new Node(kind, key, cat, group, entry, parent, style.node().get(kind), x, y);
        nodes.add(node); return node;
    }
    public void link(Node a, Node b, String kind) { links.add(new Link(a, b, kind)); }
    public Node find(String key) {
        for (Node node : nodes) if (node.key.equals(key) && !node.dying) return node;
        return null;
    }
    public boolean unlocked(Node node) {
        if (node.kind.equals("ingredient")) return true;
        if (level < node.cat.level()) return false;
        if (node.kind.equals("category")) return true;
        if (level < WorkstationDefinition.levelOf(node.cat, null, node.group)) return false;
        return node.kind.equals("group") || level >= WorkstationDefinition.levelOf(node.cat, node.entry, node.group);
    }
    public int outward(Node node, double fx, double fy) {
        double ox = node.x - fx, oy = node.y - fy, dot = Double.NEGATIVE_INFINITY;
        int best = 0;
        for (int i = 0; i < 16; i++) {
            double v = DIRS[i][0] * ox + DIRS[i][1] * oy;
            if (v > dot) { dot = v; best = i; }
        }
        return best;
    }
    public void reheat() { reheat(style.force().reheat()); }
    public void reheat(double value) { alpha = Math.max(alpha, value); }
    public void spawn(Node parent, List<Entry> entries, Entry group, double fx, double fy) {
        int n = entries.size(), base = outward(parent, fx, fy);
        for (int i = 0; i < n; i++) {
            var d = DIRS[(base + (int) Math.floor((i - (n - 1) / 2.0) * 14 / Math.max(n, 4) + .5) + 32) % 16];
            Entry entry = entries.get(i); Node child;
            if (entry.group() != null) child = add("group", "G:" + parent.cat.id() + "/" + entry.group(), parent.cat, entry, entry, parent,
                    parent.x + d[0] * 2, parent.y + d[1] * 2);
            else child = add("item", "i:" + parent.cat.id() + "/" + (group != null ? group.group() + "/" : "") + entry.family(),
                    parent.cat, group, entry, parent, parent.x + d[0] * 2, parent.y + d[1] * 2);
            child.vx = d[0] * 2; child.vy = d[1] * 2; link(parent, child, "child");
        }
    }
    public void openCategory(Node node) {
        if (open == node) return;
        closeCategory(); open = node;
        spawn(node, node.cat.items(), null, area.x + area.w / 2, area.y + area.h / 2); reheat();
    }
    public void closeCategory() {
        Node old = open; if (old == null) return;
        closeGroup(); clearSelection(); open = null;
        for (Node node : nodes) if (node.parent == old) node.dying = true;
        reheat();
    }
    public void openGroupNode(Node node) {
        if (openGroup == node) return;
        closeGroup(); openGroup = node;
        spawn(node, node.entry.items(), node.entry, node.parent.x, node.parent.y); reheat();
    }
    public void closeGroup() {
        Node old = openGroup; if (old == null) return;
        if (selected != null && selected.parent == old) clearSelection();
        openGroup = null;
        for (Node node : nodes) if (node.parent == old) node.dying = true;
        reheat(.6);
    }
    public void growIngredients(Node node) {
        var cost = node.item.cost(); var keys = new ArrayList<>(cost.keySet());
        int n = keys.size(), base = outward(node, node.parent.x, node.parent.y);
        for (int i = 0; i < n; i++) {
            Node sibling = null;
            for (Node s : nodes) if (s != node && s.kind.equals("item") && s.parent == node.parent && !s.dying && s.item != null && s.item.item().equals(keys.get(i))) sibling = s;
            if (sibling != null) { sibling.need = cost.get(keys.get(i)); link(node, sibling, "reuse"); continue; }
            var d = DIRS[Math.floorMod(base + (int) Math.floor((i - (n - 1) / 2.0) * 2.5 + .5) + 32, 16)];
            Node g = add("ingredient", "g:" + node.key + ">" + keys.get(i), node.cat, node.group, node.entry, node,
                    node.x + d[0] * 2, node.y + d[1] * 2);
            g.item = node.item; g.ingredient = keys.get(i); g.need = cost.get(keys.get(i));
            g.vx = d[0] * 2; g.vy = d[1] * 2; link(node, g, "ingredient");
        }
    }
    public void dropIngredients(Node node) {
        for (Node n : nodes) {
            if (n.kind.equals("ingredient") && n.parent == node) n.dying = true;
            if (n.kind.equals("item")) n.need = 0;
        }
        links = new ArrayList<>(links.stream().filter(l -> !l.kind.equals("reuse")).toList());
    }
    public void selectItem(Node node) {
        if (selected == node) return;
        clearSelection(); selected = node; growIngredients(node); reheat(.6);
    }
    public void clearSelection() {
        Node old = selected; if (old == null) return;
        selected = null; dropIngredients(old); reheat(.5);
    }
    public void setVariant(Node node, int index) {
        var variants = node.entry.variants(); node.variant = Math.floorMod(index, variants.size()); node.item = variants.get(node.variant);
        if (selected == node) { dropIngredients(node); growIngredients(node); reheat(.4); }
    }
    public String click(Node node) {
        if (node == null) {
            if (selected != null) { clearSelection(); return "back"; }
            if (openGroup != null) { closeGroup(); return "back"; }
            if (open != null) { closeCategory(); return "back"; }
            return "";
        }
        if (!unlocked(node)) { node.shake = style.motion().shake(); return "locked"; }
        if (node.kind.equals("category")) {
            if (open == node) { closeCategory(); return "close"; }
            openCategory(node); return "open";
        }
        if (node.kind.equals("group")) {
            if (openGroup == node) { closeGroup(); return "close"; }
            openGroupNode(node); return "open";
        }
        if (node.kind.equals("item")) {
            if (selected == node) return "craft";
            selectItem(node); return "select";
        }
        return "";
    }
    public double charge(Node node) {
        var f = style.force();
        double base = switch (node.kind) { case "category" -> f.chargeCategory(); case "group" -> f.chargeGroup(); case "item" -> f.chargeItem(); default -> f.chargeIngredient(); };
        return base * node.grow;
    }
    public double rest(Link link) {
        var l = style.link();
        if (link.kind.equals("chain")) return l.chain();
        if (link.kind.equals("ingredient")) return l.ingredient();
        if (link.kind.equals("child") && link.a.kind.equals("group")) return l.groupItem();
        return l.item();
    }
    public void tick() {
        var f = style.force(); int n = nodes.size();
        alpha += (alphaTarget - alpha) * f.alphaDecay();
        for (int i = 0; i < n; i++) {
            Node a = nodes.get(i);
            for (int j = i + 1; j < n; j++) {
                Node b = nodes.get(j); double dx = b.x - a.x, dy = b.y - a.y, l = dx * dx + dy * dy;
                if (l < 1e-6) { dx = i % 2 != 0 ? .3 : -.3; dy = j % 2 != 0 ? .3 : -.3; l = .18; }
                double qa = charge(a) * alpha / l, qb = charge(b) * alpha / l;
                a.vx += dx * qb; a.vy += dy * qb; b.vx -= dx * qa; b.vy -= dy * qa;
            }
        }
        for (Link link : links) {
            Node a = link.a, b = link.b;
            double dx = b.x + b.vx - a.x - a.vx, dy = b.y + b.vy - a.y - a.vy;
            double d = Math.sqrt(dx * dx + dy * dy); if (d == 0) d = 1e-6;
            double stiff = link.kind.equals("chain") ? .5 : link.kind.equals("reuse") ? .25 : 1;
            double k = (d - rest(link) * Math.min(a.grow, b.grow)) / d * alpha * stiff;
            double w = link.kind.equals("chain") || link.kind.equals("reuse") ? .5 : style.link().parentWeight();
            dx *= k; dy *= k;
            b.vx -= dx * (1 - w); b.vy -= dy * (1 - w); a.vx += dx * w; a.vy += dy * w;
        }
        double cx = area.x + area.w / 2, cy = area.y + area.h / 2, aspect = area.w / area.h;
        Node focus = selected != null ? selected : openGroup != null ? openGroup : open;
        for (Node a : nodes) {
            double s = a == focus ? f.focus() : a == openGroup || a == open ? f.focus() * f.pathFocus()
                    : a.kind.equals("category") ? f.centre() : f.centre() * .3;
            a.vx += (cx - a.x) * s * alpha; a.vy += (cy - a.y) * s * aspect * alpha;
        }
        double gap = style.node().get("gap");
        for (int i = 0; i < n; i++) {
            Node a = nodes.get(i);
            for (int j = i + 1; j < n; j++) {
                Node b = nodes.get(j); double min = a.r * a.grow + b.r * b.grow + gap;
                double dx = b.x + b.vx - a.x - a.vx, dy = b.y + b.vy - a.y - a.vy, l = dx * dx + dy * dy;
                if (l >= min * min) continue;
                l = Math.sqrt(l); if (l == 0) l = 1e-6;
                double push = (min - l) / l * f.collide() * .5; dx *= push; dy *= push;
                b.vx += dx; b.vy += dy; a.vx -= dx; a.vy -= dy;
            }
        }
        double x0 = area.x, y0 = area.y, x1 = x0 + area.w, y1 = y0 + area.h;
        for (Node a : nodes) {
            if (a.fixed) { a.vx = 0; a.vy = 0; continue; }
            a.vx *= 1 - f.velocityDecay(); a.vy *= 1 - f.velocityDecay();
            double speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy);
            if (speed > f.maxSpeed()) { a.vx *= f.maxSpeed() / speed; a.vy *= f.maxSpeed() / speed; }
            a.x += a.vx; a.y += a.vy; double m = a.r + 1;
            if (a.x < x0 + m) { a.x = x0 + m; a.vx = 0; } else if (a.x > x1 - m) { a.x = x1 - m; a.vx = 0; }
            if (a.y < y0 + m) { a.y = y0 + m; a.vy = 0; } else if (a.y > y1 - m) { a.y = y1 - m; a.vy = 0; }
        }
        var motion = style.motion(); List<Node> alive = new ArrayList<>();
        for (Node a : nodes) {
            if (a.dying) a.grow -= 1.0 / motion.shrink(); else if (a.grow < 1) a.grow = Math.min(1, a.grow + 1.0 / motion.grow());
            if (a.shake > 0) a.shake--; if (a.pulse > 0) a.pulse--;
            if (a.grow > 0) alive.add(a);
        }
        if (alive.size() != n) { nodes = alive; links = new ArrayList<>(links.stream().filter(l -> l.a.grow > 0 && l.b.grow > 0).toList()); }
        ticks++;
    }
    public boolean moving() {
        if (alpha > style.force().alphaMin()) return true;
        for (Node a : nodes) if (a.dying || a.grow < 1 || a.shake > 0 || a.pulse > 0) return true;
        return false;
    }
    public void settle(int max) { for (int i = 0; i < max && moving(); i++) tick(); }
    public Node nodeAt(double x, double y) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node a = nodes.get(i); if (a.dying || a.grow < .5) continue;
            double dx = x - a.x, dy = y - a.y;
            if (dx * dx + dy * dy <= (a.r + 1) * (a.r + 1)) return a;
        }
        return null;
    }
    public void grab(Node node) { node.fixed = true; alphaTarget = style.force().dragTarget(); reheat(alphaTarget); }
    public void moveTo(Node node, double x, double y) {
        double m = node.r + 1;
        node.x = Math.min(area.x + area.w - m, Math.max(area.x + m, x));
        node.y = Math.min(area.y + area.h - m, Math.max(area.y + m, y));
    }
    public void drop(Node node) { node.fixed = false; alphaTarget = 0; }
}
