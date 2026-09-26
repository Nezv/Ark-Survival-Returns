package dev.nez.arksurvivalreturns.feature.creature;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Walking with a body that has to turn. Vanilla snaps a mob up to 90 degrees a tick toward its next path
 * node; a creature instead turns at the rate its size and turn clip allow, and when the node lies far to
 * the side it stops and turns in place before stepping off, which is when the turn clip plays.
 */
final class CreatureMoveControl extends MoveControl {
    private final CreatureEntity creature;

    CreatureMoveControl(CreatureEntity creature) {
        super(creature);
        this.creature = creature;
    }

    @Override public void tick() {
        boolean moving = operation == Operation.MOVE_TO;
        float before = mob.getYRot();
        super.tick();
        if (!moving) return;
        double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        boolean running = speedModifier > 0.9;
        float wanted = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float turned = Mth.approachDegrees(before, wanted, creature.turnRate(running, true));
        mob.setYRot(turned);
        float remaining = Math.abs(Mth.wrapDegrees(wanted - turned));
        if (remaining > (running ? 110f : 50f)) {
            // Too far to the side: stand and pivot, the way a large animal lines up before it walks.
            mob.setYRot(Mth.approachDegrees(before, wanted, creature.turnRate(running, false)));
            mob.setSpeed(0);
            mob.setZza(0);
        } else if (remaining > 15f) {
            mob.setSpeed(mob.getSpeed() * (1 - remaining / 120f));
        }
    }
}
