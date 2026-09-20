package dev.nez.arksurvivalreturns.feature.tribe;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The actions another tribe member may be granted on a tame owned by a teammate.
 *
 * <p>The owner always keeps every permission; these flags only extend access to party members.
 */
public enum TribePermission {
    RIDE,
    CARGO,
    COMMANDS,
    BREEDING,
    /** Harvest work: supervising a teammate's tame while it works a job site. */
    WORK;

    public int mask() {
        return 1 << ordinal();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable TribePermission byName(String name) {
        for (TribePermission value : values()) {
            if (value.key().equalsIgnoreCase(name)) return value;
        }
        return null;
    }

    /** True when the permission bit is present in a resolved mask. */
    public static boolean allows(int mask, TribePermission permission) {
        return (mask & permission.mask()) != 0;
    }

    public static int mask(boolean ride, boolean cargo, boolean commands, boolean breeding) {
        return mask(ride, cargo, commands, breeding, false);
    }

    public static int mask(boolean ride, boolean cargo, boolean commands, boolean breeding, boolean work) {
        int mask = 0;
        if (ride) mask |= RIDE.mask();
        if (cargo) mask |= CARGO.mask();
        if (commands) mask |= COMMANDS.mask();
        if (breeding) mask |= BREEDING.mask();
        if (work) mask |= WORK.mask();
        return mask;
    }
}
