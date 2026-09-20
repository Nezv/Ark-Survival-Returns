package dev.nez.arksurvivalreturns.feature.companion;

import java.util.EnumSet;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The routine of a tamed creature: FOLLOW the owner, hold a STAY anchor, WANDER around one, and fight
 * whatever hurts the creature or its owner.
 *
 * <p>Orders are per creature and server-authoritative. A companion never teleports and never loads a
 * chunk: when the owner is gone, unreachable or in another dimension it waits where it stands and
 * resumes when the owner is back in the same dimension. Realm steering is delegated to
 * {@link CreatureEntity#companionTravel}, so walkers, flyers and swimmers all obey the same orders.
 *
 * <p>WANDER owns its own timer. The idle pause starts when a walk is chosen, so a completed walk still
 * pauses; an order change, a moved anchor or stopping the goal forgets the plan instead of resuming it.
 */
public final class CompanionGoal extends Goal {
    private final CreatureEntity mob;
    private @Nullable Vec3 wanderTarget;
    private @Nullable BlockPos wanderAnchor;
    private @Nullable CompanionOrder lastOrder;
    private long nextWanderTick;

    public CompanionGoal(CreatureEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override public boolean canUse() {
        return mob.isAlive() && mob.isTamed() && !TorporService.restricted(mob)
                && CompanionService.of(mob).order() != CompanionOrder.WORK;
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void stop() {
        mob.companionHold();
        forgetWander();
        lastOrder = null;
    }

    @Override public void tick() {
        // A rider owns the mount; the companion routine stands aside but keeps its order.
        if (mob.isRidden()) { mob.companionHold(); return; }
        LivingEntity threat = threat();
        if (threat != null) { fight(threat); return; }
        CompanionOrder order = CompanionService.of(mob).order();
        if (order != lastOrder) {
            // Every order anchors where it was given, so a plan from the previous order is stale.
            lastOrder = order;
            forgetWander();
        }
        switch (order) {
            case FOLLOW -> follow();
            case STAY -> stay();
            case WANDER -> wander();
        }
    }

    /** The creature's attacker, or a nearby owner's attacker. Friends are never valid threats. */
    private @Nullable LivingEntity threat() {
        LivingEntity attacker = mob.getLastHurtByMob();
        if (attacker != null && WildlifeSenses.validTarget(attacker) && !isFriend(attacker)) return attacker;
        var owner = CompanionService.owner(mob);
        if (owner != null && mob.distanceTo(owner) < 16 && owner.getLastHurtByMob() instanceof LivingEntity ownerAttacker
                && WildlifeSenses.validTarget(ownerAttacker) && !isFriend(ownerAttacker)) return ownerAttacker;
        return null;
    }

    private boolean isFriend(LivingEntity entity) {
        if (entity == mob || entity == CompanionService.owner(mob)) return true;
        return entity instanceof CreatureEntity creature && creature.isTamed()
                && creature.isOwnedBy(CompanionService.owner(mob));
    }

    private void fight(LivingEntity threat) {
        mob.setTarget(threat);
        if (mob.isWithinMeleeAttackRange(threat)) {
            mob.companionHold();
            mob.getLookControl().setLookAt(threat, 30, 30);
            mob.strike(threat);
        } else {
            mob.companionTravel(threat.position(), 1.0);
        }
    }

    private void follow() {
        var owner = CompanionService.owner(mob);
        if (owner == null || owner.level() != mob.level()) { mob.companionHold(); return; }
        double distance = mob.distanceTo(owner);
        if (distance <= Config.COMPANION_FOLLOW_DISTANCE.get()) {
            mob.companionHold();
            mob.getLookControl().setLookAt(owner, 10, 10);
        } else {
            // Faster the further behind it falls; there is no teleport to fall back on.
            mob.companionTravel(owner.position(), distance > 16 ? 1.3 : 0.9);
        }
    }

    private void stay() {
        BlockPos anchor = CompanionService.of(mob).anchor();
        if (anchor == null) { CompanionService.setOrder(mob, CompanionOrder.STAY); return; }
        if (mob.blockPosition().distSqr(anchor) > 4) {
            mob.companionTravel(Vec3.atBottomCenterOf(anchor), 0.8);
        } else {
            mob.companionHold();
            var owner = CompanionService.owner(mob);
            if (owner != null) mob.getLookControl().setLookAt(owner, 10, 10);
        }
    }

    /** Drops any remembered wander plan; the next walk starts from scratch. */
    private void forgetWander() {
        wanderTarget = null;
        wanderAnchor = null;
    }

    private void wander() {
        BlockPos anchor = CompanionService.of(mob).anchor();
        if (anchor == null) { CompanionService.setOrder(mob, CompanionOrder.WANDER); return; }
        if (wanderTarget != null && !anchor.equals(wanderAnchor)) forgetWander();
        if (wanderTarget == null) {
            mob.companionHold();
            if (mob.tickCount < nextWanderTick) return; // Idle pause between two walks.
            nextWanderTick = mob.tickCount + 100 + mob.getRandom().nextInt(120);
            wanderTarget = chooseWanderTarget(anchor);
            wanderAnchor = wanderTarget == null ? null : anchor;
            if (wanderTarget == null) return;
        }
        if (mob.position().distanceToSqr(wanderTarget) < 4) {
            forgetWander();
            mob.companionHold();
            return;
        }
        mob.companionTravel(wanderTarget, 0.6);
    }

    /** Picks a reachable point near the anchor; flyers and swimmers stay off the terrain. */
    private @Nullable Vec3 chooseWanderTarget(BlockPos anchor) {
        if (!(mob.level() instanceof ServerLevel world)) return null;
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        int radius = Math.max(4, Config.COMPANION_WANDER_RADIUS.get());
        BlockPos point = anchor.offset((int)Math.round(Math.cos(angle) * radius), 0, (int)Math.round(Math.sin(angle) * radius));
        if (mob.species().landHabitat() && !mob.isInWater()) {
            BlockPos ground = SpawnRules.surface(world, point.getX(), point.getZ());
            if (ground == null || Math.abs(ground.getY() - anchor.getY()) > 6) return null;
            var box = SpawnRules.bounds(mob.species(), ground);
            if (!SpawnRules.loaded(world, box.inflate(1)) || !world.noCollision(mob, box, true)) return null;
            return Vec3.atBottomCenterOf(ground);
        }
        // Flyers and swimmers roam around the anchor at their own altitude or depth.
        return Vec3.atBottomCenterOf(point).add(0, mob.getBbHeight() * 0.5, 0);
    }
}
