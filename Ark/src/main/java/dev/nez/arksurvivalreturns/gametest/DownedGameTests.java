package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.recovery.DownedPolicy;
import dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Downed state rules and the fiber-bandage revive. */
final class DownedGameTests {
    static void downedRevive(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer victim = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "ArkDownedVictim"));
        FakePlayer reviver = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "ArkDownedReviver"));
        victim.getInventory().clearContent();
        reviver.getInventory().clearContent();
        reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.FIBER_BANDAGE.get(), 2));

        var state = victim.getData(RecoveryAttachments.DOWNED);
        h.assertFalse(state.downed(), "A fresh player should not be downed");
        state.start(Config.DOWNED_WINDOW.get(), victim.position());
        h.assertTrue(state.downed(), "The downed state did not start");

        // The policy matrix: ordinary hits only down, hazards and overkill kill.
        h.assertFalse(DownedPolicy.lethal(4f, 20f, 1.5, false, false), "A normal hit should only down");
        h.assertTrue(DownedPolicy.lethal(40f, 20f, 1.5, false, false), "An overkill hit should kill");
        h.assertTrue(DownedPolicy.lethal(1f, 20f, 1.5, true, false), "Void damage should kill");
        h.assertTrue(DownedPolicy.lethal(1f, 20f, 1.5, false, true), "Lava damage should kill");
        h.assertTrue(DownedPolicy.bleedOut(100, 10f, 1.0) == 90, "Damage must shorten the rescue window");
        h.assertTrue(DownedPolicy.bleedOut(5, 100f, 1.0) == 0, "The rescue window must bottom out at zero");

        // The tribe gate refuses a stranger.
        var bandage = reviver.getItemInHand(InteractionHand.MAIN_HAND);
        var refused = ModContent.FIBER_BANDAGE.get().interactLivingEntity(bandage, reviver, victim, InteractionHand.MAIN_HAND);
        h.assertTrue(refused == InteractionResult.FAIL, "A stranger revived outside the tribe gate");
        h.assertTrue(state.downed(), "The refused revive cleared the downed state");

        // With the gate relaxed the bandage revives at the configured fraction.
        Config.DOWNED_REQUIRE_TRIBE.set(false);
        try {
            var revived = ModContent.FIBER_BANDAGE.get().interactLivingEntity(bandage, reviver, victim, InteractionHand.MAIN_HAND);
            h.assertTrue(revived == InteractionResult.SUCCESS_SERVER, "The bandage did not revive the player");
        } finally {
            Config.DOWNED_REQUIRE_TRIBE.set(true);
        }
        h.assertFalse(state.downed(), "The revive did not clear the downed state");
        h.assertTrue(victim.getHealth() >= victim.getMaxHealth() * 0.29f, "The revive health is below the fraction");
        h.assertTrue(reviver.getInventory().countItem(ModContent.FIBER_BANDAGE.get()) == 1, "The bandage was not consumed");
        // The journal discovery is granted through TamingService.discovery, but NeoForge refuses
        // advancement grants for FakePlayers, so the award itself is checked by the manual playtest.
        h.succeed();
    }

    private DownedGameTests() {}
}
