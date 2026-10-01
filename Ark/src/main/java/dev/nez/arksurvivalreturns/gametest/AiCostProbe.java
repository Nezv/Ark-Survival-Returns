package dev.nez.arksurvivalreturns.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;
import com.mojang.logging.LogUtils;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorLod;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Throwaway benchmark: server tick time per entity, vanilla villagers and cows against wild dinosaurs. */
final class AiCostProbe {
    private static final int WARMUP = 200, MEASURE = 400, SIZE = 128;
    private static final IdentityHashMap<Entity, Long> STARTED = new IdentityHashMap<>();
    private static final IdentityHashMap<Entity, Boolean> TRACKED = new IdentityHashMap<>();
    private static boolean registered, measuring;
    private static long tickNanos, totalNanos, samples, maxNanos;
    private static final List<Double> perTick = new ArrayList<>();

    private record Phase(String name, Function<GameTestHelper, List<Entity>> spawn, Vec3 observer) {}

    static void run(GameTestHelper h) {
        register();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
        var center = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(SIZE / 2, 1, SIZE / 2)));
        var phases = List.of(
                new Phase("cow", g -> vanilla(g, EntityType.COW, 16, 3), null),
                new Phase("villager_unemployed", g -> vanilla(g, EntityType.VILLAGER, 16, 3), null),
                new Phase("villager_beds_and_jobs", g -> village(g), null),
                new Phase("parasaur_full", g -> dinos(g, Species.PARASAUR, 16, 4), null),
                new Phase("triceratops_full", g -> dinos(g, Species.TRICERATOPS, 16, 7), null),
                new Phase("tyrannosaurus_full", g -> dinos(g, Species.TYRANNOSAURUS, 16, 14), null),
                new Phase("tyrannosaurus_ambient", g -> dinos(g, Species.TYRANNOSAURUS, 16, 14), center.add(100, 0, 0)),
                new Phase("tyrannosaurus_dormant", g -> dinos(g, Species.TYRANNOSAURUS, 16, 14), center.add(1000, 0, 0)),
                new Phase("titanosaur_full", g -> dinos(g, Species.TITANOSAUR, 9, 40), null),
                new Phase("titanosaur_dormant", g -> dinos(g, Species.TITANOSAUR, 9, 40), center.add(1000, 0, 0)));
        var state = new int[]{0, 0};
        var alive = new ArrayList<Entity>();
        h.onEachTick(() -> {
            if (state[0] >= phases.size()) return;
            var phase = phases.get(state[0]);
            int t = state[1]++;
            if (t == 0) {
                BehaviorLod.useTestObservers(phase.observer() == null ? null : List.of(phase.observer()));
                h.setTime(8000);
                alive.addAll(phase.spawn().apply(h));
                alive.forEach(e -> TRACKED.put(e, true));
            } else if (t == WARMUP) {
                tickNanos = totalNanos = samples = maxNanos = 0; perTick.clear(); measuring = true;
            } else if (t > WARMUP && t <= WARMUP + MEASURE) {
                perTick.add(tickNanos / 1000.0 / Math.max(1, alive.size())); tickNanos = 0;
            }
            if (t == WARMUP + MEASURE) {
                measuring = false;
                var sorted = perTick.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                long tiers = alive.stream().filter(e -> e instanceof CreatureEntity).count();
                String tier = alive.stream().filter(e -> e instanceof CreatureEntity).map(e -> ((CreatureEntity) e).behaviorTier().name())
                        .distinct().sorted().reduce((a, b) -> a + "+" + b).orElse("-");
                LogUtils.getLogger().info("AI_COST_PROBE phase={} entities={} tier={} mean_us_per_entity_tick={} median_us={} p95_us={} max_single_us={} samples={}",
                        phase.name(), alive.size(), tiers > 0 ? tier : "vanilla", String.format("%.1f", totalNanos / 1000.0 / Math.max(1, samples)),
                        String.format("%.1f", sorted[sorted.length / 2]), String.format("%.1f", sorted[(int) (sorted.length * 0.95)]),
                        String.format("%.1f", maxNanos / 1000.0), samples);
                alive.forEach(Entity::discard); alive.clear(); TRACKED.clear(); STARTED.clear();
                for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++)
                    for (int y = 1; y < 4; y++) if (!h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, y, z))).isAir())
                        h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                BehaviorLod.useTestObservers(null);
                state[0]++; state[1] = 0;
                if (state[0] >= phases.size()) h.succeed();
            }
        });
    }

    private static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityTickEvent.Pre.class, e -> {
            if (measuring && TRACKED.containsKey(e.getEntity())) STARTED.put(e.getEntity(), System.nanoTime());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Post.class, e -> {
            Long start = STARTED.remove(e.getEntity());
            if (start == null || !measuring) return;
            long spent = System.nanoTime() - start;
            tickNanos += spent; totalNanos += spent; samples++; maxNanos = Math.max(maxNanos, spent);
        });
    }

    private static List<Entity> vanilla(GameTestHelper h, EntityType<? extends Mob> type, int count, int spacing) {
        var out = new ArrayList<Entity>();
        var world = h.getLevel();
        for (int i = 0; i < count; i++) {
            var mob = type.create(world, EntitySpawnReason.COMMAND);
            var pos = h.absolutePos(new BlockPos(SIZE / 2 - 6 + i % 4 * spacing, 1, SIZE / 2 - 6 + i / 4 * spacing));
            mob.snapTo(Vec3.atBottomCenterOf(pos), 0, 0);
            mob.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
            world.addFreshEntity(mob); out.add(mob);
        }
        return out;
    }

    /** Sixteen villagers with a bed and a lectern each within reach, as in a settled village. */
    private static List<Entity> village(GameTestHelper h) {
        for (int i = 0; i < 16; i++) {
            var foot = new BlockPos(30 + i * 4, 1, 40);
            h.setBlock(foot, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
            h.setBlock(foot.north(), Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
            h.setBlock(new BlockPos(30 + i * 4, 1, 80), Blocks.LECTERN);
        }
        return vanilla(h, EntityType.VILLAGER, 16, 3);
    }

    private static List<Entity> dinos(GameTestHelper h, Species species, int count, int spacing) {
        var out = new ArrayList<Entity>();
        var world = h.getLevel();
        int side = (int) Math.ceil(Math.sqrt(count));
        int start = SIZE / 2 - (side - 1) * spacing / 2;
        SpawnGroupData group = null;
        for (int i = 0; i < count; i++) {
            CreatureEntity mob = ModContent.CREATURES.get(species).get().create(world, EntitySpawnReason.COMMAND);
            var pos = h.absolutePos(new BlockPos(start + i % side * spacing, 1, start + i / side * spacing));
            mob.snapTo(Vec3.atBottomCenterOf(pos), (i * 47) % 360, 0);
            group = mob.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, group);
            world.addFreshEntity(mob); out.add(mob);
        }
        return out;
    }

    private AiCostProbe() {}
}
