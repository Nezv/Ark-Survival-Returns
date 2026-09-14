package dev.nez.arksurvivalreturns.feature.creature;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Only follows members from the same spawn event, so neighbouring packs do not merge. */
final class FollowPackGoal extends Goal {
    private final CreatureEntity mob;
    private CreatureEntity leader;
    private int refresh;
    FollowPackGoal(CreatureEntity mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() {
        if (mob.getTarget() != null || mob.tickCount % 20 != 0) return false;
        leader = mob.level().getEntitiesOfClass(CreatureEntity.class, mob.getBoundingBox().inflate(24),
                c -> c.isAlive() && c.species() == mob.species() && c.packId().equals(mob.packId()) && c.getId() < mob.getId())
                .stream().min(Comparator.comparingInt(CreatureEntity::getId)).orElse(null);
        return leader != null && mob.distanceToSqr(leader) > 36;
    }
    @Override public boolean canContinueToUse() {
        return leader != null && leader.isAlive() && mob.getTarget() == null
                && mob.distanceToSqr(leader) > 16 && mob.distanceToSqr(leader) < 576;
    }
    @Override public void start() { refresh = 0; }
    @Override public void tick() {
        if (--refresh <= 0) { refresh = 10; mob.getNavigation().moveTo(leader, 1.0); }
    }
    @Override public void stop() { leader = null; mob.getNavigation().stop(); }
}
