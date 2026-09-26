package dev.nez.arksurvivalreturns.feature.tech;

import java.util.List;
import java.util.Set;

/** The only tree representation sent to clients: no triggers or secret objectives. */
public record TechView(List<Age> ages, List<Node> nodes, boolean ftbLinked) {
    public enum State { LOCKED, AVAILABLE, COMPLETE, HIDDEN }
    public record Age(String id, String title, int color, int left, int right) {}
    public record Node(String id, String title, String task, String age, int x, int y,
                       String icon, String kind, State state, List<String> requires) {}

    public static TechView project(TechTree tree, TechTribeProgress progress, boolean ftbLinked) {
        var ages = tree.ages().stream().map(age -> {
            int left = tree.nodes().stream().filter(n -> n.age().equals(age.id()))
                    .mapToInt(n -> n.box().get(0)).min().orElse(60) - 60;
            int right = tree.nodes().stream().filter(n -> n.age().equals(age.id()))
                    .mapToInt(n -> n.box().get(0)).max().orElse(left + 600) + 70;
            return new Age(age.id(), age.title(), 0xFF000000 | Integer.parseInt(age.color().substring(1), 16), left, right);
        }).toList();
        var nodes = tree.nodes().stream().map(n -> {
            boolean done = progress.completed(n.id());
            boolean hidden = n.kind() == TechNodeKind.SIDE && !done;
            State state = hidden ? State.HIDDEN : done ? State.COMPLETE
                    : tree.requirementsMet(n, progress) ? State.AVAILABLE : State.LOCKED;
            return new Node(n.id(), n.title(), hidden ? "???" : n.task(), n.age(), n.box().get(0), n.box().get(1),
                    hidden ? "" : n.icon().orElse(""), n.kind().getSerializedName(), state,
                    hidden ? List.of() : n.requires());
        }).toList();
        return new TechView(ages, nodes, ftbLinked);
    }

    public static TechView empty() { return new TechView(List.of(), List.of(), false); }
}
