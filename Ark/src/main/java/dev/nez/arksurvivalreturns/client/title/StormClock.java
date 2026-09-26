package dev.nez.arksurvivalreturns.client.title;

/**
 * The lightning schedule of the title scene. The two FancyMenu shaders (tools/title_scene/*.glsl) compute the
 * same strikes from FancyMenu's Unix-time uniforms, so the sky, the ferns and the creature flash together and
 * the thunder follows the flash. Keep the constants and the hash in step with the shaders.
 */
public final class StormClock {
    /** A strike may start in each slot of this many seconds. */
    public static final int SLOT_SECONDS = 9;
    /** Percent of slots that strike. */
    static final int STRIKE_PERCENT = 40;
    /** A flash fades out within this many seconds. */
    static final double FLASH_SECONDS = 1.8;

    /** One strike: when it starts, how bright it is, where the bolt is and how long the thunder takes. */
    public record Strike(long slot, long startMillis, float strength, float boltX, float thunderDelaySeconds,
                         boolean firstFlicker, boolean secondFlicker) {
        /** Brightness of this strike {@code seconds} after it started (the shaders' envelope). */
        public float flash(double seconds) {
            if (seconds < 0 || seconds > FLASH_SECONDS) return 0f;
            double f = Math.exp(-seconds * 7.0);
            if (firstFlicker) f += 0.8 * Math.exp(-Math.abs(seconds - 0.14) * 32.0);
            if (secondFlicker) f += 0.6 * Math.exp(-Math.abs(seconds - 0.33) * 26.0);
            return (float) (f * strength);
        }
    }

    /** lowbias32 (Chris Wellons), the same integer hash as the shaders' {@code hashu}. */
    static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /** The strike in a slot (Unix seconds / {@link #SLOT_SECONDS}), or null when the slot stays dark. */
    public static Strike strike(long slot) {
        int h = hash((int) slot);
        if (Integer.remainderUnsigned(h, 100) >= STRIKE_PERCENT) return null;
        long start = slot * SLOT_SECONDS * 1000L + Integer.remainderUnsigned(h >>> 8, 7000);
        float strength = 0.55f + 0.45f * Integer.remainderUnsigned(h >>> 20, 100) / 100f;
        float boltX = Integer.remainderUnsigned(h >>> 12, 100) / 50f - 1f;
        // Thunder from 1 to 4 s after the flash; strong strikes are close.
        float thunder = 1f + 3f * (1f - (strength - 0.55f) / 0.45f) * (0.5f + 0.5f * ((h >>> 26) & 7) / 7f);
        return new Strike(slot, start, strength, boltX, thunder, ((h >>> 4) & 1) != 0, ((h >>> 5) & 1) != 0);
    }

    /** Flash brightness at a Unix time in milliseconds: the brighter of this slot's and the previous slot's strike. */
    public static float flash(long epochMillis) {
        long slot = Math.floorDiv(epochMillis, SLOT_SECONDS * 1000L);
        float flash = 0f;
        for (long s = slot - 1; s <= slot; s++) {
            Strike strike = strike(s);
            if (strike != null) flash = Math.max(flash, strike.flash((epochMillis - strike.startMillis()) / 1000.0));
        }
        return flash;
    }

    private StormClock() {}
}
