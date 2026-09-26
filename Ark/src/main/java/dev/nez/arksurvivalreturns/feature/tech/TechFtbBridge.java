package dev.nez.arksurvivalreturns.feature.tech;

import java.util.HashSet;
import java.util.UUID;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.CustomTask;
import dev.ftb.mods.ftbquests.util.ProgressChange;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB owns completion once the dedicated mirror chapters are installed. Ark retains trigger
 * history and a local fallback. Never union caches: that would resurrect an FTB admin reset.
 * FTB Teams remains responsible for party join/leave/merge policy.
 */
public final class TechFtbBridge {
    public static long questId(String node) {
        return 0x4152000000000000L | (Integer.toUnsignedLong(node.hashCode()) << 1);
    }

    public static boolean ready(TechTree tree) {
        if (!ServerQuestFile.exists()) return false;
        var file = ServerQuestFile.getInstance();
        return tree.nodes().stream().allMatch(n -> file.getQuest(questId(n.id())) != null);
    }

    public static boolean pull(ServerPlayer player) {
        var tree = TechTree.current();
        if (tree == null || !ready(tree)) return false;
        var file = ServerQuestFile.getInstance();
        UUID tribe = TechService.tribeOf(player);
        var team = file.getOrCreateTeamData(tribe);
        var complete = new HashSet<String>();
        for (var node : tree.nodes()) {
            var quest = file.getQuest(questId(node.id()));
            for (var task : quest.getTasks()) {
                if (task instanceof CustomTask custom) custom.setEnableButton(false);
            }
            if (team.isCompleted(quest)) complete.add(node.id());
        }
        var data = TechProgressData.get(player.level());
        var progress = data.progress(tribe);
        if (progress.replaceCompletions(complete)) data.store(tribe, progress);
        return true;
    }

    public static void complete(ServerPlayer player, String node) {
        var tree = TechTree.current();
        if (tree == null || !ready(tree)) return;
        var file = ServerQuestFile.getInstance();
        var team = file.getOrCreateTeamData(TechService.tribeOf(player));
        var quest = file.getQuest(questId(node));
        quest.forceProgress(team, new ProgressChange(quest, player.getUUID()));
    }

    public static void reset(ServerPlayer player) {
        var tree = TechTree.current();
        if (tree == null || !ready(tree)) return;
        var file = ServerQuestFile.getInstance();
        var team = file.getOrCreateTeamData(TechService.tribeOf(player));
        for (var node : tree.nodes()) {
            var quest = file.getQuest(questId(node.id()));
            quest.forceProgress(team, new ProgressChange(quest, player.getUUID()).setReset(true));
        }
    }

    private TechFtbBridge() {}
}
