package dev.nez.arksurvivalreturns.feature.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Death recovery. Vanilla drops are replaced by one recoverable cache per death; the items live in
 * world SavedData and the placed marker only shows where to walk.
 *
 * <p>Exactly-once rules: a cache is created only from the drops event, relogging without dying never
 * creates one, and the cap folds the oldest caches into the new one instead of dropping items.
 */
public final class RecoveryService {
    /**
     * Creates a cache from the collected death drops. Callers cancel the vanilla drops when this
     * returns non-null; watch the cap, which folds older caches into the new entry.
     */
    public static UUID create(ServerPlayer player, List<ItemStack> drops) {
        if (!Config.RECOVERY_ENABLED.get()) return null;
        List<ItemStack> items = drops.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        if (items.isEmpty()) return null;

        ServerLevel level = player.level();
        RecoveryData data = RecoveryData.get(level);
        UUID owner = player.getUUID();

        List<ItemStack> merged = new ArrayList<>(items);
        int merges = RecoveryPolicy.mergeCount(data.entries(owner).size(), Config.RECOVERY_MAX_CACHES.get());
        for (int i = 0; i < merges; i++) {
            List<RecoveryEntry> existing = data.entries(owner);
            if (existing.isEmpty()) break;
            RecoveryEntry oldest = existing.getFirst();
            List<ItemStack> combined = RecoveryPolicy.merge(oldest.items(), merged);
            merged.clear();
            merged.addAll(combined);
            removeMarker(level.getServer(), oldest);
            data.remove(owner, oldest.id());
        }

        // Prefer the death spot; if it cannot hold a marker, fall back to the saved respawn point.
        ServerLevel placeLevel = level;
        Optional<BlockPos> spot = RecoveryPolicy.findSpot(player.blockPosition(),
                Config.RECOVERY_SEARCH_RADIUS.get(), candidate -> canPlace(level, candidate));
        if (spot.isEmpty()) {
            Fallback fallback = respawnSpot(player);
            if (fallback != null) {
                placeLevel = fallback.level();
                spot = Optional.of(fallback.pos());
            }
        }

        RecoveryEntry entry = new RecoveryEntry(UUID.randomUUID(), placeLevel.dimension(), spot, List.copyOf(merged));
        if (spot.isPresent()) placeLevel.setBlockAndUpdate(spot.get(), ModContent.RECOVERY_CACHE.get().defaultBlockState());
        data.add(owner, entry);
        notifyDeath(player, entry);
        TamingService.discovery(player, "journal/first_loss");
        return entry.id();
    }

    /** Right-click collection; the owner, a tribe member or a gamemaster may take it. */
    public static boolean collect(ServerPlayer clicker, ServerLevel level, BlockPos pos) {
        RecoveryData data = RecoveryData.get(level);
        Optional<RecoveryData.Owned> found = data.at(level.dimension(), pos);
        if (found.isEmpty()) return false;
        RecoveryData.Owned owned = found.get();
        if (!allowed(clicker, owned.owner())) {
            clicker.sendSystemMessage(Component.translatable("recovery.arksurvivalreturns.not_yours"), true);
            return false;
        }
        recover(clicker, level, owned.owner(), owned.entry());
        return true;
    }

    /** Claims an unplaced entry (void or unloaded death spots) by index, 1-based. */
    public static int claim(ServerPlayer player, int index) {
        RecoveryData data = RecoveryData.get(player.level());
        List<RecoveryEntry> entries = data.entries(player.getUUID());
        if (index < 1 || index > entries.size()) {
            player.sendSystemMessage(Component.translatable("recovery.arksurvivalreturns.invalid_index", index), true);
            return 0;
        }
        RecoveryEntry entry = entries.get(index - 1);
        if (entry.placed()) {
            player.sendSystemMessage(Component.translatable("recovery.arksurvivalreturns.must_travel"), true);
            return 0;
        }
        recover(player, player.level(), player.getUUID(), entry);
        return 1;
    }

    /** Lists the caller's outstanding caches with index, place and stack count. */
    public static int list(ServerPlayer player, CommandSourceStack source) {
        List<RecoveryEntry> entries = RecoveryData.get(player.level()).entries(player.getUUID());
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("recovery.arksurvivalreturns.list_empty"), false);
            return 0;
        }
        for (int i = 0; i < entries.size(); i++) {
            RecoveryEntry entry = entries.get(i);
            String index = String.valueOf(i + 1);
            source.sendSuccess(() -> Component.translatable("recovery.arksurvivalreturns.list_entry",
                    index, where(entry), entry.items().size()), false);
        }
        return entries.size();
    }

    /** Gamemaster cleanup: deletes every cache of one player, markers included. */
    public static int clear(ServerPlayer target, CommandSourceStack source) {
        RecoveryData data = RecoveryData.get(target.level());
        List<RecoveryEntry> entries = data.entries(target.getUUID());
        for (RecoveryEntry entry : entries) removeMarker(target.level().getServer(), entry);
        data.clear(target.getUUID());
        source.sendSuccess(() -> Component.translatable("recovery.arksurvivalreturns.cleared",
                entries.size(), target.getDisplayName()), true);
        return entries.size();
    }

    /** Reminds a player about outstanding caches on login and respawn. */
    public static void remind(ServerPlayer player) {
        if (!Config.RECOVERY_ENABLED.get()) return;
        int count = RecoveryData.get(player.level()).entries(player.getUUID()).size();
        if (count > 0) player.sendSystemMessage(Component.translatable("recovery.arksurvivalreturns.reminder", count), false);
    }

    /** Marker destroyed without being collected: keep the items as an unplaced, claimable entry. */
    public static void onMarkerRemoved(ServerLevel level, BlockPos pos) {
        RecoveryData.get(level).unplace(level.dimension(), pos);
    }

    private static void recover(ServerPlayer clicker, ServerLevel level, UUID owner, RecoveryEntry entry) {
        int stacks = 0;
        for (ItemStack stack : entry.items()) {
            if (stack.isEmpty()) continue;
            stacks++;
            if (!clicker.getInventory().add(stack.copy())) clicker.drop(stack.copy(), false);
        }
        RecoveryData.get(level).remove(owner, entry.id());
        entry.pos().ifPresent(pos -> {
            ServerLevel markerLevel = level.getServer().getLevel(entry.dimension());
            if (markerLevel != null && markerLevel.getBlockState(pos).is(ModContent.RECOVERY_CACHE.get())) {
                markerLevel.removeBlock(pos, false);
            }
        });
        clicker.sendSystemMessage(Component.translatable("recovery.arksurvivalreturns.collected", stacks), true);
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        TamingService.discovery(ownerPlayer != null ? ownerPlayer : clicker, "journal/first_recovery");
        if (ownerPlayer != null && ownerPlayer != clicker) {
            ownerPlayer.sendSystemMessage(Component.translatable("recovery.arksurvivalreturns.collected_owner",
                    clicker.getDisplayName()), false);
        }
    }

    private static boolean allowed(ServerPlayer clicker, UUID owner) {
        return clicker.getUUID().equals(owner)
                || TribeService.sameTribe(owner, clicker.getUUID())
                || Commands.LEVEL_GAMEMASTERS.check(clicker.permissions());
    }

    private static void notifyDeath(ServerPlayer player, RecoveryEntry entry) {
        Component where = where(entry);
        player.sendSystemMessage(Component.translatable("recovery.arksurvivalreturns.own_cache", where), false);
        if (!Config.RECOVERY_NOTIFY_TRIBE.get()) return;
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other == player || !TribeService.sameTribe(other.getUUID(), player.getUUID())) continue;
            other.sendSystemMessage(Component.translatable("recovery.arksurvivalreturns.tribe_cache",
                    player.getDisplayName(), where), false);
        }
    }

    private static Component where(RecoveryEntry entry) {
        return entry.pos().map(pos -> Component.translatable("recovery.arksurvivalreturns.position",
                        entry.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ()))
                .orElseGet(() -> Component.translatable("recovery.arksurvivalreturns.unplaced"));
    }

    private static void removeMarker(MinecraftServer server, RecoveryEntry entry) {
        entry.pos().ifPresent(pos -> {
            ServerLevel level = server.getLevel(entry.dimension());
            if (level != null && level.getBlockState(pos).is(ModContent.RECOVERY_CACHE.get())) level.removeBlock(pos, false);
        });
    }

    private record Fallback(ServerLevel level, BlockPos pos) {}

    private static Fallback respawnSpot(ServerPlayer player) {
        var config = player.getRespawnConfig();
        if (config == null) return null;
        var global = config.respawnData().globalPos();
        ServerLevel target = player.level().getServer().getLevel(global.dimension());
        if (target == null) return null;
        Optional<BlockPos> spot = RecoveryPolicy.findSpot(global.pos(), 4, candidate -> canPlace(target, candidate));
        return spot.map(pos -> new Fallback(target, pos)).orElse(null);
    }

    private static boolean canPlace(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) return false;
        if (level.getBlockState(pos).getBlock() == Blocks.LAVA) return false;
        if (!level.getFluidState(pos).isEmpty()) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.canBeReplaced()) return false;
        BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), Direction.UP) && below.getFluidState().isEmpty();
    }

    private RecoveryService() {}
}
