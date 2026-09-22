package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/**
 * Guardian targeting: encounter participants and their registered tames only.
 *
 * <p>Damage still flows through the shared authored hit-frame path, so the AI decides when to strike
 * and the animation decides when the bite lands. Targets outside the arena, unregistered wildlife
 * and unrelated players are ignored; unrelated attackers still hold the boss's attention only while
 * inside the arena for as long as their damage source resolves to a participant.
 */
final class GuardianCombatGoal extends Goal {
    private final GuardianGiganotosaurusEntity guardian;
    private @Nullable LivingEntity target;
    private int repath;

    GuardianCombatGoal(GuardianGiganotosaurusEntity guardian) {
        this.guardian = guardian;
        setFlags(EnumSet.of(Flag.MOVE, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (guardian.level().isClientSide() || !guardian.isAlive()) return false;
        target = findTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !guardian.level().isClientSide() && guardian.isAlive() && target != null && valid(target);
    }

    @Override
    public void start() {
        if (target != null) guardian.setTarget(target);
    }

    @Override
    public void stop() {
        target = null;
        guardian.setTarget(null);
        guardian.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null || !valid(target)) target = findTarget();
        if (target == null) return;
        if (repath > 0) repath--;
        guardian.setTarget(target);
        guardian.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (guardian.isWithinMeleeAttackRange(target) && guardian.hasLineOfSight(target)) {
            if (guardian.canStrike()) guardian.strike(target);
            return;
        }
        if (repath <= 0) {
            guardian.getNavigation().moveTo(target, 1.0);
            repath = 10;
        }
    }

    private boolean valid(LivingEntity candidate) {
        return candidate.isAlive()
                && candidate.level() == guardian.level()
                && GuardianService.isEncounterTarget(guardian, candidate);
    }

    private @Nullable LivingEntity findTarget() {
        var encounter = GuardianService.encounterFor(guardian);
        if (encounter.isEmpty() || !(guardian.level() instanceof ServerLevel level)) return null;
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (UUID id : encounter.get().participants()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.isSpectator()) continue;
            if (!GuardianService.isEncounterTarget(guardian, player)) continue;
            double distance = guardian.distanceToSqr(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        for (UUID id : encounter.get().tames()) {
            if (!(level.getEntity(id) instanceof LivingEntity tame)) continue;
            if (!GuardianService.isEncounterTarget(guardian, tame)) continue;
            double distance = guardian.distanceToSqr(tame);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = tame;
            }
        }
        return nearest;
    }
}
