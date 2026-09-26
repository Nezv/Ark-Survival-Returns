package dev.nez.arksurvivalreturns.feature.tech;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

/**
 * The loaded technology tree, replaced atomically on every server resource reload.
 *
 * <p>Documents may be split across several {@code data/<namespace>/tech_tree/*.json} files; ages and
 * nodes are merged and duplicate node ids are rejected so a broken data pack cannot silently replace
 * a node.
 */
public final class TechTree {
    public static final Codec<TechTree> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(TechTree::schemaVersion),
            TechAge.CODEC.listOf().optionalFieldOf("ages", List.of()).forGetter(TechTree::ages),
            TechNode.CODEC.listOf().optionalFieldOf("nodes", List.of()).forGetter(TechTree::nodes)
    ).apply(instance, TechTree::new));

    private static volatile @Nullable TechTree current;

    private final int schemaVersion;
    private final List<TechAge> ages;
    private final List<TechNode> nodes;
    private final Map<String, TechNode> byId;
    private final List<TechNode> ordered;

    public TechTree(int schemaVersion, List<TechAge> ages, List<TechNode> nodes) {
        this.schemaVersion = schemaVersion;
        if (nodes.size() > 64 || ages.size() > 8) throw new IllegalArgumentException("Technology tree exceeds UI bounds");
        this.ages = ages.stream().sorted(Comparator.comparingInt(TechAge::order)).toList();
        var ageIds = new HashSet<String>();
        for (var age : ages) {
            if (!ageIds.add(age.id()) || !age.color().matches("#[0-9a-fA-F]{6}"))
                throw new IllegalArgumentException("Invalid or duplicate technology age: " + age.id());
        }
        this.byId = new LinkedHashMap<>();
        var questIds = new HashSet<Long>();
        for (TechNode node : nodes) {
            if (!node.id().matches("[a-z0-9_]{1,48}") || !ageIds.contains(node.age())
                    || node.title().length() > 80 || node.task().length() > 256
                    || node.icon().orElse("").length() > 200 || node.requires().size() > 4
                    || node.box().size() != 4 || node.box().get(0) < 0 || node.box().get(0) > 4096
                    || node.box().get(1) < 35 || node.box().get(1) > 240 || !questIds.add(TechFtbBridge.questId(node.id())))
                throw new IllegalArgumentException("Invalid technology presentation: " + node.id());
            if (byId.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate technology node: " + node.id());
            }
            for (String requirement : node.requires()) {
                if (nodes.stream().noneMatch(candidate -> candidate.id().equals(requirement))) {
                    throw new IllegalArgumentException("Node " + node.id() + " requires missing node " + requirement);
                }
            }
        }
        this.nodes = List.copyOf(nodes);
        this.ordered = List.copyOf(byId.values());
        var pending = new HashSet<>(byId.keySet());
        var done = new HashSet<String>();
        while (!pending.isEmpty()) {
            var ready = pending.stream().filter(id -> done.containsAll(byId.get(id).requires())).toList();
            if (ready.isEmpty()) throw new IllegalArgumentException("Cycle in technology prerequisites");
            done.addAll(ready);
            pending.removeAll(ready);
        }
    }

    public static @Nullable TechTree current() {
        return current;
    }

    static void set(@Nullable TechTree tree) {
        current = tree;
    }

    /** Merges parsed documents; a duplicate age or node id fails the whole load. */
    static TechTree merge(List<TechTree> documents) {
        int schema = documents.stream().mapToInt(TechTree::schemaVersion).max().orElse(1);
        List<TechAge> ages = new ArrayList<>();
        List<TechNode> nodes = new ArrayList<>();
        for (TechTree document : documents) {
            ages.addAll(document.ages());
            nodes.addAll(document.nodes());
        }
        return new TechTree(schema, ages, nodes);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public List<TechAge> ages() {
        return ages;
    }

    public List<TechNode> nodes() {
        return nodes;
    }

    public List<TechNode> ordered() {
        return ordered;
    }

    public Optional<TechNode> node(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<TechAge> age(String id) {
        return ages.stream().filter(candidate -> candidate.id().equals(id)).findFirst();
    }

    /** Nodes whose requirements are complete but which are not completed yet. */
    public List<TechNode> available(TechTribeProgress progress) {
        List<TechNode> available = new ArrayList<>();
        for (TechNode node : ordered) {
            if (progress.completed(node.id())) continue;
            if (requirementsMet(node, progress)) available.add(node);
        }
        return available;
    }

    public boolean requirementsMet(TechNode node, TechTribeProgress progress) {
        for (String requirement : node.requires()) {
            if (!progress.completed(requirement)) return false;
        }
        return true;
    }
}
