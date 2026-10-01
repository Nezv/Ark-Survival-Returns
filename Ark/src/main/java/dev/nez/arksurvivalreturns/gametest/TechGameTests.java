package dev.nez.arksurvivalreturns.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.farm.TroughBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveForgeBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.CrusherBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.StationContent;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.tech.TechEvent;
import dev.nez.arksurvivalreturns.feature.tech.TechEventKind;
import dev.nez.arksurvivalreturns.feature.tech.TechEvents;
import dev.nez.arksurvivalreturns.feature.tech.TechNode;
import dev.nez.arksurvivalreturns.feature.tech.TechNodeKind;
import dev.nez.arksurvivalreturns.feature.tech.TechProgressData;
import dev.nez.arksurvivalreturns.feature.tech.TechService;
import dev.nez.arksurvivalreturns.feature.tech.TechTree;
import dev.nez.arksurvivalreturns.feature.tech.TechTribeProgress;
import dev.nez.arksurvivalreturns.feature.tech.TechTrigger;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** The shipped technology tree, its codecs, dependency gating and the progression flow. */
final class TechGameTests {
    static void tree(GameTestHelper h) {
        TechTree tree = TechTree.current();
        h.assertTrue(tree != null, "The technology tree did not load");
        // 57: Prehistoric 19, Bronze 19, Iron 19 (starter, bonus, four planned four-step lanes whose items do
        // not exist yet, and a finale needing all four of them).
        h.assertTrue(tree.nodes().size() == 57, "Node count changed: " + tree.nodes().size());
        h.assertTrue(tree.ages().size() == 3, "Age count changed: " + tree.ages().size());
        for (TechNode node : tree.nodes()) {
            h.assertFalse(node.title().isEmpty(), "Node has no title: " + node.id());
            h.assertTrue(node.trigger() != null, "Node has no trigger: " + node.id());
            h.assertTrue(tree.age(node.age()).isPresent(), "Node age is unknown: " + node.id());
        }
        h.assertTrue(tree.node("forge").map(node -> node.kind() == TechNodeKind.GATE && node.age().equals("prehistoric")).orElse(false),
                "forge must be the Prehistoric finale");
        h.assertTrue(tree.node("shiny").map(node -> node.kind() == TechNodeKind.GATE).orElse(false),
                "shiny must be the Bronze starter");
        h.assertTrue(tree.node("steel").map(node -> node.kind() == TechNodeKind.GATE).orElse(false),
                "steel must be the Iron starter");
        List<List<String>> ironLanes = List.of(List.of("cartography", "radar", "target", "completionist"),
                List.of("wires", "generator", "circuit", "shock"), List.of("steel_set", "backpack", "boots", "rifle"),
                List.of("engine", "sheet", "pipe", "screw"));
        for (int lane = 0; lane < ironLanes.size(); lane++) {
            String previous = "steel";
            for (String id : ironLanes.get(lane)) {
                int index = lane;
                String requires = previous;
                // Planned only: every Iron item is still unregistered, so a live trigger could never fire.
                h.assertTrue(tree.node(id).map(node -> node.age().equals("iron") && node.lane() == index
                        && node.requires().equals(List.of(requires)) && node.trigger().type() == TechTrigger.Type.FUTURE)
                        .orElse(false), "Iron node missing, misplaced or wired early: " + id);
                previous = id;
            }
            h.assertTrue(tree.node("subdue").orElseThrow().requires().contains(previous), "subdue must need Iron lane " + lane);
        }
        for (String deleted : List.of("prepare", "greed", "rawr", "prometheus", "alloy", "weapon", "charcoal",
                "harder", "better", "faster", "stronger", "vault", "curtain", "trickshot")) {
            h.assertTrue(tree.node(deleted).isEmpty(), "Deleted node still present: " + deleted);
        }
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
        quest.forceProgress(team, new dev.ftb.mods.ftbquests.util.ProgressChange(quest, player.getUUID()).setReset(false));
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
        progress.complete("forge");
        TechNode shiny = tree.node("shiny").orElseThrow();
        h.assertTrue(tree.requirementsMet(shiny, progress), "shiny should be reachable after forge");

        TechTribeProgress steelProgress = new TechTribeProgress();
        steelProgress.complete("kaboom");
        TechNode steel = tree.node("steel").orElseThrow();
        h.assertTrue(tree.requirementsMet(steel, steelProgress), "steel should be reachable after kaboom");
        h.assertFalse(steel.trigger().satisfied(steelProgress, "steel:", null),
                "A node without a mechanic must never complete");

        TechTribeProgress partial = new TechTribeProgress();
        partial.complete("lasting");
        partial.complete("scavenge");
        h.assertFalse(available(tree, partial, "forge"), "Prehistoric finale opened after only two paths");
        partial.complete("narcotics");
        h.assertFalse(available(tree, partial, "forge"), "Prehistoric finale opened without Arms & Armour");
        partial.complete("armoured");
        h.assertTrue(available(tree, partial, "forge"), "Prehistoric finale did not open after all four paths");
        h.assertFalse(available(tree, partial, "shiny"), "Bronze opened before the Prehistoric forge");
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

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        return player;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /** Every Bronze-completion node (proposal 03) carries the exact trigger the design contract describes. */
    static void bronzeWiring(GameTestHelper h) {
        TechTree tree = TechTree.current();
        Map<String, TechTrigger> expected = new LinkedHashMap<>();
        expected.put("forge", new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:primitive_forge")), 1));
        expected.put("mattress", new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:mattress")), 1));
        expected.put("shiny", new TechTrigger.Produce(TechEventKind.SMELT, List.of(Identifier.parse("arksurvivalreturns:bronze_ingot")), 1));
        expected.put("home", new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:bedroll")), 1));
        expected.put("feed", new TechTrigger.Event(TechEventKind.TROUGH_FEED));
        expected.put("theri", new TechTrigger.Tame(Optional.of(Identifier.parse("arksurvivalreturns:therizinosaurus"))));
        expected.put("slavery", new TechTrigger.AllOf(List.of(
                new TechTrigger.Event(TechEventKind.TAME_KILL), new TechTrigger.Event(TechEventKind.TAME_WORK))));
        expected.put("coal", new TechTrigger.Collect(List.of(Identifier.parse("minecraft:coal"), Identifier.parse("minecraft:charcoal")), 1, false));
        expected.put("ironsmelt", new TechTrigger.Produce(TechEventKind.SMELT, List.of(Identifier.parse("minecraft:iron_ingot")), 1));
        expected.put("minerals", new TechTrigger.Collect(List.of(Identifier.parse("arksurvivalreturns:sulphur")), 1, false));
        expected.put("sparklers", new TechTrigger.Produce(TechEventKind.CRUSHER_OUTPUT, List.of(Identifier.parse("minecraft:gunpowder")), 1));
        expected.put("glass", new TechTrigger.Collect(List.of(Identifier.parse("minecraft:glass")), 1, false));
        expected.put("ambulance", new TechTrigger.PlaceBlock(List.of(Identifier.parse("arksurvivalreturns:medicine_bench")), false));
        expected.put("bandage", new TechTrigger.Collect(List.of(Identifier.parse("arksurvivalreturns:herbal_bandage")), 1, false));
        expected.put("vitamins", new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:vitamins")), 1));
        expected.put("knight", new TechTrigger.Collect(
                List.of(Identifier.parse("arksurvivalreturns:bronze_longsword"), Identifier.parse("arksurvivalreturns:bronze_hammer")), 1, false));
        expected.put("tools", new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:bronze_pickaxe"),
                Identifier.parse("arksurvivalreturns:bronze_axe"), Identifier.parse("arksurvivalreturns:bronze_shovel"),
                Identifier.parse("arksurvivalreturns:bronze_hoe")), 1));
        expected.put("tincan", new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:bronze_helmet"),
                Identifier.parse("arksurvivalreturns:bronze_chestplate"), Identifier.parse("arksurvivalreturns:bronze_leggings"),
                Identifier.parse("arksurvivalreturns:bronze_boots")), 1));
        expected.put("colossus", new TechTrigger.Collect(List.of(Identifier.parse("arksurvivalreturns:bronze_helmet"),
                Identifier.parse("arksurvivalreturns:bronze_chestplate"), Identifier.parse("arksurvivalreturns:bronze_leggings"),
                Identifier.parse("arksurvivalreturns:bronze_boots")), 1, true));
        expected.put("kaboom", new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:explosive_arrow")), 1));
        for (var entry : expected.entrySet()) {
            TechNode node = tree.node(entry.getKey()).orElseThrow(() -> new AssertionError("Missing node " + entry.getKey()));
            h.assertTrue(node.trigger().equals(entry.getValue()), entry.getKey() + " trigger mismatch: " + node.trigger());
        }
        h.succeed();
    }

    /** Real stations and creatures driving the new triggers, not just codec round-trips. */
    static void bronzeFlow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        TechTree tree = TechTree.current();

        // --- smelt: only a real forge extraction advances the node, never mere possession ---
        FakePlayer smelter = player(level, "ArkSmelter");
        level.addFreshEntity(smelter);
        BlockPos forgeRel = new BlockPos(8, 3, 8);
        h.setBlock(forgeRel, PrimitiveContent.PRIMITIVE_FORGE.get().defaultBlockState());
        BlockPos forgePos = h.absolutePos(forgeRel);
        PrimitiveForgeBlockEntity forge = (PrimitiveForgeBlockEntity) level.getBlockEntity(forgePos);
        h.assertTrue(forge != null, "The forge block entity is missing");
        forge.insert(new ItemStack(Items.RAW_IRON), PrimitiveForgeBlockEntity.INPUT);
        forge.insert(new ItemStack(Items.CHARCOAL), PrimitiveForgeBlockEntity.FUEL);
        for (int tick = 0; tick < 1000 && forge.output().isEmpty(); tick++) forge.step();
        h.assertTrue(forge.output().is(Items.IRON_INGOT), "Setup: the forge must have smelted iron for this check");
        UUID smelterTribe = TechService.tribeOf(smelter);
        TechTrigger ironsmelt = tree.node("ironsmelt").orElseThrow().trigger();
        TechTribeProgress bystander = new TechTribeProgress();
        ironsmelt.observe(new TechEvent(TechEventKind.OBTAIN, null, new ItemStack(Items.IRON_INGOT), null, null, 0L), bystander, "ironsmelt:");
        h.assertFalse(ironsmelt.satisfied(bystander, "ironsmelt:", null), "Merely obtaining iron must not count as smelting it");
        level.getBlockState(forgePos).useWithoutItem(level, smelter, hit(forgePos));
        h.assertTrue(smelter.getInventory().countItem(Items.IRON_INGOT) == 1, "Setup: taking the result must give the ingot");
        h.assertTrue(ironsmelt.satisfied(TechProgressData.get(level).progress(smelterTribe), "ironsmelt:", smelter),
                "Extracting from the real forge must satisfy the smelt trigger");

        // --- crusher: 2 coal + 2 sulphur ground into gunpowder and taken by hand satisfies Sparklers ---
        BlockPos crusherRel = new BlockPos(10, 3, 8);
        h.setBlock(crusherRel, StationContent.CRUSHER.get().defaultBlockState());
        BlockPos crusherPos = h.absolutePos(crusherRel);
        CrusherBlockEntity crusher = (CrusherBlockEntity) level.getBlockEntity(crusherPos);
        h.assertTrue(crusher != null, "The crusher block entity is missing");
        crusher.insert(new ItemStack(Items.COAL, 2));
        crusher.insert(new ItemStack(dev.nez.arksurvivalreturns.feature.sulphur.SulphurContent.SULPHUR.get(), 2));
        for (int tick = 0; tick < 200; tick++) CrusherBlockEntity.tick(level, crusherPos, level.getBlockState(crusherPos), crusher);
        FakePlayer crusherTaker = player(level, "ArkCrusher");
        level.addFreshEntity(crusherTaker);
        level.getBlockState(crusherPos).useWithoutItem(level, crusherTaker, hit(crusherPos));
        h.assertTrue(crusherTaker.getInventory().countItem(Items.GUNPOWDER) > 0, "The crusher must grind coal and sulphur into gunpowder");
        UUID crusherTribe = TechService.tribeOf(crusherTaker);
        h.assertTrue(tree.node("sparklers").orElseThrow().trigger()
                        .satisfied(TechProgressData.get(level).progress(crusherTribe), "sparklers:", crusherTaker),
                "Gunpowder taken from the real crusher must satisfy Sparklers");

        // --- trough feed and species tame, from real creatures ---
        FakePlayer rancher = player(level, "ArkRancher");
        rancher.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 3, 4))));
        level.addFreshEntity(rancher);
        CreatureEntity theriDino = ModContent.CREATURES.get(Species.THERIZINOSAURUS).get().create(level, EntitySpawnReason.COMMAND);
        theriDino.setNoAi(true);
        theriDino.setPos(rancher.position());
        TamingService.of(theriDino).setOwner(rancher.getUUID());
        theriDino.applyTameState();
        theriDino.setHealth(theriDino.getMaxHealth());
        level.addFreshEntity(theriDino);
        TamingService.of(theriDino).setHunger(90.0);
        BlockPos troughRel = new BlockPos(5, 3, 4);
        h.setBlock(troughRel, ModContent.TROUGH.get().defaultBlockState());
        TroughBlockEntity trough = (TroughBlockEntity) level.getBlockEntity(h.absolutePos(troughRel));
        h.assertTrue(trough.insert(new ItemStack(Items.COOKED_BEEF, 8)) == 8, "Setup: the trough must accept food");
        UUID ranchTribe = TechService.tribeOf(rancher);
        h.assertTrue(trough.feedNearby(level) == 1, "Setup: the trough must feed the hungry tame");
        h.assertTrue(tree.node("feed").orElseThrow().trigger().satisfied(TechProgressData.get(level).progress(ranchTribe), "feed:", null),
                "A real trough feeding must satisfy Feed the Beast");

        TechEvents.onTamed(theriDino, rancher.getUUID());
        h.assertTrue(tree.node("theri").orElseThrow().trigger().satisfied(TechProgressData.get(level).progress(ranchTribe), "theri:", null),
                "Taming a real Therizinosaurus must satisfy Multi-tool");
        FakePlayer otherRancher = player(level, "ArkOtherRancher");
        CreatureEntity parasaur = ModContent.CREATURES.get(Species.PARASAUR).get().create(level, EntitySpawnReason.COMMAND);
        parasaur.setNoAi(true);
        parasaur.setPos(rancher.position());
        level.addFreshEntity(parasaur);
        TechEvents.onTamed(parasaur, otherRancher.getUUID());
        UUID otherTribe = TechService.tribeOf(otherRancher);
        h.assertFalse(tree.node("theri").orElseThrow().trigger().satisfied(TechProgressData.get(level).progress(otherTribe), "theri:", null),
                "Taming a Parasaur must never satisfy the Therizinosaurus-only node");

        // --- place a real Medicine Bench, and collect coal/glass by simple possession ---
        FakePlayer medic = player(level, "ArkMedic");
        UUID medicTribe = TechService.tribeOf(medic);
        TechService.notify(level, medic.getUUID(), TechEvent.place(medic, StationContent.MEDICINE_BENCH.get().defaultBlockState()));
        h.assertTrue(tree.node("ambulance").orElseThrow().trigger().satisfied(TechProgressData.get(level).progress(medicTribe), "ambulance:", null),
                "Placing a real Medicine Bench must satisfy Call ambulance");

        FakePlayer prospector = player(level, "ArkProspector");
        prospector.getInventory().add(new ItemStack(Items.COAL));
        prospector.getInventory().add(new ItemStack(Items.GLASS));
        h.assertTrue(tree.node("coal").orElseThrow().trigger().satisfied(new TechTribeProgress(), "coal:", prospector),
                "Holding coal must satisfy Black Gold");
        h.assertTrue(tree.node("glass").orElseThrow().trigger().satisfied(new TechTribeProgress(), "glass:", prospector),
                "Holding glass must satisfy Invisible");

        theriDino.discard();
        parasaur.discard();
        smelter.discard();
        crusherTaker.discard();
        rancher.discard();
        h.succeed();
    }

    private TechGameTests() {}
}
