package dev.nez.arksurvivalreturns.feature.companion;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * What the command wheel asks of a tame (F22), clockwise from the top of the wheel: the four standing orders,
 * PET for the one tame under the crosshair, and CALL, which brings tames to the caller with their orders kept.
 */
public enum CompanionCommand {
    FOLLOW(CompanionOrder.FOLLOW, 9),
    STAY(CompanionOrder.STAY, 1),
    WANDER(CompanionOrder.WANDER, 14),
    WORK(CompanionOrder.WORK, 18),
    PET(null, 0),
    CALL(null, 5);

    private final @Nullable CompanionOrder order;
    private final int note;

    CompanionCommand(@Nullable CompanionOrder order, int note) {
        this.order = order;
        this.note = note;
    }

    /** The standing order this command gives, or null for PET and CALL. */
    public @Nullable CompanionOrder order() { return order; }

    /** Colour of the note a tame that heard the whistle shows above its head (a note block's 0 to 24). */
    public int note() { return note; }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static CompanionCommand byOrdinal(int value) {
        return values()[Math.clamp(value, 0, values().length - 1)];
    }

    public static @Nullable CompanionCommand of(CompanionOrder order) {
        for (var command : values()) if (command.order == order) return command;
        return null;
    }
}
