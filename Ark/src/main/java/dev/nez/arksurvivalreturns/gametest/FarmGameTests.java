package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.List;
import dev.nez.arksurvivalreturns.feature.farm.DryingRackBlock;
import dev.nez.arksurvivalreturns.feature.farm.TroughBlock;
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
        h.assertTrue(trough.getBlockState().getValue(TroughBlock.FILLED), "Inserted food must be visible");
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

        trike.discard();
        wild.discard();

        // --- drying rack: raw food becomes a ration over the configured batches ---
        BlockPos rackRel = new BlockPos(7, 3, 8);
        h.setBlock(rackRel, ModContent.DRYING_RACK.get().defaultBlockState());
        DryingRackBlockEntity rack = (DryingRackBlockEntity) level.getBlockEntity(h.absolutePos(rackRel));
        h.assertTrue(rack != null, "The drying rack block entity is missing");
        h.assertTrue(rack.insert(new ItemStack(Items.BEEF, 3)) == 3, "The rack must accept raw food");
        for (int i = 0; i < Config.FARM_DRYING_BATCHES.get(); i++) {
            h.assertTrue(rack.advance(), "The rack must advance each batch");
        }
        h.assertTrue(rack.output().is(dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent.DRIED_MEAT.get())
                        && rack.output().getCount() == 1
                        && dev.nez.arksurvivalreturns.feature.primitive.DriedMeatItem.tier(rack.output()) == 1,
                "One drying cycle must turn meat into Dried Meat I");
        h.assertTrue(rack.input().getCount() == 2, "One raw item is consumed per cycle");

        h.assertTrue(rack.getBlockState().getValue(DryingRackBlock.HANGING) == 2
                && rack.getBlockState().getValue(DryingRackBlock.FOOD) == DryingRackBlock.Food.MEAT
                && rack.getBlockState().getValue(DryingRackBlock.READY), "Rack art must show raw meat and the finished ration");
        var rackPos = h.absolutePos(rackRel);
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atBottomCenterOf(rackPos.above()),
                net.minecraft.core.Direction.UP, rackPos, false);
        var context = new net.minecraft.world.item.context.BlockPlaceContext(owner, net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(ModContent.DRYING_RACK_ITEM.get()), hit);
        h.assertTrue(ModContent.DRYING_RACK_ITEM.get().place(context).consumesAction(), "A second rack must be placeable on the first");
        var upper = (DryingRackBlockEntity) level.getBlockEntity(rackPos.above());
        h.assertTrue(upper != null && upper.getBlockState().getValue(DryingRackBlock.FACING) == rack.getBlockState().getValue(DryingRackBlock.FACING),
                "Stacked uprights must align");
        upper.insert(new ItemStack(Items.COD));
        h.assertTrue(upper.getBlockState().getValue(DryingRackBlock.FOOD) == DryingRackBlock.Food.FISH, "Fish must have its own display");
        upper.extract();
        upper.insert(new ItemStack(ModContent.BERRIES.get("tintoberry").get()));
        h.assertTrue(upper.getBlockState().getValue(DryingRackBlock.FOOD) == DryingRackBlock.Food.BERRIES, "Berries must use the woven pouch");
        upper.extract();
        rack.extract();
        rack.extract();
        h.assertTrue(rack.getBlockState().getValue(DryingRackBlock.HANGING) == 0
                && !rack.getBlockState().getValue(DryingRackBlock.READY), "An emptied rack must remove all displayed food");
        h.assertTrue(upper.input().isEmpty(), "Tiers must have independent inventories");
        verifyTroughVariants(h);

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

    private static void verifyTroughVariants(GameTestHelper h) {
        var level = h.getLevel();
        for (var entry : ModContent.TROUGHS.entrySet()) {
            var block = entry.getValue().get();
            BlockPos pos = new BlockPos(4, 3, 5);
            h.setBlock(pos, block.defaultBlockState());
            var trough = (TroughBlockEntity) level.getBlockEntity(h.absolutePos(pos));
            h.assertTrue(trough != null && trough.getType().isValid(block.defaultBlockState()), "Missing trough entity for " + entry.getKey());
            trough.insert(new ItemStack(Items.WHEAT, 2));
            h.assertTrue(trough.getBlockState().getValue(TroughBlock.FILLED), "The wood variant must show food");
            trough.extract();
            h.assertTrue(!trough.getBlockState().getValue(TroughBlock.FILLED), "The wood variant must empty its display");
            String id = entry.getKey().equals("oak") ? "trough" : entry.getKey() + "_trough";
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                    dev.nez.arksurvivalreturns.ArkSurvivalReturns.id(id));
            var holder = level.getServer().getRecipeManager().byKey(key);
            h.assertTrue(holder.isPresent(), "Missing variant recipe: " + id);
            var recipe = (net.minecraft.world.item.crafting.ShapedRecipe) holder.get().value();
            var plank = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                    net.minecraft.resources.Identifier.withDefaultNamespace(entry.getKey() + "_planks"));
            var slots = new java.util.ArrayList<ItemStack>(List.of(new ItemStack(plank), ItemStack.EMPTY, new ItemStack(plank),
                    new ItemStack(plank), new ItemStack(Items.RESIN_CLUMP), new ItemStack(plank),
                    ItemStack.EMPTY, new ItemStack(ModContent.PLANT_FIBER.get()), ItemStack.EMPTY));
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, slots);
            h.assertTrue(recipe.matches(input, level) && recipe.assemble(input).is(block.asItem()), "Wood must survive crafting: " + id);
            slots.set(4, ItemStack.EMPTY);
            h.assertTrue(!recipe.matches(net.minecraft.world.item.crafting.CraftingInput.of(3, 3, slots), level), "Resin must be required: " + id);
        }
    }

    /** Medicine without a station: the concentrated dose outranks the berry and the improved arrow. */
    static void medicine(GameTestHelper h) {
        var berry = (dev.nez.arksurvivalreturns.feature.taming.SedativeItem) ModContent.BERRIES.get("narcoberry").get();
        var concentrated = ModContent.CONCENTRATED_SEDATIVE.get();
        var basicArrow = ModContent.TRANQUILIZER_ARROW_ITEM.get();
        var improvedArrow = ModContent.IMPROVED_TRANQUILIZER_ARROW_ITEM.get();
        h.assertTrue(concentrated.potency() > berry.potency(), "The concentrated sedative must be stronger");
        h.assertTrue(basicArrow.potency() == Config.TRANQUILIZER_ARROW_POTENCY.get(),
                "The baseline arrow must use the configured arrow dose");
        h.assertTrue(improvedArrow.potency() == Config.CONCENTRATED_SEDATIVE_POTENCY.get(),
                "The improved arrow must use the concentrated dose");
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
