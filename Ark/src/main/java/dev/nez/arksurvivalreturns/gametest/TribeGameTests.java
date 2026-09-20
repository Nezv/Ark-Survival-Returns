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

        // Ownership itself is untouched by the permission layer.
        h.assertTrue(owner.getUUID().equals(TamingService.of(creature).owner()), "Tribe checks changed the owner");
        creature.discard();
        h.succeed();
    }

    private TribeGameTests() {}
}
