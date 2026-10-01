package dev.nez.arksurvivalreturns.feature.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Plain graph data, usable by both the dedicated server and deterministic unit tests. */
public record WorkstationDefinition(String station, String title, String verb, String accent, String well,
        List<Category> categories) {
    public WorkstationDefinition {
        verb = verb == null ? "Craft" : verb;
        accent = accent == null ? "#e6e8e1" : accent;
        well = well == null ? "dots" : well;
        categories = List.copyOf(categories);
    }

    public record Category(String id, String title, String icon, String color, int level, String after,
            boolean planned, List<Entry> items) {
        public Category { items = items == null ? List.of() : List.copyOf(items); }
        Category withItems(List<Entry> entries) { return new Category(id, title, icon, color, level, after, planned, entries); }
    }

    /** Either a folder, an authored item or a compiled family of variants. */
    public record Entry(String group, String family, String title, String icon, Integer level, boolean planned,
            List<Entry> items, List<Variant> variants, String item, int count, Map<String, Integer> cost) {
        public Entry {
            items = items == null ? List.of() : List.copyOf(items);
            variants = variants == null ? List.of() : List.copyOf(variants);
            cost = cost == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cost));
            count = count == 0 ? 1 : count;
        }
        Entry withItems(List<Entry> entries) {
            return new Entry(group, family, title, icon, level, planned, entries, variants, item, count, cost);
        }
        Entry normalise(int inherited, Predicate<String> registered) {
            int gate = level == null ? inherited : level;
            if (group != null) return new Entry(group, family, title, icon, level, planned,
                    items.stream().map(e -> e.normalise(gate, registered)).filter(e -> e != null).toList(),
                    List.of(), null, 1, Map.of());
            List<Variant> choices = item == null ? variants : List.of(new Variant(item, count, cost, null, false, null));
            choices = choices.stream().filter(v -> registered.test(v.item())).toList();
            if (choices.isEmpty()) return null;
            String key = family == null ? item.substring(item.indexOf(':') + 1) : family;
            // Registered planned items are usable as soon as they ship (the Armoury is one).
            return new Entry(null, key, title, icon, gate, false, List.of(), choices, null, 1, Map.of());
        }
    }

    public record Variant(String item, int count, Map<String, Integer> cost, String recipe, boolean apply, String name, String output) {
        public Variant(String item, int count, Map<String, Integer> cost, String recipe, boolean apply, String name) {
            this(item, count, cost, recipe, apply, name, null);
        }
        public Variant {
            count = count == 0 ? 1 : count;
            cost = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cost));
        }
    }
    public record Craft(Category category, Entry group, Entry entry, Variant variant) {
        public int level() { return Math.max(category.level(), Math.max(levelOf(category, null, group), levelOf(category, entry, group))); }
    }
    public static int levelOf(Category cat, Entry entry, Entry group) {
        if (entry != null && entry.level() != null) return entry.level();
        if (group != null && group.level() != null) return group.level();
        return cat.level();
    }

    public WorkstationDefinition normalise(WorkstationDefinition rules, Predicate<String> registered) {
        List<Category> cats = new ArrayList<>();
        for (Category cat : categories) {
            Category extra = rules == null ? null : rules.categories.stream().filter(c -> c.id.equals(cat.id)).findFirst().orElse(null);
            List<Entry> authored = cat.items.stream().map(e -> e.normalise(cat.level, registered)).filter(e -> e != null).toList();
            List<Entry> added = extra == null ? List.of() : extra.items.stream().map(e -> e.normalise(cat.level, registered)).filter(e -> e != null).toList();
            List<Entry> merged = new ArrayList<>();
            authored.stream().filter(e -> e.group == null).forEach(merged::add);
            added.stream().filter(e -> e.group == null).forEach(merged::add);
            for (Entry group : authored) if (group.group != null) {
                Entry supplement = added.stream().filter(e -> group.group.equals(e.group)).findFirst().orElse(null);
                List<Entry> children = new ArrayList<>(group.items);
                if (supplement != null) children.addAll(supplement.items);
                if (!children.isEmpty()) merged.add(group.withItems(children));
            }
            cats.add(cat.withItems(merged));
        }
        return new WorkstationDefinition(station, title, verb, accent, well, cats);
    }
    public List<Craft> crafts() {
        List<Craft> out = new ArrayList<>();
        for (Category cat : categories) for (Entry entry : cat.items) {
            if (entry.group != null) for (Entry child : entry.items) {
                for (Variant variant : child.variants) out.add(new Craft(cat, entry, child, variant));
            } else for (Variant variant : entry.variants) out.add(new Craft(cat, null, entry, variant));
        }
        return out;
    }
}
