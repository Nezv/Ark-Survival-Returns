package dev.nez.arksurvivalreturns.feature.taming;

/**
 * How one creature is ridden and where the rider sits.
 *
 * <p>Coordinates are entity-local blocks: {@code x} is the lateral axis, {@code y} is height above the
 * entity's feet and {@code z} runs along the body with the vanilla forward direction at negative z. They
 * were measured from the runtime GeckoLib model by {@code tools/build_taming_manifests.py}; the manifest
 * in {@code docs/taming-seat-manifest.json} records the bone, the raw pivot and the model-space values.
 *
 * @param mode            movement domain the controller drives
 * @param seatX           lateral seat offset
 * @param seatY           seat height above the entity's feet
 * @param seatZ           fore/aft seat offset, negative is forward
 * @param yawOffset       degrees added to the entity's yaw for a seat that sits across a side-mounted rig
 * @param groundDismount  fallback dismount offset behind the creature
 * @param altDismount     secondary dismount offset beside the creature
 * @param bone            the model bone the seat was measured from
 * @param riderPose       readable description of the intended rider posture
 */
public record CreatureRideProfile(
        MovementMode mode,
        double seatX, double seatY, double seatZ, double yawOffset,
        double groundDismountX, double groundDismountY, double groundDismountZ,
        double altDismountX, double altDismountY, double altDismountZ,
        String bone, String riderPose) {

    public enum MovementMode {
        /** Ground navigation with step height and gravity. */
        GROUND,
        /** Powered flight; needs the look direction to climb and dive. */
        FLIGHT,
        /** Free swimming in three dimensions. */
        SWIM
    }

    public boolean flying() {
        return mode == MovementMode.FLIGHT;
    }

    public boolean swimming() {
        return mode == MovementMode.SWIM;
    }
}
