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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
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
 *       a side effect (the Mattress never becomes a spawn point). When the sleeper wakes (morning, a
 *       monster, or by hand) every cell is removed with no drop. Breaking it before anyone sleeps in it
 *       still drops the item, since that path never goes through {@link #disposeAfterSleep}.</li>
 *   <li>{@code setsRespawn} (the Bedroll): the same sleep flow, but the respawn point it sets is kept
 *       (config {@code camp.bedrollSetsSpawn}) and the Bedroll is never consumed.</li>
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
        return sleep(world, head, server, setsRespawn && Config.CAMP_BEDROLL_SETS_SPAWN.get());
    }

    /** Both props are beds to the sleep code: without this the sleeper is woken on the next tick. */
    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
        return true;
    }

    /** There is no OCCUPIED property; the vanilla default would throw. */
    @Override
    public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) {}

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    /**
     * Runs the normal vanilla sleep flow (range, obstruction and monster checks; messages, stats and
     * advancements). {@link ServerPlayer#startSleepInBed} sets the respawn point as a side effect, even by
     * day; unless {@code keepRespawn} (the Bedroll with spawn-setting enabled) that is reverted at once.
     */
    private InteractionResult sleep(ServerLevel world, BlockPos head, ServerPlayer server, boolean keepRespawn) {
        ServerPlayer.RespawnConfig before = server.getRespawnConfig();
        var result = server.startSleepInBed(head);
        if (!keepRespawn && !Objects.equals(server.getRespawnConfig(), before)) server.setRespawnPosition(before, false);
        if (setsRespawn && !keepRespawn)
            server.sendSystemMessage(Component.translatable("camp.arksurvivalreturns.bedroll_disabled"), true);
        result.ifLeft(problem -> {
            if (problem.message() != null) server.sendOverlayMessage(problem.message());
        });
        return InteractionResult.SUCCESS;
    }

    /** Called by {@link SleepEvents} once the sleeper wakes. */
    void disposeAfterSleep(ServerLevel level, BlockPos head) {
        BlockState state = level.getBlockState(head);
        if (!state.is(this)) return;
        for (BlockPos other : otherParts(state, head)) removeQuietly(level, other);
        removeQuietly(level, head);
    }

    private void removeQuietly(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT_REMOVE);
        level.levelEvent(2001, pos, getId(state));
    }

    /**
     * Removes a partner cell without drops and without the neighbour-shape cascade: in 26.1 that cascade
     * clears UPDATE_SUPPRESS_DROPS, so a partner reacting to the gap would drop a second item.
     */
    private static final int SILENT_REMOVE = UPDATE_ALL | UPDATE_SUPPRESS_DROPS | UPDATE_KNOWN_SHAPE;

    /**
     * Exactly one item per instance: only the anchor ({@link #headPos}) has loot. Mining the anchor removes
     * the other cells silently and drops through the normal loot path; mining any other cell destroys the
     * anchor (dropping unless the player prevents drops, e.g. creative) and silently clears the rest.
     * Explosions and lost support follow the same rule through the anchor's loot condition.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos anchor = headPos(state, pos);
            for (BlockPos other : otherParts(state, pos)) {
                if (other.equals(anchor)) continue;
                BlockState otherState = level.getBlockState(other);
                if (otherState.is(this)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), SILENT_REMOVE);
                    level.levelEvent(player, 2001, other, getId(otherState));
                }
            }
            if (!anchor.equals(pos) && level.getBlockState(anchor).is(this)) level.destroyBlock(anchor, !player.preventsBlockDrops(), player);
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
