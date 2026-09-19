package dev.nez.arksurvivalreturns.feature.taming;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Suppresses every voluntary creature action while torpor is above the wake threshold.
 *
 * <p>It is a goal, not an effect: claiming MOVE, LOOK, JUMP and TARGET stops the wildlife controller,
 * path following, jump control and target selection by normal goal arbitration. Physics stays untouched,
 * which is exactly what the design asks for: gravity, fluids, knockback and damage still move the body,
 * while nothing it does is voluntary. Blindness, a frozen pose or a movement-speed penalty would not
 * satisfy this.
 */
public final class UnconsciousBehavior extends Goal {
    private final Mob mob;

    public UnconsciousBehavior(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET));
    }

    @Override public boolean canUse() {
        return mob.isAlive() && TorporService.restricted(mob);
    }

    @Override public boolean canContinueToUse() {
        return canUse();
    }

    @Override public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override public void start() {
        release();
    }

    @Override public void stop() {
        // Nothing to restore: the suppressed goals re-evaluate on their own.
    }

    @Override public void tick() {
        release();
    }

    /** Clears intent every tick so a stale navigation target cannot drive the body. */
    private void release() {
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setSprinting(false);
        if (mob.getLastHurtByMob() != null) mob.setLastHurtByMob(null);
    }
}
