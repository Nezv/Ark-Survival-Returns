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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.feature.farm.DryingRackBlockEntity;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.station.CrusherBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.StationCraftingMenu;
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
        for (String id : List.of("working_station", "storage_crate", "smithing_table", "medicine_bench", "crusher",
                "mortar_and_pestle", "herbal_bandage", "healing_mixture", "narcotics")) {
            h.assertTrue(recipes.byKey(recipe("arksurvivalreturns:" + id)).isPresent(), "Missing station recipe " + id);
        }
        for (String id : List.of("crafting_table", "chest", "trapped_chest", "smithing_table")) {
            h.assertTrue(recipes.byKey(recipe("minecraft:" + id)).isEmpty(), "The vanilla recipe survived: " + id);
        }

        // Recipes that used a replaced block take the Ark block: the crate is a wooden chest for the hopper
        // (NeoForge's recipe takes #c:chests/wooden, so a stray vanilla chest still works until it converts).
        ItemStack iron = new ItemStack(Items.IRON_INGOT), none = ItemStack.EMPTY;
        var withCrate = CraftingInput.of(3, 3, List.of(iron, none, iron, iron, new ItemStack(StationContent.STORAGE_CRATE_ITEM.get()), iron,
                none, iron, none));
        var hopper = recipes.getRecipeFor(RecipeType.CRAFTING, withCrate, level);
        h.assertTrue(hopper.isPresent() && hopper.get().value().assemble(withCrate).is(Items.HOPPER),
                "Five iron around a Storage Crate must make a hopper");

        // The herbal remedies belong to the Mortar & Pestle; the Medicine Bench waits for the Iron Age.
        h.assertTrue(StationContent.mortar(new ItemStack(StationContent.HERBAL_BANDAGE.get()))
                && StationContent.mortar(new ItemStack(StationContent.HEALING_MIXTURE.get()))
                && StationContent.mortar(new ItemStack(ModContent.NARCOTICS.get())), "Mortar tag is incomplete");
        h.assertFalse(StationContent.mortar(new ItemStack(ModContent.FIBER_BANDAGE.get())), "The fiber bandage stays a field craft");
        h.assertFalse(StationContent.medicine(new ItemStack(StationContent.HERBAL_BANDAGE.get())), "The Medicine Bench makes nothing yet");

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
        // Leave nothing behind: cargo tests nearby pull from any storage in range.
        if (level.getBlockEntity(pos) instanceof StorageCrateBlockEntity crate) crate.clearContent();
        h.setBlock(rel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        h.setBlock(rel.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        // The crusher table: stone down the chain, ores doubled.
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
        var mortar = new StationCraftingMenu(12, player.getInventory(), ContainerLevelAccess.create(level, table),
                StationContent.MORTAR_AND_PESTLE.get(), StationContent::mortar);
        h.assertTrue(fill(mortar, 1, berry).is(ModContent.NARCOTICS.get()), "The Mortar & Pestle must still grind Narcotics");
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
