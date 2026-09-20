package dev.nez.arksurvivalreturns.feature.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Pure decisions behind the recovery cache: placing it and consolidating old ones. */
public final class RecoveryPolicy {
    /** Copies both lists into one, oldest first; the item data is the only store, so nothing is capped. */
    public static List<ItemStack> merge(List<ItemStack> older, List<ItemStack> newer) {
        return RecoveryMath.merge(older, newer, ItemStack::copy);
    }

    /** How many oldest entries must fold into a new one so no more than {@code max} remain. */
    public static int mergeCount(int current, int max) {
        return RecoveryMath.mergeCount(current, max);
    }

    /**
     * Ring search for a replaceable spot on solid ground, nearest first.
     *
     * <p>Vertical offsets are checked in both directions before widening the ring, so ground-level
     * deaths reuse the exact spot while mid-air or overhanging deaths prefer nearby terrain.
     */
    public static Optional<BlockPos> findSpot(BlockPos origin, int radius, Predicate<BlockPos> canPlace) {
        if (canPlace.test(origin)) return Optional.of(origin);
        for (int ring = 1; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    for (int dy : new int[]{1, -1, 2, -2, 3, -3}) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (canPlace.test(candidate)) return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private RecoveryPolicy() {}
}
