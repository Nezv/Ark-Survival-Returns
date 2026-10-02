package dev.nez.arksurvivalreturns.feature.creature;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation of a creature. While a large carnivore tramples ({@link CreatureEntity#tramplesTrees()})
 * its paths are planned as if natural trees were not there; {@link TreeTrample} then knocks down what the
 * body meets. A log wall is still a wall, so the path goes round it.
 *
 * <p>A body wider than a block is placed on its path by the corner of its footprint: the footprint check, the
 * node-reached test and the steering target all read a node as that corner. Vanilla starts the path at the block
 * under the body's centre and ends it at the target block, also read as corners, so every new path first led half
 * a body to one side and ended half a body past its target; a Giganotosaurus turned away from its prey at the
 * start of each path, lost sight of it and began its warning again. Start and target are moved to the corner that
 * puts the centre where the body is and where it is going.
 */
final class CreatureNavigation extends GroundPathNavigation {
    CreatureNavigation(CreatureEntity creature, Level level) {
        super(creature, level);
    }

    @Override protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new Evaluator();
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    private static final class Evaluator extends WalkNodeEvaluator {
        @Override public void prepare(PathNavigationRegion level, Mob mob) {
            super.prepare(level, mob);
            if (mob instanceof CreatureEntity creature && creature.tramplesTrees()) currentContext = new ThroughTrees(level, mob);
        }

        @Override public Node getStart() {
            Node start = super.getStart();
            if (entityWidth < 2) return start;
            return getStartNode(new BlockPos(corner(mob.getX()), start.y, corner(mob.getZ())));
        }

        @Override public Target getTarget(double x, double y, double z) {
            if (entityWidth < 2) return super.getTarget(x, y, z);
            return super.getTarget(corner(x + 0.5), y, corner(z + 0.5));
        }

        /** The footprint corner whose node puts the body's centre at this coordinate. */
        private int corner(double center) {
            return Mth.floor(center - entityWidth / 2.0 + 0.5);
        }
    }

    /** Sees air where a natural tree stands; the shared path type cache keeps the real answer for everyone else. */
    private static final class ThroughTrees extends PathfindingContext {
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        /** Trunk columns already judged during this search: a tree, or logs someone stacked. */
        private final Long2BooleanOpenHashMap trunks = new Long2BooleanOpenHashMap();

        ThroughTrees(PathNavigationRegion level, Mob mob) {
            super(level, mob);
        }

        @Override public PathType getPathTypeFromState(int x, int y, int z) {
            BlockState state = getBlockState(probe.set(x, y, z));
            if (TreeTrample.untended(state)) return PathType.OPEN;
            if (state.is(BlockTags.LOGS)) {
                long column = BlockPos.asLong(x, 0, z);
                if (!trunks.containsKey(column)) trunks.put(column, TreeTrample.naturalTrunk(this::getBlockState, new BlockPos(x, y, z)));
                if (trunks.get(column)) return PathType.OPEN;
            }
            return super.getPathTypeFromState(x, y, z);
        }
    }
}
