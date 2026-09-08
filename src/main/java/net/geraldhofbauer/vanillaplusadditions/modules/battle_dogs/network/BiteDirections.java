package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last direction each wolf was seen biting in, as told by the server.
 *
 * <p>Deliberately free of any client-only type so the packet handler that fills it can be
 * registered from the common module class without dragging client classes onto a server.
 *
 * <p>Entries are timestamped rather than tick-counted and expire on their own: a swing lasts six
 * ticks, so anything older than {@link #LIFETIME_MS} belongs to a bite that is already over and the
 * animation falls back to the head yaw. That also means nothing has to be cleaned up when a wolf
 * dies or unloads — a stale entry is simply never read again, and the map only ever holds wolves
 * that bit something nearby.
 */
public final class BiteDirections {

    /** How long a reported direction stays usable, comfortably longer than a six-tick swing. */
    private static final long LIFETIME_MS = 500L;

    private static final Map<Integer, long[]> LAST_BITE = new ConcurrentHashMap<>();

    private BiteDirections() {
    }

    /**
     * Records the direction a wolf just bit in.
     *
     * @param wolfId entity id of the wolf
     * @param yaw    yaw in degrees, {@code Entity.getYRot()} convention
     */
    public static void record(int wolfId, float yaw) {
        LAST_BITE.put(wolfId, new long[]{Float.floatToIntBits(yaw), System.currentTimeMillis()});
    }

    /**
     * The direction a wolf bit in, if it did so recently enough to still be animating.
     *
     * @param wolfId   entity id of the wolf
     * @param fallback yaw to use when there is no fresh report
     * @return the reported yaw, or the fallback
     */
    public static float yawOr(int wolfId, float fallback) {
        long[] entry = LAST_BITE.get(wolfId);
        if (entry == null || System.currentTimeMillis() - entry[1] > LIFETIME_MS) {
            return fallback;
        }
        return Float.intBitsToFloat((int) entry[0]);
    }
}
