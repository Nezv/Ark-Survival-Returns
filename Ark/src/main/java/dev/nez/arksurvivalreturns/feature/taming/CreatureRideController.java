package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Validated rider input, movement-mode handling and safe dismount search for every registered creature.
 *
 * <p>The ridden creature follows the vanilla contract: {@code getControllingPassenger} exposes the player,
 * {@code getRiddenInput} translates that player's intent, and the local client simulates the mount and
 * streams the result to the server. Server-side code therefore never trusts a client-supplied position; it
 * only reads the input flags the client already reports through vanilla's own input packet, and it keeps
 * the seat itself authoritative through {@code positionRider}.
 */
public final class CreatureRideController {
    /** Horizontal intent of a player, resolved on both sides. */
    public record RiderInput(float forward, float strafe, float vertical, boolean jump, boolean descend) {
        public static final RiderInput NONE = new RiderInput(0f, 0f, 0f, false, false);
    }

    /**
     * Reads rider intent. A remote player's {@code xxa}/{@code zza} fields are never filled on the server,
     * so the server reads the last input packet instead; the local player has its movement fields set.
     */
    public static RiderInput input(Player controller, float pitchDegrees) {
        if (controller instanceof ServerPlayer server) {
            Input input = server.getLastClientInput();
            float forward = (input.forward() ? 1f : 0f) - (input.backward() ? 1f : 0f);
            float strafe = (input.left() ? 1f : 0f) - (input.right() ? 1f : 0f);
            return new RiderInput(forward, strafe, verticalFor(forward, pitchDegrees),
                    input.jump(), input.shift());
        }
        float forward = controller.zza;
        float strafe = controller.xxa;
        return new RiderInput(forward, strafe, verticalFor(forward, pitchDegrees),
                controller.isJumping(), controller.isShiftKeyDown());
    }

    /** Looking up climbs and looking down dives while moving, which needs no extra key binding. */
    private static float verticalFor(float forward, float pitchDegrees) {
        if (Math.abs(forward) < 0.01f) return 0f;
        return Math.clamp((float) -Math.sin(Math.toRadians(pitchDegrees)), -1f, 1f) * Math.signum(forward);
    }

    /** Converts intent into the local-space vector the movement code expects. */
    public static Vec3 toLocal(CreatureRideController.RiderInput input, boolean threeDimensional) {
        return new Vec3(input.strafe(), threeDimensional ? input.vertical() : 0.0, input.forward());
    }

    /** Speed scale for one ridden movement mode. */
    public static float riddenSpeed(CreatureEntity creature, CreatureRideProfile profile) {
        float base = (float) creature.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        return switch (profile.mode()) {
            case FLIGHT -> base * (float) (double) Config.RIDDEN_FLIGHT_SPEED_MULTIPLIER.get();
            case SWIM -> base * (float) (double) Config.RIDDEN_SWIM_SPEED_MULTIPLIER.get();
            case GROUND -> base;
        };
    }

    /**
     * Whether the creature and the rider may mount right now: tamed, saddled, awake, owned, not already
     * carrying someone, and with physical room for the rider's hitbox.
     */
    public static boolean canMount(CreatureEntity creature, Player player) {
        if (!ArkSurvivalReturns.tamingEnabled()) return false;
        if (!creature.isAlive() || TorporService.restricted(creature)) return false;
        if (!dev.nez.arksurvivalreturns.feature.tribe.TribeService.canRide(creature, player)) return false;
        if (!creature.isSaddled()) return false;
        if (!creature.getPassengers().isEmpty()) return false;
        return player.getVehicle() == null;
    }

    /** Room check for the rider at the seat, so mounting under a ceiling is refused instead of clipping. */
    public static boolean hasRoomForRider(CreatureEntity creature, Player player) {
        Vec3 seat = creature.seatPosition();
        AABB box = player.getDimensions(player.getPose()).makeBoundingBox(
                seat.x, seat.y + seatHeightOffset(creature), seat.z);
        return creature.level().noCollision(player, box);
    }

    private static double seatHeightOffset(CreatureEntity creature) {
        return 0.0;
    }

    /**
     * Places a dismounted rider on a safe surface near the creature. Returns null when the rider should be
     * left where the physics put them, for example while swimming or falling with no ground nearby.
     */
    public static @Nullable Vec3 findSafePosition(ServerLevel level, CreatureEntity creature, Entity passenger) {
        var profile = creature.rideProfile();
        double[][] offsets = {
                {profile.groundDismountX(), profile.groundDismountY(), profile.groundDismountZ()},
                {profile.altDismountX(), profile.altDismountY(), profile.altDismountZ()},
                {-profile.altDismountX(), profile.altDismountY(), profile.altDismountZ()},
                {profile.groundDismountX(), profile.groundDismountY(), -profile.groundDismountZ()},
        };
        for (double[] offset : offsets) {
            Vec3 candidate = creature.position().add(localToWorld(creature, offset[0], offset[1], offset[2]));
            if (!isLoaded(level, candidate)) continue;
            AABB box = passenger.getDimensions(passenger.getPose())
                    .makeBoundingBox(candidate.x, candidate.y, candidate.z);
            if (!level.noCollision(passenger, box)) continue;
            if (!level.getFluidState(net.minecraft.core.BlockPos.containing(candidate)).isEmpty()) continue;
            return candidate;
        }
        return null;
    }

    /** True when the dismount target is safe enough to move the rider there. */
    private static boolean isLoaded(ServerLevel level, Vec3 position) {
        return level.hasChunkAt(net.minecraft.core.BlockPos.containing(position));
    }

    /** Rotates a seat offset by the creature's yaw, matching vanilla's passenger attachment transform. */
    public static Vec3 localToWorld(CreatureEntity creature, double x, double y, double z) {
        return new Vec3(x, y, z).yRot(-creature.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD);
    }

    /** Rejects a mount attempt with a readable reason. */
    public static net.minecraft.network.chat.Component mountFailure(CreatureEntity creature, Player player) {
        if (TorporService.restricted(creature)) return net.minecraft.network.chat.Component.translatable("taming.arksurvivalreturns.mount.unconscious");
        if (!TamingService.of(creature).tamed()) return net.minecraft.network.chat.Component.translatable("taming.arksurvivalreturns.mount.wild");
        if (!TamingService.ownedBy(creature, player)
                && !dev.nez.arksurvivalreturns.feature.tribe.TribeService.isTribeMember(creature, player))
            return net.minecraft.network.chat.Component.translatable("taming.arksurvivalreturns.mount.notowner");
        if (!dev.nez.arksurvivalreturns.feature.tribe.TribeService.canRide(creature, player))
            return net.minecraft.network.chat.Component.translatable("taming.arksurvivalreturns.mount.permission", creature.getDisplayName());
        if (!creature.isSaddled()) return net.minecraft.network.chat.Component.translatable("taming.arksurvivalreturns.mount.unsaddled");
        if (!creature.getPassengers().isEmpty()) return net.minecraft.network.chat.Component.translatable("taming.arksurvivalreturns.mount.occupied");
        return net.minecraft.network.chat.Component.translatable("taming.arksurvivalreturns.mount.noroom");
    }

    private CreatureRideController() {}
}
