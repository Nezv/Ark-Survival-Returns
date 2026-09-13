package dev.nez.arksurvivalreturns.feature.debug;

import java.util.*;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-selected targets and two bounded pages/second per active viewer. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DinoDebugSync {
    public static final int RANGE = 96, UPDATE_TICKS = 10;
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static final class View {
        UUID target; int page, pages = 1; int lastTurn = Integer.MIN_VALUE / 2;
        View(UUID target) { this.target = target; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(DinoDebugPayload.TYPE, DinoDebugPayload.STREAM_CODEC);
        registrar.playToServer(DinoDebugPayload.TurnPage.TYPE, DinoDebugPayload.TurnPage.STREAM_CODEC, (packet, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !DebugSpyglass.using(player)) return;
            var view = VIEWS.get(player.getUUID());
            if (view == null || !view.target.equals(packet.target()) || Math.abs((long) packet.direction()) != 1
                    || player.tickCount - view.lastTurn < 2) return;
            view.lastTurn = player.tickCount;
            view.page = Math.clamp(view.page + packet.direction(), 0, view.pages - 1);
        });
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!DebugSpyglass.using(player)) { clear(player); return; }
        if (player.tickCount % UPDATE_TICKS != 0) return;
        var dino = target(player);
        if (dino == null) { clear(player); return; }
        var view = VIEWS.computeIfAbsent(player.getUUID(), ignored -> new View(dino.getUUID()));
        if (!view.target.equals(dino.getUUID())) { view.target = dino.getUUID(); view.page = 0; }
        var lines = DinoDebugSnapshot.capture(dino);
        view.pages = Math.max(1, (lines.size() + DinoDebugSnapshot.PAGE_LINES - 1) / DinoDebugSnapshot.PAGE_LINES);
        view.page = Math.clamp(view.page, 0, view.pages - 1);
        int first = view.page * DinoDebugSnapshot.PAGE_LINES;
        String name = dino.getDisplayName().getString();
        PacketDistributor.sendToPlayer(player, new DinoDebugPayload(dino.getUUID(), player.level().dimension().identifier().toString(),
                name.substring(0, Math.min(name.length(), 128)), view.page, view.pages,
                lines.subList(first, Math.min(lines.size(), first + DinoDebugSnapshot.PAGE_LINES))));
    }
    /** Intersect loaded creatures only, with the first solid block as the ray endpoint. */
    public static CreatureEntity target(Player player) {
        var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1).scale(RANGE));
        end = new LoadedBlocks(player.level()).clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        double nearest = start.distanceToSqr(end);
        CreatureEntity selected = null;
        for (var dino : player.level().getEntitiesOfClass(CreatureEntity.class,
                player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1), c -> c.isAlive() && !c.isInvisible())) {
            var box = dino.getBoundingBox();
            var hit = box.contains(start) ? Optional.of(start) : box.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < nearest) {
                nearest = start.distanceToSqr(hit.get()); selected = dino;
            }
        }
        return selected;
    }
    /** Unloaded terrain is opaque, including neighbor reads performed by collision shapes. */
    private record LoadedBlocks(Level level) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            return level.hasChunkAt(pos) ? level.getBlockState(pos) : Blocks.BARRIER.defaultBlockState();
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
    private static void clear(ServerPlayer player) {
        if (VIEWS.remove(player.getUUID()) != null) PacketDistributor.sendToPlayer(player,
                DinoDebugPayload.empty(player.level().dimension().identifier().toString()));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { VIEWS.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) clear(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) clear(p); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { VIEWS.clear(); }
    private DinoDebugSync() {}
}
