package dev.nez.arksurvivalreturns.feature.recovery;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

/**
 * Downed state for one player: a bleed-out window and the anchor they fell at.
 *
 * <p>Persisted with the player, so relogging does not pause or duplicate the window; the remaining
 * ticks resume unchanged. No offline drain happens because nothing ticks while the player is away.
 */
public final class DownedState implements ValueIOSerializable {
    public static final StreamCodec<RegistryFriendlyByteBuf, DownedState> STREAM_CODEC =
            StreamCodec.of((buffer, state) -> state.writeTo(buffer), DownedState::readFrom);

    private boolean downed;
    private int ticksLeft;
    private int totalTicks;
    private boolean anchorSet;
    private double anchorX, anchorY, anchorZ;

    public boolean downed() {
        return downed;
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public boolean hasAnchor() {
        return anchorSet;
    }

    public Vec3 anchor() {
        return new Vec3(anchorX, anchorY, anchorZ);
    }

    public void setAnchor(Vec3 anchor) {
        anchorSet = true;
        anchorX = anchor.x;
        anchorY = anchor.y;
        anchorZ = anchor.z;
    }

    public void start(int windowTicks, Vec3 anchor) {
        downed = true;
        ticksLeft = Math.max(1, windowTicks);
        totalTicks = ticksLeft;
        setAnchor(anchor);
    }

    /** Incoming damage shortens the rescue window; the window can reach zero here. */
    public void bleed(float damage, double factor) {
        ticksLeft = DownedPolicy.bleedOut(ticksLeft, damage, factor);
    }

    public void tick() {
        if (downed) ticksLeft = Math.max(0, ticksLeft - 1);
    }

    public void revive() {
        downed = false;
        ticksLeft = 0;
        totalTicks = 0;
    }

    public void clear() {
        revive();
        anchorSet = false;
    }

    @Override public void serialize(ValueOutput output) {
        output.putBoolean("Downed", downed);
        output.putInt("DownedTicksLeft", ticksLeft);
        output.putInt("DownedTotalTicks", totalTicks);
        output.putBoolean("DownedAnchorSet", anchorSet);
        if (anchorSet) {
            output.putDouble("DownedAnchorX", anchorX);
            output.putDouble("DownedAnchorY", anchorY);
            output.putDouble("DownedAnchorZ", anchorZ);
        }
    }

    @Override public void deserialize(ValueInput input) {
        downed = input.getBooleanOr("Downed", false);
        ticksLeft = Math.max(0, input.getIntOr("DownedTicksLeft", 0));
        totalTicks = Math.max(0, input.getIntOr("DownedTotalTicks", 0));
        anchorSet = input.getBooleanOr("DownedAnchorSet", false);
        if (anchorSet) {
            anchorX = input.getDoubleOr("DownedAnchorX", 0.0);
            anchorY = input.getDoubleOr("DownedAnchorY", 0.0);
            anchorZ = input.getDoubleOr("DownedAnchorZ", 0.0);
        }
    }

    private void writeTo(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(downed);
        buffer.writeVarInt(ticksLeft);
        buffer.writeVarInt(totalTicks);
    }

    private static DownedState readFrom(RegistryFriendlyByteBuf buffer) {
        var state = new DownedState();
        state.downed = buffer.readBoolean();
        state.ticksLeft = Math.max(0, buffer.readVarInt());
        state.totalTicks = Math.max(0, buffer.readVarInt());
        return state;
    }
}
