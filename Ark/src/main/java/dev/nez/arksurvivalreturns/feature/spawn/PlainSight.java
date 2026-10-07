package dev.nez.arksurvivalreturns.feature.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Whether a player would watch an animal appear, or vanish, at a spot. It is in plain sight when it stands
 * within the distance animals are shown to that player, inside what their screen holds, large enough there to
 * be made out, and with a line from their eye to it that no ground, wall or foliage crosses. The population
 * budget places and removes wildlife only where nobody has it in plain sight.
 */
public final class PlainSight {
    /**
     * How far from where a player looks the screen still shows something, its corners included: a field of view
     * of 110 on a wide screen reaches 71 degrees, and running widens it.
     */
    public static final double VIEW_DEGREES = 80.0;
    /**
     * Below this many degrees across an animal is a speck no eye is drawn to: about ten pixels of a screen
     * 1080 high. An animal a block tall is that small from 76 blocks on, one of two blocks from 153.
     */
    public static final double SPECK_DEGREES = 0.75;
    /** The server sends a creature to a client within 12 chunks (ModContent, clientTrackingRange) and never beyond the view distance. */
    private static final int SHOWN_CHUNKS = 12;
    private static final double SPECK = Math.tan(Math.toRadians(SPECK_DEGREES));

    /** True when one of the players has an animal of this size, standing with its feet at the point, in plain sight. */
    public static boolean seen(ServerLevel level, Iterable<? extends Player> players, double x, double y, double z, float width, float height) {
        for (Player player : players) if (seenBy(level, player, x, y, z, width, height)) return true;
        return false;
    }

    public static boolean seenBy(ServerLevel level, Player player, double x, double y, double z, float width, float height) {
        if (player.isSpectator() || player.level() != level) return false;
        Vec3 eye = player.getEyePosition();
        double dx = x - eye.x, dy = y + height * 0.5 - eye.y, dz = z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz), distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float size = Math.max(width, height);
        if (flat > shown(level, player) + width) return false;
        if (distance <= size) return true;
        if (size < SPECK * distance) return false;
        // Inside the picture: the angle from where the player looks, less the half of the animal that reaches in.
        Vec3 look = player.getViewVector(1f);
        double angle = Math.acos(Math.clamp((dx * look.x + dy * look.y + dz * look.z) / distance, -1.0, 1.0));
        if (angle > Math.toRadians(VIEW_DEGREES) + Math.atan(size * 0.5 / distance)) return false;
        // Its feet, middle and head, and its two flanks as the player would see them side by side.
        Loaded blocks = new Loaded(level);
        double sideX = flat > 1e-6 ? -dz / flat * width * 0.5 : 0.0, sideZ = flat > 1e-6 ? dx / flat * width * 0.5 : 0.0, middle = y + height * 0.5;
        return clear(blocks, eye, x, y + Math.min(0.25, height * 0.5), z) || clear(blocks, eye, x, middle, z) || clear(blocks, eye, x, y + height, z)
                || clear(blocks, eye, x + sideX, middle, z + sideZ) || clear(blocks, eye, x - sideX, middle, z - sideZ);
    }

    /** Blocks from the player within which the server shows them a creature. */
    private static double shown(ServerLevel level, Player player) {
        // A server that was never told a view distance (the headless test server) reports none: the creature's own range then.
        int server = level.getServer().getPlayerList().getViewDistance(), chunks = server > 0 ? Math.min(SHOWN_CHUNKS, server) : SHOWN_CHUNKS;
        if (player instanceof ServerPlayer online) chunks = Math.min(chunks, Math.max(2, online.requestedViewDistance()));
        return chunks * 16.0;
    }

    private static boolean clear(Loaded blocks, Vec3 eye, double x, double y, double z) {
        return blocks.clip(new ClipContext(eye, new Vec3(x, y, z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }

    /** The world as far as it is loaded; what is not loaded hides nothing, and no chunk is ever loaded for a look. */
    private record Loaded(ServerLevel level) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            return level.hasChunkAt(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos) {
            return level.hasChunkAt(pos) ? level.getFluidState(pos) : Fluids.EMPTY.defaultFluidState();
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return level.hasChunkAt(pos) ? level.getBlockEntity(pos) : null;
        }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }

    private PlainSight() {}
}
