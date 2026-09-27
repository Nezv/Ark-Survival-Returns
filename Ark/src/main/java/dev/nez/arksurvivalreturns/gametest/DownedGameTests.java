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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
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

    /**
     * Going down is decided on the damage that would really land. A raw hit that armor brings under the
     * player's health, or one their absorption hearts soak, used to down them anyway.
     */
    static void mitigated(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        var hit = world.damageSources().cactus();   // armor applies, no difficulty scaling

        // Full diamond: a raw 22 against 20 health lands as about 9, so the player stays up.
        FakePlayer armored = vulnerable(world, "ArkDownedArmored");
        armored.getAttribute(Attributes.ARMOR).setBaseValue(20);
        armored.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        armored.setHealth(20f);
        h.assertTrue(armored.hurtServer(world, hit, 22f), "The armored hit did not land");
        h.assertFalse(armored.getData(RecoveryAttachments.DOWNED).downed(), "Damage armor absorbed still downed the player");
        h.assertTrue(armored.getHealth() > 1f && armored.getHealth() < 20f, "The armored hit left " + armored.getHealth());

        // Absorption soaks first: 7 against 5 health and 4 absorption is survivable.
        FakePlayer soaked = vulnerable(world, "ArkDownedSoaked");
        soaked.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);   // absorption is capped by this, 0 without the effect
        soaked.setHealth(5f);
        soaked.setAbsorptionAmount(4f);
        soaked.hurtServer(world, hit, 7f);
        var state = soaked.getData(RecoveryAttachments.DOWNED);
        h.assertFalse(state.downed(), "A hit absorption covers downed the player");
        h.assertTrue(Math.abs(soaked.getHealth() - 2f) < 0.01f && soaked.getAbsorptionAmount() == 0f,
                "Absorption did not soak first: " + soaked.getHealth() + " health, " + soaked.getAbsorptionAmount() + " absorption");

        // Past health and absorption the player goes down: absorption is spent and health ends at exactly 1.
        soaked.invulnerableTime = 0;
        soaked.setAbsorptionAmount(2f);
        soaked.hurtServer(world, hit, 6f);
        h.assertTrue(state.downed(), "A hit past health and absorption did not down the player");
        h.assertTrue(soaked.getHealth() == 1f && soaked.getAbsorptionAmount() == 0f && !soaked.isDeadOrDying(),
                "Going down left " + soaked.getHealth() + " health, " + soaked.getAbsorptionAmount() + " absorption");
        state.clear();

        if (ModList.get().isLoaded("curios")) AmberProbe.check(h, world);
        h.succeed();
    }

    /** Kept apart so Curios classes load only when Curios is installed. */
    private static final class AmberProbe {
        /** A survivable hit keeps the amulet's charge; a fatal one cracks it instead of downing the player. */
        static void check(GameTestHelper h, ServerLevel world) {
            var hit = world.damageSources().cactus();
            FakePlayer wearer = vulnerable(world, "ArkAmberWearer");
            var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(wearer).orElseThrow();
            curios.reset();
            var accessory = dev.nez.arksurvivalreturns.feature.accessory.Accessory.AMBER_AMULET;
            curios.setEquippedCurio(accessory.slot.id(), 0,
                    new ItemStack(dev.nez.arksurvivalreturns.feature.accessory.AccessoryContent.ITEMS.get(accessory).get()));
            dev.nez.arksurvivalreturns.feature.accessory.Worn.invalidate(wearer);
            h.assertTrue(dev.nez.arksurvivalreturns.feature.accessory.Worn.has(wearer, accessory), "The amulet is not worn");
            var recharge = dev.nez.arksurvivalreturns.feature.accessory.AccessoryContent.RECHARGE.get();

            wearer.getAttribute(Attributes.ARMOR).setBaseValue(20);
            wearer.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
            wearer.setHealth(20f);
            wearer.hurtServer(world, hit, 22f);
            var amulet = dev.nez.arksurvivalreturns.feature.accessory.Worn.stack(wearer, accessory);
            h.assertFalse(amulet.has(recharge), "A hit armor made survivable spent the amulet");

            wearer.invulnerableTime = 0;
            wearer.setHealth(4f);
            wearer.hurtServer(world, hit, 30f);
            h.assertTrue(amulet.has(recharge), "A fatal hit did not crack the amulet");
            h.assertFalse(wearer.getData(RecoveryAttachments.DOWNED).downed(), "The amulet's save still downed the player");
            h.assertTrue(wearer.getHealth() >= 8f, "The amulet did not restore 40% health: " + wearer.getHealth());
            curios.reset();
        }
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
