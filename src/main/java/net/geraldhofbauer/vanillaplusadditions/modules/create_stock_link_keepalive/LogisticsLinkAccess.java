package net.geraldhofbauer.vanillaplusadditions.modules.create_stock_link_keepalive;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

/**
 * Reflection-only bridge to Create's logistics links - the Stock Links and Stock Tickers whose
 * inventories make up a network's stock figure.
 *
 * <p><strong>Why this exists.</strong> A link only counts towards the network summary while it sits
 * in {@code LogisticallyLinkedBehaviour.LINKS}, a {@code TickBasedCache(20, true)} - so its entry
 * dies twenty ticks, i.e. <em>one second</em>, after the last refresh. The only thing that refreshes
 * it is the link's own {@code lazyTick()}, which of course requires the link's chunk to be
 * <em>ticking</em>.</p>
 *
 * <p>That is the whole bug, measured on 2026-09-23. When a player disconnects, their chunk tickets
 * go away and the chunks stop ticking - but they do not unload for a while. Reconnect 1.4 seconds
 * later and the block entities were never unloaded, so Create's {@code loadedLinks} still lists them
 * and the {@code waitingForNetwork} guard stays false; the {@code LINKS} cache, however, expired
 * after 1.0 second. The gauge ticks, reads a summary that no link contributed to, and orders against
 * a full vault. No {@code ChunkEvent.Load} is involved anywhere, which is why a grace window tied to
 * chunk loading could never catch it.</p>
 *
 * <p>The cure is to refresh the cache from our own {@code ServerTickEvent}, which runs whether or not
 * the links' chunks tick. {@code keepAlive} is public and does nothing but re-stamp the entry, so
 * this is as small an intervention as the problem allows.</p>
 */
final class LogisticsLinkAccess {

    private static final String BEHAVIOUR_CLASS =
            "com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour";
    private static final String BEHAVIOUR_BASE =
            "com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour";
    private static final String BEHAVIOUR_TYPE =
            "com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType";

    private static final Object TYPE;
    private static final Method GET_BEHAVIOUR;
    private static final Method KEEP_ALIVE;
    private static final boolean AVAILABLE;

    static {
        Object type = null;
        Method getBehaviour = null;
        Method keepAlive = null;
        boolean ok = false;
        try {
            Class<?> behaviourClass = Class.forName(BEHAVIOUR_CLASS);
            type = behaviourClass.getField("TYPE").get(null);
            keepAlive = behaviourClass.getMethod("keepAlive", behaviourClass);
            getBehaviour = Class.forName(BEHAVIOUR_BASE)
                    .getMethod("get", BlockEntity.class, Class.forName(BEHAVIOUR_TYPE));
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        TYPE = type;
        GET_BEHAVIOUR = getBehaviour;
        KEEP_ALIVE = keepAlive;
        AVAILABLE = ok;
    }

    private LogisticsLinkAccess() {
    }

    /**
     * Whether every reflective handle resolved. If not, the module stays inert rather than throwing.
     *
     * @return true if the bridge is usable
     */
    static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Re-stamps a logistics link's entry in Create's link cache, so it keeps contributing to the
     * network's stock figure even while its chunk is not ticking.
     *
     * <p>Harmless to repeat and harmless to call on a block that carries no such behaviour: the
     * lookup simply returns null and nothing happens. It never revives a link that is really gone -
     * positions are dropped from the registry as soon as their chunk unloads or the block breaks.</p>
     *
     * @param blockEntity The block entity to refresh (may be null)
     * @return true if a link behaviour was found and refreshed
     */
    static boolean keepAlive(BlockEntity blockEntity) {
        if (!AVAILABLE || blockEntity == null) {
            return false;
        }
        try {
            Object behaviour = GET_BEHAVIOUR.invoke(null, blockEntity, TYPE);
            if (behaviour == null) {
                return false;
            }
            KEEP_ALIVE.invoke(null, behaviour);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
