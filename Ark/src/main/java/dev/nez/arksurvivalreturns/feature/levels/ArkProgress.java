package dev.nez.arksurvivalreturns.feature.levels;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

/** Permanent level, XP towards the next level, and exact recipe IDs already rewarded. */
public final class ArkProgress implements ValueIOSerializable {
    // Craft history stays on the server; the owner only needs the bar's values.
    public static final StreamCodec<RegistryFriendlyByteBuf, ArkProgress> STREAM_CODEC = StreamCodec.of(
            (buffer, state) -> { buffer.writeVarInt(state.level); buffer.writeVarLong(state.xp); },
            buffer -> {
                var state = new ArkProgress();
                state.level = Math.max(0, buffer.readVarInt());
                state.xp = Math.max(0L, buffer.readVarLong());
                return state;
            });

    private int level;
    private long xp;
    private final Set<Identifier> crafted = new HashSet<>();

    public int level() { return level; }
    public long xp() { return xp; }
    public Set<Identifier> craftedRecipes() { return Set.copyOf(crafted); }

    boolean markCrafted(Identifier recipe) { return crafted.add(recipe); }
    void set(int level, long xp) { this.level = level; this.xp = xp; }

    @Override public void serialize(ValueOutput output) {
        output.putInt("level", level);
        output.putLong("xp", xp);
        output.store("crafted", Identifier.CODEC.listOf(), new ArrayList<>(crafted));
    }

    @Override public void deserialize(ValueInput input) {
        level = Math.max(0, input.getIntOr("level", 0));
        xp = Math.max(0L, input.getLongOr("xp", 0L));
        crafted.clear();
        crafted.addAll(input.read("crafted", Identifier.CODEC.listOf()).orElseGet(java.util.List::of));
    }
}
