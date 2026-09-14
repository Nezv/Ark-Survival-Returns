package dev.nez.arksurvivalreturns.feature.behavior;

import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class WildlifeSenses {
    public record Detection(boolean visible, double strength) {}
    public static double windAngle(ServerLevel world) {
        return (world.getSeed() & 65535) / 65536.0 * Math.PI * 2 + world.getGameTime() / 48000.0;
    }
    public static boolean validTarget(LivingEntity target) {
        return target.isAlive() && !target.isSpectator() && !(target instanceof Player p && p.isCreative());
    }
    public static boolean hasNightCycle(CreatureEntity creature) {
        var type = creature.level().dimensionType();
        return !creature.species().flyer() && Config.NIGHTTIME.get() && type.hasSkyLight()
                && !type.hasCeiling() && !type.hasFixedTime() && type.defaultClock().isPresent();
    }
    public static boolean night(CreatureEntity creature) {
        return hasNightCycle(creature) && NighttimeCycle.individualNight(creature.level().getDefaultClockTime(),
                creature.getUUID().getLeastSignificantBits(), Config.NIGHT_START.get(), Config.NIGHT_END.get(), Config.NIGHT_TRANSITION.get());
    }
    public static double sightRange(CreatureEntity observer) {
        return NighttimeCycle.sight(observer.species().solitary() ? 48 : 32, !observer.level().isBrightOutside(),
                observer.species().predator && night(observer), Config.NIGHT_VISION.get());
    }
    public static double bodyDistance(LivingEntity a, LivingEntity b) {
        var x = a.getBoundingBox(); var y = b.getBoundingBox();
        double dx = Math.max(0, Math.max(x.minX - y.maxX, y.minX - x.maxX));
        double dy = Math.max(0, Math.max(x.minY - y.maxY, y.minY - x.maxY));
        double dz = Math.max(0, Math.max(x.minZ - y.maxZ, y.minZ - x.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    public static Detection detect(CreatureEntity observer, LivingEntity target) {
        if (!validTarget(target)) return new Detection(false, 0);
        var world = (ServerLevel) observer.level();
        Vec3 offset = target.position().subtract(observer.position());
        double distance = offset.length(), sight = sightRange(observer);
        boolean crouching = target.isShiftKeyDown(), wet = target.isInWaterOrRain();
        if (world.isRaining()) sight *= 0.80;
        if (crouching) sight *= 0.60;
        Vec3 flat = new Vec3(offset.x, 0, offset.z).normalize();
        Vec3 facing = Vec3.directionFromRotation(0, observer.yBodyRot);
        boolean clear = dev.nez.arksurvivalreturns.feature.spawn.SpawnRules.loaded(world,
                new net.minecraft.world.phys.AABB(observer.position(), target.position()).inflate(1)) && observer.hasLineOfSight(target);
        boolean visible = distance < sight && (distance < 4 + observer.getBbWidth() || facing.dot(flat) > -0.15)
                && !target.isInvisible() && clear;
        boolean moving = target.position().distanceToSqr(new Vec3(target.xo, target.yo, target.zo)) > 0.0004;
        double hearing = moving ? (target.isSprinting() ? 28 : crouching ? 3 : 12) : 0;
        if (world.isRaining()) hearing *= 0.6;
        // Sound can reveal an approximate direction through cover, never authorize a melee hit.
        if (!clear) hearing *= 0.4;
        double angle = windAngle(world);
        Vec3 wind = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        boolean smelled = distance < (wet ? 8 : 24) && wind.dot(flat.scale(-1)) > 0.65;
        return new Detection(visible, visible ? 1 : distance < hearing ? 0.65 : smelled ? 0.25 : 0);
    }
    private WildlifeSenses() {}
}
