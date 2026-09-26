package dev.nez.arksurvivalreturns.feature.tech;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** What one tribe has done and completed. Marks and counters are historical and never decay. */
public final class TechTribeProgress {
    public static final Codec<TechTribeProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("marks", List.of()).forGetter(progress -> List.copyOf(progress.marks)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of())
                    .forGetter(progress -> progress.counters),
            Codec.STRING.listOf().optionalFieldOf("completed", List.of())
                    .forGetter(progress -> List.copyOf(progress.completed))
    ).apply(instance, TechTribeProgress::new));

    private final Set<String> marks;
    private final Map<String, Integer> counters;
    private final Set<String> completed;

    public TechTribeProgress() {
        this(List.of(), Map.of(), List.of());
    }

    public TechTribeProgress(Collection<String> marks, Map<String, Integer> counters, Collection<String> completed) {
        this.marks = new LinkedHashSet<>(marks);
        this.counters = new LinkedHashMap<>(counters);
        this.completed = new LinkedHashSet<>(completed);
    }

    /** Returns true when the mark was new. */
    public boolean mark(String key) {
        return marks.add(key);
    }

    public boolean marked(String key) {
        return marks.contains(key);
    }

    /** Returns true when the counter changed. */
    public boolean addCount(String key, int amount) {
        if (amount <= 0) return false;
        counters.merge(key, amount, Integer::sum);
        return true;
    }

    public int count(String key) {
        return counters.getOrDefault(key, 0);
    }

    /** Returns true when the node was not completed before. */
    public boolean complete(String nodeId) {
        return completed.add(nodeId);
    }

    public boolean completed(String nodeId) {
        return completed.contains(nodeId);
    }

    public int completedCount() {
        return completed.size();
    }

    public Set<String> completedNodes() {
        return Set.copyOf(completed);
    }

    /** FTB's current completion set replaces this cache, including admin revocations. */
    public boolean replaceCompletions(Collection<String> values) {
        Set<String> next = new LinkedHashSet<>(values);
        if (completed.equals(next)) return false;
        completed.clear();
        completed.addAll(next);
        return true;
    }
}
