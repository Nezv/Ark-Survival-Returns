package dev.nez.arksurvivalreturns.feature.camp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Shared behaviour for the mod's two sleeping props: the disposable {@link MattressBlock} (Prehistoric)
 * and the reusable {@link BedrollBlock} (Bronze Age). Every cell is a separate block instance sharing a
 * {@code FACING} property; each subclass supplies its own footprint (how many cells make up one instance,
 * and where they sit relative to the placement position) since the two use very different assets.
 *
 * <p>Two independent flags select the interaction:
 * <ul>
 *   <li>{@code disposable} (the Mattress): right-clicking runs the ordinary vanilla sleep flow through
 *       {@link ServerPlayer#startSleepInBed}, immediately undoing the respawn point that vanilla sets as
 *       a side effect (the Mattress never becomes a spawn point). Once the sleeper wakes, every cell is
 *       removed with no drop, provided at least one sleep tick elapsed while they were down — an instant
 *       interrupt on the very tick the player lay down (0 elapsed ticks) leaves the mattress standing,
 *       since it was never meaningfully used. Waking up early after actually dozing off (interrupted by a
 *       monster, or manually) still counts as a use and consumes it, matching "night skipped or woke
 *       normally". Breaking it by hand before anyone ever sleeps in it still drops the item normally,
 *       since that path never goes through {@link #disposeAfterSleep}.</li>
 *   <li>{@code setsRespawn} (the Bedroll): right-clicking only sets the personal respawn point at the
 *       head cell and never puts the player to sleep, exactly like the original Primitive Bedroll.</li>
 * </ul>
 * The flags are mutually exclusive today but kept separate in case a future prop wants both or neither.
 */
public abstract class AbstractSleepingBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected final boolean disposable;
    protected final boolean setsRespawn;

    protected AbstractSleepingBlock(Properties properties, boolean disposable, boolean setsRespawn) {
        super(properties);
        this.disposable = disposable;
        this.setsRespawn = setsRespawn;
    }

    public boolean isDisposable() { return disposable; }

    /** The cell sleepers stand up from; for the Bedroll this is also the saved respawn position. */
    protected abstract BlockPos headPos(BlockState state, BlockPos pos);

    /** Every other cell belonging to this instance (excluding {@code pos} itself). */
    protected abstract List<BlockPos> otherParts(BlockState state, BlockPos pos);

    /** The item a sneak-use pickup, or the loot table on a hand break, hands back. */
    protected abstract ItemStack rolledItem();

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel world) || !(player instanceof ServerPlayer server)) return InteractionResult.SUCCESS;
        if (player.isSecondaryUseActive() && Config.CAMP_BEDROLL_PICKUP.get()) return rollUp(world, pos, state, player);
        BlockPos head = headPos(state, pos);
        return disposable ? sleep(world, head, server) : setRespawn(world, head, server);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    /** Sets the personal respawn point; unchanged from the original Primitive Bedroll. */
    private InteractionResult setRespawn(ServerLevel world, BlockPos head, ServerPlayer server) {
        if (!Config.CAMP_BEDROLL_SETS_SPAWN.get()) {
            server.sendSystemMessage(Component.translatable("camp.arksurvivalreturns.bedroll_disabled"), true);
            return InteractionResult.SUCCESS;
        }
        server.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(world.dimension(), head, server.getYRot(), server.getXRot()), false), true);
        server.sendSystemMessage(Component.translatable("camp.arksurvivalreturns.bedroll_set"), false);
        return InteractionResult.SUCCESS;
    }

    /**
     * Runs the normal vanilla sleep flow (range, obstruction and monster checks; messages, stats and
     * advancements) and then immediately reverts the respawn point {@link ServerPlayer#startSleepInBed}
     * sets as a side effect whenever the dimension allows it: the Mattress is never a spawn point.
     */
    private InteractionResult sleep(ServerLevel world, BlockPos head, ServerPlayer server) {
        ServerPlayer.RespawnConfig before = server.getRespawnConfig();
        var result = server.startSleepInBed(head);
        if (!Objects.equals(server.getRespawnConfig(), before)) server.setRespawnPosition(before, false);
        result.ifLeft(problem -> {
            if (problem.message() != null) server.sendOverlayMessage(problem.message());
        });
        return InteractionResult.SUCCESS;
    }

    /** Called by {@link SleepEvents} once the sleeper wakes, provided at least one sleep tick elapsed. */
    void disposeAfterSleep(ServerLevel level, BlockPos head) {
        BlockState state = level.getBlockState(head);
        if (!state.is(this)) return;
        for (BlockPos other : otherParts(state, head)) removeQuietly(level, other);
        removeQuietly(level, head);
    }

    private void removeQuietly(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
        level.levelEvent(2001, pos, getId(state));
    }

    /**
     * Breaking any cell (by hand or in creative) always clears every other cell of the same instance,
     * silently: only the loot-table condition on the anchor cell (see the subclasses' Javadoc) ever
     * drops an item, so the rest must never re-trigger it. This is deliberately unconditional rather than
     * relying on {@link #updateShape}'s neighbour cascade, since the Bedroll's extra "side" pairing is
     * left non-destructive (an old, two-cell Primitive Bedroll save has no side partner at all, and must
     * keep working rather than vanish the next time a neighbour update touches it).
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            for (BlockPos other : otherParts(state, pos)) {
                BlockState otherState = level.getBlockState(other);
                if (otherState.is(this)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, other, getId(otherState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Only the Bedroll ({@code setsRespawn}) ever answers this; the Mattress is never a respawn point. */
    @Override
    public Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition(BlockState state, EntityType<?> type,
            LevelReader level, BlockPos pos, float orientation) {
        if (!setsRespawn) return Optional.empty();
        return BedBlock.findStandUpPosition(type, level, pos, state.getValue(FACING), orientation)
                .map(standUp -> ServerPlayer.RespawnPosAngle.of(standUp, pos, 0.0f));
    }

    /** Sneak-use rolls every cell back into a single item; the block never drops loot for this path. */
    private InteractionResult rollUp(ServerLevel world, BlockPos pos, BlockState state, Player player) {
        if (player.isSpectator()) return InteractionResult.SUCCESS;
        for (BlockPos other : otherParts(state, pos)) removeQuietly(world, other);
        removeQuietly(world, pos);
        ItemStack roll = rolledItem();
        Component message = Component.translatable("camp.arksurvivalreturns.rolled_up", roll.getHoverName());
        if (!player.getInventory().add(roll)) player.drop(roll, false);
        if (player instanceof ServerPlayer server) server.sendSystemMessage(message, true);
        else player.sendSystemMessage(message);
        return InteractionResult.SUCCESS;
    }
}
