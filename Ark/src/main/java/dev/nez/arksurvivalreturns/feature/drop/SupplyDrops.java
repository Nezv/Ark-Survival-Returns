package dev.nez.arksurvivalreturns.feature.drop;

import java.util.List;
import java.util.Optional;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Sends the supply drops: one every {@code supplyDrops.intervalDays} Overworld days, near a player picked at
 * random, announced to everyone with where it will land. A drop is only ever aimed at a chunk that is loaded
 * and ticking; with nobody about, or no such place, it waits and comes as soon as there is one.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class SupplyDrops {
    public static final long DAY_TICKS = 24000;
    private static final int ATTEMPTS = 16;

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) tick(event.getServer());
    }

    public static void tick(MinecraftServer server) {
        if (!Config.DROPS_ENABLED.get()) return;
        ServerLevel world = server.overworld();
        SupplyDropData data = SupplyDropData.get(server);
        long day = world.getOverworldClockTime() / DAY_TICKS;
        if (!due(data.lastDay(), day, Config.DROP_INTERVAL_DAYS.get())) {
            // A new world, or a clock set back: the count starts from today.
            if (data.lastDay() == SupplyDropData.UNSET || day < data.lastDay()) data.setLastDay(day);
            return;
        }
        List<ServerPlayer> players = world.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
        if (players.isEmpty()) return;
        ServerPlayer near = players.get(world.getRandom().nextInt(players.size()));
        if (send(world, near, SupplyTier.roll(world.getRandom())).isPresent()) data.setLastDay(day);
    }

    /** Public for tests: a drop is due once the interval has passed since the last one. */
    public static boolean due(long lastDay, long day, int interval) {
        return lastDay != SupplyDropData.UNSET && day >= lastDay && day - lastDay >= interval;
    }

    /** A drop of this tier near the player, announced; empty when no loaded place was found for it. */
    public static Optional<SupplyDropEntity> send(ServerLevel world, ServerPlayer near, SupplyTier tier) {
        Optional<BlockPos> landing = landing(world, near.blockPosition(), world.getRandom());
        landing.ifPresent(pos -> world.getServer().getPlayerList().broadcastSystemMessage(announcement(tier, pos), false));
        return landing.map(pos -> launch(world, pos, tier));
    }

    public static Component announcement(SupplyTier tier, BlockPos pos) {
        return Component.translatable("drop.arksurvivalreturns.incoming",
                Component.translatable("drop.arksurvivalreturns.tier." + tier.id).withColor(tier.colour),
                Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withColor(tier.colour));
    }

    /** The drop itself, in the sky over the block the crate should come to stand in. */
    public static SupplyDropEntity launch(ServerLevel world, BlockPos landing, SupplyTier tier) {
        double y = Math.min(landing.getY() + Config.DROP_FALL_HEIGHT.get(), world.getMaxY() + 64);
        SupplyDropEntity drop = new SupplyDropEntity(world, landing.getX() + .5, y, landing.getZ() + .5, tier);
        world.addFreshEntity(drop);
        return drop;
    }

    /**
     * Where a crate dropped near this point would come to stand: on the ground or on water under open sky, a
     * walk away, in a chunk that is already loaded and ticking. Lava is refused; early tries keep to the
     * player's own level so the crate does not land on a peak or a monolith.
     */
    public static Optional<BlockPos> landing(ServerLevel world, BlockPos near, RandomSource random) {
        int min = Config.DROP_MIN_DISTANCE.get(), max = Math.max(min, Config.DROP_MAX_DISTANCE.get());
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = Mth.lerp(random.nextDouble(), min, max);
            int x = near.getX() + Mth.floor(Math.cos(angle) * distance), z = near.getZ() + Mth.floor(Math.sin(angle) * distance);
            LevelChunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) continue;
            BlockPos pos = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1, z);
            if (!world.isPositionEntityTicking(pos) || !world.getWorldBorder().isWithinBounds(pos)) continue;
            var surface = chunk.getFluidState(pos.below());
            if (surface.is(FluidTags.LAVA)) continue;
            // On water the crate takes the place of the top block of it.
            if (!surface.isEmpty()) pos = pos.below();
            if (attempt < ATTEMPTS / 2 && Math.abs(pos.getY() - near.getY()) > 24) continue;
            return Optional.of(pos);
        }
        return Optional.empty();
    }

    private SupplyDrops() {}
}
