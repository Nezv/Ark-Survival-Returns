package dev.nez.arksurvivalreturns.feature.creature;

import dev.nez.arksurvivalreturns.feature.recorder.Row;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Walking with a body that has to turn. Vanilla snaps a mob up to 90 degrees a tick toward its next path
 * node; a creature instead turns at the rate its size and turn clip allow, and when the node lies far to
 * the side it stops and turns in place before stepping off, which is when the turn clip plays.
 */
final class CreatureMoveControl extends MoveControl {
    private final CreatureEntity creature;
    private boolean steering, pivoting;
    private float turnLeft;

    CreatureMoveControl(CreatureEntity creature) {
        super(creature);
        this.creature = creature;
    }

    @Override public void tick() {
        boolean moving = operation == Operation.MOVE_TO;
        float before = mob.getYRot();
        super.tick();
        steering = moving;
        pivoting = false;
        turnLeft = 0;
        if (!moving) return;
        double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        boolean running = speedModifier > 0.9;
        float wanted = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float turned = Mth.approachDegrees(before, wanted,
                MovementTuning.navigationTurnRate(creature.turnRate(running, true), creature.getBbWidth()));
        mob.setYRot(turned);
        float remaining = Math.abs(Mth.wrapDegrees(wanted - turned));
        if (remaining > 90f) {
            // Too far to the side: stand and pivot, the way a large animal lines up before it walks.
            mob.setYRot(Mth.approachDegrees(before, wanted,
                    MovementTuning.navigationTurnRate(creature.turnRate(running, false), creature.getBbWidth())));
            mob.setSpeed(0);
            mob.setZza(0);
            pivoting = true;
        } else if (remaining > 15f) {
            mob.setSpeed(mob.getSpeed() * Math.max(0.5f, 1 - remaining / 180f));
        }
        turnLeft = Math.abs(Mth.wrapDegrees(wanted - mob.getYRot()));
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
