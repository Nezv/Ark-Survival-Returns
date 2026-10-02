package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.companion.CompanionOrder;
import dev.nez.arksurvivalreturns.feature.companion.CompanionService;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** The registered goals, navigation and world ticks must perform the action, not just set intent. */
final class NavigationGameTests {
    private static void floor(GameTestHelper h) {
        for (int x = 16; x < 112; x++) for (int z = 16; z < 112; z++) h.setBlock(x, 2, z, Blocks.DIRT);
    }
    private static Player owner(GameTestHelper h, BlockPos pos) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(Vec3.atBottomCenterOf(h.absolutePos(pos))); h.getLevel().addFreshEntity(owner);
        GameTestCleanup.onFinish(h, owner::discard);
        return owner;
    }
    private static CreatureEntity tame(GameTestHelper h, Player owner, Species species, BlockPos pos) {
        var mob = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        mob.getRandom().setSeed(0L); mob.initializeLevel(1); mob.setPersistenceRequired();
        mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(pos)));
        TamingService.of(mob).setOwner(owner.getUUID()); mob.applyTameState();
        h.getLevel().addFreshEntity(mob);
        GameTestCleanup.onFinish(h, mob::discard);
        h.assertFalse(mob.isNoAi(), "Navigation fixture accidentally disables AI");
        return mob;
    }
    static Runnable watchChunkLoads(GameTestHelper h) {
        var world = h.getLevel();
        var area = h.getBounds().inflate(16);
        var loads = new java.util.concurrent.ConcurrentLinkedQueue<net.minecraft.world.level.ChunkPos>();
        java.util.function.Consumer<ChunkEvent.Load> listener = event -> {
            var pos = event.getChunk().getPos();
            if (event.getLevel() == world && pos.getMinBlockX() <= area.maxX && pos.getMaxBlockX() >= area.minX
                    && pos.getMinBlockZ() <= area.maxZ && pos.getMaxBlockZ() >= area.minZ) loads.add(pos);
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, listener);
        GameTestCleanup.onFinish(h, () -> NeoForge.EVENT_BUS.unregister(listener));
        // Track the plot and a full chunk beyond it. Other batches have independent chunk tickets.
        return () -> h.assertTrue(loads.isEmpty(), "Navigation loaded chunks around its plot: " + loads);
    }
    static void companion(GameTestHelper h) {
        floor(h);
        var owner = owner(h, new BlockPos(72, 3, 64));
        var mob = tame(h, owner, Species.VELOCIRAPTOR, new BlockPos(56, 3, 64));
        Vec3 start = mob.position();
        CompanionService.setOrder(mob, CompanionOrder.FOLLOW);
        Vec3[] anchor = {null}; boolean[] wandered = {false};
        var noChunkLoads = watchChunkLoads(h);
        h.runAfterDelay(110, () -> {
            h.assertTrue(mob.position().distanceToSqr(start) > 16, "Registered FOLLOW goal never moved its creature");
            h.assertTrue(mob.distanceTo(owner) <= Config.COMPANION_FOLLOW_DISTANCE.get(), "FOLLOW never arrived at its stop distance: " + mob.position());
            CompanionService.setOrder(mob, CompanionOrder.STAY); anchor[0] = mob.position();
            owner.setPos(owner.position().add(12, 0, 0));
        });
        h.runAfterDelay(160, () -> {
            h.assertTrue(mob.position().distanceToSqr(anchor[0]) <= 4, "STAY followed the departing owner");
            mob.getRandom().setSeed(0L);
            CompanionService.setOrder(mob, CompanionOrder.WANDER);
            anchor[0] = Vec3.atBottomCenterOf(CompanionService.of(mob).anchor());
        });
        h.onEachTick(() -> {
            if (anchor[0] != null && CompanionService.of(mob).order() == CompanionOrder.WANDER) {
                double distance = mob.position().subtract(anchor[0]).horizontalDistanceSqr();
                if (distance > 9) wandered[0] = true;
                h.assertTrue(distance <= Math.pow(Config.COMPANION_WANDER_RADIUS.get() + 2, 2), "WANDER escaped its anchor radius");
            }
        });
        h.runAfterDelay(350, () -> {
            h.assertTrue(wandered[0], "WANDER chose intent but never walked away from its anchor");
            noChunkLoads.run();
            mob.discard(); owner.discard(); h.succeed();
        });
    }
    static void blocked(GameTestHelper h) {
        floor(h);
        for (int x = 60; x <= 68; x++) for (int z = 60; z <= 68; z++) for (int y = 3; y <= 8; y++)
            if (x == 60 || x == 68 || z == 60 || z == 68 || y == 8) h.setBlock(x, y, z, Blocks.STONE);
        var owner = owner(h, new BlockPos(78, 3, 64));
        var mob = tame(h, owner, Species.VELOCIRAPTOR, new BlockPos(64, 3, 64));
        CompanionService.setOrder(mob, CompanionOrder.FOLLOW);
        var noChunkLoads = watchChunkLoads(h);
        h.runAfterDelay(60, () -> {
            var enclosure = new net.minecraft.world.phys.AABB(h.absoluteVec(new Vec3(61, 3, 61)), h.absoluteVec(new Vec3(68, 8, 68)));
            h.assertTrue(enclosure.contains(mob.position()), "Blocked companion teleported or passed through its enclosure: " + mob.position() + " bounds=" + enclosure);
            for (int z = 63; z <= 65; z++) for (int y = 3; y <= 7; y++) h.setBlock(68, y, z, Blocks.AIR);
        });
        h.runAfterDelay(200, () -> {
            h.assertTrue(mob.distanceTo(owner) <= Config.COMPANION_FOLLOW_DISTANCE.get(), "Companion did not resume after its route opened");
            noChunkLoads.run();
            mob.discard(); owner.discard(); h.succeed();
        });
    }
    static void work(GameTestHelper h) {
        floor(h);
        int plants = 0;
        for (int x = 72; x <= 80; x++) for (int z = 56; z <= 72; z++) { h.setBlock(x, 3, z, Blocks.SHORT_GRASS); plants++; }
        final int initialPlants = plants;
        var owner = owner(h, new BlockPos(118, 3, 64));
        var mob = tame(h, owner, Species.TRICERATOPS, new BlockPos(64, 3, 64));
        h.assertFalse(dev.nez.arksurvivalreturns.feature.work.WorkGoal.supervised(mob), "Work fixture owner must start outside supervision range");
        mob.harnessSlot().setItem(0, new net.minecraft.world.item.ItemStack(ModContent.PACK_HARNESS.get()));
        CompanionService.setOrder(mob, CompanionOrder.WORK);
        Vec3 start = mob.position(); var noChunkLoads = watchChunkLoads(h);
        boolean[] supervised = {false};
        h.runAfterDelay(30, () -> {
            h.assertTrue(mob.tamingInventory().isEmpty() && mob.position().distanceToSqr(start) < 1, "Unsupervised work moved or harvested");
            owner.setPos(start.add(0, 0, -5)); supervised[0] = true;
        });
        h.onEachTick(() -> {
            int fiber = mob.tamingInventory().countItem(ModContent.PLANT_FIBER.get());
            if (!supervised[0] || fiber == 0) return;
            CompanionService.setOrder(mob, CompanionOrder.STAY);
            h.assertTrue(fiber == Config.WORK_FIBER_PER_ACTION.get(), "One live harvest produced the wrong fiber yield");
            h.assertTrue(mob.position().distanceToSqr(start) > 16, "Work never navigated to the distant target");
            int remaining = 0;
            for (int x = 72; x <= 80; x++) for (int z = 56; z <= 72; z++) if (h.getBlockState(new BlockPos(x, 3, z)).is(Blocks.SHORT_GRASS)) remaining++;
            h.assertTrue(remaining == initialPlants - 1, "Live work did not cut exactly one natural plant");
            noChunkLoads.run();
            mob.discard(); owner.discard(); h.succeed();
        });
        h.runAfterDelay(380, () -> h.fail("Registered WORK goal never found, reached and harvested a supervised target"));
    }
    private NavigationGameTests() {}
}
