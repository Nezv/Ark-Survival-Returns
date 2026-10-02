package dev.nez.arksurvivalreturns.feature.creature;

import java.util.HashSet;
import java.util.function.Function;
import dev.nez.arksurvivalreturns.feature.recorder.SessionRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * A large carnivore in pursuit goes through the forest instead of around it: a trunk in its way falls
 * and the foliage it pushes into breaks, the way a Ravager breaks leaves. Only natural trees give way
 * (untended leaves grow on the trunk), so a log wall or a hedge someone planted stands; nothing drops,
 * and a sapling is left where a rooted trunk stood. The mob griefing rule switches it off.
 */
public final class TreeTrample {
    /** Bodies at least this wide cannot pass between forest trunks. */
    public static final double MIN_WIDTH = 3.0;
    private static final int MAX_BLOCKS = 160, MAX_TRUNK = 48;
    private static final BlockState UNKNOWN = Blocks.VOID_AIR.defaultBlockState();

    /** Foliage nobody placed: it grew, and it gives way. */
    public static boolean untended(BlockState state) {
        return state.is(BlockTags.LEAVES) && (!state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT));
    }

    /**
     * A log is part of a tree when untended leaves grow beside it, higher on its column or around its top;
     * a log wall has none. Reads blocks only through {@code blocks}, which must not load chunks.
     */
    public static boolean naturalTrunk(Function<BlockPos, BlockState> blocks, BlockPos log) {
        var cursor = log.mutable();
        for (int height = 0; height < MAX_TRUNK && blocks.apply(cursor).is(BlockTags.LOGS); height++) {
            if (leavesWithin(blocks, cursor, 1, 0)) return true;
            cursor.move(0, 1, 0);
        }
        return leavesWithin(blocks, cursor, 2, 1);
    }

    private static boolean leavesWithin(Function<BlockPos, BlockState> blocks, BlockPos center, int radius, int above) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, above, radius)))
            if (untended(blocks.apply(pos))) return true;
        return false;
    }

    /** Block lookup that answers from loaded chunks only. */
    public static Function<BlockPos, BlockState> loaded(ServerLevel world) {
        return pos -> at(world, pos);
    }

    /** Breaks the natural tree blocks the body is pushing into. Returns how many blocks went. */
    public static int clear(ServerLevel world, CreatureEntity mob) {
        if (!EventHooks.canEntityGrief(world, mob)) return 0;
        Function<BlockPos, BlockState> blocks = loaded(world);
        Vec3 motion = mob.getDeltaMovement();
        Vec3 ahead = new Vec3(motion.x, 0, motion.z);
        if (ahead.lengthSqr() < 1.0E-4) ahead = Vec3.directionFromRotation(0, mob.getYRot());
        AABB box = mob.getBoundingBox().inflate(0.25, 0, 0.25).expandTowards(ahead.normalize().scale(0.75));
        var natural = new HashSet<Long>();
        int logs = 0, leaves = 0;
        for (BlockPos cell : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY + 0.01), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY - 0.01), Mth.floor(box.maxZ))) {
            if (logs + leaves >= MAX_BLOCKS) break;
            BlockState state = at(world, cell);
            if (untended(state)) {
                if (world.destroyBlock(cell.immutable(), false, mob)) leaves++;
            } else if (state.is(BlockTags.LOGS)) {
                long column = BlockPos.asLong(cell.getX(), 0, cell.getZ());
                if (natural.contains(column) || naturalTrunk(blocks, cell)) {
                    natural.add(column);
                    logs += fell(world, cell.immutable(), mob);
                }
            }
        }
        if (logs + leaves > 0 && SessionRecorder.on()) SessionRecorder.event(mob, "trample", "logs=" + logs + " leaves=" + leaves);
        return logs + leaves;
    }

    /** The block of a loaded chunk; a chunk is never loaded for the answer. */
    private static BlockState at(ServerLevel world, BlockPos pos) {
        return world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null ? UNKNOWN : world.getBlockState(pos);
    }

    /** Takes the trunk down from its base to its top; a rooted trunk leaves its sapling. */
    private static int fell(ServerLevel world, BlockPos log, CreatureEntity mob) {
        BlockState wood = world.getBlockState(log);
        var base = log.mutable();
        while (log.getY() - base.getY() < MAX_TRUNK && at(world, base.below()).is(BlockTags.LOGS)) base.move(0, -1, 0);
        boolean rooted = at(world, base.below()).is(BlockTags.DIRT);
        int broken = 0;
        var cursor = base.mutable();
        for (int height = 0; height < MAX_TRUNK && at(world, cursor).is(BlockTags.LOGS); height++) {
            if (world.destroyBlock(cursor.immutable(), false, mob)) broken++;
            cursor.move(0, 1, 0);
        }
        if (rooted) {
            Block sapling = sapling(wood);
            if (sapling != Blocks.AIR && sapling.defaultBlockState().canSurvive(world, base))
                world.setBlockAndUpdate(base.immutable(), sapling.defaultBlockState());
        }
        return broken;
    }

    /** The sapling of a log's tree by name (oak_log to oak_sapling), or air when the tree has none. */
    private static Block sapling(BlockState log) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(log.getBlock());
        String wood = id.getPath().replace("stripped_", "").replaceAll("_(log|wood|stem|hyphae)$", "");
        for (String name : new String[] {wood + "_sapling", wood + "_propagule"}) {
            var found = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), name));
            if (found.isPresent()) return found.get();
        }
        return Blocks.AIR;
    }

    private TreeTrample() {}
}
