package dev.nez.arksurvivalreturns.gametest;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.levels.LevelAttachments;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.taming.TamingFeedback;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.feature.tech.TechEvent;
import dev.nez.arksurvivalreturns.feature.tech.TechProgressData;
import dev.nez.arksurvivalreturns.feature.tech.TechService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Real attachment cloning, recipe callbacks, gameplay awards, commands and FTB party boundaries. */
final class LevelGameTests {
    private static ServerPlayer player(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    static void levels(GameTestHelper h) {
        var player = player(h, "ArkLevel");
        h.assertTrue(ArkLevels.get(player).level() == 0 && ArkLevels.requires(player, 0), "New player is not level zero");
        player.experienceLevel = 500;
        h.assertFalse(ArkLevels.requires(player, 1), "Vanilla XP bypassed the permanent gate");
        long cost = ArkLevels.xpForNextLevel(0);
        ArkLevels.addXp(player, cost - 1, ArkLevels.Source.COMMAND);
        h.assertFalse(ArkLevels.requires(player, 1), "Level advanced before its threshold");
        ArkLevels.addXp(player, 1, ArkLevels.Source.COMMAND);
        h.assertTrue(ArkLevels.get(player).level() == 1 && ArkLevels.get(player).xp() == 0, "Exact threshold lost XP");
        player.experienceLevel = 0;
        h.assertTrue(ArkLevels.requires(player, 1), "Vanilla XP spending revoked the Ark level");
        ArkLevels.addXp(player, ArkLevels.xpForNextLevel(1) + ArkLevels.xpForNextLevel(2) + 7, ArkLevels.Source.COMMAND);
        h.assertTrue(ArkLevels.get(player).level() == 3 && ArkLevels.get(player).xp() == 7, "Multi-level award lost remainder");
        ArkLevels.addXp(player, -10, ArkLevels.Source.COMMAND);
        h.assertTrue(ArkLevels.get(player).xp() == 7, "Negative XP reduced permanent progress");
        ArkLevels.addXp(player, Long.MAX_VALUE, ArkLevels.Source.COMMAND);
        h.assertTrue(ArkLevels.get(player).level() == Config.PLAYER_LEVEL_CAP.get() && ArkLevels.get(player).xp() == 0,
                "Huge XP overflowed or bypassed the cap");
        ArkLevels.addXp(player, Long.MAX_VALUE, ArkLevels.Source.COMMAND);
        h.assertTrue(ArkLevels.get(player).xp() == 0, "Capped XP accumulated");
        h.succeed();
    }

    static void persistence(GameTestHelper h) {
        var original = player(h, "ArkLevelSave");
        ArkLevels.setLevel(original, 7);
        ArkLevels.addXp(original, 23, ArkLevels.Source.COMMAND);
        var recipe = ArkSurvivalReturns.id("level_test/recipe");
        ArkLevels.crafted(original, recipe);
        long xp = ArkLevels.get(original).xp();
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        original.saveWithoutId(output);
        var loaded = player(h, "ArkLevelLoad");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(ArkLevels.get(loaded).level() == 7 && ArkLevels.get(loaded).xp() == xp
                && ArkLevels.get(loaded).craftedRecipes().contains(recipe), "Player save lost progress or recipe history");
        for (boolean death : List.of(true, false)) {
            var clone = player(h, "ArkLevelClone");
            clone.copyAttachmentsFrom(original, death);
            h.assertTrue(ArkLevels.get(clone).level() == 7 && ArkLevels.get(clone).xp() == xp,
                    "Respawn/End return lost permanent progress: death=" + death);
            ArkLevels.crafted(clone, recipe);
            h.assertTrue(ArkLevels.get(clone).xp() == xp, "Respawn rewarded the same recipe again");
            ArkLevels.crafted(clone, ArkSurvivalReturns.id("level_test/clone_only"));
            h.assertFalse(ArkLevels.get(original).craftedRecipes().contains(ArkSurvivalReturns.id("level_test/clone_only")),
                    "Cloning aliased the recipe set");
        }
        h.assertTrue(loaded.hasData(LevelAttachments.PROGRESS), "Progress was not saved as a player attachment");
        h.succeed();
    }

    static void sources(GameTestHelper h) {
        var player = online(h, "ArkLevelSources");
        player.getInventory().clearContent();
        var world = h.getLevel();
        try {
            var victim = ModContent.CREATURES.get(Species.LYSTROSAURUS).get().create(world, EntitySpawnReason.COMMAND);
            victim.setNoAi(true);
            victim.initializeLevel(3);
            victim.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 2, 5))));
            world.addFreshEntity(victim);
            victim.hurtServer(world, world.damageSources().playerAttack(player), Float.MAX_VALUE);
            h.assertTrue(total(player) == 3L * Config.PLAYER_XP_KILL.get(), "Creature kill did not award level-scaled XP");
            victim.discard();

            var tame = ModContent.CREATURES.get(Species.LYSTROSAURUS).get().create(world, EntitySpawnReason.COMMAND);
            tame.setNoAi(true);
            tame.initializeLevel(3);
            TorporService.tickEntity(tame);
            var state = TamingService.of(tame);
            state.setProgress(99F);
            state.setHunger(100);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tame.profile().preferred().getFirst().get(), 8));
            long before = total(player);
            h.assertTrue(TamingService.feedByHand(tame, player, InteractionHand.MAIN_HAND) == TamingFeedback.Result.ACCEPTED
                    && state.tamed(), "Test tame did not complete");
            h.assertTrue(total(player) - before == 3L * Config.PLAYER_XP_TAME.get(), "Tame did not award 10 XP per level");
            before = total(player);
            TamingService.feedByHand(tame, player, InteractionHand.MAIN_HAND);
            h.assertTrue(total(player) == before, "Feeding a finished tame awarded XP twice");
            tame.discard();
            player.getInventory().clearContent();

            var recipe = world.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                    ArkSurvivalReturns.id("fiber_bandage"))).orElseThrow();
            player.triggerRecipeCrafted(recipe, List.of());
            h.assertTrue(total(player) - before == Config.PLAYER_XP_FIRST_CRAFT.get(), "Actual recipe callback did not award XP");
            before = total(player);
            player.triggerRecipeCrafted(recipe, List.of());
            h.assertTrue(total(player) == before, "Repeated recipe callback awarded XP twice");
            ArkLevels.crafted(player, ArkSurvivalReturns.id("level_test/alternate_bandage_recipe"));
            h.assertTrue(total(player) - before == Config.PLAYER_XP_FIRST_CRAFT.get(), "Distinct recipe failed to earn its own reward");

            before = total(player);
            var progress = TechProgressData.get(world).progress(TechService.tribeOf(player));
            int completed = progress.completedCount();
            TechService.notify(player, TechEvent.obtain(player, new ItemStack(PrimitiveContent.ROCK.get())));
            int added = TechProgressData.get(world).progress(TechService.tribeOf(player)).completedCount() - completed;
            h.assertTrue(added > 0 && total(player) - before == (long) added * Config.PLAYER_XP_TECH_NODE.get(),
                    "New tech completions did not award configured XP");
            before = total(player);
            TechService.notify(player, TechEvent.obtain(player, new ItemStack(PrimitiveContent.ROCK.get())));
            h.assertTrue(total(player) == before, "Already completed tech node awarded XP again");

            var boss = ModContent.GUARDIAN_GIGANOTOSAURUS.get().create(world, EntitySpawnReason.COMMAND);
            boss.setNoAi(true);
            boss.initializeGuardian("level_test", h.absolutePos(new BlockPos(8, 2, 8)), 100, 1, 0);
            world.addFreshEntity(boss);
            boss.hurtServer(world, world.damageSources().playerAttack(player), Float.MAX_VALUE);
            h.assertTrue(total(player) - before == Config.PLAYER_XP_BOSS.get(), "Boss XP was absent or also counted as a creature kill");
            boss.discard();
        } finally {
            offline(player);
        }
        h.succeed();
    }

    static void share(GameTestHelper h) {
        var actor = online(h, "ArkLevelActor");
        var near = online(h, "ArkLevelNear");
        var far = online(h, "ArkLevelFar");
        var stranger = online(h, "ArkLevelStranger");
        var manager = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        PartyTeam party = null;
        try {
            party = (PartyTeam) manager.createPartyTeam(actor, "LevelTest" + actor.getUUID().toString().substring(0, 8), "", Color4I.WHITE);
            for (var member : List.of(near, far)) {
                party.addMember(member.getUUID(), TeamRank.MEMBER);
                manager.getPersonalTeamForPlayerID(member.getUUID()).setEffectiveTeam(party);
            }
            actor.setPos(0, 200, 0);
            near.setPos(Config.PLAYER_XP_TRIBE_RADIUS.get(), 200, 0);
            far.setPos(Config.PLAYER_XP_TRIBE_RADIUS.get() + 0.01, 200, 0);
            stranger.setPos(0, 200, 0);
            ArkLevels.addXp(actor, 400, ArkLevels.Source.KILL);
            h.assertTrue(total(actor) == 400, "Party sharing reduced the actor's earned XP");
            h.assertTrue(total(near) == (long) (400 * Config.PLAYER_XP_TRIBE_SHARE.get()), "Member at the radius missed the bonus");
            h.assertTrue(total(far) == 0 && total(stranger) == 0, "Share reached a distant member or a stranger");
            long bonus = total(near);
            ArkLevels.addXp(actor, 100, ArkLevels.Source.COMMAND);
            ArkLevels.addXp(actor, 100, ArkLevels.Source.TRIBE);
            h.assertTrue(total(near) == bonus, "Administrative or shared XP recursively shared");
            far.setPos(0, 200, 0);
            ArkLevels.addXp(actor, 400, ArkLevels.Source.TAME);
            h.assertTrue(total(near) == 2 * bonus && total(far) == bonus, "Each nearby party member must receive the bonus once");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException(e);
        } finally {
            if (party != null) {
                for (var member : List.of(actor, near, far)) {
                    var personal = manager.getPersonalTeamForPlayerID(member.getUUID());
                    personal.setEffectiveTeam(personal);
                }
                manager.getTeamMap().remove(party.getId());
                manager.markDirty();
            }
            for (var member : List.of(actor, near, far, stranger)) offline(member);
        }
        h.succeed();
    }

    static void commands(GameTestHelper h) {
        var target = online(h, "ArkLevelCommands");
        var server = h.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var operator = server.createCommandSourceStack();
        try {
            String name = target.getGameProfile().name();
            h.assertTrue(dispatcher.execute("arklevel set " + name + " 4", operator) == 1 && ArkLevels.get(target).level() == 4,
                    "Operator set did not set the permanent level");
            dispatcher.execute("arklevel addxp " + name + " 17", operator);
            h.assertTrue(ArkLevels.get(target).xp() == 17, "Operator addxp did not award XP");
            h.assertTrue(dispatcher.execute("arklevel get " + name, operator) == 1, "Operator get failed");
            h.assertTrue(dispatcher.execute("arklevel set " + name + " " + (Config.PLAYER_LEVEL_CAP.get() + 1), operator) == 0,
                    "Operator set accepted an invalid level");
            h.assertTrue(ArkLevels.get(target).level() == 4, "Invalid command changed progress");
            h.assertFalse(dispatcher.getRoot().getChild("arklevel").canUse(target.createCommandSourceStack()),
                    "Non-operator can use arklevel");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException(e);
        } finally { offline(target); }
        h.succeed();
    }

    private static long total(ServerPlayer player) {
        var progress = ArkLevels.get(player);
        long total = progress.xp();
        for (int level = 0; level < progress.level(); level++) total += ArkLevels.xpForNextLevel(level);
        return total;
    }

    // FakePlayer has a no-op packet listener. The vanilla GameTest mock login does not negotiate
    // NeoForge custom payloads. Register fake players in the online collections so the real FTB
    // APIs, UUID attribution and command selectors run without bypassing their production code.
    @SuppressWarnings("unchecked")
    private static ServerPlayer online(GameTestHelper h, String name) {
        var player = player(h, name);
        var list = h.getLevel().getServer().getPlayerList();
        try {
            var players = PlayerList.class.getDeclaredField("players");
            var byUuid = PlayerList.class.getDeclaredField("playersByUUID");
            players.setAccessible(true);
            byUuid.setAccessible(true);
            ((List<ServerPlayer>) players.get(list)).add(player);
            ((java.util.Map<UUID, ServerPlayer>) byUuid.get(list)).put(player.getUUID(), player);
            ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(player, player.getUUID(), name);
            return player;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    @SuppressWarnings("unchecked")
    private static void offline(ServerPlayer player) {
        var list = player.level().getServer().getPlayerList();
        try {
            ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedOut(player);
            var players = PlayerList.class.getDeclaredField("players");
            var byUuid = PlayerList.class.getDeclaredField("playersByUUID");
            players.setAccessible(true);
            byUuid.setAccessible(true);
            ((List<ServerPlayer>) players.get(list)).remove(player);
            ((java.util.Map<UUID, ServerPlayer>) byUuid.get(list)).remove(player.getUUID());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private LevelGameTests() {}
}
