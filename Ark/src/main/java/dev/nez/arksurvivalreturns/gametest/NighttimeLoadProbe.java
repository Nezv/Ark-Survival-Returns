package dev.nez.arksurvivalreturns.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/** Opt-in AI batch timings, including real perception/path requests. Not a whole-server TPS benchmark. */
final class NighttimeLoadProbe {
    static void run(GameTestHelper h) {
        var world = h.getLevel(); var clock = world.dimensionType().defaultClock().orElseThrow();
        long originalTime = world.getDefaultClockTime(); boolean enabled = Config.NIGHTTIME.get();
        int chunks = world.getChunkSource().getLoadedChunksCount();
        try {
            for (long time : new long[]{6000, 18000}) for (int count : new int[]{24, 96, 192}) for (boolean active : new boolean[]{false, true}) {
                world.clockManager().setTotalTicks(clock, time); Config.NIGHTTIME.set(active);
                var creatures = new ArrayList<CreatureEntity>();
                try {
                    for (int i = 0; i < count; i++) {
                        var species = i % 4 == 0 ? Species.VELOCIRAPTOR : Species.TRICERATOPS;
                        var mob = ModContent.CREATURES.get(species).get().create(world, EntitySpawnReason.COMMAND);
                        mob.setUUID(new UUID(0x4E494748544C4F41L, i + 1));
                        mob.initializeLevel(1); mob.setNoAi(true); mob.setOnGround(true);
                        mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(20 + i % 14 * 6, 2, 20 + i / 14 * 6))));
                        mob.yBodyRot = 0;
                        mob.wildlife().mind().restoreNeeds(species.predator ? 0.8 : 0.2, 0.1, 0);
                        world.addFreshEntity(mob); creatures.add(mob);
                    }
                    long[] samples = new long[21];
                    for (int batch = -5; batch < samples.length; batch++) {
                        long start = System.nanoTime();
                        for (var mob : creatures) { mob.tickCount += 10; mob.wildlife().think(); }
                        if (batch >= 0) samples[batch] = System.nanoTime() - start;
                    }
                    Arrays.sort(samples);
                    long hunts = creatures.stream().filter(c -> c.behavior().combat()).count();
                    com.mojang.logging.LogUtils.getLogger().info(
                            "NIGHTTIME_PROBE count={} phase={} enabled={} median_ms={} p95_ms={} combat={}",
                            count, time == 6000 ? "day" : "night", active, samples[10] / 1e6, samples[19] / 1e6, hunts);
                } finally { creatures.forEach(Entity::discard); }
            }
            h.assertTrue(chunks == world.getChunkSource().getLoadedChunksCount(), "Nighttime load probe forced chunks");
        } finally { world.clockManager().setTotalTicks(clock, originalTime); Config.NIGHTTIME.set(enabled); }
    }
    private NighttimeLoadProbe() {}
}
