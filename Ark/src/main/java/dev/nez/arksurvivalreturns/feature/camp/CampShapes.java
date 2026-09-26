package dev.nez.arksurvivalreturns.feature.camp;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Precomputed outlines keep the open camp models' collision independent of their detail count. */
public final class CampShapes {
    public static Map<Direction, VoxelShape> horizontal(VoxelShape north) {
        var result = new EnumMap<Direction, VoxelShape>(Direction.class);
        result.put(Direction.NORTH, north);
        VoxelShape current = north;
        for (Direction direction : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape[] next = {Shapes.empty()};
            current.forAllBoxes((x, y, z, xx, yy, zz) -> next[0] = Shapes.or(next[0],
                    Shapes.box(1 - zz, y, x, 1 - z, yy, xx)));
            current = next[0].optimize();
            result.put(direction, current);
        }
        return Map.copyOf(result);
    }

    private CampShapes() {}
}
