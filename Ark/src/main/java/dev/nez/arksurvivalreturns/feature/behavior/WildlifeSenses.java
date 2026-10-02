package dev.nez.arksurvivalreturns.feature.behavior;

import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryAttributes;
import dev.nez.arksurvivalreturns.feature.accessory.AccessoryEffects;
import dev.nez.arksurvivalreturns.feature.recorder.SessionRecorder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WildlifeSenses {
    public record Detection(boolean visible, double strength) {}
    /** The numbers behind one {@link #detect} call; filled only while the session recorder runs. */
    public static final class Detail {
        public double distance, sight, near, facing, hearing, scentRange, downwind, strength;
        public boolean invalid, disguised, loaded, clear, eyeClear, invisible, visible, moving, crouching, wet, smelled;
        public int sightRays;
        public Detail copy() {
            var d = new Detail();
            d.distance = distance; d.sight = sight; d.near = near; d.facing = facing; d.hearing = hearing;
            d.scentRange = scentRange; d.downwind = downwind; d.strength = strength;
            d.invalid = invalid; d.disguised = disguised; d.loaded = loaded; d.clear = clear; d.invisible = invisible;
            d.eyeClear = eyeClear; d.sightRays = sightRays;
            d.visible = visible; d.moving = moving; d.crouching = crouching; d.wet = wet; d.smelled = smelled;
            return d;
        }
    }
    /** Detail of the last detect call on the server thread. */
    public static final Detail LAST = new Detail();
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
    private record Sight(boolean clear, boolean eyeClear, int rays) {}
    /** Several physical sight lines; solid terrain and leaves still obstruct every ray they intersect. */
    public static boolean hasSightLine(CreatureEntity observer, LivingEntity target) {
        if (!(observer.level() instanceof ServerLevel world) || target.level() != world) return false;
        if (!dev.nez.arksurvivalreturns.feature.spawn.SpawnRules.loaded(world,
                observer.getBoundingBox().minmax(target.getBoundingBox()).inflate(1))) return false;
        return sight(world, observer, target, true).clear();
    }
    private static boolean ray(ServerLevel world, CreatureEntity observer, Vec3 from, Vec3 to) {
        return world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer))
                .getType() == HitResult.Type.MISS;
    }
    private static Sight sight(ServerLevel world, CreatureEntity observer, LivingEntity target, boolean alternatives) {
        Vec3 eye = observer.getEyePosition(), head = target.getEyePosition();
        boolean direct = ray(world, observer, eye, head);
        if (direct || !alternatives || !observer.species().landHabitat()) return new Sight(direct, direct, 1);
        Vec3 direction = head.subtract(eye).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        var body = target.getBoundingBox();
        Vec3 chest = new Vec3(target.getX(), body.minY + body.getYsize() * 0.55, target.getZ());
        double shoulder = target.getBbWidth() * 0.35;
        Vec3[] targets = {head, chest.add(side.scale(shoulder)), chest.subtract(side.scale(shoulder))};
        double spread = Math.min(1.5, observer.getBbWidth() * 0.35);
        Vec3[] origins = {eye, eye.add(side.scale(spread)), eye.subtract(side.scale(spread))};
        int rays = 1;
        for (int i = 0; i < origins.length; i++) {
            // An offset eye must itself be accessible from the central eye: never peek through a wall.
            if (i > 0 && !ray(world, observer, eye, origins[i])) continue;
            for (int j = i == 0 ? 1 : 0; j < targets.length; j++) {
                rays++;
                if (ray(world, observer, origins[i], targets[j])) return new Sight(true, false, rays);
            }
        }
        return new Sight(false, false, rays);
    }
    public static Detection detect(CreatureEntity observer, LivingEntity target) {
        boolean recorded = SessionRecorder.on();
        if (!validTarget(target)) {
            if (recorded) { LAST.invalid = true; LAST.disguised = false; LAST.strength = 0; }
            return new Detection(false, 0);
        }
        if (AccessoryEffects.herdDisguise(observer, target)) {
            if (recorded) { LAST.invalid = false; LAST.disguised = true; LAST.strength = 0; }
            return new Detection(false, 0);
        }
        var world = (ServerLevel) observer.level();
        Vec3 offset = target.position().subtract(observer.position());
        // Worn gear scales each sense (visibility, noise, scent attributes; 1.0 for everything else).
        double seen = AccessoryAttributes.value(target, AccessoryAttributes.VISIBILITY);
        double heard = AccessoryAttributes.value(target, AccessoryAttributes.NOISE);
        double smelt = AccessoryAttributes.value(target, AccessoryAttributes.SCENT);
        double distance = offset.length(), sight = sightRange(observer) * seen;
        boolean crouching = target.isShiftKeyDown(), wet = target.isInWaterOrRain();
        if (world.isRaining()) sight *= 0.80;
        if (crouching) sight *= 0.60;
        Vec3 flat = new Vec3(offset.x, 0, offset.z).normalize();
        Vec3 facing = Vec3.directionFromRotation(0, observer.yBodyRot);
        boolean loaded = dev.nez.arksurvivalreturns.feature.spawn.SpawnRules.loaded(world,
                observer.getBoundingBox().minmax(target.getBoundingBox()).inflate(1));
        double ahead = facing.dot(flat);
        boolean inView = distance < sight && (distance < 4 + observer.getBbWidth() || ahead > -0.15) && !target.isInvisible();
        Sight lines = loaded ? sight(world, observer, target, inView) : new Sight(false, false, 0);
        boolean clear = lines.clear(), visible = inView && clear;
        // Movement packets absSnapTo real players, overwriting xo/yo/zo. The packet listener retains this delta.
        Vec3 movement = target instanceof ServerPlayer ? target.getKnownMovement()
                : target.position().subtract(new Vec3(target.xo, target.yo, target.zo));
        boolean moving = movement.lengthSqr() > 0.0004;
        double hearing = (moving ? (target.isSprinting() ? 28 : crouching ? 3 : 12) : 0) * heard;
        if (world.isRaining()) hearing *= 0.6;
        // Sound can reveal an approximate direction through cover, never authorize a melee hit.
        if (!clear) hearing *= 0.4;
        double angle = windAngle(world);
        Vec3 wind = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        double downwind = wind.dot(flat.scale(-1));
        boolean smelled = distance < (wet ? 8 : 24) * smelt && downwind > 0.65;
        double strength = visible ? 1 : distance < hearing ? 0.65 : smelled ? 0.25 : 0;
        if (recorded) {
            LAST.invalid = false; LAST.disguised = false; LAST.distance = distance; LAST.sight = sight;
            LAST.near = 4 + observer.getBbWidth(); LAST.facing = ahead; LAST.loaded = loaded; LAST.clear = clear;
            LAST.eyeClear = lines.eyeClear(); LAST.sightRays = lines.rays();
            LAST.invisible = target.isInvisible(); LAST.visible = visible; LAST.moving = moving; LAST.crouching = crouching;
            LAST.hearing = hearing; LAST.wet = wet; LAST.scentRange = (wet ? 8 : 24) * smelt; LAST.downwind = downwind;
            LAST.smelled = smelled; LAST.strength = strength;
        }
        return new Detection(visible, strength);
    }
    private WildlifeSenses() {}
}
