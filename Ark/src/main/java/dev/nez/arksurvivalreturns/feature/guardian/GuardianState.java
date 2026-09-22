package dev.nez.arksurvivalreturns.feature.guardian;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Lifecycle of one ritual anchor. Absent records read as {@link #LOCKED}. */
public enum GuardianState implements StringRepresentable {
    /** No valid heart has been offered at this anchor yet. */
    LOCKED("locked"),
    /** A heart unlocked the current attempt; the tribe may summon the Guardian again for free. */
    READY("ready"),
    /** One Guardian owns the encounter and is alive. */
    ACTIVE("active"),
    /** The Guardian was removed because the tribe retreated; the free retry window starts after the delay. */
    RESETTING("resetting"),
    /** Rewards were issued exactly once; a deliberate rematch needs another heart. */
    DEFEATED("defeated");

    public static final Codec<GuardianState> CODEC = StringRepresentable.fromEnum(GuardianState::values);
    private final String name;

    GuardianState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
