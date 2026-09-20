package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.farm.BerryBushBlock;
import dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity;
import dev.nez.arksurvivalreturns.feature.farm.TroughBlockEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Homestead stations: trough feeding, drying batches, berry harvest and chunk safety. */
final class FarmGameTests {
    static void batch(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ArkFarmer"));
        owner.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 8))));
        level.addFreshEntity(owner);

        CreatureEntity trike = ModContent.CREATURES.get(Species.TRICERATOPS).get().create(level, EntitySpawnReason.COMMAND);
        trike.setNoAi(true);
        trike.setPos(owner.position());
        TamingService.of(trike).setOwner(owner.getUUID());
        trike.applyTameState();
        level.addFreshEntity(trike);

        // --- trough: feeds hungry tames, never wild creatures, and never loads chunks ---
        BlockPos troughRel = new BlockPos(9, 3, 8);
        h.setBlock(troughRel, ModContent.TROUGH.get().defaultBlockState());
        TroughBlockEntity trough = (TroughBlockEntity) level.getBlockEntity(h.absolutePos(troughRel));
        h.assertTrue(trough != null, "The trough block entity is missing");
        int chunks = level.getChunkSource().getLoadedChunksCount();

        TamingService.of(trike).setHunger(90.0);
        trike.setHealth(10.0f);
        h.assertTrue(trough.insert(new ItemStack(Items.COOKED_BEEF, 8)) == 8, "The trough must accept food");
        h.assertTrue(trough.feedNearby(level) == 1, "The trough must feed the hungry tame");
        h.assertTrue(TamingService.of(trike).hunger() < 90.0, "Feeding must reduce hunger");
        h.assertTrue(trike.getHealth() > 10.0f, "Feeding must heal the tame");
        h.assertTrue(trough.stored().getCount() == 7, "Exactly one item is consumed per feeding");

        // A wild creature is never fed, even with trough food and high hunger.
        TamingService.of(trike).setHunger(0.0);
        CreatureEntity wild = ModContent.CREATURES.get(Species.PARASAUR).get().create(level, EntitySpawnReason.COMMAND);
        wild.setNoAi(true);
        wild.setPos(owner.position());
        TamingService.of(wild).setHunger(100.0);
        level.addFreshEntity(wild);
        h.assertTrue(trough.feedNearby(level) == 0, "Troughs must not feed wild creatures");

        // --- drying rack: raw food becomes a ration over the configured batches ---
        BlockPos rackRel = new BlockPos(7, 3, 8);
        h.setBlock(rackRel, ModContent.DRYING_RACK.get().defaultBlockState());
        DryingRackBlockEntity rack = (DryingRackBlockEntity) level.getBlockEntity(h.absolutePos(rackRel));
        h.assertTrue(rack != null, "The drying rack block entity is missing");
        h.assertTrue(rack.insert(new ItemStack(Items.BEEF, 3)) == 3, "The rack must accept raw food");
        for (int i = 0; i < Config.FARM_DRYING_BATCHES.get(); i++) {
            h.assertTrue(rack.advance(), "The rack must advance each batch");
        }
        h.assertTrue(rack.output().is(ModContent.DRIED_RATION.get()) && rack.output().getCount() == 1,
                "One drying cycle must yield one ration");
        h.assertTrue(rack.input().getCount() == 2, "One raw item is consumed per cycle");

        // --- berry bush: a ripe bush yields berries and resets to growing ---
        BlockPos bushRel = new BlockPos(6, 3, 8);
        h.setBlock(bushRel, ModContent.TINTOBERRY_BUSH.get().defaultBlockState().setValue(BerryBushBlock.AGE, 3));
        BlockPos bushPos = h.absolutePos(bushRel);
        owner.getInventory().clearContent();
        BerryBushBlock.harvest(level, bushPos, level.getBlockState(bushPos), owner);
        h.assertTrue(level.getBlockState(bushPos).getValue(BerryBushBlock.AGE) == 1, "A harvest resets the bush");
        h.assertTrue(count(owner, ModContent.BERRIES.get("tintoberry").get()) >= 2, "A ripe bush yields berries");

        h.assertTrue(level.getChunkSource().getLoadedChunksCount() == chunks, "Farm batches must not load chunks");
        trike.discard();
        wild.discard();
        owner.discard();
        h.succeed();
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private FarmGameTests() {}
}
