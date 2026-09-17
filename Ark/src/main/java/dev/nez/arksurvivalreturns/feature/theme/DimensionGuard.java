package dev.nez.arksurvivalreturns.feature.theme;

import java.util.Set;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Refuses all access to the Nether and the End while keeping their saved data and ids intact.
 *
 * <p>Portals cannot be lit, every route that changes an entity's dimension is cancelled, and
 * the vanilla dimension rules this theme depends on are enforced on each level. Players who
 * are already inside a removed dimension, including characters saved there before this patch,
 * are moved to a safe Overworld position on join and on respawn.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DimensionGuard {
    /** Fired when fire or another source would build a portal shape. */
    @SubscribeEvent
    public static void portalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (!Config.THEME_DIMENSIONS.get()) return;
        event.setCanceled(true);
    }

    /** Fired for every route that changes an entity's dimension, portals included. */
    @SubscribeEvent
    public static void travel(EntityTravelToDimensionEvent event) {
        if (!Config.THEME_DIMENSIONS.get()) return;
        if (ThemePolicy.removedDimension(event.getDimension())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) evacuate(player);
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) evacuate(player);
    }

    /**
     * Applies the vanilla rules this theme relies on. They are re-applied on every start so a
     * level that already exists cannot keep portal access, raids, patrols, phantoms or
     * wardens switched on.
     */
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        var server = event.getServer();
        for (var world : server.getAllLevels()) {
            var rules = world.getGameRules();
            if (Config.THEME_DIMENSIONS.get()) rules.set(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false, server);
            if (!Config.THEME_MONSTERS.get()) continue;
            rules.set(GameRules.RAIDS, false, server);
            rules.set(GameRules.SPAWN_PATROLS, false, server);
            rules.set(GameRules.SPAWN_PHANTOMS, false, server);
            rules.set(GameRules.SPAWN_WARDENS, false, server);
        }
    }

    /** Moves a player standing in a removed dimension to a safe Overworld position. */
    public static void evacuate(ServerPlayer player) {
        if (!Config.THEME_DIMENSIONS.get()) return;
        if (!(player.level() instanceof ServerLevel from) || !ThemePolicy.removedDimension(from.dimension())) return;
        var overworld = from.getServer().overworld();
        var target = returnPosition(overworld, player, player.position(), from.dimension());
        if (target == null) return;
        player.stopRiding();
        player.teleportTo(overworld, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), true);
    }

    /**
     * Safe Overworld landing position for a character leaving {@code from} at {@code origin}.
     * The recorded Overworld respawn point is preferred because it is already loaded; a world
     * whose respawn point is elsewhere maps a Nether exit at the vanilla 8:1 ratio, or its own
     * coordinates for any other dimension. A column that is not loaded yet is only completed as
     * a last resort at the world spawn, so ordinary returns never generate terrain.
     */
    public static @Nullable BlockPos returnPosition(ServerLevel overworld, Entity traveller, Vec3 origin, ResourceKey<Level> from) {
        var respawn = overworld.getServer().getRespawnData();
        double x, z;
        if (respawn.dimension() == Level.OVERWORLD) {
            x = respawn.pos().getX() + 0.5;
            z = respawn.pos().getZ() + 0.5;
        } else if (from == Level.NETHER) {
            x = origin.x * 8.0;
            z = origin.z * 8.0;
        } else {
            x = origin.x;
            z = origin.z;
        }
        var target = safeSurface(overworld, traveller, x, z, true);
        if (target == null) {
            // No loaded Overworld anchor is available, so the mapped column is completed.
            target = safeSurface(overworld, traveller, x, z, false);
        }
        if (target == null) {
            var fallback = LevelData.RespawnData.DEFAULT.pos();
            target = safeSurface(overworld, traveller, fallback.getX() + 0.5, fallback.getZ() + 0.5, false);
        }
        return target;
    }

    private static BlockPos safeSurface(ServerLevel level, Entity traveller, double x, double z, boolean requireLoaded) {
        if (!level.getWorldBorder().isWithinBounds(x, z)) return null;
        int blockX = Mth.floor(x), blockZ = Mth.floor(z);
        if (requireLoaded && level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4) == null) return null;
        var surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(blockX, level.getMinY(), blockZ));
        if (!level.getFluidState(surface).isEmpty()) return null;
        if (!level.getBlockState(surface.below()).blocksMotion()) return null;
        var box = new AABB(surface.getX() + 0.2, surface.getY(), surface.getZ() + 0.2,
                surface.getX() + 0.8, surface.getY() + 1.8, surface.getZ() + 0.8);
        return level.noCollision(traveller, box) ? surface : null;
    }

    private DimensionGuard() {}
}
