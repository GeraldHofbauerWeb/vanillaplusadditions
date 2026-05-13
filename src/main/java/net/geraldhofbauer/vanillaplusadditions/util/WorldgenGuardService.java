package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for handling worldgen crash guard notifications.
 *
 * <p>NOTE: Region files are NOT deleted at runtime to avoid infinite regeneration loops:
 * deleting a file causes the server to retry generation, which fails again and again → hang.
 * Instead, problematic chunk coordinates are logged so administrators can manually
 * delete the region file after a clean server shutdown.
 *
 * <p>Broadcasts are scheduled on the main server thread (safe to call from any thread).
 * Rate-limiting prevents log/chat spam when many chunks fail in quick succession.
 */
public final class WorldgenGuardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldgenGuardService.class);

    // Rate-limit player broadcasts: max one message per 10 seconds
    private static final long BROADCAST_COOLDOWN_MS = 10_000L;
    private static final AtomicLong LAST_BROADCAST_MS = new AtomicLong(0L);

    // Rate-limit detailed log output: max one full log per 5 seconds
    private static final long LOG_COOLDOWN_MS = 5_000L;
    private static final AtomicLong LAST_LOG_MS = new AtomicLong(0L);

    private WorldgenGuardService() {
        // Utility class
    }

    /**
     * Logs a suppressed worldgen error and broadcasts a rate-limited warning to all players.
     * Safe to call from any thread - player broadcast is scheduled on the server main thread.
     *
     * <p>Does NOT delete region files: that must be done after a clean server shutdown.
     *
     * @param chunkPos      The chunk position that failed
     * @param level         The server level (used for broadcast and dimension info)
     * @param exceptionType Simple name of the suppressed exception class
     */
    public static void logAndBroadcast(ChunkPos chunkPos, ServerLevel level, String exceptionType) {
        String dimension = level.dimension().location().toString();
        long now = System.currentTimeMillis();

        // Rate-limited detailed log
        long lastLog = LAST_LOG_MS.get();
        if (now - lastLog >= LOG_COOLDOWN_MS && LAST_LOG_MS.compareAndSet(lastLog, now)) {
            LOGGER.warn(
                    "[Worldgen Guard] Suppressed {} at chunk {},{} in {}. "
                            + "To fix permanently: shut down server and delete region file r.{}.{}.mca",
                    exceptionType, chunkPos.x, chunkPos.z, dimension,
                    chunkPos.x >> 5, chunkPos.z >> 5
            );
        }

        // Rate-limited player broadcast, always scheduled on main thread
        long lastBroadcast = LAST_BROADCAST_MS.get();
        if (now - lastBroadcast >= BROADCAST_COOLDOWN_MS
                && LAST_BROADCAST_MS.compareAndSet(lastBroadcast, now)) {
            if (level.getServer() != null) {
                final String message = "[Worldgen Guard] Chunk " + chunkPos.x + "," + chunkPos.z
                        + " in " + dimension + " skipped (" + exceptionType + ")."
                        + " Delete region r." + (chunkPos.x >> 5) + "." + (chunkPos.z >> 5)
                        + ".mca after server restart to regenerate.";
                level.getServer().execute(() -> MessageBroadcaster.broadcast(level, message));
            }
        }
    }
}
