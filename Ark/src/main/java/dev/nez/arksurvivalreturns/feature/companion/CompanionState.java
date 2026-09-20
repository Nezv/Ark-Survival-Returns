package dev.nez.arksurvivalreturns.feature.companion;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import org.jspecify.annotations.Nullable;

/**
 * Persistent standing order and its anchor for one tamed creature.
 *
 * <p>STAY and WANDER keep the position where the order was given; FOLLOW carries no anchor and chases the
 * owner instead. State lives on the entity like torpor and taming, is saved with it and can be synced to
 * observers, so no UUID map is involved.
 */
public final class CompanionState implements ValueIOSerializable {
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionState> STREAM_CODEC = StreamCodec.of(
            (buffer, state) -> state.writeTo(buffer), CompanionState::readFrom);

    private CompanionOrder order = CompanionOrder.FOLLOW;
    private @Nullable BlockPos anchor;
    private boolean dirty = true;

    public CompanionOrder order() { return order; }
    public @Nullable BlockPos anchor() { return anchor; }
    public boolean dirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void setOrder(CompanionOrder value, @Nullable BlockPos anchor) {
        dirty |= value != order || !Objects.equals(this.anchor, anchor);
        order = value;
        this.anchor = anchor;
    }

    @Override public void serialize(ValueOutput output) {
        output.putInt("CompanionOrder", order.ordinal());
        if (anchor != null) output.putLong("CompanionAnchor", anchor.asLong());
    }

    @Override public void deserialize(ValueInput input) {
        order = CompanionOrder.byOrdinal(input.getIntOr("CompanionOrder", CompanionOrder.FOLLOW.ordinal()));
        long packed = input.getLongOr("CompanionAnchor", Long.MIN_VALUE);
        anchor = packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
        dirty = true;
    }

    private void writeTo(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(order.ordinal());
        buffer.writeBoolean(anchor != null);
        if (anchor != null) buffer.writeBlockPos(anchor);
    }

    private static CompanionState readFrom(RegistryFriendlyByteBuf buffer) {
        var state = new CompanionState();
        state.order = CompanionOrder.byOrdinal(buffer.readVarInt());
        state.anchor = buffer.readBoolean() ? buffer.readBlockPos() : null;
        state.dirty = false;
        return state;
    }
}
