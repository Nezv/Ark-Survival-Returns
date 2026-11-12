package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.tribe.TribeAllies;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * A ridden mount fights (BUG-05). The rider's attack key sends {@link Strike}; the server checks that the sender
 * drives the mount and that it may strike, takes the nearest valid target in reach in front of it and starts the
 * mount's own strike, which lands on its hit frame like any other. A blow aimed at the rider of a large mount, who
 * sits out of reach of anything on the ground, lands on the mount instead ({@code riding.shieldRider}).
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class MountedCombat {
    /** Half the arc in front of the mount a target must be in, in degrees. */
    private static final double ARC = 75;
    /** A body at least this wide carries its rider out of reach: blows aimed at the rider hit it instead. */
    public static final double SHIELD_WIDTH = 3.0;

    /** The rider pressed attack. No data: the server decides everything. */
    public record Strike() implements CustomPacketPayload {
        public static final Type<Strike> TYPE = new Type<>(ArkSurvivalReturns.id("mount_strike"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Strike> STREAM_CODEC = StreamCodec.unit(new Strike());
        @Override public Type<Strike> type() { return TYPE; }
    }

    @SubscribeEvent static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Strike.TYPE, Strike.STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) strike(player);
        });
    }

    /** Server: the mount the player drives strikes the best target in front of it. True when a strike started. */
    public static boolean strike(Player rider) {
        if (rider.level().isClientSide()) return false;
        if (!(rider.getControlledVehicle() instanceof CreatureEntity mount) || !mount.isRidden()) return false;
        if (TorporService.restricted(rider) || !mount.canStrike()) return false;
        LivingEntity target = target(mount, rider);
        return target != null && mount.strike(target);
    }

    /** The nearest living thing the mount can reach in its front arc that is not on the rider's side. */
    public static @Nullable LivingEntity target(CreatureEntity mount, Player rider) {
        if (!(mount.level() instanceof ServerLevel level)) return null;
        double reach = Math.max(2.0, mount.getBbWidth() * 0.5);
        LivingEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (var candidate : level.getEntitiesOfClass(LivingEntity.class, mount.getBoundingBox().inflate(reach),
                c -> c != mount && c != rider && c.isAlive() && !c.isPassengerOfSameVehicle(rider) && WildlifeSenses.validTarget(c)
                        && !TribeAllies.allied(rider, c))) {
            if (!mount.isWithinMeleeAttackRange(candidate) || !inFront(mount, candidate)) continue;
            double distance = mount.distanceToSqr(candidate);
            if (distance < nearest && mount.hasLineOfSight(candidate)) {
                nearest = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean inFront(CreatureEntity mount, LivingEntity target) {
        Vec3 to = target.position().subtract(mount.position());
        if (to.horizontalDistanceSqr() < 1.0E-4) return true;
        float heading = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        return Math.abs(Mth.wrapDegrees(heading - mount.getYRot())) <= ARC;
    }

    /** A melee blow at the rider of a large mount strikes the mount. Projectiles still find the rider. */
    @SubscribeEvent public static void shieldRider(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player rider) || rider.level().isClientSide() || !Config.RIDING_SHIELD_RIDER.get()) return;
        if (!(rider.getVehicle() instanceof CreatureEntity mount) || mount.getBbWidth() < SHIELD_WIDTH) return;
        var source = event.getSource();
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker || attacker == mount) return;
        if (!(rider.level() instanceof ServerLevel level)) return;
        event.setCanceled(true);
        mount.hurtServer(level, source, event.getAmount());
    }

    private MountedCombat() {}
}
