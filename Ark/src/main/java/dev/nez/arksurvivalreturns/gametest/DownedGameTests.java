package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.recovery.DownedHandler;
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

    /**
     * An expired rescue window and /kill on a downed player each end in one ordinary death. The finishing
     * blow used to re-enter the downed branch, which absorbed it and expired again until the stack overflowed.
     */
    static void bleedOut(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer victim = vulnerable(world, "ArkBleedOutVictim");
        var state = victim.getData(RecoveryAttachments.DOWNED);
        state.start(100, victim.position());
        victim.setHealth(1.0f);

        // An ordinary hit while downed is absorbed into the rescue window.
        h.assertFalse(victim.hurtServer(world, world.damageSources().generic(), 5f), "A downed player took an ordinary hit");
        h.assertTrue(victim.getHealth() == 1.0f, "The absorbed hit still cost health");
        int left = DownedPolicy.bleedOut(100, 5f, Config.DOWNED_BLEED_FACTOR.get());
        h.assertTrue(state.downed() && state.ticksLeft() == left, "The hit did not shorten the window: " + state.ticksLeft());

        // The window runs out: one finishing blow, no recursion.
        for (int tick = 0; tick <= left && state.downed(); tick++) DownedHandler.step(victim);
        h.assertFalse(state.downed(), "The rescue window never expired");
        h.assertTrue(victim.isDeadOrDying(), "An expired rescue window did not kill the player");

        // /kill is fatal for a downed player as well.
        FakePlayer killed = vulnerable(world, "ArkBleedOutKilled");
        killed.getData(RecoveryAttachments.DOWNED).start(100, killed.position());
        killed.setHealth(1.0f);
        killed.hurtServer(world, world.damageSources().genericKill(), Float.MAX_VALUE);
        h.assertTrue(killed.isDeadOrDying(), "/kill on a downed player was absorbed");
        h.succeed();
    }

    /** Fake players refuse ordinary damage by default; the bleed-out checks need it to land. */
    private static FakePlayer vulnerable(ServerLevel world, String name) {
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), name));
        player.setInvulnerable(false);
        player.connection.markClientLoaded();
        return player;
    }

    private DownedGameTests() {}
}
