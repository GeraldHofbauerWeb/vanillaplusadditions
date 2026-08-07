package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client;

import net.minecraft.core.BlockPos;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network.LinkDisplay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Short-lived client-side memory of the link data the server sent for a queried block, so the
 * overlay can render every frame off one request per second or so.
 */
public final class InventoryLinkClientCache {

    /** After this many ticks an entry is refreshed; after twice that it stops rendering. */
    public static final long REFRESH_AFTER_TICKS = 40L;
    public static final long EXPIRE_AFTER_TICKS = 80L;

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();

    private InventoryLinkClientCache() {
    }

    public record Entry(List<LinkDisplay> links, long receivedAt) {
        public boolean needsRefresh(long now) {
            return now - receivedAt > REFRESH_AFTER_TICKS;
        }

        public boolean isExpired(long now) {
            return now - receivedAt > EXPIRE_AFTER_TICKS;
        }
    }

    public static void put(BlockPos pos, List<LinkDisplay> links, long now) {
        ENTRIES.put(pos.immutable(), new Entry(links, now));
    }

    public static Entry get(BlockPos pos) {
        return ENTRIES.get(pos);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    /** Drops entries nobody is looking at any more, so the map can't grow unbounded. */
    public static void prune(long now) {
        ENTRIES.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}
