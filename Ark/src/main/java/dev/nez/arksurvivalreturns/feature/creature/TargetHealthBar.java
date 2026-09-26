package dev.nez.arksurvivalreturns.feature.creature;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** One server-synchronized vanilla HP bar per viewer, selected by an occluded eye ray. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TargetHealthBar {
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 4 != 0) return;
        CreatureEntity target = Config.HEALTH_BAR.get() && player.isAlive() && !player.isSpectator() ? target(player) : null;
        if (target == null) { remove(player.getUUID()); return; }
        var bar = BARS.computeIfAbsent(player.getUUID(), id -> {
            var created = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            created.addPlayer(player);
            return created;
        });
        var name = Component.translatable("hud.arksurvivalreturns.creature", target.getDisplayName(), target.creatureLevel(),
                (int) Math.ceil(target.getHealth()), (int) Math.ceil(target.getMaxHealth()))
                .append(" | ").append(Component.translatable(target.behavior().key()));
        // Walkers and swimmers also show the step they are performing (noticing, roaring, grazing...).
        if (!target.species().flyer()) name.append(" / ").append(Component.translatable(target.action().key()));
        bar.setName(name);
        bar.setProgress(Math.clamp(target.getHealth() / target.getMaxHealth(), 0f, 1f));
        bar.setColor(target.species().predator ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN);
    }
    private static CreatureEntity target(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1).scale(Config.HEALTH_BAR_RANGE.get()));
        var hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        end = hit.getLocation();
        double nearest = start.distanceToSqr(end);
        CreatureEntity selected = null;
        for (var creature : player.level().getEntitiesOfClass(CreatureEntity.class,
                player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                c -> c.isAlive() && !c.isInvisible()
                        && !(c instanceof dev.nez.arksurvivalreturns.feature.guardian.GuardianGiganotosaurusEntity))) {
            var box = creature.getBoundingBox().inflate(0.2);
            var point = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (point.isPresent() && start.distanceToSqr(point.get()) <= nearest) {
                nearest = start.distanceToSqr(point.get()); selected = creature;
            }
        }
        return selected;
    }
    private static void remove(UUID id) { var bar = BARS.remove(id); if (bar != null) bar.removeAllPlayers(); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { BARS.values().forEach(ServerBossEvent::removeAllPlayers); BARS.clear(); }
    private TargetHealthBar() {}
}
