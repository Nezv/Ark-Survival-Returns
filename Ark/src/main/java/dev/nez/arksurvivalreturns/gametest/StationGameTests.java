package dev.nez.arksurvivalreturns.gametest;

import java.util.List;
import dev.nez.arksurvivalreturns.feature.station.CrusherRecipes;
import dev.nez.arksurvivalreturns.feature.station.StationContent;
import dev.nez.arksurvivalreturns.feature.station.StorageCrateBlockEntity;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.station.CrusherBlockEntity;
import dev.nez.arksurvivalreturns.feature.tech.TechTrigger;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** F14: the exclusive workstations replace their vanilla blocks, and their storage, grinding and filters work. */
final class StationGameTests {
    private static ResourceKey<Recipe<?>> recipe(String id) {
        return ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
    }

    static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var recipes = level.getServer().getRecipeManager();
        WorkstationGameTests.run(h);
        for (String id : List.of("working_station")) {
            h.assertTrue(recipes.byKey(recipe("arksurvivalreturns:" + id)).isPresent(), "Missing field craft " + id);
        }
        // Planks stay in the hand grid as well as on the bench: the Working Station itself costs planks.
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo")) {
            h.assertTrue(recipes.byKey(recipe("minecraft:" + wood + "_planks")).isPresent(), "Planks must stay craftable by hand: " + wood);
        }
        for (String id : List.of("medicine_bench", "storage_crate", "smithing_table", "crusher", "mortar_and_pestle",
                "herbal_bandage", "healing_mixture", "narcotics", "vitamins")) {
            h.assertTrue(recipes.byKey(recipe("arksurvivalreturns:" + id)).isEmpty(), "Phase A grid recipe survived: " + id);
        }
        h.assertTrue(dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.all().size() == 5, "All phase A designs must load");
        for (String id : List.of("armoury", "working_station", "mortar_and_pestle", "medicine_bench", "smithing_table")) {
            var definition = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:" + id);
            h.assertTrue(definition != null && !definition.crafts().isEmpty(), "Missing graph recipes for " + id);
        }
        h.assertTrue(recipes.byKey(recipe("minecraft:hopper")).isEmpty(), "The hopper moved to the Smithing Table");
        var smith = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:smithing_table");
        h.assertTrue(smith.crafts().stream().anyMatch(c -> c.variant().item().equals("minecraft:hopper")
                && c.variant().cost().containsKey("arksurvivalreturns:storage_crate")), "The smith's hopper must take a crate");
        // The Medicine Bench costs no glass, and the bottles its remedies need are made on it (no 3x3 grid is left).
        var working = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:working_station");
        h.assertTrue(working.crafts().stream().anyMatch(c -> c.variant().item().equals("arksurvivalreturns:medicine_bench")
                && c.variant().cost().containsKey("arksurvivalreturns:mortar_and_pestle")
                && c.variant().cost().keySet().stream().noneMatch(cost -> cost.contains("glass"))), "The Medicine Bench must not cost glass");
        var medicine = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.get("arksurvivalreturns:medicine_bench");
        h.assertTrue(medicine.crafts().stream().anyMatch(c -> c.variant().item().equals("minecraft:glass_bottle")
                && c.variant().cost().equals(java.util.Map.of("minecraft:glass", 3))), "The Medicine Bench must make glass bottles");

        var medicinePlayer = FakePlayerFactory.get(level, new GameProfile(java.util.UUID.randomUUID(), "ArkMedicineBench"));
        medicinePlayer.experienceLevel = 99;
        dev.nez.arksurvivalreturns.feature.levels.ArkLevels.setLevel(medicinePlayer, 50);
        BlockPos benchRel = new BlockPos(9, 2, 9), benchPos = h.absolutePos(benchRel);
        h.setBlock(benchRel, StationContent.MEDICINE_BENCH.get().defaultBlockState());
        medicinePlayer.setPos(benchPos.getX() + .5, benchPos.getY() + 1, benchPos.getZ() + .5);
        var bench = new dev.nez.arksurvivalreturns.feature.station.WorkstationMenu(20, medicinePlayer.getInventory(), benchPos, "arksurvivalreturns:medicine_bench");
        medicinePlayer.containerMenu = bench;
        medicinePlayer.getInventory().clearContent();
        medicinePlayer.getInventory().setItem(0, new ItemStack(ModContent.BERRIES.get("amarberry").get()));
        medicinePlayer.getInventory().setItem(1, new ItemStack(ModContent.BERRIES.get("narcoberry").get()));
        medicinePlayer.getInventory().setItem(2, new ItemStack(Items.SHORT_GRASS));
        h.assertTrue(dev.nez.arksurvivalreturns.feature.station.WorkstationCrafting.craft(medicinePlayer,
                new dev.nez.arksurvivalreturns.feature.station.WorkstationPayload.Craft(bench.station, "arksurvivalreturns:herbal_bandage", 1,
                        "i:bandages/herbal_bandage", 0, 20)), "The Medicine Bench must mix Bandages from the design cost");
        h.assertTrue(medicinePlayer.getInventory().countItem(StationContent.HERBAL_BANDAGE.get()) == 2, "The design makes two Bandages");
        medicinePlayer.getInventory().clearContent();
        medicinePlayer.getInventory().setItem(0, new ItemStack(ModContent.BERRIES.get("azulberry").get(), 4));
        medicinePlayer.getInventory().setItem(1, new ItemStack(ModContent.BERRIES.get("tintoberry").get(), 4));
        medicinePlayer.getInventory().setItem(2, new ItemStack(Items.POTION));
        var vitaminsPacket = new dev.nez.arksurvivalreturns.feature.station.WorkstationPayload.Craft(bench.station,
                "arksurvivalreturns:vitamins", 1, "i:remedies/vitamins", 0, 20);
        h.assertFalse(dev.nez.arksurvivalreturns.feature.station.WorkstationCrafting.craft(medicinePlayer, vitaminsPacket), "A non-water potion must fail");
        h.assertTrue(medicinePlayer.getInventory().getItem(0).getCount() == 4, "Failed payment consumed berries");
        medicinePlayer.getInventory().setItem(2, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        h.assertTrue(dev.nez.arksurvivalreturns.feature.station.WorkstationCrafting.craft(medicinePlayer, vitaminsPacket), "Water and berries must make Vitamins");
        h.assertTrue(medicinePlayer.getInventory().countItem(StationContent.VITAMINS.get()) == 1, "Vitamins missing after mix");
        h.setBlock(benchRel, Blocks.AIR.defaultBlockState());
        medicinePlayer.containerMenu = medicinePlayer.inventoryMenu;

        // Eating Vitamins applies both effects and returns a bottle of water's glass.
        ItemStack vitaminsStack = new ItemStack(StationContent.VITAMINS.get());
        medicinePlayer.setItemInHand(InteractionHand.MAIN_HAND, vitaminsStack);
        ItemStack remainder = vitaminsStack.finishUsingItem(level, medicinePlayer);
        h.assertTrue(remainder.is(Items.GLASS_BOTTLE), "Eating Vitamins must return a glass bottle");
        h.assertTrue(medicinePlayer.hasEffect(MobEffects.HEALTH_BOOST) && medicinePlayer.hasEffect(MobEffects.REGENERATION),
                "Eating Vitamins must give Health Boost and Regeneration");

        // A crate keeps and exposes only its own 27 slots.
        BlockPos rel = new BlockPos(4, 3, 4);
        h.setBlock(rel, StationContent.STORAGE_CRATE.get().defaultBlockState());
        h.setBlock(rel.east(), StationContent.STORAGE_CRATE.get().defaultBlockState());
        BlockPos pos = h.absolutePos(rel);
        h.assertTrue(level.getBlockEntity(pos) instanceof StorageCrateBlockEntity, "The crate has no block entity");
        var handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
        h.assertTrue(handler != null && handler.size() == StorageCrateBlockEntity.SIZE,
                "A crate must expose exactly its own slots, even beside another crate");
        try (Transaction transaction = Transaction.openRoot()) {
            h.assertTrue(handler.insert(ItemResource.of(Items.COBBLESTONE), 64, transaction) == 64, "The crate must accept items");
            transaction.commit();
        }
        var neighbour = level.getCapability(Capabilities.Item.BLOCK, h.absolutePos(rel.east()), null);
        int elsewhere = 0;
        for (int i = 0; neighbour != null && i < neighbour.size(); i++) elsewhere += neighbour.getAmountAsInt(i);
        h.assertTrue(neighbour != null && elsewhere == 0, "A joined crate must not show its neighbour's items");
        // The distinct menu must preserve native chest transfers and the authored panel coordinates.
        var crate = (StorageCrateBlockEntity) level.getBlockEntity(pos);
        var cratePlayer = FakePlayerFactory.get(level, new GameProfile(
                java.util.UUID.fromString("5a60190f-7c15-413d-8585-5c3508790b03"), "CrateUiTest"));
        cratePlayer.getInventory().clearContent();
        var crateMenu = crate.createMenu(23, cratePlayer.getInventory(), cratePlayer);
        h.assertTrue(crateMenu.getType() == ModContent.STORAGE_CRATE_MENU.get(), "Crate must open its dedicated screen type");
        var clientMenu = new dev.nez.arksurvivalreturns.feature.station.StorageCrateMenu(23, cratePlayer.getInventory());
        h.assertTrue(crateMenu.slots.size() == 63 && clientMenu.slots.size() == 63, "Crate menu must synchronize all 63 slots");
        for (int i = 0; i < 63; i++) {
            var actual = crateMenu.slots.get(i);
            var remote = clientMenu.slots.get(i);
            h.assertTrue(actual.x == remote.x && actual.y == remote.y, "Crate client/server slot layouts diverged");
        }
        h.assertTrue(crateMenu.slots.get(27).y == 85 && crateMenu.slots.get(54).y == 143,
                "Player slots must align with the crate texture");
        h.assertTrue(crateMenu.quickMoveStack(cratePlayer, 0).getCount() == 64 && crate.isEmpty(),
                "Shift-click must transfer the stack out of the crate");
        h.assertTrue(crateMenu.quickMoveStack(cratePlayer, 62).getCount() == 64 && crate.getItem(0).getCount() == 64,
                "Shift-click must transfer the stack back into the crate");
        crateMenu.removed(cratePlayer);
        clientMenu.removed(cratePlayer);
        cratePlayer.getInventory().clearContent();
        // Leave nothing behind: cargo tests nearby pull from any storage in range.
        crate.clearContent();
        h.setBlock(rel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        h.setBlock(rel.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        // The crusher table: stone down the chain; ore doubling is disabled.
        h.assertTrue(CrusherRecipes.find(new ItemStack(Items.COBBLESTONE)).output() == Items.GRAVEL, "Cobblestone must crush to gravel");
        h.assertTrue(CrusherRecipes.find(new ItemStack(Items.IRON_ORE)) == null, "The crusher no longer doubles ore");
        h.assertTrue(CrusherRecipes.find(new ItemStack(Items.FLINT)) == null, "Gunpowder waits for the Bronze Age");
        h.assertTrue(CrusherRecipes.find(new ItemStack(Items.STICK)) == null, "Sticks are not crushable");
        h.succeed();
    }

    /**
     * X04 regressions: station-only results stay at their station, a drying load survives midnight, the crusher's
     * hopper slot is closed from below, and the stone fire's outline reaches its spit.
     */
    static void guards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(java.util.UUID.randomUUID(), "ArkStationGuards"));
        var berry = ModContent.BERRIES.get("narcoberry").get();
        h.assertTrue(fill(player.inventoryMenu, 1, berry).isEmpty(), "The 2x2 inventory grid must not grind Narcotics");
        BlockPos table = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        var village = new CraftingMenu(11, player.getInventory(), ContainerLevelAccess.create(level, table));
        h.assertTrue(fill(village, 1, berry).isEmpty(), "A village crafting table must not grind Narcotics");
        level.setBlockAndUpdate(table, StationContent.MORTAR_AND_PESTLE.get().defaultBlockState());
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(berry, 4));
        player.experienceLevel = 99;
        dev.nez.arksurvivalreturns.feature.levels.ArkLevels.setLevel(player, 50);
        player.setPos(table.getX() + .5, table.getY() + 1, table.getZ() + .5);
        var mortar = new dev.nez.arksurvivalreturns.feature.station.WorkstationMenu(12, player.getInventory(), table, "arksurvivalreturns:mortar_and_pestle");
        player.containerMenu = mortar;
        h.assertTrue(dev.nez.arksurvivalreturns.feature.station.WorkstationCrafting.craft(player,
                new dev.nez.arksurvivalreturns.feature.station.WorkstationPayload.Craft(mortar.station, "arksurvivalreturns:narcotics", 1,
                        "i:berries/narcotics", 0, 12)), "The Mortar & Pestle must grind Narcotics from the design");
        player.containerMenu = player.inventoryMenu;
        level.setBlockAndUpdate(table, Blocks.AIR.defaultBlockState());
        var grid = CraftingInput.of(2, 2, List.of(new ItemStack(berry), new ItemStack(berry), new ItemStack(berry), new ItemStack(berry)));
        h.assertTrue(CrafterBlock.getPotentialResults(level, grid).isEmpty(), "The Crafter must not grind Narcotics");

        BlockPos rackRel = new BlockPos(5, 2, 5);
        h.setBlock(rackRel, ModContent.DRYING_RACK.get().defaultBlockState());
        if (!(level.getBlockEntity(h.absolutePos(rackRel)) instanceof DryingRackBlockEntity rack)) throw h.assertionException("No rack");
        rack.insert(new ItemStack(Items.BEEF, 3));
        for (int i = 0; i < 40 && rack.output().isEmpty(); i++) rack.advance();
        // The first piece was dried "yesterday": the rest must still join the shelf.
        CustomData.update(DataComponents.CUSTOM_DATA, rack.output(),
                tag -> tag.putLong(TechTrigger.DRIED_DAY_TAG, tag.getLongOr(TechTrigger.DRIED_DAY_TAG, 0L) - 1));
        for (int i = 0; i < 40 && !rack.input().isEmpty(); i++) rack.advance();
        h.assertTrue(rack.input().isEmpty() && rack.output().getCount() == 3, "A drying load that spans midnight stalled");
        rack.extract();
        h.setBlock(rackRel, Blocks.AIR.defaultBlockState());

        BlockPos crusherRel = new BlockPos(7, 2, 2);
        h.setBlock(crusherRel, StationContent.CRUSHER.get().defaultBlockState());
        BlockPos crusherPos = h.absolutePos(crusherRel);
        if (!(level.getBlockEntity(crusherPos) instanceof CrusherBlockEntity crusher)) throw h.assertionException("No crusher");
        crusher.insert(new ItemStack(Items.COBBLESTONE, 8));
        var below = level.getCapability(Capabilities.Item.BLOCK, crusherPos, Direction.DOWN);
        int pulled;
        try (Transaction tx = Transaction.openRoot()) {
            pulled = below == null ? -1 : below.extract(0, ItemResource.of(Items.COBBLESTONE), 8, tx);
        }
        h.assertTrue(below != null && below.size() == 1 && pulled == 0, "Automation below the crusher must only reach its output");
        crusher.clearContent();
        h.setBlock(crusherRel, Blocks.AIR.defaultBlockState());

        var fire = PrimitiveContent.STONE_FIRE.get().defaultBlockState();
        h.assertTrue(fire.getShape(level, BlockPos.ZERO).max(Direction.Axis.Y) >= 9.4 / 16,
                "The stone fire outline must reach the spit");
        h.assertTrue(fire.getCollisionShape(level, BlockPos.ZERO, CollisionContext.empty()).max(Direction.Axis.Y) <= 7.01 / 16,
                "The stone fire must stay walkable");

        // Low fixes: outlines cover the models, pick-block works without item forms, stations leave foreign items alone.
        var crusherState = StationContent.CRUSHER.get().defaultBlockState();
        h.assertTrue(crusherState.getShape(level, BlockPos.ZERO).max(Direction.Axis.X) > 1.25
                && crusherState.getCollisionShape(level, BlockPos.ZERO).max(Direction.Axis.X) <= 1.0,
                "The crusher outline must include the flywheel, its collision must not");
        h.assertTrue(StationContent.MEDICINE_BENCH.get().defaultBlockState().getShape(level, BlockPos.ZERO).max(Direction.Axis.Y) >= 18.5 / 16,
                "The Medicine Bench outline must cover its bottles");
        h.assertTrue(ModContent.NARCOBERRY_BUSH.get().defaultBlockState().getCloneItemStack(level, BlockPos.ZERO, false).is(berry)
                && PrimitiveContent.LOOSE_ROCK.get().defaultBlockState().getCloneItemStack(level, BlockPos.ZERO, false).is(PrimitiveContent.ROCK.get()),
                "Pick-block on a bush or a loose rock must give its item");
        BlockPos troughRel = new BlockPos(9, 2, 5);
        h.setBlock(troughRel, ModContent.TROUGH.get().defaultBlockState());
        var torch = new ItemStack(Items.TORCH);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, torch);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(troughRel)),
                Direction.UP, h.absolutePos(troughRel), false);
        var result = level.getBlockState(h.absolutePos(troughRel)).useItemOn(torch, level, player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(result == net.minecraft.world.InteractionResult.PASS, "A trough must pass on items it does not take, like the client");
        h.setBlock(troughRel, Blocks.AIR.defaultBlockState());
        h.succeed();
    }

    /** Four of one item into grid slots 1-4 (a shapeless recipe matches anywhere); returns the result slot. */
    private static ItemStack fill(AbstractContainerMenu menu, int first, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 4; i++) menu.getSlot(first + i).set(new ItemStack(item));
        menu.broadcastChanges();
        return menu.getSlot(0).getItem();
    }

    private StationGameTests() {}
}
