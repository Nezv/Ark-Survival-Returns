package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.UUID;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Per-creature routine owner: the shared decision model, its persistence and the alarm channels.
 *
 * Every realm supplies one so status, saved needs, home position and group signals behave the
 * same for ground, amphibious and water-bound wildlife. {@link WildlifeGoal} is the ground
 * adapter; the aquatic realm adds a movement adapter over the same {@link WildlifeMind}.
 */
public abstract class WildlifeController extends Goal {
    protected final CreatureEntity mob;
    protected WildlifeController(CreatureEntity mob) { this.mob = mob; }
    public CreatureEntity mob() { return mob; }
    /** The decision model driving status, needs and routines. */
    public abstract WildlifeMind mind();
    /** Stable home anchor used by territory checks, saves and habitat adoption. */
    public abstract BlockPos home();
    /** One decision pass; called on the realm's own cadence. */
    public abstract void think();
    public abstract void save(ValueOutput out);
    public abstract void load(ValueInput in);
    public abstract void interruptSleep();
    /** Current group prey link, or null. */
    public UUID preyHerd() { return null; }
    public void followPreyHerd(UUID herd) {}
    public void receiveAlarm(Vec3 position) {}
    public void receiveHerdThreat(LivingEntity threat) {}
}
