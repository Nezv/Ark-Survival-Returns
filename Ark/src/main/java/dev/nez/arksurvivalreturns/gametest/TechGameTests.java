package dev.nez.arksurvivalreturns.gametest;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.feature.tech.TechEvent;
import dev.nez.arksurvivalreturns.feature.tech.TechEventKind;
import dev.nez.arksurvivalreturns.feature.tech.TechNode;
import dev.nez.arksurvivalreturns.feature.tech.TechNodeKind;
import dev.nez.arksurvivalreturns.feature.tech.TechProgressData;
import dev.nez.arksurvivalreturns.feature.tech.TechService;
import dev.nez.arksurvivalreturns.feature.tech.TechTree;
import dev.nez.arksurvivalreturns.feature.tech.TechTribeProgress;
import dev.nez.arksurvivalreturns.feature.tech.TechTrigger;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** The shipped technology tree, its codecs, dependency gating and the progression flow. */
final class TechGameTests {
    static void tree(GameTestHelper h) {
        TechTree tree = TechTree.current();
        h.assertTrue(tree != null, "The technology tree did not load");
        h.assertTrue(tree.nodes().size() == 40, "Node count changed: " + tree.nodes().size());
        h.assertTrue(tree.ages().size() == 3, "Age count changed: " + tree.ages().size());
        for (TechNode node : tree.nodes()) {
            h.assertFalse(node.title().isEmpty(), "Node has no title: " + node.id());
            h.assertTrue(node.trigger() != null, "Node has no trigger: " + node.id());
            h.assertTrue(tree.age(node.age()).isPresent(), "Node age is unknown: " + node.id());
        }
        h.assertTrue(tree.node("prepare").map(node -> node.kind() == TechNodeKind.GATE).orElse(false),
                "prepare must be the Bronze gate");
        h.assertTrue(tree.node("greed").map(node -> node.kind() == TechNodeKind.GATE).orElse(false),
                "greed must be the Iron starter");
        h.succeed();
    }

    static void progress(GameTestHelper h) {
        TechTribeProgress original = new TechTribeProgress();
        original.mark("mark");
        original.addCount("counter", 3);
        original.complete("monkeys");
        var encoded = TechTribeProgress.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        TechTribeProgress decoded = TechTribeProgress.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        h.assertTrue(decoded.marked("mark"), "Marks did not survive the codec");
        h.assertTrue(decoded.count("counter") == 3, "Counters did not survive the codec");
        h.assertTrue(decoded.completed("monkeys"), "Completions did not survive the codec");

        ServerLevel world = h.getLevel();
        UUID tribe = UUID.randomUUID();
        TechProgressData data = TechProgressData.get(world);
        data.store(tribe, original);
        h.assertTrue(data.progress(tribe).completed("monkeys"), "Stored progress did not read back");
        h.assertTrue(data.reset(tribe), "Reset reported nothing to forget");
        h.assertFalse(data.progress(tribe).completed("monkeys"), "Reset left progress behind");
        h.succeed();
    }

    static void triggers(GameTestHelper h) {
        List<TechTrigger> triggers = List.of(
                new TechTrigger.Collect(List.of(Identifier.parse("minecraft:stone")), 2, true),
                new TechTrigger.Consume(List.of(Identifier.parse("arksurvivalreturns:dried_ration")), 3),
                new TechTrigger.PlaceBlock(List.of(Identifier.parse("minecraft:campfire")), true),
                new TechTrigger.Event(TechEventKind.TROUGH_FEED),
                TechTrigger.Tame.any(),
                new TechTrigger.Tame(java.util.Optional.of(Identifier.parse("arksurvivalreturns:tyrannosaurus"))),
                new TechTrigger.AllOf(List.of(new TechTrigger.Event(TechEventKind.TAME_WORK), new TechTrigger.Future())),
                new TechTrigger.Future()
        );
        for (TechTrigger trigger : triggers) {
            var encoded = TechTrigger.CODEC.encodeStart(NbtOps.INSTANCE, trigger).getOrThrow();
            TechTrigger decoded = TechTrigger.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
            h.assertTrue(decoded.equals(trigger), "Trigger codec lost data: " + trigger);
        }
        TechTribeProgress progress = new TechTribeProgress();
        TechEvent event = new TechEvent(TechEventKind.TROUGH_FEED, null, null, null, null, 0L);
        h.assertTrue(new TechTrigger.Event(TechEventKind.TROUGH_FEED).observe(event, progress, "x:"),
                "Event trigger did not record");
        h.assertTrue(new TechTrigger.Event(TechEventKind.TROUGH_FEED).satisfied(progress, "x:", null),
                "Event trigger is not satisfied");
        h.assertFalse(new TechTrigger.Future().satisfied(progress, "x:", null), "A future trigger must never satisfy");
        h.succeed();
    }

    static void flow(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "ArkTechPlayer"));
        player.getInventory().clearContent();
        world.addFreshEntity(player);
        UUID tribe = TechService.tribeOf(player);
        var tree = TechTree.current();
        h.assertTrue(dev.nez.arksurvivalreturns.feature.tech.TechFtbBridge.ready(tree), "FTB mirror chapters did not load");
        player.getInventory().add(new ItemStack(Items.COBBLESTONE));
        TechService.scan(player);
        h.assertFalse(TechProgressData.get(world).progress(tribe).completed("monkeys"), "Deferred triggers completed automatically");
        h.assertTrue(TechService.unlock(player, "monkeys"), "Operator grant failed");
        var file = dev.ftb.mods.ftbquests.quest.ServerQuestFile.getInstance();
        var team = file.getOrCreateTeamData(tribe);
        var quest = file.getQuest(dev.nez.arksurvivalreturns.feature.tech.TechFtbBridge.questId("monkeys"));
        h.assertTrue(team.isCompleted(quest), "Ark grant did not reach FTB Quests");
        quest.forceProgress(team, new dev.ftb.mods.ftbquests.util.ProgressChange(quest, player.getUUID()).setReset(true));
        dev.nez.arksurvivalreturns.feature.tech.TechFtbBridge.pull(player);
        h.assertFalse(TechProgressData.get(world).progress(tribe).completed("monkeys"), "FTB reset was resurrected by Ark cache");
        quest.forceProgress(team, new dev.ftb.mods.ftbquests.util.ProgressChange(quest, player.getUUID()));
        dev.nez.arksurvivalreturns.feature.tech.TechFtbBridge.pull(player);
        h.assertTrue(TechProgressData.get(world).progress(tribe).completed("monkeys"), "FTB grant did not reach Ark");
        h.assertFalse(TechProgressData.get(world).progress(UUID.randomUUID()).completed("monkeys"), "Completion leaked to another tribe");
        h.assertTrue(TechService.reset(player), "Ark reset failed");
        h.assertFalse(team.isCompleted(quest), "Ark reset did not reach FTB");
        player.discard();
        h.succeed();
    }

    static void future(GameTestHelper h) {
        TechTree tree = TechTree.current();
        TechTribeProgress progress = new TechTribeProgress();
        progress.complete("prepare");
        TechNode shiny = tree.node("shiny").orElseThrow();
        h.assertTrue(tree.requirementsMet(shiny, progress), "shiny should be reachable after prepare");
        h.assertFalse(shiny.trigger().satisfied(progress, "shiny:", null),
                "A node without a mechanic must never complete");

        TechTribeProgress partial = new TechTribeProgress();
        partial.complete("lasting");
        partial.complete("scavenge");
        h.assertFalse(available(tree, partial, "narcotics"), "Prehistoric finale opened after only two paths");
        partial.complete("dish");
        h.assertTrue(available(tree, partial, "narcotics"), "Prehistoric finale did not open after all three paths");
        h.assertFalse(available(tree, partial, "prepare"), "Bronze opened before Narcotraffic");
        for (String id : List.of("dried", "golden", "cocaine")) {
            var hidden = dev.nez.arksurvivalreturns.feature.tech.TechView.project(tree, partial, true).nodes().stream()
                    .filter(n -> n.id().equals(id)).findFirst().orElseThrow();
            h.assertTrue(hidden.state() == dev.nez.arksurvivalreturns.feature.tech.TechView.State.HIDDEN, "Food bonus not concealed");
            h.assertTrue(hidden.task().equals("???") && hidden.icon().isEmpty() && hidden.requires().isEmpty(), "Secret detail leaked into network view");
            partial.complete(id);
            var revealed = dev.nez.arksurvivalreturns.feature.tech.TechView.project(tree, partial, true).nodes().stream()
                    .filter(n -> n.id().equals(id)).findFirst().orElseThrow();
            h.assertTrue(revealed.state() == dev.nez.arksurvivalreturns.feature.tech.TechView.State.COMPLETE && !revealed.task().equals("???"), "Completed food did not reveal");
        }
        h.succeed();
    }

    private static boolean available(TechTree tree, TechTribeProgress progress, String node) {
        return tree.available(progress).stream().anyMatch(candidate -> candidate.id().equals(node));
    }

    private TechGameTests() {}
}
