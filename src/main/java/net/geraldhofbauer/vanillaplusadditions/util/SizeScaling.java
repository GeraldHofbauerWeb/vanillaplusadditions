package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.util.Mth;

/**
 * How much stronger a creature is for being bigger.
 *
 * <p>One ladder, read by every module that cares about size, so they cannot drift apart. Which ladder
 * is a genuine design choice with no right answer, so both are here and the config picks:
 *
 * <pre>
 *   size    ADDITIVE   MULTIPLICATIVE
 *   0.5       0.82         0.67
 *   1.0       1.00         1.00
 *   2.0       1.50         1.50
 *   3.0       2.25         1.87
 *   3.25      2.49         1.97
 *   4.0       3.38         2.25
 * </pre>
 *
 * <p>They agree at double size and part company past it. {@link Curve#ADDITIVE} counts whole blocks
 * of extra size, so three times as big is 1.5 twice over; {@link Curve#MULTIPLICATIVE} counts
 * doublings, which makes shrinking the exact inverse of growing but makes three times as big worth
 * less than the sum of its parts.
 *
 * <p>Deliberately not a stat in itself: this returns a plain multiplier and holds no state. What each
 * module does with it — attribute modifiers, blocks of absorbed fall — is the module's own.
 */
public final class SizeScaling {

    /** Vanilla's own bounds for {@code generic.scale}; nothing outside them can be asked about. */
    private static final double MIN_SCALE = 0.0625D;
    private static final double MAX_SCALE = 16.0D;

    private static final double LOG_TWO = Math.log(2.0D);

    private SizeScaling() {
    }

    /** The two ways size can turn into strength. */
    public enum Curve {
        /** {@code factor^(size-1)} — each whole block of extra size is worth another factor. */
        ADDITIVE,
        /** {@code factor^log2(size)} — each DOUBLING is worth another factor, so shrinking inverts it exactly. */
        MULTIPLICATIVE
    }

    /**
     * The multiplier a creature of this size earns.
     *
     * @param scale  the creature's size, i.e. its {@code generic.scale} value
     * @param factor what one rung of the ladder is worth; 1.0 or less switches scaling off
     * @param curve  which ladder to climb
     * @return the multiplier, 1.0 at ordinary size
     */
    public static double multiplier(double scale, double factor, Curve curve) {
        if (factor <= 1.0D) {
            return 1.0D;
        }
        double size = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
        double rungs = curve == Curve.MULTIPLICATIVE
                ? Math.log(size) / LOG_TWO
                : size - 1.0D;
        return Math.pow(factor, rungs);
    }
}
