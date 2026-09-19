package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.Config;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

/**
 * Server-authoritative sedation of one living entity: a normalized torpor meter, the recovery delay that
 * follows the latest sedative and the unconsciousness phase machine.
 *
 * <p>Torpor and taming progress are deliberately separate. Additional sedative only refills the meter and
 * restarts the recovery delay; it never advances {@link TamingState}.
 *
 * <p>The phase machine exists so the physical restrictions and the authored animation agree. The entity
 * is already unable to act from {@link Phase#COLLAPSING} onwards, which is why the animation cannot
 * become a substitute for the real server-side restriction.
 */
public final class TorporState implements ValueIOSerializable {
    public enum Phase {
        AWAKE, COLLAPSING, TORPID, WAKING_TAMED, WAKING_WILD;

        /** Any phase past AWAKE blocks voluntary action. */
        public boolean restricted() {
            return this != AWAKE;
        }
    }

    /** Result of one tick, so the service can apply the side effects of a transition. */
    public enum Event { NONE, COLLAPSE_STARTED, TORPID_ENTERED, WAKE_REQUESTED, WOKE }

    public static final StreamCodec<RegistryFriendlyByteBuf, TorporState> STREAM_CODEC = StreamCodec.of(
            (buffer, state) -> state.writeTo(buffer), TorporState::readFrom);

    private double maximum = 100.0;
    private double torpor;
    private int recoveryDelay;
    private Phase phase = Phase.AWAKE;
    private int phaseTicks;
    private int phaseDuration;
    private int feedTicks;
    private double anchorX, anchorY, anchorZ;
    private boolean anchorSet;
    private int externalTicks;
    private int lastDenialTick = Integer.MIN_VALUE / 2;
    private boolean dirty = true;

    public double maximum() {
        return maximum;
    }

    /** Called every tick from the profile so a config change or a new level takes effect immediately. */
    public void setMaximum(double value) {
        double clamped = Math.max(1.0, value);
        if (clamped == maximum) return;
        maximum = clamped;
        if (torpor > maximum) torpor = maximum;
        dirty = true;
    }

    public double torpor() {
        return torpor;
    }

    public double ratio() {
        return Math.clamp(torpor / maximum, 0.0, 1.0);
    }

    public double wakeThreshold() {
        return maximum * Config.WAKE_THRESHOLD_RATIO.get();
    }

    public int recoveryDelay() {
        return recoveryDelay;
    }

    public Phase phase() {
        return phase;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public int feedTicks() {
        return feedTicks;
    }

    /** True while the entity may not act and, for a knock-out tame, while it may be fed. */
    public boolean restricted() {
        return phase.restricted();
    }

    public boolean torpid() {
        return phase == Phase.TORPID;
    }

    public boolean dirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    /**
     * One sedative dose.
     *
     * @param potency dose in the same normalized units as the meter
     * @param resistance incoming potency multiplier; below one means a more resistant creature
     * @return true when the meter or the recovery delay changed
     */
    public boolean sedate(double potency, double resistance) {
        double before = torpor;
        int delayBefore = recoveryDelay;
        torpor = Math.clamp(torpor + potency * resistance, 0.0, maximum);
        recoveryDelay = Config.TORPOR_RECOVERY_DELAY.get();
        if (torpor != before || recoveryDelay != delayBefore) dirty = true;
        return torpor != before || recoveryDelay != delayBefore;
    }

    /** Debug and test entry point: jumps the meter to an exact value. */
    public void setTorpor(double value) {
        double before = torpor;
        torpor = Math.clamp(value, 0.0, maximum);
        dirty |= torpor != before;
    }

    public void setRecoveryDelay(int ticks) {
        recoveryDelay = Math.max(0, ticks);
        dirty = true;
    }

    /** Starts the collapse. The entity is restricted from this moment, not from the end of the clip. */
    public void startCollapse(int ticks) {
        phase = Phase.COLLAPSING;
        phaseTicks = 0;
        phaseDuration = Math.max(1, ticks);
        dirty = true;
    }

    /** Starts the wake sequence after a completed or abandoned attempt. */
    public void startWake(boolean tamed, int ticks) {
        phase = tamed ? Phase.WAKING_TAMED : Phase.WAKING_WILD;
        phaseTicks = 0;
        phaseDuration = Math.max(1, ticks);
        dirty = true;
    }

    /** Forces the entity awake without animation, for death handling and administrative resets. */
    public void reset() {
        phase = Phase.AWAKE;
        phaseTicks = 0;
        phaseDuration = 0;
        torpor = 0;
        recoveryDelay = 0;
        feedTicks = 0;
        dirty = true;
    }

    /** Marks one meal so the animation bridge plays the authored feeding clip. */
    public void markFeeding(int ticks) {
        feedTicks = Math.max(feedTicks, ticks);
        dirty = true;
    }

    /** The position a sleeping player must stay near. */
    public boolean hasAnchor() {
        return anchorSet;
    }

    public void setAnchor(net.minecraft.world.phys.Vec3 position) {
        anchorX = position.x;
        anchorY = position.y;
        anchorZ = position.z;
        anchorSet = true;
        dirty = true;
    }

    public net.minecraft.world.phys.Vec3 anchor() {
        return new net.minecraft.world.phys.Vec3(anchorX, anchorY, anchorZ);
    }

    public void clearAnchor() {
        if (!anchorSet) return;
        anchorSet = false;
        dirty = true;
    }

    /**
     * Ticks during which external movement (knockback, explosions, fluids) is allowed to move the entity.
     * While this window is open the caller must not clamp the position.
     */
    public int externalTicks() {
        return externalTicks;
    }

    public void openImpulseWindow(int ticks) {
        externalTicks = Math.max(externalTicks, ticks);
    }

    public void tickExternal() {
        if (externalTicks > 0) externalTicks--;
    }

    /** Rate limit for "you are unconscious" messages. */
    public boolean allowDenialMessage(int tickCount) {
        if (tickCount - lastDenialTick < 40) return false;
        lastDenialTick = tickCount;
        return true;
    }

    /**
     * One simulation step. Returns the transition the caller has to act on; timing lives here so the
     * service does not have to duplicate phase durations.
     */
    public Event tick() {
        if (feedTicks > 0) {
            feedTicks--;
            dirty = true;
        }
        // Recovery runs in every phase: a creature that wakes near the wake threshold keeps clearing the
        // rest of the dose instead of freezing at the threshold.
        if (recoveryDelay > 0) {
            recoveryDelay--;
            dirty = true;
        } else if (torpor > 0.0) {
            torpor = Math.max(0.0, torpor - maximum * Config.TORPOR_RECOVERY_PER_SECOND.get() / 20.0);
            dirty = true;
        }
        switch (phase) {
            case COLLAPSING -> {
                if (++phaseTicks >= phaseDuration) {
                    phase = Phase.TORPID;
                    phaseTicks = 0;
                    dirty = true;
                    return Event.TORPID_ENTERED;
                }
            }
            case TORPID -> {
                if (torpor < wakeThreshold()) return Event.WAKE_REQUESTED;
            }
            case WAKING_TAMED, WAKING_WILD -> {
                // A dose during the wake clip maintains unconsciousness instead of being lost: the entity
                // returns to the unconscious loop rather than waking and collapsing again next tick.
                if (torpor >= wakeThreshold()) {
                    phase = Phase.TORPID;
                    phaseTicks = 0;
                    dirty = true;
                    return Event.TORPID_ENTERED;
                }
                if (++phaseTicks >= phaseDuration) {
                    phase = Phase.AWAKE;
                    phaseTicks = 0;
                    dirty = true;
                    return Event.WOKE;
                }
            }
            default -> {
            }
        }
        return switch (phase) {
            case AWAKE -> torpor >= maximum ? Event.COLLAPSE_STARTED : Event.NONE;
            default -> Event.NONE;
        };
    }

    @Override public void serialize(ValueOutput output) {
        output.putDouble("Torpor", torpor);
        output.putDouble("TorporMaximum", maximum);
        output.putInt("TorporRecoveryDelay", recoveryDelay);
        output.putString("TorporPhase", phase.name());
        output.putInt("TorporPhaseTicks", phaseTicks);
        output.putInt("TorporPhaseDuration", phaseDuration);
        output.putBoolean("TorporAnchorSet", anchorSet);
        if (anchorSet) {
            output.putDouble("TorporAnchorX", anchorX);
            output.putDouble("TorporAnchorY", anchorY);
            output.putDouble("TorporAnchorZ", anchorZ);
        }
    }

    @Override public void deserialize(ValueInput input) {
        maximum = Math.max(1.0, input.getDoubleOr("TorporMaximum", 100.0));
        torpor = Math.clamp(input.getDoubleOr("Torpor", 0.0), 0.0, maximum);
        recoveryDelay = Math.max(0, input.getIntOr("TorporRecoveryDelay", 0));
        phase = parsePhase(input.getStringOr("TorporPhase", Phase.AWAKE.name()));
        phaseTicks = Math.max(0, input.getIntOr("TorporPhaseTicks", 0));
        phaseDuration = Math.max(1, input.getIntOr("TorporPhaseDuration", 1));
        anchorSet = input.getBooleanOr("TorporAnchorSet", false);
        if (anchorSet) {
            anchorX = input.getDoubleOr("TorporAnchorX", 0.0);
            anchorY = input.getDoubleOr("TorporAnchorY", 0.0);
            anchorZ = input.getDoubleOr("TorporAnchorZ", 0.0);
        }
        externalTicks = 0;
        dirty = true;
    }

    private void writeTo(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(torpor);
        buffer.writeDouble(maximum);
        buffer.writeVarInt(recoveryDelay);
        buffer.writeVarInt(phase.ordinal());
        buffer.writeVarInt(phaseTicks);
        buffer.writeVarInt(phaseDuration);
        buffer.writeVarInt(feedTicks);
        buffer.writeBoolean(anchorSet);
        if (anchorSet) {
            buffer.writeDouble(anchorX);
            buffer.writeDouble(anchorY);
            buffer.writeDouble(anchorZ);
        }
    }

    private static TorporState readFrom(RegistryFriendlyByteBuf buffer) {
        var state = new TorporState();
        state.torpor = buffer.readDouble();
        state.maximum = Math.max(1.0, buffer.readDouble());
        state.recoveryDelay = buffer.readVarInt();
        state.phase = Phase.values()[Math.clamp(buffer.readVarInt(), 0, Phase.values().length - 1)];
        state.phaseTicks = buffer.readVarInt();
        state.phaseDuration = buffer.readVarInt();
        state.feedTicks = buffer.readVarInt();
        state.anchorSet = buffer.readBoolean();
        if (state.anchorSet) {
            state.anchorX = buffer.readDouble();
            state.anchorY = buffer.readDouble();
            state.anchorZ = buffer.readDouble();
        }
        state.dirty = false;
        return state;
    }

    private static Phase parsePhase(String name) {
        for (Phase candidate : Phase.values()) if (candidate.name().equals(name)) return candidate;
        return Phase.AWAKE;
    }
}
