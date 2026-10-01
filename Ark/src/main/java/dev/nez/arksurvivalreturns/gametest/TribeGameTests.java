package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.tribe.TribePermission;
import dev.nez.arksurvivalreturns.feature.tribe.TribePermissions;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Tribe permission rules: the owner always passes, party membership gates everyone else. */
final class TribeGameTests {
    static void permissions(GameTestHelper h) {
        var world = h.getLevel();
        var creature = ModContent.CREATURES.get(Species.PARASAUR).get().create(world, EntitySpawnReason.COMMAND);
        creature.setNoAi(true);
        creature.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 8))));
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var outsider = h.makeMockPlayer(GameType.SURVIVAL);
        TamingService.of(creature).setOwner(owner.getUUID());

        h.assertTrue(TribeService.canRide(creature, owner), "Owner lost riding permission");
        h.assertTrue(TribeService.canAccessCargo(creature, owner), "Owner lost cargo access");
        h.assertTrue(TribeService.canCommand(creature, owner), "Owner lost order permission");
        h.assertFalse(TribeService.canRide(creature, outsider), "Stranger gained riding permission");
        h.assertFalse(TribeService.canAccessCargo(creature, outsider), "Stranger gained cargo access");
        h.assertFalse(TribeService.isTribeMember(creature, outsider), "Stranger counted as a tribe member");

        // A flag alone cannot grant access: membership in the owner's party is also required.
        // Revoking a default-on flag stores an explicit deviation; restoring the default drops it.
        var permissions = TribePermissions.get(world);
        int defaults = TribeService.defaultMask();
        h.assertTrue(permissions.set(outsider.getUUID(), TribePermission.RIDE, false, defaults), "Revoked flag was not stored");
        h.assertFalse(TribeService.canRide(creature, outsider), "Flag bypassed party membership");
        h.assertTrue(permissions.customized(outsider.getUUID()), "Stored flag was lost");

        // Persisted permission data survives a serialization round trip.
        var saved = TribePermissions.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, permissions).getOrThrow();
        var restored = TribePermissions.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
        h.assertTrue(restored.customized(outsider.getUUID()), "Custom flag lost on reload");
        h.assertFalse(restored.allows(outsider.getUUID(), TribePermission.RIDE, defaults), "Reload restored the revoked permission");

        // Granting a non-default flag is stored too, and resetting clears the entry.
        h.assertTrue(permissions.set(outsider.getUUID(), TribePermission.BREEDING, true, defaults), "Granted flag was not stored");
        h.assertTrue(permissions.allows(outsider.getUUID(), TribePermission.BREEDING, defaults), "Granted flag was lost");
        h.assertTrue(permissions.clear(outsider.getUUID()), "Custom flag was not cleared");
        h.assertFalse(permissions.customized(outsider.getUUID()), "Cleared flag stayed customized");

        // An absent entry follows the supplied default mask.
        h.assertTrue(permissions.allows(owner.getUUID(), TribePermission.RIDE, defaults),
                "Absent entry ignored the default mask");
        h.assertFalse(permissions.allows(owner.getUUID(), TribePermission.RIDE, 0),
                "Absent entry ignored an empty default mask");

        // Editing flags needs a party you own: a solo player, or a member editing themselves, is refused.
        h.assertFalse(TribeService.ownsTeamOf(outsider, outsider.getUUID()), "A player without a party edited their own flags");
        h.assertFalse(TribeService.ownsTeamOf(outsider, owner.getUUID()), "A stranger edited another player's flags");

        // Ownership itself is untouched by the permission layer.
        h.assertTrue(owner.getUUID().equals(TamingService.of(creature).owner()), "Tribe checks changed the owner");
        creature.discard();
        h.succeed();
    }

    static void partyFlow(GameTestHelper h) {
        var world = h.getLevel();
        var owner = LevelGameTests.online(h, "ArkPartyOwner");
        var member = LevelGameTests.online(h, "ArkPartyMember");
        var outsider = LevelGameTests.online(h, "ArkPartyStranger");
        var manager = (dev.ftb.mods.ftbteams.data.TeamManagerImpl) dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
        dev.ftb.mods.ftbteams.data.PartyTeam party = null;
        boolean ownerOnline = true;
        var creature = ModContent.CREATURES.get(Species.PARASAUR).get().create(world, EntitySpawnReason.COMMAND);
        creature.setNoAi(true); TamingService.of(creature).setOwner(owner.getUUID());
        try {
            party = (dev.ftb.mods.ftbteams.data.PartyTeam) manager.createPartyTeam(owner, "ArkTest" + owner.getUUID().toString().substring(0, 8), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
            party.invite(owner, java.util.List.of(new net.minecraft.server.players.NameAndId(member.getUUID(), member.getGameProfile().name())));
            party.join(member);
            h.assertTrue(TribeService.isTribeMember(creature, member), "Actual party join did not grant membership");
            h.assertTrue(TribeService.ownsTeamOf(owner, member.getUUID()), "Party owner cannot edit a member's permissions");
            h.assertFalse(TribeService.ownsTeamOf(member, member.getUUID()), "Member can edit their own party permissions");
            var flags = TribePermissions.get(world);
            int defaults = TribeService.defaultMask();
            for (var permission : TribePermission.values()) {
                flags.set(member.getUUID(), permission, true, defaults);
                h.assertTrue(permitted(creature, member, permission), "Party grant failed: " + permission);
                h.assertFalse(permitted(creature, outsider, permission), "Party grant reached an outsider: " + permission);
                flags.set(member.getUUID(), permission, false, defaults);
                h.assertFalse(permitted(creature, member, permission), "Party revocation failed: " + permission);
                h.assertTrue(permitted(creature, owner, permission), "Member revocation removed owner access: " + permission);
                flags.set(member.getUUID(), permission, true, defaults);
            }
            var file = dev.ftb.mods.ftbquests.quest.ServerQuestFile.getInstance();
            h.assertTrue(dev.nez.arksurvivalreturns.feature.tech.TechService.unlock(owner, "monkeys"), "Party tech grant failed");
            dev.nez.arksurvivalreturns.feature.tech.TechFtbBridge.pull(member);
            var data = dev.nez.arksurvivalreturns.feature.tech.TechProgressData.get(world);
            h.assertTrue(data.progress(dev.nez.arksurvivalreturns.feature.tech.TechService.tribeOf(member)).completed("monkeys"), "Party member missed shared completion");
            h.assertFalse(data.progress(dev.nez.arksurvivalreturns.feature.tech.TechService.tribeOf(outsider)).completed("monkeys"), "Shared quest reached outsider");
            dev.nez.arksurvivalreturns.feature.tech.TechService.reset(member);
            dev.nez.arksurvivalreturns.feature.tech.TechFtbBridge.pull(owner);
            h.assertFalse(data.progress(party.getId()).completed("monkeys"), "Party reset was resurrected from the owner's cache");
            for (String id : java.util.List.of("monkeys", "fight", "ride")) dev.nez.arksurvivalreturns.feature.tech.TechService.unlock(owner, id);
            LevelGameTests.offline(owner); ownerOnline = false;
            var tame = ModContent.CREATURES.get(Species.LYSTROSAURUS).get().create(world, EntitySpawnReason.COMMAND);
            tame.setNoAi(true); tame.initializeLevel(3);
            dev.nez.arksurvivalreturns.feature.taming.TorporService.tickEntity(tame);
            TamingService.of(tame).setProgress(99F); TamingService.of(tame).setHunger(100);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(tame.profile().preferred().getFirst().get(), 8));
            h.assertTrue(TamingService.feedByHand(tame, owner, net.minecraft.world.InteractionHand.MAIN_HAND)
                    == dev.nez.arksurvivalreturns.feature.taming.TamingFeedback.Result.ACCEPTED && tame.isTamed(), "Offline-attributed tame did not complete");
            var quest = file.getQuest(dev.nez.arksurvivalreturns.feature.tech.TechFtbBridge.questId("companions"));
            h.assertTrue(data.progress(party.getId()).completed("companions") && file.getOrCreateTeamData(party.getId()).isCompleted(quest),
                    "Offline owner's gameplay completion did not reach the shared FTB mirror");
            LevelGameTests.online(owner); ownerOnline = true;
            dev.nez.arksurvivalreturns.feature.tech.TechFtbBridge.pull(owner);
            h.assertTrue(data.progress(party.getId()).completed("companions"), "Login lost an offline completion");
            tame.discard();
            party.leave(member.getUUID());
            for (var permission : TribePermission.values()) h.assertFalse(permitted(creature, member, permission), "Leaving party retained " + permission);
            h.assertFalse(TribeService.isTribeMember(creature, member), "Party departure did not invalidate membership");
            flags.clear(member.getUUID());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
        finally {
            if (party != null) {
                for (var player : java.util.List.of(owner, member)) {
                    var personal = manager.getPersonalTeamForPlayerID(player.getUUID()); personal.setEffectiveTeam(personal);
                }
                manager.getTeamMap().remove(party.getId()); manager.markDirty();
            }
            if (ownerOnline) LevelGameTests.offline(owner);
            LevelGameTests.offline(member); LevelGameTests.offline(outsider); creature.discard();
        }
        h.succeed();
    }
    private static boolean permitted(dev.nez.arksurvivalreturns.feature.creature.CreatureEntity creature, net.minecraft.world.entity.player.Player player, TribePermission permission) {
        return switch (permission) {
            case RIDE -> TribeService.canRide(creature, player);
            case CARGO -> TribeService.canAccessCargo(creature, player);
            case COMMANDS -> TribeService.canCommand(creature, player);
            case BREEDING -> TribeService.canBreed(creature, player);
            case WORK -> TribeService.canWork(creature, player);
        };
    }
    private TribeGameTests() {}
}
