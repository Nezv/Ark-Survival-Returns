package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.mass.MassRules;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Player carried-mass accounting, the warning-only band, overload effects and the off switch. */
final class MassGameTests {
    static void load(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "ArkMassCarrier"));
        player.getInventory().clearContent();
        int chunks = world.getChunkSource().getLoadedChunksCount();

        MassService.refresh(player);
        h.assertTrue(MassService.load(player).mass() < 0.001, "An empty inventory should carry no load");
        h.assertTrue(MassService.load(player).band() == MassRules.Band.NORMAL, "An empty load must be normal");

        player.getInventory().add(new ItemStack(Items.STONE, 64));
        MassService.refresh(player);
        double stone = MassService.load(player).mass();
        h.assertTrue(Math.abs(stone - 64.0) < 0.1, "A stack of stone should weigh 64 units, got " + stone);
        h.assertTrue(MassService.load(player).band() == MassRules.Band.NORMAL, "64/100 is below the warning band");

        player.getInventory().add(new ItemStack(Items.RAW_IRON, 8));
        MassService.refresh(player);
        var warn = MassService.load(player);
        h.assertTrue(Math.abs(warn.mass() - 80.0) < 0.1, "Raw iron should count two units each, got " + warn.mass());
        h.assertTrue(warn.band() == MassRules.Band.WARN, "80/100 should warn");
        h.assertTrue(MassRules.sprintAllowed(warn.ratio()), "The warning band must not deny sprint");
        h.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MassService.OVERLOAD_MODIFIER) == null,
                "The warning band must not slow movement");

        player.getInventory().add(new ItemStack(Items.RAW_IRON, 16));
        MassService.refresh(player);
        var overload = MassService.load(player);
        h.assertTrue(overload.band() == MassRules.Band.OVERLOAD, "112/100 should be overloaded");
        h.assertTrue(!MassRules.sprintAllowed(overload.ratio()), "Overload must deny sprint");
        var modifier = player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MassService.OVERLOAD_MODIFIER);
        h.assertTrue(modifier != null, "Overload must slow movement");
        h.assertTrue(modifier.amount() < 0.0, "The overload modifier must reduce speed");

        player.getInventory().add(new ItemStack(Items.RAW_IRON, 32));
        MassService.refresh(player);
        h.assertTrue(MassService.load(player).band() == MassRules.Band.HEAVY, "160/100 should be the heavy band");
        h.assertTrue(Math.abs(MassRules.speedFactor(MassService.load(player).ratio()) - Config.MASS_SPEED_FLOOR.get()) < 0.001,
                "The heavy band must sit at the speed floor");

        player.getInventory().clearContent();
        MassService.refresh(player);
        h.assertTrue(MassService.load(player).band() == MassRules.Band.NORMAL, "Emptying the inventory must clear the band");
        h.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MassService.OVERLOAD_MODIFIER) == null,
                "Emptying the inventory must remove the modifier");

        Config.MASS_PRESET.set(MassRules.Preset.OFF);
        try {
            player.getInventory().add(new ItemStack(Items.STONE, 64));
            MassService.refresh(player);
            h.assertTrue(MassService.load(player).mass() < 0.001, "The off preset must disable mass");
            h.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MassService.OVERLOAD_MODIFIER) == null,
                    "The off preset must remove the modifier");
        } finally {
            Config.MASS_PRESET.set(MassRules.Preset.STANDARD);
            player.getInventory().clearContent();
            MassService.refresh(player);
        }

        h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks, "Mass accounting must not load chunks");
        MassService.clear(player);
        h.succeed();
    }

    private MassGameTests() {}
}
