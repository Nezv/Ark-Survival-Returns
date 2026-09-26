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

/** F14: the exclusive workstations replace their vanilla blocks, and their storage and grinding work. */
final class StationGameTests {
    private static ResourceKey<Recipe<?>> recipe(String id) {
        return ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
    }

    static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var recipes = level.getServer().getRecipeManager();
        for (String id : List.of("working_station", "storage_crate", "smithing_table", "medicine_bench", "crusher",
                "herbal_bandage", "healing_mixture", "concentrated_sedative")) {
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

        // The Working Station leaves medicine to the Medicine Bench.
        h.assertTrue(StationContent.medicine(new ItemStack(StationContent.HERBAL_BANDAGE.get()))
                && StationContent.medicine(new ItemStack(StationContent.HEALING_MIXTURE.get()))
                && StationContent.medicine(new ItemStack(ModContent.CONCENTRATED_SEDATIVE.get())), "Medicine tag is incomplete");
        h.assertFalse(StationContent.medicine(new ItemStack(ModContent.FIBER_BANDAGE.get())), "The fiber bandage stays a field craft");

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

        // The crusher table: stone down the chain, ores doubled.
        h.assertTrue(CrusherRecipes.find(new ItemStack(Items.COBBLESTONE)).output() == Items.GRAVEL, "Cobblestone must crush to gravel");
        var ore = CrusherRecipes.find(new ItemStack(Items.IRON_ORE));
        h.assertTrue(ore != null && ore.output() == Items.RAW_IRON && ore.count() == 2, "Iron ore must crush to two raw iron");
        h.assertTrue(CrusherRecipes.find(new ItemStack(Items.STICK)) == null, "Sticks are not crushable");
        h.succeed();
    }

    private StationGameTests() {}
}
