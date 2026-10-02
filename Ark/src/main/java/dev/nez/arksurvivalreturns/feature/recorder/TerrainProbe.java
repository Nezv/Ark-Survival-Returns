package dev.nez.arksurvivalreturns.feature.recorder;

import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * The blocks around a body that stopped moving: every block that is not air in the body box plus a
 * margin, with what it does to movement. Coordinates alone cannot say that a trunk or a canopy was in
 * the way; this can. Only loaded chunks are read, the scan is bounded and the record says when it was
 * clipped or cut short.
 */
final class TerrainProbe {
    /** Block positions read by one capture at most; a Titanosaur's lower body fits. */
    static final int MAX_SCAN = 40_000;
    /** Blocks listed by one capture at most. */
    static final int MAX_BLOCKS = 4_000;
    static final int MARGIN = 2;
    /** Collision kinds: nothing, a full cube, a partial shape (bounds follow in {@code shapes}), a fluid. */
    static final int NONE = 0, FULL = 1, PARTIAL = 2, FLUID = 3;

    static Row capture(ServerLevel level, AABB body) {
        int x0 = Mth.floor(body.minX) - MARGIN, x1 = Mth.floor(body.maxX) + MARGIN;
        int z0 = Mth.floor(body.minZ) - MARGIN, z1 = Mth.floor(body.maxZ) + MARGIN;
        // The block under the feet up to one block over the head.
        int y0 = Math.max(level.getMinY(), Mth.floor(body.minY) - 1), y1 = Math.min(level.getMaxY(), Mth.floor(body.maxY) + 1);
        boolean clipped = false;
        long area = (long) (x1 - x0 + 1) * (z1 - z0 + 1);
        if (area * (y1 - y0 + 1) > MAX_SCAN) {
            // Trunks, canopy edges and ledges stop a body near the ground; a giant loses its upper layers first.
            y1 = y0 + (int) Math.max(3, MAX_SCAN / area) - 1;
            clipped = true;
        }
        var row = new Row("terrain").block("o", x0, y0, z0).block("size", x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
                .doubles("box", new double[]{body.minX, body.minY, body.minZ, body.maxX, body.maxY, body.maxZ});
        var loaded = new Loaded(level);
        var palette = new HashMap<Block, Integer>();
        var names = new ArrayList<String>();
        var blocks = new IntList();
        var shapes = new IntList();
        var pos = new BlockPos.MutableBlockPos();
        int scanned = 0, unloaded = 0, listed = 0;
        boolean truncated = false;
        scan:
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) { unloaded += y1 - y0 + 1; continue; }
                for (int y = y0; y <= y1; y++) {
                    if (++scanned > MAX_SCAN) { truncated = true; break scan; }
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) continue;
                    if (listed >= MAX_BLOCKS) { truncated = true; break scan; }
                    var shape = state.getCollisionShape(loaded, pos);
                    int kind = shape.isEmpty() ? (state.getFluidState().isEmpty() ? NONE : FLUID)
                            : Block.isShapeFullBlock(shape) ? FULL : PARTIAL;
                    Integer index = palette.get(state.getBlock());
                    if (index == null) {
                        index = names.size();
                        palette.put(state.getBlock(), index);
                        names.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                    }
                    if (kind == PARTIAL) {
                        var bounds = shape.bounds();
                        shapes.add(listed);
                        shapes.add((int) Math.round(bounds.minX * 16)); shapes.add((int) Math.round(bounds.minY * 16));
                        shapes.add((int) Math.round(bounds.minZ * 16)); shapes.add((int) Math.round(bounds.maxX * 16));
                        shapes.add((int) Math.round(bounds.maxY * 16)); shapes.add((int) Math.round(bounds.maxZ * 16));
                    }
                    blocks.add(x - x0); blocks.add(y - y0); blocks.add(z - z0); blocks.add(index); blocks.add(kind);
                    listed++;
                }
            }
        }
        row.list("pal", names).ints("b", blocks.toArray());
        if (shapes.size > 0) row.ints("shapes", shapes.toArray());
        return row.put("scanned", scanned).put("unloaded", unloaded).flag("clip", clipped).flag("trunc", truncated);
    }

    /** Collision shapes may ask for a neighbour or a block entity; an unloaded chunk answers as solid, never loads. */
    private record Loaded(ServerLevel level) implements BlockGetter {
        private boolean has(BlockPos pos) { return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null; }
        @Override public BlockState getBlockState(BlockPos pos) {
            return has(pos) ? level.getBlockState(pos) : Blocks.BARRIER.defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos) {
            return has(pos) ? level.getFluidState(pos) : Fluids.EMPTY.defaultFluidState();
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return has(pos) ? level.getBlockEntity(pos) : null; }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }

    private static final class IntList {
        int[] values = new int[256];
        int size;
        void add(int value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, size * 2);
            values[size++] = value;
        }
        int[] toArray() { return java.util.Arrays.copyOf(values, size); }
    }

    private TerrainProbe() {}
}
