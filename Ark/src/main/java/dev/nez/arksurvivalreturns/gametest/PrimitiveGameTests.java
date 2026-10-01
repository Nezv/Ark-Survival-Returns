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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
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

        var craft = new TechTrigger.Craft(List.of(Identifier.parse("arksurvivalreturns:rock_sword")), 1);
        TechTribeProgress crafted = new TechTribeProgress();
        ItemStack knife = new ItemStack(PrimitiveContent.ROCK_SWORD.get());
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
        h.assertFalse(PrimitiveEvents.needsAxe(log, new ItemStack(PrimitiveContent.STONE_HATCHET.get())), "The rock axe must harvest logs");
        var recipes = level.getServer().getRecipeManager();
        h.assertTrue(recipes.byKey(recipe("minecraft:furnace")).isEmpty(), "The furnace recipe must be removed");
        h.assertTrue(recipes.byKey(recipe("minecraft:wooden_pickaxe")).isEmpty(), "Wooden tools must be removed");
        for (String id : List.of("stone_hatchet", "cooked_carnivore_meat_from_campfire_cooking")) {
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

    /**
     * The answered recipe-gate decisions (F12): the rock set with Fiber in any slot, mining like stone; the
     * mattress on the 3x3 grid and the bedroll built from it; the retired vanilla recipes; Narcotics from the
     * Mortar & Pestle into tranquilizer arrows; the Bronze Age items left unmade; the Blueberry as food; Tom's
     * Storage without ender pearls.
     */
    static void decisions(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var recipes = level.getServer().getRecipeManager();
        ItemStack rock = new ItemStack(PrimitiveContent.ROCK.get()), stick = new ItemStack(Items.STICK),
                fiber = new ItemStack(ModContent.PLANT_FIBER.get()), none = ItemStack.EMPTY;
        // Phase A recipes no longer match any grid. Their cost is the reviewed workstation data.
        h.assertTrue(recipes.byKey(recipe("arksurvivalreturns:rock_pickaxe")).isEmpty(), "The pickaxe moved to the Armoury");
        var armoury = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:armoury");
        h.assertTrue(armoury.crafts().stream().anyMatch(c -> c.variant().item().equals("arksurvivalreturns:rock_pickaxe")
                && c.variant().cost().equals(java.util.Map.of("arksurvivalreturns:rock", 3, "minecraft:stick", 2, "arksurvivalreturns:plant_fiber", 1))),
                "The Armoury pickaxe must retain three rocks, two twigs and one fiber");
        // Wooden stats, stone tier: iron ore drops for the rock pickaxe, diamond ore does not.
        ItemStack pickaxe = new ItemStack(PrimitiveContent.ROCK_PICKAXE.get());
        h.assertTrue(pickaxe.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()), "The rock pickaxe must mine iron ore");
        h.assertFalse(pickaxe.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()), "The rock pickaxe must stop at stone tier");
        h.assertTrue(pickaxe.getMaxDamage() == 59, "Rock tools keep wooden durability");
        var working = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:working_station");
        h.assertTrue(working.crafts().stream().anyMatch(c -> c.variant().item().equals("arksurvivalreturns:mattress")
                && c.variant().cost().getOrDefault("arksurvivalreturns:plant_fiber", 0) == 9), "The Working Station must make the nine-fiber mattress");
        h.assertTrue(working.crafts().stream().anyMatch(c -> c.variant().item().equals("arksurvivalreturns:bedroll")), "The Working Station must make the bedroll");
        h.assertTrue(recipes.byKey(recipe("arksurvivalreturns:mattress")).isEmpty(), "A grid must not make the mattress");
        // Retired vanilla recipes and the Bronze Age items.
        for (String id : List.of("minecraft:stone_pickaxe", "minecraft:stone_sword", "minecraft:stone_spear", "minecraft:wooden_spear",
                "minecraft:campfire", "arksurvivalreturns:cobblestone_from_rocks", "arksurvivalreturns:stone_knife",
                "arksurvivalreturns:concentrated_sedative", "arksurvivalreturns:improved_tranquilizer_arrow")) {
            h.assertTrue(recipes.byKey(recipe(id)).isEmpty(), "Recipe should be gone: " + id);
        }
        // Blackberries grind into Narcotics at the Mortar & Pestle only; Narcotics tip the tranquilizer arrows.
        ItemStack berry = new ItemStack(ModContent.BERRIES.get("narcoberry").get());
        var grind = CraftingInput.of(2, 2, List.of(berry, berry, berry, berry));
        var narcotics = recipes.getRecipeFor(RecipeType.CRAFTING, grind, level);
        h.assertTrue(narcotics.isEmpty(), "Narcotics must not match the 2x2 grid");
        var mortar = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:mortar_and_pestle");
        h.assertTrue(mortar.crafts().stream().anyMatch(c -> c.variant().item().equals("arksurvivalreturns:narcotics") && c.variant().count() == 4),
                "The Mortar & Pestle must grind four Narcotics");
        h.assertTrue(armoury.crafts().stream().anyMatch(c -> c.variant().item().equals("arksurvivalreturns:tranquilizer_arrow")
                && c.variant().cost().getOrDefault("arksurvivalreturns:narcotics", 0) == 1), "Narcotics must tip tranquilizer arrows at the Armoury");
        // The Blueberry is food: one hunger point.
        var food = new ItemStack(ModContent.BERRIES.get("azulberry").get()).get(DataComponents.FOOD);
        h.assertTrue(food != null && food.nutrition() == 1, "The blueberry must satiate one hunger point");
        // Tom's Storage: no ender pearls, comparators or glowstone left in its recipes.
        if (net.neoforged.fml.ModList.get().isLoaded("toms_storage")) {
            for (String id : List.of("inventory_connector", "storage_terminal", "wireless_terminal", "inventory_interface")) {
                var holder = recipes.byKey(recipe("toms_storage:" + id));
                h.assertTrue(holder.isPresent(), "Tom's Storage recipe missing: " + id);
                var shaped = (net.minecraft.world.item.crafting.ShapedRecipe) holder.get().value();
                for (var cell : shaped.getIngredients()) {
                    h.assertFalse(cell.isPresent() && (cell.get().test(new ItemStack(Items.ENDER_PEARL))
                            || cell.get().test(new ItemStack(Items.COMPARATOR)) || cell.get().test(new ItemStack(Items.GLOWSTONE_DUST))),
                            "Tom's Storage " + id + " still needs an ender pearl, comparator or glowstone");
                }
            }
        }
        h.succeed();
    }

    /**
     * The keratin tier: a Triceratops carcass yields keratin, a sharp rock (not flint) tips arrows, the forge is
     * stone only, the spear is a vanilla spear and the chestplate carries its four armour points.
     */
    static void keratin(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var recipes = level.getServer().getRecipeManager();
        h.assertTrue(recipes.byKey(recipe("arksurvivalreturns:sharp_rock")).isPresent(), "Sharp rock stays a field craft");
        var armoury = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:armoury");
        for (String id : List.of("keratin_spear", "keratin_helmet", "keratin_chestplate", "keratin_leggings", "keratin_boots")) {
            h.assertTrue(recipes.byKey(recipe("arksurvivalreturns:" + id)).isEmpty(), "Keratin grid recipe survived: " + id);
            h.assertTrue(armoury.crafts().stream().anyMatch(c -> c.variant().item().equals("arksurvivalreturns:" + id)), "Armoury recipe missing: " + id);
        }
        h.assertTrue(armoury.crafts().stream().anyMatch(c -> c.variant().item().equals("minecraft:arrow")
                && c.variant().cost().containsKey("arksurvivalreturns:sharp_rock") && !c.variant().cost().containsKey("minecraft:flint")),
                "Sharp rocks must tip arrows at the Armoury");
        h.assertTrue(recipes.byKey(recipe("minecraft:arrow")).isEmpty() && recipes.byKey(recipe("arksurvivalreturns:arrow")).isEmpty(),
                "Grids must not make arrows");
        var working = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:working_station");
        h.assertTrue(working.crafts().stream().anyMatch(c -> c.variant().item().equals("arksurvivalreturns:primitive_forge")),
                "The Working Station must make the Primitive Forge");

        ItemStack spear = new ItemStack(PrimitiveContent.KERATIN_SPEAR.get());
        h.assertTrue(spear.has(DataComponents.KINETIC_WEAPON) && spear.has(DataComponents.PIERCING_WEAPON), "The keratin spear must be a vanilla spear");
        double armor = new ItemStack(PrimitiveContent.KERATIN_CHESTPLATE.get())
                .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.attribute().is(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum();
        h.assertTrue(armor == 4.0, "The keratin chestplate must give 4 armour, got " + armor);

        BlockPos spot = h.absolutePos(new BlockPos(8, 3, 8));
        CreatureEntity trike = ModContent.CREATURES.get(Species.TRICERATOPS).get().create(level, EntitySpawnReason.COMMAND);
        trike.setNoAi(true);
        trike.setPos(Vec3.atBottomCenterOf(spot));
        level.addFreshEntity(trike);
        trike.kill(level);
        h.runAfterDelay(2, () -> {
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(5));
            int keratin = drops.stream().filter(item -> item.getItem().is(PrimitiveContent.KERATIN.get()))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            drops.forEach(ItemEntity::discard);
            h.assertTrue(keratin >= 2, "A Triceratops carcass must drop at least two keratin, got " + keratin);
            // A Flint Knife kill cuts three more (2-4 becomes 5-7).
            FakePlayer butcher = player(level, "ArkButcher");
            butcher.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModContent.FLINT_KNIFE.get()));
            CreatureEntity carcass = ModContent.CREATURES.get(Species.TRICERATOPS).get().create(level, EntitySpawnReason.COMMAND);
            carcass.setNoAi(true);
            carcass.setPos(Vec3.atBottomCenterOf(spot));
            level.addFreshEntity(carcass);
            // Straight to the death loot, with the knife-wielding player as the attacker (the creature's own
            // damage handling is not what this checks).
            carcass.setHealth(0f);
            carcass.die(level.damageSources().playerAttack(butcher));
            h.runAfterDelay(2, () -> {
                var knifeDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(5));
                int cut = knifeDrops.stream().filter(item -> item.getItem().is(PrimitiveContent.KERATIN.get()))
                        .mapToInt(item -> item.getItem().getCount()).sum();
                knifeDrops.forEach(ItemEntity::discard);
                h.assertTrue(cut >= 5, "A Flint Knife kill must cut at least five keratin, got " + cut);
                h.succeed();
            });
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

        // A fake player looks south: the reusable Bedroll's 2x2 footprint extends south (head) and west (the
        // extra side); place()'s cross-shaped floor already covers the south and west neighbours, only the
        // south-west corner (under the head-right cell) needs its own support block.
        BlockPos floorRel = new BlockPos(4, 1, 10);
        h.setBlock(floorRel.south().west(), Blocks.STONE.defaultBlockState());
        BlockPos foot = place(h, player, floorRel, ModContent.BEDROLL_ITEM.get());
        BlockPos headPos = foot.relative(player.getDirection());
        BlockPos footRight = foot.west(), headRight = headPos.west();
        h.assertTrue(level.getBlockState(headPos).is(ModContent.BEDROLL.get()), "The bedroll must be two blocks long");
        h.assertTrue(level.getBlockState(footRight).is(ModContent.BEDROLL.get()) && level.getBlockState(headRight).is(ModContent.BEDROLL.get()),
                "The bedroll must occupy its full 2x2 footprint");
        // A real mining break (ServerPlayerGameMode#destroyBlock): playerWillDestroy clears the other cells,
        // the mined cell is removed, and loot comes from the state captured before the break.
        var footState = level.getBlockState(foot);
        footState.getBlock().playerWillDestroy(level, foot, footState, player);
        level.removeBlock(foot, false);
        net.minecraft.world.level.block.Block.dropResources(footState, level, foot, null, player, ItemStack.EMPTY);
        h.assertTrue(level.getBlockState(headPos).isAir() && level.getBlockState(footRight).isAir() && level.getBlockState(headRight).isAir(),
                "The whole 2x2 bedroll must break together");
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
