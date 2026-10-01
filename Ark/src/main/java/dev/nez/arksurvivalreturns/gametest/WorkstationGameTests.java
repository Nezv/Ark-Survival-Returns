package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent;
import dev.nez.arksurvivalreturns.feature.station.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class WorkstationGameTests {
    static void assertCraftable(GameTestHelper h, String item) {
        h.assertTrue(WorkstationCatalog.all().values().stream().flatMap(s -> s.crafts().stream())
                .anyMatch(c -> c.variant().item().equals(item)), "Missing workstation recipe: " + item);
    }
    /** Exercise the same entry point as a client craft packet, with real inventory and tags. */
    static void run(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(java.util.UUID.randomUUID(), "ArkWorkstation"));
        BlockPos rel = new BlockPos(3, 2, 3), pos = h.absolutePos(rel);
        h.setBlock(rel, StationContent.ARMOURY.get().defaultBlockState());
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        var menu = new WorkstationMenu(77, player.getInventory(), pos, "arksurvivalreturns:armoury");
        player.containerMenu = menu;
        var inventory = player.getInventory(); inventory.clearContent();
        inventory.setItem(0, new ItemStack(PrimitiveContent.SHARP_ROCK.get(), 5));
        inventory.setItem(1, new ItemStack(Items.STICK, 7));
        inventory.setItem(2, new ItemStack(Items.FEATHER, 3));
        var allArrows = new WorkstationPayload.Craft(menu.station, "minecraft:arrow", -1, "i:wood/arrow", 0, 77);
        ArkLevels.setLevel(player, 0); player.experienceLevel = 0;
        if (Config.WORKSTATION_LEVEL_GATE.get()) h.assertFalse(WorkstationCrafting.craft(player, allArrows), "Low level must not craft arrows");
        h.assertTrue(inventory.getItem(0).getCount() == 5, "Rejected craft consumed sharp rocks");
        ArkLevels.setLevel(player, 50); player.experienceLevel = 50;
        player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
        h.assertFalse(WorkstationCrafting.craft(player, allArrows), "A remote packet must not craft");
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        h.assertFalse(WorkstationCrafting.craft(player, new WorkstationPayload.Craft("arksurvivalreturns:working_station", "minecraft:arrow", 1, "i:wood/arrow", 0, 77)), "Wrong bench must be refused");
        h.assertFalse(WorkstationCrafting.craft(player, new WorkstationPayload.Craft(menu.station, "minecraft:diamond", 1, "i:wood/arrow", 0, 77)), "Forged output must be refused");
        h.assertFalse(WorkstationCrafting.craft(player, new WorkstationPayload.Craft(menu.station, "minecraft:arrow", Integer.MAX_VALUE, "i:wood/arrow", 0, 77)), "Overflow craft must be refused");
        h.assertTrue(WorkstationCrafting.craft(player, allArrows), "Shift must craft the affordable maximum");
        h.assertTrue(inventory.countItem(Items.ARROW) == 12 && inventory.countItem(Items.FEATHER) == 0
                && inventory.countItem(PrimitiveContent.SHARP_ROCK.get()) == 2 && inventory.countItem(Items.STICK) == 4, "Shift paid the wrong cost or output");
        // With every slot occupied and ingredients only partly consumed, output must drop.
        for (int s = 0; s < inventory.getNonEquipmentItems().size(); s++) inventory.setItem(s, new ItemStack(Items.COBBLESTONE, 64));
        inventory.setItem(0, new ItemStack(PrimitiveContent.SHARP_ROCK.get(), 64));
        inventory.setItem(1, new ItemStack(Items.STICK, 64)); inventory.setItem(2, new ItemStack(Items.FEATHER, 64));
        h.assertTrue(WorkstationCrafting.craft(player, new WorkstationPayload.Craft(menu.station, "minecraft:arrow", 1, "i:wood/arrow", 0, 77)), "Full inventory must still craft");
        var dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4), e -> e.getItem().is(Items.ARROW));
        h.assertTrue(dropped.stream().mapToInt(e -> e.getItem().getCount()).sum() == 4, "Overflow did not drop four arrows");
        dropped.forEach(ItemEntity::discard);
        // Mixed wood pays the two tags in the proposed Armoury recipe.
        h.setBlock(rel, StationContent.WORKING_STATION.get().defaultBlockState());
        menu = new WorkstationMenu(78, inventory, pos, "arksurvivalreturns:working_station"); player.containerMenu = menu;
        inventory.clearContent(); inventory.setItem(0, new ItemStack(Items.OAK_LOG)); inventory.setItem(1, new ItemStack(Items.SPRUCE_LOG));
        inventory.setItem(2, new ItemStack(Items.OAK_PLANKS, 2)); inventory.setItem(3, new ItemStack(Items.BIRCH_PLANKS, 2));
        inventory.setItem(4, new ItemStack(PrimitiveContent.ROCK.get(), 4)); inventory.setItem(5, new ItemStack(ModContent.PLANT_FIBER.get(), 3));
        h.assertTrue(WorkstationCrafting.craft(player, new WorkstationPayload.Craft(menu.station, "arksurvivalreturns:armoury", 1, "i:camp/stations/armoury", 0, 78)),
                "Registered planned Armoury must craft at its proposed mixed-tag cost");
        h.assertTrue(inventory.countItem(StationContent.ARMOURY_ITEM.get()) == 1, "Armoury output missing");
        // Trim an actual damaged, named armour piece; components must survive the inventory craft.
        h.setBlock(rel, StationContent.SMITHING_TABLE.get().defaultBlockState());
        menu = new WorkstationMenu(79, inventory, pos, "arksurvivalreturns:smithing_table"); player.containerMenu = menu;
        inventory.clearContent();
        var smith = WorkstationCatalog.get(menu.station);
        var trim = smith.crafts().stream().filter(c -> c.variant().apply() && c.variant().item().equals("minecraft:coast_armor_trim_smithing_template")).findFirst().orElseThrow();
        ItemStack armour = new ItemStack(Items.IRON_CHESTPLATE);
        armour.set(net.minecraft.core.component.DataComponents.DAMAGE, 7);
        armour.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Bound armour"));
        inventory.setItem(0, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE)); inventory.setItem(1, armour); inventory.setItem(2, new ItemStack(Items.IRON_INGOT));
        h.assertTrue(WorkstationCrafting.craft(player, new WorkstationPayload.Craft(menu.station, trim.variant().item(), 1,
                "i:trims/armour_trim", trim.entry().variants().indexOf(trim.variant()), 79)), "Smithing graph must apply a trim");
        ItemStack trimmed = inventory.getNonEquipmentItems().stream().filter(s -> s.is(Items.IRON_CHESTPLATE)).findFirst().orElseThrow();
        h.assertTrue(trimmed.has(net.minecraft.core.component.DataComponents.TRIM) && trimmed.getDamageValue() == 7
                && trimmed.getHoverName().getString().equals("Bound armour"), "Trimming lost armour components");
        // Tonics keep the effect in the original result data instead of producing an empty stew.
        h.setBlock(rel, StationContent.MEDICINE_BENCH.get().defaultBlockState());
        menu = new WorkstationMenu(80, inventory, pos, "arksurvivalreturns:medicine_bench"); player.containerMenu = menu;
        inventory.clearContent();
        var medicine = WorkstationCatalog.get(menu.station);
        var tonic = medicine.crafts().stream().filter(c -> c.variant().item().equals("minecraft:suspicious_stew") && c.variant().cost().containsKey("minecraft:poppy")).findFirst().orElseThrow();
        int slot = 0;
        for (var cost : tonic.variant().cost().entrySet()) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(cost.getKey()));
            inventory.setItem(slot++, new ItemStack(item, cost.getValue()));
        }
        h.assertTrue(WorkstationCrafting.craft(player, new WorkstationPayload.Craft(menu.station, tonic.variant().item(), 1,
                "i:tonics/suspicious_stew", tonic.entry().variants().indexOf(tonic.variant()), 80)), "Medicine graph must mix the flower tonic");
        ItemStack stew = inventory.getNonEquipmentItems().stream().filter(s -> s.is(Items.SUSPICIOUS_STEW)).findFirst().orElseThrow();
        h.assertTrue(stew.has(net.minecraft.core.component.DataComponents.SUSPICIOUS_STEW_EFFECTS), "The flower's tonic effect was lost");
        h.setBlock(rel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        h.assertFalse(WorkstationCrafting.craft(player, new WorkstationPayload.Craft(menu.station, "arksurvivalreturns:armoury", 1, "i:camp/stations/armoury", 0, 78)), "A removed bench must invalidate its menu");
        player.containerMenu = player.inventoryMenu; inventory.clearContent();
    }
    private WorkstationGameTests() {}
}
