package dev.nez.arksurvivalreturns.feature.tech;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The technology tree engine.
 *
 * <p>Progress belongs to the player's FTB Teams tribe, with a solo player UUID as the fallback.
 * Events and a periodic inventory pass feed trigger history; completion cascades through the
 * dependency graph so a gate only opens when all of its paths are done. Everything is
 * server-authoritative and persisted through {@link TechProgressData}.
 */
public final class TechService {
    public static UUID tribeOf(UUID player) {
        return TribeService.team(player).map(team -> team.getId()).orElse(player);
    }

    public static UUID tribeOf(ServerPlayer player) {
        return TribeService.team(player).map(team -> team.getId()).orElse(player.getUUID());
    }

    /** Feeds one gameplay event to every node trigger and re-evaluates the tribe. */
    public static void notify(ServerLevel level, UUID actor, TechEvent event) {
        if (!Config.TECH_ENABLED.get()) return;
        TechTree tree = TechTree.current();
        if (tree == null) return;
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(actor);
        if (online != null) TechFtbBridge.pull(online);
        UUID tribe = tribeOf(actor);
        TechProgressData data = TechProgressData.get(level);
        TechTribeProgress progress = data.progress(tribe);
        boolean changed = false;
        for (TechNode node : tree.nodes()) {
            changed |= node.trigger().observe(event, progress, node.id() + ":");
        }
        changed |= evaluate(tree, progress, online, tribe, level);
        if (changed) data.store(tribe, progress);
    }

    public static void notify(ServerPlayer player, TechEvent event) {
        notify(player.level(), player.getUUID(), event);
    }

    /** Periodic possession pass; catches items that entered the inventory through a menu. */
    public static void scan(ServerPlayer player) {
        if (!Config.TECH_ENABLED.get()) return;
        TechFtbBridge.pull(player);
        TechTree tree = TechTree.current();
        if (tree == null) return;
        UUID tribe = tribeOf(player);
        TechProgressData data = TechProgressData.get(player.level());
        TechTribeProgress progress = data.progress(tribe);
        if (evaluate(tree, progress, player, tribe, player.level())) data.store(tribe, progress);
    }

    private static boolean evaluate(TechTree tree, TechTribeProgress progress, @Nullable ServerPlayer player, UUID tribe,
            ServerLevel level) {
        boolean any = false;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (TechNode node : tree.ordered()) {
                if (progress.completed(node.id()) || !tree.requirementsMet(node, progress)) continue;
                if (!node.trigger().satisfied(progress, node.id() + ":", player)) continue;
                progress.complete(node.id());
                if (player != null) TechFtbBridge.complete(player, node.id());
                changed = true;
                any = true;
                announce(node, tribe, level);
            }
        }
        return any;
    }

    private static void announce(TechNode node, UUID tribe, ServerLevel level) {
        if (!Config.TECH_ANNOUNCE.get()) return;
        Component text = node.kind() == TechNodeKind.GATE
                ? Component.translatable("tech.arksurvivalreturns.gate", node.title())
                : Component.translatable("tech.arksurvivalreturns.completed", node.title());
        for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
            if (tribeOf(other).equals(tribe)) other.sendSystemMessage(text, false);
        }
    }

    /** Diagnostic lines for `/arktech status`. */
    public static List<Component> status(ServerPlayer player) {
        TechFtbBridge.pull(player);
        List<Component> lines = new ArrayList<>();
        TechTree tree = TechTree.current();
        if (tree == null) {
            lines.add(Component.translatable("tech.arksurvivalreturns.unavailable"));
            return lines;
        }
        TechTribeProgress progress = TechProgressData.get(player.level()).progress(tribeOf(player));
        lines.add(Component.translatable("tech.arksurvivalreturns.status_total",
                progress.completedCount(), tree.nodes().size()));
        for (TechAge age : tree.ages()) {
            int done = 0;
            int total = 0;
            for (TechNode node : tree.nodes()) {
                if (!node.age().equals(age.id())) continue;
                total++;
                if (progress.completed(node.id())) done++;
            }
            lines.add(Component.translatable("tech.arksurvivalreturns.status_age", age.title(), done, total));
        }
        for (TechNode node : tree.available(progress)) {
            if (node.kind() == TechNodeKind.SIDE) continue;
            lines.add(Component.translatable("tech.arksurvivalreturns.status_available", node.title(), node.task()));
        }
        return lines;
    }

    /** Operator grant: complete one node for the caller's tribe. */
    public static boolean unlock(ServerPlayer player, String nodeId) {
        TechTree tree = TechTree.current();
        if (tree == null || tree.node(nodeId).isEmpty()) return false;
        TechFtbBridge.pull(player);
        UUID tribe = tribeOf(player);
        TechProgressData data = TechProgressData.get(player.level());
        TechTribeProgress progress = data.progress(tribe);
        if (!progress.complete(nodeId)) return false;
        TechFtbBridge.complete(player, nodeId);
        data.store(tribe, progress);
        return true;
    }

    /** Operator reset: forget the caller's tribe technology state. */
    public static boolean reset(ServerPlayer player) {
        TechFtbBridge.pull(player);
        TechFtbBridge.reset(player);
        return TechProgressData.get(player.level()).reset(tribeOf(player));
    }

    private TechService() {}
}
