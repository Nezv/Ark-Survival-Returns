package dev.nez.arksurvivalreturns.gametest;

import java.lang.reflect.*;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.cargo.*;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.mass.MassRules;
import dev.nez.arksurvivalreturns.feature.station.StationContent;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class CargoRegressionGameTests {
    private static ServerPlayer player(GameTestHelper h, String name, BlockPos pos) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atBottomCenterOf(pos)); return player;
    }
    private static CreatureEntity cargo(GameTestHelper h, ServerPlayer owner, BlockPos pos) {
        var mob = ModContent.CREATURES.get(Species.TRICERATOPS).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        mob.setNoAi(true); mob.setPos(Vec3.atBottomCenterOf(pos)); TamingService.of(mob).setOwner(owner.getUUID());
        mob.applyTameState(); mob.harnessSlot().setItem(0, new ItemStack(ModContent.PACK_HARNESS.get()));
        h.getLevel().addFreshEntity(mob); GameTestCleanup.onFinish(h, mob::discard); return mob;
    }
    private static Container crate(GameTestHelper h, BlockPos pos, ItemStack content) {
        h.getLevel().setBlock(pos, StationContent.STORAGE_CRATE.get().defaultBlockState(), 3);
        var inventory = (Container) h.getLevel().getBlockEntity(pos); inventory.setItem(0, content); return inventory;
    }
    /** The registered payload handler is exercised; only the network context is a test double. */
    private static void request(ServerPlayer player, int entity, boolean load) {
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> { if (method.getName().equals("player")) return player; throw new UnsupportedOperationException(method.getName()); });
        try {
            var handler = CargoSync.class.getDeclaredMethod("handle", CargoTransferPayload.class, IPayloadContext.class);
            handler.setAccessible(true); handler.invoke(null, new CargoTransferPayload(entity, load), context);
        } catch (InvocationTargetException e) { if (e.getCause() instanceof RuntimeException r) throw r; throw new IllegalStateException(e.getCause()); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    static void requests(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(64, 3, 64));
        var owner = player(h, "ArkCargoPacket", pos.north(3));
        var outsider = player(h, "ArkCargoIntruder", pos.north(3));
        var mob = cargo(h, owner, pos);
        var source = crate(h, pos.east(3), new ItemStack(Items.COPPER_INGOT, 4));
        var pig = EntityType.PIG.create(h.getLevel(), EntitySpawnReason.COMMAND); pig.setNoAi(true); pig.setPos(mob.position()); h.getLevel().addFreshEntity(pig);
        GameTestCleanup.onFinish(h, () -> {
            pig.discard(); CargoSync.loggedOut(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(owner));
            CargoSync.loggedOut(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(outsider));
        });
        request(outsider, mob.getId(), true); request(owner, -1, true); request(owner, pig.getId(), true);
        owner.setPos(mob.position().add(20, 0, 0)); request(owner, mob.getId(), true);
        h.assertTrue(source.countItem(Items.COPPER_INGOT) == 4 && mob.tamingInventory().isEmpty(), "Invalid cargo requests moved inventory");
        owner.setPos(Vec3.atBottomCenterOf(pos.north(3))); request(owner, mob.getId(), true);
        h.assertTrue(source.isEmpty() && mob.tamingInventory().countItem(Items.COPPER_INGOT) == 4, "Valid request was denied or invalid requests consumed its cooldown");
        source.setItem(0, new ItemStack(Items.COPPER_INGOT, 5));
        request(owner, mob.getId(), true); request(owner, mob.getId(), false);
        h.assertTrue(source.countItem(Items.COPPER_INGOT) == 5 && mob.tamingInventory().countItem(Items.COPPER_INGOT) == 4, "Immediate request bypassed cooldown");
        h.runAfterDelay(9, () -> {
            request(owner, mob.getId(), true);
            h.assertTrue(source.countItem(Items.COPPER_INGOT) == 5 && mob.tamingInventory().countItem(Items.COPPER_INGOT) == 4, "Tick 9 bypassed the request cooldown");
        });
        h.runAfterDelay(10, () -> {
            request(owner, mob.getId(), true);
            h.assertTrue(source.isEmpty() && mob.tamingInventory().countItem(Items.COPPER_INGOT) == 9, "Tick 10 did not release the request cooldown");
        });
        h.runAfterDelay(20, () -> {
            request(owner, mob.getId(), false);
            h.assertTrue(source.countItem(Items.COPPER_INGOT) == 9 && mob.tamingInventory().isEmpty(), "Valid unload request lost or duplicated items"); h.succeed();
        });
    }
    static void storageGuards(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(64, 3, 64));
        var owner = player(h, "ArkCargoLocks", pos.north(3)); var mob = cargo(h, owner, pos);
        var chestPos = pos.east(3); h.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (BaseContainerBlockEntity) h.getLevel().getBlockEntity(chestPos);
        ItemStack locked = new ItemStack(Items.CHEST);
        locked.set(DataComponents.LOCK, new LockCode(ItemPredicate.Builder.item().of(h.getLevel().registryAccess().lookupOrThrow(Registries.ITEM), Items.DIAMOND).build()));
        chest.applyComponentsFromItemStack(locked); chest.setItem(0, new ItemStack(Items.EMERALD, 3));
        h.assertTrue(CargoTransferService.load(owner, mob) == 0 && chest.countItem(Items.EMERALD) == 3 && mob.tamingInventory().isEmpty(), "Locked storage was drained");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        h.assertTrue(CargoTransferService.load(owner, mob) == 3 && chest.isEmpty() && mob.tamingInventory().countItem(Items.EMERALD) == 3, "Matching key did not unlock storage");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(CargoTransferService.unload(owner, mob) == 0 && mob.tamingInventory().countItem(Items.EMERALD) == 3, "Unload bypassed the container lock");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        h.assertTrue(CargoTransferService.unload(owner, mob) == 3 && chest.countItem(Items.EMERALD) == 3, "Unlocked unload lost cargo");
        var border = h.getLevel().getWorldBorder(); double x = border.getCenterX(), z = border.getCenterZ(), size = border.getSize();
        try {
            border.setCenter(pos.getX() + .5, pos.getZ() + .5); border.setSize(4);
            h.assertTrue(CargoTransferService.load(owner, mob) == 0 && chest.countItem(Items.EMERALD) == 3 && mob.tamingInventory().isEmpty(), "Cargo reached storage outside the world border");
        } finally { border.setCenter(x, z); border.setSize(size); }
        h.assertTrue(CargoTransferService.load(owner, mob) == 3, "Border guard failed to release valid storage after restoration"); h.succeed();
    }
    static void scanLimits(GameTestHelper h) {
        var base = h.absolutePos(new BlockPos(64, 3, 64));
        var owner = player(h, "ArkCargoLimits", base.north(3)); var mob = cargo(h, owner, base);
        int radius = Config.CARGO_TRANSFER_RADIUS.get(); Config.CARGO_TRANSFER_RADIUS.set(16);
        try {
            var world = h.getLevel(); int chunks = world.getChunkSource().getLoadedChunksCount();
            for (int y : new int[]{world.getMinY() + 1, world.getMaxY() - 2}) {
                var center = new BlockPos(base.getX(), y, base.getZ());
                if (y == world.getMinY() + 1) for (int dx = -17; dx <= 17; dx++) for (int dz = -17; dz <= 17; dz++)
                    for (int dy = 0; dy <= 8; dy++) world.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                mob.setPos(Vec3.atBottomCenterOf(center)); owner.setPos(Vec3.atBottomCenterOf(center.north(3)));
                var aPos = center.offset(-16, 0, -16); var bPos = center.offset(16, Math.min(4, world.getMaxY() - 1 - y), 16);
                var a = crate(h, aPos, new ItemStack(Items.EMERALD)); var b = crate(h, bPos, new ItemStack(Items.LAPIS_LAZULI));
                h.assertTrue(CargoTransferService.load(owner, mob) == 2 && a.isEmpty() && b.isEmpty()
                        && mob.tamingInventory().countItem(Items.EMERALD) == 1 && mob.tamingInventory().countItem(Items.LAPIS_LAZULI) == 1,
                        "Radius 16 scan omitted an outer corner at world height " + y);
                h.assertTrue(CargoTransferService.unload(owner, mob) == 2 && mob.tamingInventory().isEmpty(), "Clipped scan unload lost cargo");
                world.removeBlock(aPos, false); world.removeBlock(bPos, false);
            }
            h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks, "Radius 16/world-height scan loaded chunks");
        } finally { Config.CARGO_TRANSFER_RADIUS.set(radius); }
        h.succeed();
    }
    static void unloadedBoundary(GameTestHelper h) {
        var world = h.getLevel(); var chunks = world.getChunkSource();
        var plotCenter = h.absolutePos(new BlockPos(64, 3, 64));
        var origin = new ChunkPos(plotCenter.getX() >> 4, plotCenter.getZ() >> 4);
        BlockPos center = null; Direction outward = null;
        search: for (var direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            for (int step = 0; step < 128; step++) {
                int x = origin.x() + direction.getStepX() * step, z = origin.z() + direction.getStepZ() * step;
                if (chunks.getChunkNow(x, z) != null && chunks.getChunkNow(x + direction.getStepX(), z + direction.getStepZ()) == null) {
                    center = new BlockPos((x << 4) + (direction == Direction.EAST ? 15 : direction == Direction.WEST ? 0 : 8), 100,
                            (z << 4) + (direction == Direction.SOUTH ? 15 : direction == Direction.NORTH ? 0 : 8)); outward = direction; break search;
                }
            }
        }
        h.assertTrue(center != null, "No genuine loaded/unloaded boundary found for the fixture");
        var absent = center.relative(outward); h.assertFalse(world.isLoaded(absent), "Boundary fixture accidentally loaded its neighbor");
        var chestPos = center.relative(outward.getOpposite(), 2); h.assertTrue(world.getBlockState(chestPos).isAir(), "Boundary fixture would overwrite existing world content");
        var owner = player(h, "ArkCargoBoundary", center.relative(outward.getOpposite(), 3)); var mob = cargo(h, owner, center);
        var inventory = crate(h, chestPos, new ItemStack(Items.EMERALD, 2)); int loaded = chunks.getLoadedChunksCount();
        var cleanupPos = chestPos; GameTestCleanup.onFinish(h, () -> world.removeBlock(cleanupPos, false));
        h.assertTrue(CargoTransferService.load(owner, mob) == 2 && inventory.isEmpty(), "Scan crossing an unloaded chunk skipped valid loaded storage");
        h.assertTrue(CargoTransferService.unload(owner, mob) == 2 && inventory.countItem(Items.EMERALD) == 2, "Boundary unload lost items");
        h.assertFalse(world.isLoaded(absent), "Cargo scan loaded the absent boundary chunk");
        h.assertTrue(chunks.getLoadedChunksCount() == loaded, "Boundary transfer changed the loaded-chunk count"); h.succeed();
    }
    static void tomsNetwork(GameTestHelper h) {
        var world = h.getLevel(); var pos = h.absolutePos(new BlockPos(64, 3, 64));
        var owner = player(h, "ArkTomsNetwork", pos.north(3)); var mob = cargo(h, owner, pos);
        BlockPos connector = pos.east(3);
        world.setBlock(connector, BuiltInRegistries.BLOCK.getValue(Identifier.parse("toms_storage:inventory_connector")).defaultBlockState(), 3);
        for (int x = 4; x <= 18; x++) world.setBlock(pos.east(x), BuiltInRegistries.BLOCK.getValue(Identifier.parse("toms_storage:inventory_cable")).defaultBlockState(), 3);
        world.setBlock(pos.east(19), BuiltInRegistries.BLOCK.getValue(Identifier.parse("toms_storage:inventory_cable_connector")).defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);
        ItemStack named = new ItemStack(Items.DIAMOND, 7); named.set(DataComponents.CUSTOM_NAME, Component.literal("Network cargo"));
        var remote = crate(h, pos.east(20), named.copy());
        h.runAfterDelay(45, () -> {
            int chunks = world.getChunkSource().getLoadedChunksCount();
            var capability = world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, connector, null);
            h.assertTrue(capability != null && capability.size() > 0, "Real cable network never exposed its remote inventory");
            h.assertTrue(CargoTransferService.load(owner, mob) == 7 && remote.isEmpty(), "Cargo did not load through the connector from beyond its direct scan radius");
            h.assertTrue(ItemStack.matches(mob.tamingInventory().getItem(0), named), "Network load lost item components or quantity");
            h.assertTrue(CargoTransferService.unload(owner, mob) == 7 && mob.tamingInventory().isEmpty() && ItemStack.matches(remote.getItem(0), named), "Network round trip lost or duplicated component-bearing cargo");
            var preset = Config.MASS_PRESET.get(); Config.MASS_PRESET.set(MassRules.Preset.OFF);
            try {
                for (int slot = 0; slot < mob.tamingInventory().getContainerSize(); slot++) mob.tamingInventory().setItem(slot, new ItemStack(Items.FEATHER, 64));
                var mismatched = named.copyWithCount(63); mismatched.set(DataComponents.CUSTOM_NAME, Component.literal("Different cargo")); mob.tamingInventory().setItem(0, mismatched);
                h.assertTrue(CargoTransferService.load(owner, mob) == 0 && remote.getItem(0).getCount() == 7, "Network load merged incompatible components into a full hold");
                mob.tamingInventory().setItem(0, named.copyWithCount(63));
                h.assertTrue(CargoTransferService.load(owner, mob) == 1 && remote.getItem(0).getCount() == 6 && mob.tamingInventory().getItem(0).getCount() == 64,
                        "Partial network load failed exact item conservation");
                mob.tamingInventory().clearContent(); mob.tamingInventory().setItem(0, named.copyWithCount(4));
                for (int slot = 0; slot < remote.getContainerSize(); slot++) remote.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                remote.setItem(0, named.copyWithCount(63));
                h.assertTrue(CargoTransferService.unload(owner, mob) == 1 && remote.getItem(0).getCount() == 64 && mob.tamingInventory().getItem(0).getCount() == 3,
                        "Partial network unload failed exact item conservation");
                h.assertTrue(CargoTransferService.unload(owner, mob) == 0 && mob.tamingInventory().getItem(0).getCount() == 3, "Full network storage deleted cargo");
            } finally { Config.MASS_PRESET.set(preset); }
            h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks, "Cable network cargo forced chunk loads"); h.succeed();
        });
    }
    private CargoRegressionGameTests() {}
}
