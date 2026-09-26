package dev.nez.arksurvivalreturns.gametest;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.feature.cargo.CargoTransferService;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Integrations listed in config/integrations.json. Each probe touches the other mod's classes only through
 * a nested holder, so the suite still loads (and passes as "not installed") without that mod.
 */
final class IntegrationGameTests {
    /** I07: Ark's accessory set reaches players (two head, necklace, body, belt, legs, two feet, charm). */
    static void curios(GameTestHelper h) {
        if (!ModList.get().isLoaded("curios")) {
            h.succeed();
            return;
        }
        CuriosProbe.check(h);
    }

    /**
     * I04: Load and Unload reach storage that only exposes the item capability: a Tom's Filing Cabinet,
     * which holds unstackable items only.
     */
    static void tomsStorage(GameTestHelper h) {
        if (!ModList.get().isLoaded("toms_storage")) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        var cabinetBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse("toms_storage:filing_cabinet"));
        BlockPos cabinetRel = new BlockPos(11, 3, 8);
        h.setBlock(cabinetRel, cabinetBlock.defaultBlockState());
        BlockPos cabinetPos = h.absolutePos(cabinetRel);
        var handler = level.getCapability(Capabilities.Item.BLOCK, cabinetPos, null);
        h.assertTrue(handler != null, "The filing cabinet must expose the item capability");
        h.assertFalse(level.getBlockEntity(cabinetPos) instanceof net.minecraft.world.Container,
                "The probe needs storage that is not a vanilla Container");
        try (Transaction transaction = Transaction.openRoot()) {
            h.assertTrue(handler.insert(ItemResource.of(Items.IRON_SWORD), 3, transaction) == 3, "The cabinet must accept unstackable tools");
            transaction.commit();
        }
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ArkTomsProbe"));
        CreatureEntity trike = ModContent.CREATURES.get(Species.TRICERATOPS).get().create(level, EntitySpawnReason.COMMAND);
        trike.setNoAi(true);
        trike.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 3, 8))));
        level.addFreshEntity(trike);
        TamingService.of(trike).setOwner(owner.getUUID());
        trike.harnessSlot().setItem(0, new ItemStack(ModContent.PACK_HARNESS.get()));
        h.assertTrue(CargoTransferService.load(owner, trike) == 3, "Fast Load must pull the cabinet's swords");
        h.assertTrue(trike.tamingInventory().countItem(Items.IRON_SWORD) == 3, "The swords must land in the cargo");
        h.assertTrue(CargoTransferService.unload(owner, trike) == 3, "Fast Unload must return them to the cabinet");
        h.assertTrue(contains(handler, 3), "The cabinet must hold the swords again");
        trike.discard();
        h.succeed();
    }

    private static boolean contains(net.neoforged.neoforge.transfer.ResourceHandler<ItemResource> handler, int amount) {
        int total = 0;
        for (int i = 0; i < handler.size(); i++) if (handler.getResource(i).is(Items.IRON_SWORD)) total += handler.getAmountAsInt(i);
        return total >= amount;
    }

    /** I08: Terralith biomes inherit Ark habitats through their vanilla analog; danger comes from the area only. */
    static void terralith(GameTestHelper h) {
        if (!ModList.get().isLoaded("terralith")) {
            h.succeed();
            return;
        }
        var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        var rainforest = biomes.get(ResourceKey.create(Registries.BIOME, Identifier.parse("terralith:amethyst_rainforest")));
        h.assertTrue(rainforest.isPresent(), "Terralith's amethyst rainforest must be registered");
        TagKey<net.minecraft.world.level.biome.Biome> raptors = TagKey.create(Registries.BIOME, Identifier.parse("arksurvivalreturns:spawns/velociraptor"));
        h.assertTrue(rainforest.get().is(raptors), "A jungle-like Terralith biome must spawn raptors");
        TagKey<net.minecraft.world.level.biome.Biome> wetland = TagKey.create(Registries.BIOME, Identifier.parse("arksurvivalreturns:habitat/wetland"));
        h.assertTrue(rainforest.get().is(wetland), "A jungle-like Terralith biome must also be crocodilian wetland");
        h.succeed();
    }

    /** Kept apart so Curios classes load only when Curios is installed. */
    /**
     * I11: Better Combat loads Ark's movesets: the keratin spear stabs two-handed with extra reach, the knives
     * slash as daggers and the hatchet as an axe.
     */
    static void betterCombat(GameTestHelper h) {
        if (!ModList.get().isLoaded("bettercombat")) {
            h.succeed();
            return;
        }
        BetterCombatProbe.check(h);
    }

    private static final class BetterCombatProbe {
        static void check(GameTestHelper h) {
            var spear = net.bettercombat.logic.WeaponRegistry.getAttributes(
                    new ItemStack(dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent.KERATIN_SPEAR.get()));
            h.assertTrue(spear != null && "spear".equals(spear.category()) && spear.isTwoHanded() && spear.rangeBonus() > 0,
                    "The keratin spear has no Better Combat spear moveset");
            var knife = net.bettercombat.logic.WeaponRegistry.getAttributes(
                    new ItemStack(dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent.STONE_KNIFE.get()));
            h.assertTrue(knife != null && "dagger".equals(knife.category()), "The stone knife must swing as a dagger");
            var hatchet = net.bettercombat.logic.WeaponRegistry.getAttributes(
                    new ItemStack(dev.nez.arksurvivalreturns.feature.primitive.PrimitiveContent.STONE_HATCHET.get()));
            h.assertTrue(hatchet != null && "axe".equals(hatchet.category()), "The stone hatchet must swing as an axe");
            h.succeed();
        }
    }

    private static final class CuriosProbe {
        static void check(GameTestHelper h) {
            var slots = top.theillusivec4.curios.api.CuriosSlotTypes.getDefaultEntitySlotTypes(EntityType.PLAYER, false);
            for (String id : new String[]{"head", "necklace", "body", "belt", "legs", "feet", "charm"}) {
                h.assertTrue(slots.containsKey(id), "Players are missing the Ark curio slot " + id);
            }
            h.assertTrue(slots.get("head").getSize() >= 2, "Ark gives players two head slots (crown, hat)");
            h.assertTrue(slots.get("feet").getSize() >= 2, "Ark gives players two feet slots (socks, shoes)");
            if (arkFork()) layout(h);
            h.succeed();
        }

        /** The Ark fork of Curios places slots by ArkLayout; upstream Curios keeps its side panel. */
        private static boolean arkFork() {
            try {
                Class.forName("top.theillusivec4.curios.common.inventory.container.ArkLayout");
                return true;
            } catch (ClassNotFoundException upstream) {
                return false;
            }
        }

        private static void layout(GameTestHelper h) {
            var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ArkCuriosProbe"));
            // A fake player never joins the world, so its curio slots are built explicitly.
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.reset());
            var menu = new top.theillusivec4.curios.common.inventory.container.CuriosMenu(1, player.getInventory());
            java.util.Map<String, int[]> expected = java.util.Map.of("head0", new int[]{8, 8}, "head1", new int[]{26, 8},
                    "necklace0", new int[]{8, 26}, "body0", new int[]{26, 26}, "belt0", new int[]{8, 44},
                    "legs0", new int[]{26, 44}, "feet0", new int[]{8, 62}, "feet1", new int[]{26, 62}, "charm0", new int[]{134, 62});
            int placed = 0;
            for (var slot : menu.slots) {
                if (!(slot instanceof top.theillusivec4.curios.common.inventory.CurioSlot curio)) continue;
                int[] want = expected.get(curio.getIdentifier() + curio.getSlotIndex());
                if (want == null) continue;
                h.assertTrue(slot.x == want[0] && slot.y == want[1],
                        "Curio slot " + curio.getIdentifier() + curio.getSlotIndex() + " is at " + slot.x + "," + slot.y);
                placed++;
            }
            h.assertTrue(placed == expected.size(), "The Ark layout placed " + placed + " of " + expected.size() + " curio slots");
            h.assertTrue(menu.panelWidth == 0, "No Ark slot may spill into the side panel");
        }
    }

    private IntegrationGameTests() {}
}
