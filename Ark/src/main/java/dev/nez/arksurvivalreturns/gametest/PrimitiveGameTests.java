package dev.nez.arksurvivalreturns.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.camp.TallBlocks;
import dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity;
import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlock;
import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.DinoMeat;
import dev.nez.arksurvivalreturns.feature.primitive.DriedMeatItem;
import dev.nez.arksurvivalreturns.feature.primitive.LooseRockBlock;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveEvents;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveForgeBlock;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveForgeBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlock;
import dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlockEntity;
import dev.nez.arksurvivalreturns.feature.tech.TechEvent;
import dev.nez.arksurvivalreturns.feature.tech.TechEventKind;
import dev.nez.arksurvivalreturns.feature.tech.TechTribeProgress;
import dev.nez.arksurvivalreturns.feature.tech.TechTrigger;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Prehistoric progression: rocks, the stone fire, the forge, curing tiers, carcass loot and the recipe gates. */
final class PrimitiveGameTests {
    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        return player;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    /** Loose rocks: generation follows the ground, water refuses, and right-click picks the rock up. */
    static void rocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var feature = PrimitiveContent.LOOSE_ROCK_FEATURE.get();
        BlockPos sandRel = new BlockPos(4, 2, 4);
        h.setBlock(sandRel, Blocks.SAND.defaultBlockState());
        h.setBlock(sandRel.above(), Blocks.AIR.defaultBlockState());
        BlockPos rockPos = h.absolutePos(sandRel.above());
        h.assertTrue(feature.place(new FeaturePlaceContext<>(Optional.empty(), level, level.getChunkSource().getGenerator(),
                level.getRandom(), rockPos, NoneFeatureConfiguration.INSTANCE)), "A loose rock must generate on sand");
        h.assertTrue(level.getBlockState(rockPos).getValue(LooseRockBlock.VARIANT) == LooseRockBlock.Variant.SANDSTONE,
                "The rock must take the look of its ground");

        BlockPos poolRel = new BlockPos(6, 2, 4);
        h.setBlock(poolRel, Blocks.STONE.defaultBlockState());
        h.setBlock(poolRel.above(), Blocks.WATER.defaultBlockState());
        h.assertFalse(feature.place(new FeaturePlaceContext<>(Optional.empty(), level, level.getChunkSource().getGenerator(),
                level.getRandom(), h.absolutePos(poolRel.above()), NoneFeatureConfiguration.INSTANCE)), "Rocks must never generate under water");

        FakePlayer player = player(level, "ArkRockPicker");
        level.getBlockState(rockPos).useWithoutItem(level, player, hit(rockPos));
        h.assertTrue(level.getBlockState(rockPos).isAir(), "Picking the rock must remove it");
        h.assertTrue(player.getInventory().countItem(PrimitiveContent.ROCK.get()) == 1, "Picking the rock must give one rock");
        h.succeed();
    }

    /** Stone fire: fuel, lighting, spit cooking, torch lighting, pot heat and burning out. */
    static void fire(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FakePlayer player = player(level, "ArkFireKeeper");
        BlockPos fireRel = new BlockPos(8, 3, 8);
        h.setBlock(fireRel, PrimitiveContent.STONE_FIRE.get().defaultBlockState());
        BlockPos firePos = h.absolutePos(fireRel);
        StoneFireBlockEntity fire = (StoneFireBlockEntity) level.getBlockEntity(firePos);
        h.assertTrue(fire != null, "The stone fire block entity is missing");
        h.assertFalse(fire.light(), "A fire without fuel must not light");
        ItemStack logs = new ItemStack(Items.OAK_LOG, 4);
        for (int i = 0; i < 4; i++) h.assertTrue(fire.addFuel(logs), "Logs must fuel the fire");
        h.assertTrue(level.getBlockState(firePos).getValue(StoneFireBlock.FUELED), "Fuel must show in the pit");
        h.assertTrue(fire.light() && level.getBlockState(firePos).getValue(StoneFireBlock.LIT), "Fuel must light");
        h.assertTrue(fire.insertFood(new ItemStack(Items.BEEF, 6)) == StoneFireBlockEntity.SPIT_CAPACITY, "The spit holds four");
        h.assertTrue(fire.insertFood(new ItemStack(Items.IRON_INGOT)) == 0, "Only campfire recipes go on the spit");
        for (int tick = 0; tick < 1200 && fire.cooked().getCount() < 2; tick++) fire.step();
        h.assertTrue(fire.cooked().is(Items.COOKED_BEEF) && fire.cooked().getCount() >= 2, "The spit must cook beef");
        h.assertTrue(fire.spit().getCount() <= 2, "Each cooked item consumes one raw item");

        ItemStack stick = new ItemStack(Items.STICK, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stick);
        level.getBlockState(firePos).useItemOn(stick, level, player, InteractionHand.MAIN_HAND, hit(firePos));
        h.assertTrue(player.getInventory().countItem(Items.TORCH) == 1 && stick.getCount() == 1, "A stick in the fire must become a torch");

        BlockPos potRel = fireRel.above();
        h.setBlock(potRel, ModContent.COOKING_POT.get().defaultBlockState());
        var potState = level.getBlockState(h.absolutePos(potRel));
        h.assertTrue(potState.getBlock() instanceof CookingPotBlock, "The pot must place on the fire");
        h.setBlock(potRel, potState.setValue(CookingPotBlock.ON_CAMPFIRE, CookingPotBlock.isHearth(level.getBlockState(firePos))));
        CookingPotBlockEntity pot = (CookingPotBlockEntity) level.getBlockEntity(h.absolutePos(potRel));
        h.assertTrue(pot != null && pot.isHeated(), "A lit stone fire must heat the pot above it");
        h.assertTrue(level.getBlockState(h.absolutePos(potRel)).getValue(CookingPotBlock.ON_CAMPFIRE), "The pot must stand on its trivet");
        h.setBlock(potRel, level.getBlockState(h.absolutePos(potRel)).setValue(CookingPotBlock.ON_STONES, true));
        h.setBlock(fireRel, level.getBlockState(firePos).setValue(StoneFireBlock.POT, true));
        var seat = new BlockHitResult(Vec3.atBottomCenterOf(firePos).add(0, 9 / 16.0, 0), Direction.NORTH, firePos, false);
        h.assertTrue(level.getBlockState(firePos).getShape(level, firePos).bounds().maxY > 0.6, "The seated pot must be part of the fire's outline");
        h.assertTrue(level.getBlockState(firePos).useWithoutItem(level, player, seat).consumesAction(), "Clicking the seated pot must reach the pot");

        for (int tick = 0; tick < Config.PRIMITIVE_FIRE_MAX_FUEL.get() + 10 && fire.isLit(); tick++) fire.step();
        h.assertFalse(level.getBlockState(firePos).getValue(StoneFireBlock.LIT), "A fire must burn out");
        h.assertFalse(pot.isHeated(), "A dead fire must stop heating the pot");
        h.succeed();
    }

    /** Primitive forge: smelts with fuel, keeps food for the fire, and pays out the result. */
    static void forge(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos forgeRel = new BlockPos(8, 3, 8);
        h.setBlock(forgeRel, PrimitiveContent.PRIMITIVE_FORGE.get().defaultBlockState());
        PrimitiveForgeBlockEntity forge = (PrimitiveForgeBlockEntity) level.getBlockEntity(h.absolutePos(forgeRel));
        h.assertTrue(forge != null, "The forge block entity is missing");
        h.assertTrue(forge.insert(new ItemStack(Items.BEEF), PrimitiveForgeBlockEntity.INPUT) == 0, "The forge must refuse food");
        h.assertTrue(forge.insert(new ItemStack(Items.RAW_IRON, 2), PrimitiveForgeBlockEntity.INPUT) == 2, "The forge must take ore");
        for (int tick = 0; tick < 400; tick++) forge.step();
        h.assertTrue(forge.output().isEmpty(), "The forge must not smelt without fuel");
        h.assertTrue(forge.insert(new ItemStack(Items.CHARCOAL), PrimitiveForgeBlockEntity.FUEL) == 1, "Charcoal must fuel the forge");
        for (int tick = 0; tick < 1000 && forge.output().getCount() < 2; tick++) forge.step();
        h.assertTrue(forge.output().is(Items.IRON_INGOT) && forge.output().getCount() == 2, "Two raw iron must become two ingots");
        h.assertTrue(forge.insert(new ItemStack(Items.OAK_LOG), PrimitiveForgeBlockEntity.INPUT) == 1, "Logs must smelt into charcoal");
        ItemStack taken = forge.extract();
        h.assertTrue(taken.is(Items.IRON_INGOT) && taken.getCount() == 2, "An empty hand takes the result first");
        h.succeed();
    }

    /** Drying rack curing: Dried Meat I, then II and III while it keeps hanging; the tech tiers follow. */
    static void curing(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rackRel = new BlockPos(8, 3, 8);
        h.setBlock(rackRel, ModContent.DRYING_RACK.get().defaultBlockState());
        DryingRackBlockEntity rack = (DryingRackBlockEntity) level.getBlockEntity(h.absolutePos(rackRel));
        h.assertTrue(rack != null && rack.insert(new ItemStack(PrimitiveContent.RAW_MEAT.get(DinoMeat.CARNIVORE).get())) == 1,
                "Dinosaur meat must hang on the rack");
        for (int i = 0; i < Config.FARM_DRYING_BATCHES.get(); i++) rack.advance();
        h.assertTrue(DriedMeatItem.tier(rack.output()) == 1, "Fresh jerky must be Dried Meat I");
        for (int i = 0; i < Config.FARM_DRIED_TIER_TWO.get(); i++) rack.advance();
        h.assertTrue(DriedMeatItem.tier(rack.output()) == 2, "Hanging meat must cure into Dried Meat II");
        for (int i = 0; i < Config.FARM_DRIED_TIER_THREE.get(); i++) rack.advance();
        ItemStack best = rack.output().copy();
        h.assertTrue(DriedMeatItem.tier(best) == 3, "Long curing must reach Dried Meat III");
        h.assertTrue(best.getHoverName().getString().endsWith("III"), "The tier must show in the name");
        h.assertTrue(best.get(net.minecraft.core.component.DataComponents.FOOD).nutrition() == 8, "Tier III must eat as tier III");

        var trigger = new TechTrigger.Consume(List.of(Identifier.parse("arksurvivalreturns:dried_meat")), 0, 3);
        TechTribeProgress progress = new TechTribeProgress();
        trigger.observe(new TechEvent(TechEventKind.CONSUME, null, DriedMeatItem.withTier(2), null, null, 0L), progress, "dried:");
        h.assertFalse(trigger.satisfied(progress, "dried:", null), "Dried Meat II must not count as III");
        trigger.observe(new TechEvent(TechEventKind.CONSUME, null, best, null, null, 0L), progress, "dried:");
        h.assertTrue(trigger.satisfied(progress, "dried:", null), "Eating Dried Meat III must complete its node");

        var craft = new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:stone_knife")), 1);
        TechTribeProgress crafted = new TechTribeProgress();
        ItemStack knife = new ItemStack(PrimitiveContent.STONE_KNIFE.get());
        craft.observe(new TechEvent(TechEventKind.OBTAIN, null, knife, null, null, 0L), crafted, "london:");
        h.assertFalse(craft.satisfied(crafted, "london:", null), "Picking up a knife is not crafting one");
        craft.observe(new TechEvent(TechEventKind.CRAFT, null, knife, null, null, 0L), crafted, "london:");
        h.assertTrue(craft.satisfied(crafted, "london:", null), "Crafting the knife must complete its node");
        h.succeed();
    }

    /** The gates: logs need an axe, the furnace and wooden tools are gone, rocks lead to cobblestone, carcasses drop meat. */
    static void gates(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var log = Blocks.OAK_LOG.defaultBlockState();
        h.assertTrue(PrimitiveEvents.needsAxe(log, ItemStack.EMPTY), "Bare hands must not harvest logs");
        h.assertFalse(PrimitiveEvents.needsAxe(log, new ItemStack(PrimitiveContent.STONE_HATCHET.get())), "The stone hatchet must harvest logs");
        var recipes = level.getServer().getRecipeManager();
        h.assertTrue(recipes.byKey(recipe("minecraft:furnace")).isEmpty(), "The furnace recipe must be removed");
        h.assertTrue(recipes.byKey(recipe("minecraft:wooden_pickaxe")).isEmpty(), "Wooden tools must be removed");
        for (String id : List.of("cobblestone_from_rocks", "stone_knife", "stone_hatchet", "fire_starter", "stone_fire",
                "primitive_forge", "lead_from_fiber", "cooked_carnivore_meat_from_campfire_cooking")) {
            h.assertTrue(recipes.byKey(recipe("arksurvivalreturns:" + id)).isPresent(), "Missing recipe " + id);
        }

        BlockPos spot = h.absolutePos(new BlockPos(8, 3, 8));
        CreatureEntity parasaur = ModContent.CREATURES.get(Species.PARASAUR).get().create(level, EntitySpawnReason.COMMAND);
        parasaur.setNoAi(true);
        parasaur.setPos(Vec3.atBottomCenterOf(spot));
        level.addFreshEntity(parasaur);
        parasaur.kill(level);
        h.runAfterDelay(2, () -> {
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(4));
            boolean meat = drops.stream().anyMatch(item -> item.getItem().is(PrimitiveContent.RAW_MEAT.get(DinoMeat.HERBIVORE).get()));
            drops.forEach(ItemEntity::discard);
            h.assertTrue(meat, "A parasaur carcass must drop herbivore meat");
            h.succeed();
        });
    }

    private static ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipe(String id) {
        return ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
    }

    /**
     * Two-block stations: the forge and the drying rack place their top half, take clicks on either half, and
     * break as one with a single drop; the bedroll lies two blocks long and drops once.
     */
    static void tallStations(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FakePlayer player = player(level, "ArkBuilder");
        BlockPos forgePos = place(h, player, new BlockPos(4, 1, 4), PrimitiveContent.PRIMITIVE_FORGE_ITEM.get());
        BlockPos chimney = forgePos.above();
        h.assertTrue(level.getBlockState(chimney).is(PrimitiveContent.PRIMITIVE_FORGE.get()) && TallBlocks.isUpper(level.getBlockState(chimney)),
                "The forge must stand two blocks tall");
        h.assertTrue(level.getBlockEntity(chimney) == null && level.getBlockEntity(forgePos) instanceof PrimitiveForgeBlockEntity,
                "Only the lower half keeps the forge's inventory");
        PrimitiveForgeBlockEntity forge = (PrimitiveForgeBlockEntity) level.getBlockEntity(forgePos);
        level.getBlockState(chimney).useItemOn(new ItemStack(Items.RAW_IRON, 2), level, player, InteractionHand.MAIN_HAND, hit(chimney));
        h.assertTrue(forge.input().getCount() == 2, "Clicking the chimney must load the forge");
        forge.insert(new ItemStack(Items.CHARCOAL), PrimitiveForgeBlockEntity.FUEL);
        forge.step();
        h.assertTrue(level.getBlockState(chimney).getValue(PrimitiveForgeBlock.LIT), "The chimney must glow with the fire");
        level.destroyBlock(chimney, true);
        h.assertTrue(level.getBlockState(forgePos).isAir(), "Breaking the chimney must take the whole forge");
        h.assertTrue(dropped(level, forgePos, PrimitiveContent.PRIMITIVE_FORGE_ITEM.get()) == 1, "A broken forge drops exactly one forge");

        BlockPos rackPos = place(h, player, new BlockPos(10, 1, 4), ModContent.DRYING_RACK_ITEM.get());
        BlockPos rackTop = rackPos.above();
        h.assertTrue(level.getBlockState(rackTop).is(ModContent.DRYING_RACK.get()) && TallBlocks.isUpper(level.getBlockState(rackTop)),
                "The drying rack must stand two blocks tall");
        level.getBlockState(rackTop).useItemOn(new ItemStack(Items.BEEF), level, player, InteractionHand.MAIN_HAND, hit(rackTop));
        h.assertTrue(((DryingRackBlockEntity) level.getBlockEntity(rackPos)).input().is(Items.BEEF), "Food hung from the top reaches the rack");
        level.destroyBlock(rackPos, true);
        h.assertTrue(level.getBlockState(rackTop).isAir(), "Breaking the base must take the top half");
        h.assertTrue(dropped(level, rackPos, ModContent.DRYING_RACK_ITEM.get()) == 1, "A broken rack drops exactly one rack");

        // A fake player looks south: the head lies one block south of the foot.
        BlockPos foot = place(h, player, new BlockPos(4, 1, 10), ModContent.BEDROLL_ITEM.get());
        BlockPos headPos = foot.relative(player.getDirection());
        h.assertTrue(level.getBlockState(headPos).is(ModContent.BEDROLL.get()), "The bedroll must be two blocks long");
        level.destroyBlock(foot, true);
        h.assertTrue(level.getBlockState(headPos).isAir(), "The head must break with the foot");
        h.assertTrue(dropped(level, foot, ModContent.BEDROLL_ITEM.get()) == 1, "A broken bedroll drops exactly one bedroll");
        h.succeed();
    }

    /** Places a block item on a stone floor block the way a player would; returns where it landed. */
    private static BlockPos place(GameTestHelper h, FakePlayer player, BlockPos floorRel, net.minecraft.world.item.Item item) {
        h.setBlock(floorRel, Blocks.STONE.defaultBlockState());
        for (var side : Direction.Plane.HORIZONTAL) h.setBlock(floorRel.relative(side), Blocks.STONE.defaultBlockState());
        BlockPos floor = h.absolutePos(floorRel);
        var context = new net.minecraft.world.item.context.BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item),
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
        h.assertTrue(((net.minecraft.world.item.BlockItem) item).place(context).consumesAction(), item + " must place");
        return floor.above();
    }

    private static int dropped(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private PrimitiveGameTests() {}
}
