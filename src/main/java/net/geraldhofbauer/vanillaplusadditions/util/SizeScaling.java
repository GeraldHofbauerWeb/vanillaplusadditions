package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.util.Mth;

/**
 * How much stronger a creature is for being bigger.
 *
 * <p>One ladder, one formula, so every module that cares about size answers the question the same
 * way: {@code factor ^ (scale - 1)}. At the default factor of 1.5 that reads
 *
 * <pre>
 *   size 0.5 -> 0.82      size 2.0 -> 1.50
 *   size 1.0 -> 1.00      size 3.0 -> 2.25
 * </pre>
 *
 * <p>The rungs are <strong>additive in size</strong>, not multiplicative: each whole block of extra
 * size is worth another 1.5, so twice as big is 1.5 and three times as big is 1.5 × 1.5. That is the
 * ladder the numbers were chosen on. The alternative — keying the exponent to {@code log2(size)}, so
 * that shrinking is the exact inverse of growing — makes three times as big worth only 1.87, which
 * is not what the sizes are supposed to mean here.
 *
 * <p>Deliberately not a stat in itself: this returns a plain multiplier and holds no state. What
 * each module does with it — attribute modifiers, blocks of absorbed fall — is the module's own.
 */
public final class SizeScaling {

    /** Vanilla's own bounds for {@code generic.scale}; nothing outside them can be asked about. */
    private static final double MIN_SCALE = 0.0625D;
    private static final double MAX_SCALE = 16.0D;

    private SizeScaling() {
    }

    /**
     * The multiplier a creature of this size earns.
     *
     * @param scale  the creature's size, i.e. its {@code generic.scale} value
     * @param factor what one whole block of extra size is worth; 1.0 or less switches scaling off
     * @return the multiplier, 1.0 at ordinary size
     */
    public static double multiplier(double scale, double factor) {
        if (factor <= 1.0D) {
            return 1.0D;
        }
        return Math.pow(factor, Mth.clamp(scale, MIN_SCALE, MAX_SCALE) - 1.0D);
    }
}
