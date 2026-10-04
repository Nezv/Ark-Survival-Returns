package dev.nez.arksurvivalreturns.feature.creature;

import dev.nez.arksurvivalreturns.feature.recorder.Row;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Walking with a body that has weight. Vanilla snaps a mob up to 90 degrees a tick toward its next path
 * node and gives it its full speed at once; a creature instead turns at the rate its bulk allows, gathers
 * and loses pace over a time that grows with it ({@link Inertia}), slows into a bend, and when the node lies
 * far to the side it stops and turns in place before stepping off, which is when the turn clip plays.
 */
final class CreatureMoveControl extends MoveControl {
    private final CreatureEntity creature;
    private boolean steering, pivoting;
    private float turnLeft;
    /** The forward speed the body really has, in movement-attribute units; it trails what the path asks for. */
    private float pace;

    CreatureMoveControl(CreatureEntity creature) {
        super(creature);
        this.creature = creature;
    }

    @Override public void tick() {
        // A rider drives the body directly; the routine's pace must not leak into the ride.
        if (creature.isRidden()) { pace = 0; steering = pivoting = false; turnLeft = 0; super.tick(); return; }
        boolean moving = operation == Operation.MOVE_TO;
        float before = mob.getYRot();
        super.tick();
        steering = moving;
        pivoting = false;
        turnLeft = 0;
        double bulk = creature.bulk();
        float full = (float) mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float wanted = 0;
        if (moving) {
            double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ();
            if (dx * dx + dz * dz >= 1.0E-4) {
                boolean running = speedModifier > 0.9;
                float heading = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
                // Vanilla has already snapped the facing; the body turns from where it really was.
                mob.setYRot(before);
                creature.steerYaw(heading, running);
                float remaining = Math.abs(Mth.wrapDegrees(heading - mob.getYRot()));
                float pivot = Inertia.pivotAngle(bulk);
                wanted = (float) speedModifier * full;
                // Too far to the side: stand and pivot, the way a large animal lines up before it walks.
                if (remaining > pivot) { pivoting = true; wanted = 0; }
                // Into a bend the body slows, so its arc tightens instead of swinging wide of the path.
                else if (remaining > 12f) wanted *= Math.max(0.3f, 1 - remaining / (pivot * 1.15f));
                turnLeft = remaining;
            }
        } else if (operation == Operation.JUMPING) {
            wanted = (float) speedModifier * full;
        }
        // Fighting or bolting, the body digs in to stop; calm, it runs its pace out.
        float brake = full / Inertia.brakeTicks(bulk) * (creature.getTarget() != null || creature.isStriking() ? 2.5f : 1f);
        pace = Inertia.pace(pace, wanted, full / Inertia.accelTicks(bulk), brake);
        if (pace > 1.0E-4f && wanted <= 0 && !moving && !groundAhead()) pace = 0;
        // Mob.setSpeed also sets the forward input: with nothing left to run out the body stands.
        if (pace > 1.0E-4f) mob.setSpeed(pace);
        else { pace = 0; mob.setZza(0); }
    }

    /** A body running its pace out must not carry itself over an edge or into a wall the path would have gone round. */
    private boolean groundAhead() {
        float yaw = mob.getYRot() * Mth.DEG_TO_RAD;
        double reach = mob.getBbWidth() / 2 + 0.6;
        var feet = BlockPos.containing(mob.getX() - Mth.sin(yaw) * reach, mob.getY() - 0.2, mob.getZ() + Mth.cos(yaw) * reach);
        var level = mob.level();
        if (!level.isLoaded(feet)) return false;
        for (int drop = 0; drop <= 1; drop++) {
            var below = feet.below(drop);
            if (!level.getFluidState(below).isEmpty()) return mob.isInLiquid();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return true;
        }
        return false;
    }

    /** True when the last tick stood and turned toward a path node instead of walking. */
    boolean pivoting() { return pivoting; }

    /** Session recorder view of the last tick: whether a node was being steered at, and the turn still owed. */
    void record(Row row) {
        row.flag("steer", steering).flag("pivot", pivoting);
        if (turnLeft > 0) row.put("turn", turnLeft);
        if (steering) row.put("pace", (float) speedModifier);
    }
}
