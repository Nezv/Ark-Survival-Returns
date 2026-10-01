package dev.nez.arksurvivalreturns.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.camp.*;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.primitive.*;
import dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments;
import dev.nez.arksurvivalreturns.feature.station.*;
import dev.nez.arksurvivalreturns.feature.tech.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Real events and on-disk reloads, in addition to the smaller trigger/codec contract tests. */
final class ProgressionGameTests {
    static void events(GameTestHelper h) {
        var player = LevelGameTests.online(h, "ArkRealEvents");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        GameTestCleanup.onFinish(h, () -> { player.containerMenu = player.inventoryMenu; LevelGameTests.offline(player); });
        player.getInventory().clearContent();
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(64, 3, 64))));
        for (int x = 60; x <= 70; x++) for (int z = 60; z <= 70; z++) h.setBlock(x, 2, z, Blocks.STONE);
        var drop = pickup(h, player, new ItemStack(PrimitiveContent.ROCK.get(), 3));
        h.assertTrue(drop.isRemoved() && progress(player).count("monkeys:arksurvivalreturns:rock") == 3, "Full pickup did not credit exactly the picked stack");
        completed(h, player, "monkeys");
        for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().setItem(0, new ItemStack(PrimitiveContent.ROCK.get(), 63));
        drop = pickup(h, player, new ItemStack(PrimitiveContent.ROCK.get(), 6));
        h.assertTrue(drop.getItem().getCount() == 5 && player.getInventory().getItem(0).getCount() == 64
                && progress(player).count("monkeys:arksurvivalreturns:rock") == 4, "Partial pickup credited items left on the ground: remaining=" + drop.getItem().getCount()
                + " slot=" + player.getInventory().getItem(0).getCount() + " credit=" + progress(player).count("monkeys:arksurvivalreturns:rock") + " infinite=" + player.hasInfiniteMaterials());
        drop.playerTouch(player);
        h.assertTrue(drop.getItem().getCount() == 5 && progress(player).count("monkeys:arksurvivalreturns:rock") == 4,
                "Failed pickup credited items despite a full inventory");
        drop.discard(); player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(dev.nez.arksurvivalreturns.registry.ModContent.MATTRESS_ITEM.get()));
        TechService.scan(player);
        h.assertFalse(progress(player).completed("mattress"), "Possession bypassed the mattress craft requirement");
        craft(h, player, "working_station", "arksurvivalreturns:mattress");
        completed(h, player, "mattress");
        h.assertTrue(progress(player).count("mattress:crafted:arksurvivalreturns:mattress") == 1, "Actual workstation craft event did not record exactly one output");
        player.containerMenu = player.inventoryMenu;
        TechService.unlock(player, "glass"); // Fixture prerequisite; completion below must come from placement.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.MEDICINE_BENCH_ITEM.get()));
        BlockPos support = h.absolutePos(new BlockPos(68, 2, 64));
        h.assertTrue(player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(support)).consumesAction(), "Medicine Bench block-item placement failed");
        h.assertTrue(h.getLevel().getBlockState(support.above()).is(StationContent.MEDICINE_BENCH.get()), "The bench was not actually placed");
        completed(h, player, "ambulance");
        TechService.unlock(player, "lasting");
        player.getFoodData().setFoodLevel(8);
        player.setItemInHand(InteractionHand.MAIN_HAND, DriedMeatItem.withTier(1));
        player.gameMode.useItem(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        h.onEachTick(player::doTick);
        h.runAfterDelay(40, () -> {
            h.assertTrue(player.getMainHandItem().isEmpty(), "Low-tier meat did not finish normal consumption");
            h.assertFalse(progress(player).completed("dried"), "Low-tier meat bypassed the fully-dried requirement");
            player.getFoodData().setFoodLevel(8);
            player.setItemInHand(InteractionHand.MAIN_HAND, DriedMeatItem.withTier(3));
            player.gameMode.useItem(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(player.getMainHandItem().isEmpty(), "Fully dried meat did not finish normal consumption");
            completed(h, player, "dried"); h.succeed();
        });
    }
    static void lighting(GameTestHelper h) {
        var player = LevelGameTests.online(h, "ArkRealLighting");
        GameTestCleanup.onFinish(h, () -> LevelGameTests.offline(player));
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(64, 3, 61))));
        TechService.unlock(player, "monkeys"); TechService.unlock(player, "mattress");
        BlockPos rel = new BlockPos(64, 3, 64), pos = h.absolutePos(rel);
        h.setBlock(rel.below(), Blocks.STONE);
        h.setBlock(rel, PrimitiveContent.STONE_FIRE.get().defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
        h.runAfterDelay(3, () -> {
            h.assertFalse(h.getBlockState(rel).getValue(StoneFireBlock.LIT) || progress(player).completed("warmth"), "Unfueled fire earned a lighting completion");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG));
            player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
            h.assertTrue(h.getBlockState(rel).getValue(StoneFireBlock.FUELED), "Actual fuel interaction failed");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            player.getData(RecoveryAttachments.DOWNED).start(100, player.position());
            h.assertFalse(player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos)).consumesAction(), "Downed player was allowed to light a fire");
            player.getData(RecoveryAttachments.DOWNED).clear();
        });
        h.runAfterDelay(6, () -> {
            h.assertFalse(h.getBlockState(rel).getValue(StoneFireBlock.LIT) || progress(player).completed("warmth"), "Cancelled lighting earned a completion");
            player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
        });
        h.runAfterDelay(9, () -> {
            h.assertTrue(h.getBlockState(rel).getValue(StoneFireBlock.LIT), "Successful interaction did not light the fire");
            completed(h, player, "warmth"); h.succeed();
        });
    }
    private static ItemEntity pickup(GameTestHelper h, ServerPlayer player, ItemStack stack) {
        var entity = new ItemEntity(h.getLevel(), player.getX(), player.getY(), player.getZ(), stack);
        entity.setNoPickUpDelay(); h.getLevel().addFreshEntity(entity); entity.playerTouch(player); return entity;
    }
    private static void craft(GameTestHelper h, ServerPlayer player, String bench, String item) {
        var definition = WorkstationCatalog.get("arksurvivalreturns:" + bench);
        var chosen = definition.crafts().stream().filter(c -> c.variant().item().equals(item)).findFirst().orElseThrow();
        BlockPos pos = h.absolutePos(new BlockPos(64, 3, 66));
        h.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.getValue(Identifier.parse(definition.station())).defaultBlockState(), 3);
        player.setPos(Vec3.atBottomCenterOf(pos.north()));
        player.getInventory().clearContent(); int slot = 0;
        for (var cost : chosen.variant().cost().entrySet()) {
            Item ingredient = cost.getKey().startsWith("#") ? BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.parse(cost.getKey().substring(1)))).iterator().next().value()
                    : BuiltInRegistries.ITEM.getValue(Identifier.parse(cost.getKey()));
            player.getInventory().setItem(slot++, new ItemStack(ingredient, cost.getValue()));
        }
        ArkLevels.setLevel(player, 50);
        player.containerMenu = new WorkstationMenu(92, player.getInventory(), pos, definition.station());
        String key = "i:" + chosen.category().id() + "/" + (chosen.group() == null ? "" : chosen.group().group() + "/") + chosen.entry().family();
        h.assertTrue(WorkstationCrafting.craft(player, new WorkstationPayload.Craft(definition.station(), item, 1, key, chosen.entry().variants().indexOf(chosen.variant()), 92)), "Actual workstation craft failed: " + item);
    }
    private static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false); }
    private static TechTribeProgress progress(ServerPlayer player) { return TechProgressData.get(player.level()).progress(TechService.tribeOf(player)); }
    private static void completed(GameTestHelper h, ServerPlayer player, String node) {
        var file = dev.ftb.mods.ftbquests.quest.ServerQuestFile.getInstance();
        h.assertTrue(progress(player).completed(node), "Real gameplay did not complete Ark node " + node);
        h.assertTrue(file.getOrCreateTeamData(TechService.tribeOf(player)).isCompleted(file.getQuest(TechFtbBridge.questId(node))), "Real gameplay did not complete FTB node " + node);
    }
    /** Save through Minecraft's disk writers, evict the SavedData cache, then log the same UUID back in. */
    static void reload(GameTestHelper h) {
        var player = LevelGameTests.online(h, "ArkDiskReload");
        var world = h.getLevel();
        try {
            player.getInventory().clearContent(); StarterKitService.onLogin(player);
            ArkLevels.addXp(player, 17, ArkLevels.Source.COMMAND);
            Identifier recipe = Identifier.parse("arksurvivalreturns:restart_probe"); ArkLevels.crafted(player, recipe);
            TechService.unlock(player, "monkeys");
            long xp = ArkLevels.get(player).xp(); int level = ArkLevels.get(player).level();
            var kitBefore = StarterKitData.get(world); var techBefore = TechProgressData.get(world);
            var storage = world.getServer().overworld().getDataStorage();
            var io = world.getServer().getPlayerList().getPlayerIo(); io.save(player); storage.saveAndJoin();
            LevelGameTests.offline(player);
            var field = SavedDataStorage.class.getDeclaredField("cache"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var cache = (Map<SavedDataType<?>, Optional<SavedData>>) field.get(storage);
            cache.remove(StarterKitData.TYPE); cache.remove(TechProgressData.TYPE);
            h.assertTrue(StarterKitData.get(world) != kitBefore && TechProgressData.get(world) != techBefore, "Reload reused in-memory SavedData");
            h.assertTrue(StarterKitData.get(world).granted(player.getUUID()), "On-disk starter grant was lost");
            h.assertTrue(TechProgressData.get(world).progress(TechService.tribeOf(player)).completed("monkeys"), "On-disk tech completion was lost");
            var loaded = new net.neoforged.neoforge.common.util.FakePlayer(world, player.getGameProfile());
            var saved = io.load(new NameAndId(player.getUUID(), player.getGameProfile().name())).orElseThrow();
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), saved));
            h.assertTrue(ArkLevels.get(loaded).level() == level && ArkLevels.get(loaded).xp() == xp && ArkLevels.get(loaded).craftedRecipes().contains(recipe), "Player disk reload lost level, XP or rewarded recipes");
            LevelGameTests.online(loaded);
            try {
                StarterKitService.onLogin(loaded); ArkLevels.crafted(loaded, recipe); TechFtbBridge.pull(loaded);
                h.assertTrue(loaded.getInventory().countItem(dev.nez.arksurvivalreturns.registry.ModContent.FLINT_KNIFE.get()) == 1, "Login duplicated or lost a saved starter kit");
                h.assertTrue(ArkLevels.get(loaded).xp() == xp && ArkLevels.get(loaded).level() == level, "Login/repeated craft duplicated XP");
                completed(h, loaded, "monkeys");
                TechService.reset(loaded); storage.saveAndJoin(); cache.remove(TechProgressData.TYPE);
                TechFtbBridge.pull(loaded);
                h.assertFalse(progress(loaded).completed("monkeys"), "Disk reload resurrected reset progress");
            } finally { LevelGameTests.offline(loaded); }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        finally { if (world.getServer().getPlayerList().getPlayer(player.getUUID()) == player) LevelGameTests.offline(player); }
        h.succeed();
    }
    private ProgressionGameTests() {}
}
