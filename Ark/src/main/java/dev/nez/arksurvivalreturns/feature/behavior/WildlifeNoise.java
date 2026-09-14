package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Short-lived action sounds, bounded to 64 stimuli per dimension. No entity references are retained. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class WildlifeNoise {
    public record Noise(UUID owner, Vec3 position, double radius, long expiry) {}
    private static final Map<ServerLevel, ArrayDeque<Noise>> SOUNDS = new WeakHashMap<>();
    public static void emit(Player source, Vec3 pos, double radius) {
        if (!(source.level() instanceof ServerLevel world) || !WildlifeSenses.validTarget(source)) return;
        var sounds = SOUNDS.computeIfAbsent(world, unused -> new ArrayDeque<>());
        while (sounds.size() >= 64) sounds.removeFirst();
        sounds.addLast(new Noise(source.getUUID(), pos, radius, world.getGameTime() + 40));
    }
    public static Noise hear(CreatureEntity listener) {
        var world = (ServerLevel) listener.level();
        var sounds = SOUNDS.get(world);
        if (sounds == null) return null;
        sounds.removeIf(n -> n.expiry <= world.getGameTime());
        Noise nearest = null;
        double best = Double.MAX_VALUE;
        for (var sound : sounds) {
            var source = world.getPlayerByUUID(sound.owner);
            if (source != null && !WildlifeSenses.validTarget(source)) continue;
            double distance = listener.position().distanceToSqr(sound.position);
            double radius = sound.radius * (world.isRaining() ? 0.65 : 1);
            if (!dev.nez.arksurvivalreturns.feature.spawn.SpawnRules.loaded(world,
                    new net.minecraft.world.phys.AABB(listener.position(), sound.position).inflate(1))) continue;
            var obstruction = world.clip(new net.minecraft.world.level.ClipContext(listener.getEyePosition(), sound.position,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, listener));
            if (obstruction.getType() != net.minecraft.world.phys.HitResult.Type.MISS) radius *= 0.4;
            if (distance < radius * radius && distance < best) { nearest = sound; best = distance; }
        }
        return nearest;
    }
    @SubscribeEvent public static void broken(BreakBlockEvent event) { if (!event.isCanceled()) emit(event.getPlayer(), Vec3.atCenterOf(event.getPos()), 24); }
    @SubscribeEvent public static void attack(AttackEntityEvent event) { if (!event.isCanceled()) emit(event.getEntity(), event.getTarget().position(), 32); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SOUNDS.clear(); }
    private WildlifeNoise() {}
}
