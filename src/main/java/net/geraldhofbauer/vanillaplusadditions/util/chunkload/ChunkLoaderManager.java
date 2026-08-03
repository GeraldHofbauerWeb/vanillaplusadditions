package net.geraldhofbauer.vanillaplusadditions.util.chunkload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Server-side bookkeeping for active loader blocks and their forced chunks.
 *
 * <p>Each loader block that a vehicle recently rode over is "active" with a last-seen game tick.
 * On reconcile, active blocks force a (2R+1)x(2R+1) square of chunks (ticking, owner = block pos)
 * via the NeoForge {@link TicketController}; blocks whose timeout elapsed release their chunks.
 * The {@code LoadingValidationCallback} drops all tickets on world load; active state is
 * re-derived from vehicle movement plus the persistent {@link ChunkLoaderData} set.</p>
 *
 * <p>Shared between the minecart (chunk loader rail) and train (chunk loader track) modules —
 * each instance gets its own SavedData name and ticket controller.</p>
 */
public final class ChunkLoaderManager {

    private final String savedDataName;
    private TicketController controller;

    /** level -> (loader pos -> last active game tick). */
    private final Map<ServerLevel, Map<BlockPos, Long>> active = new HashMap<>();
    /** level -> (loader pos -> set of forced chunk longs). */
    private final Map<ServerLevel, Map<BlockPos, Set<Long>>> forced = new HashMap<>();

    public ChunkLoaderManager(String savedDataName) {
        this.savedDataName = savedDataName;
    }

    public void setController(TicketController controller) {
        this.controller = controller;
    }

    private ChunkLoaderData data(ServerLevel level) {
        return ChunkLoaderData.get(level, savedDataName);
    }

    /** Marks a loader block as active (a vehicle is currently on it). */
    public void markActive(ServerLevel level, BlockPos railPos, long gameTime) {
        active.computeIfAbsent(level, k -> new HashMap<>()).put(railPos.immutable(), gameTime);
    }

    /**
     * Forces freshly-active blocks' chunks and releases expired ones. Call periodically per level.
     *
     * @param radius        Chebyshev chunk radius around each active loader block
     * @param timeoutTicks  ticks of inactivity before a loader block releases its chunks
     */
    public void reconcile(ServerLevel level, long now, int radius, long timeoutTicks) {
        if (controller == null) {
            return;
        }
        Map<BlockPos, Long> lvlActive = active.get(level);
        if (lvlActive == null || lvlActive.isEmpty()) {
            return;
        }
        Map<BlockPos, Set<Long>> lvlForced = forced.computeIfAbsent(level, k -> new HashMap<>());

        Iterator<Map.Entry<BlockPos, Long>> it = lvlActive.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            BlockPos railPos = entry.getKey();
            boolean expired = now - entry.getValue() > timeoutTicks;

            if (expired) {
                releaseRail(level, lvlForced, railPos);
                it.remove();
            } else if (!lvlForced.containsKey(railPos)) {
                forceRail(level, lvlForced, railPos, radius);
            }
        }
    }

    private void forceRail(ServerLevel level, Map<BlockPos, Set<Long>> lvlForced, BlockPos railPos, int radius) {
        forceChunksFor(level, lvlForced, railPos, radius);
        // Persist so the loader can be resumed after a "no players" pause or a server restart.
        data(level).add(railPos.asLong());
    }

    private void forceChunksFor(ServerLevel level, Map<BlockPos, Set<Long>> lvlForced, BlockPos railPos, int radius) {
        int cx = railPos.getX() >> 4;
        int cz = railPos.getZ() >> 4;
        Set<Long> set = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                controller.forceChunk(level, railPos, x, z, true, true);
                set.add(ChunkPos.asLong(x, z));
            }
        }
        lvlForced.put(railPos, set);
    }

    private void releaseRail(ServerLevel level, Map<BlockPos, Set<Long>> lvlForced, BlockPos railPos) {
        unforceChunksFor(level, lvlForced, railPos);
        // Vehicle left this loader for good — forget it so it isn't resumed later.
        data(level).remove(railPos.asLong());
    }

    private void unforceChunksFor(ServerLevel level, Map<BlockPos, Set<Long>> lvlForced, BlockPos railPos) {
        Set<Long> set = lvlForced.remove(railPos);
        if (set != null) {
            for (long packed : set) {
                controller.forceChunk(level, railPos, ChunkPos.getX(packed), ChunkPos.getZ(packed), false, true);
            }
        }
    }

    /**
     * Releases ALL currently forced chunks for a level (e.g. last player left), but KEEPS the
     * persistent {@link ChunkLoaderData} set so the loaders can be resumed later.
     */
    public void releaseAll(ServerLevel level) {
        Map<BlockPos, Set<Long>> lvlForced = forced.get(level);
        if (lvlForced != null) {
            for (Map.Entry<BlockPos, Set<Long>> entry : lvlForced.entrySet()) {
                BlockPos owner = entry.getKey();
                for (long packed : entry.getValue()) {
                    controller.forceChunk(level, owner, ChunkPos.getX(packed), ChunkPos.getZ(packed), false, true);
                }
            }
            lvlForced.clear();
        }
        Map<BlockPos, Long> lvlActive = active.get(level);
        if (lvlActive != null) {
            lvlActive.clear();
        }
    }

    /**
     * Re-forces every loader recorded in {@link ChunkLoaderData} (server start / first player
     * join), so chunks with stuck vehicles load again and the vehicles continue moving.
     */
    public void resume(ServerLevel level, int radius) {
        if (controller == null) {
            return;
        }
        ChunkLoaderData data = data(level);
        if (data.rails().isEmpty()) {
            return;
        }
        Map<BlockPos, Long> lvlActive = active.computeIfAbsent(level, k -> new HashMap<>());
        Map<BlockPos, Set<Long>> lvlForced = forced.computeIfAbsent(level, k -> new HashMap<>());
        long now = level.getGameTime();
        for (long packed : new ArrayList<>(data.rails())) {
            BlockPos railPos = BlockPos.of(packed);
            lvlActive.put(railPos, now);
            if (!lvlForced.containsKey(railPos)) {
                forceChunksFor(level, lvlForced, railPos, radius);
            }
        }
    }

    /** Drops in-memory tracking for an unloading level (tickets vanish with the level). */
    public void forgetLevel(ServerLevel level) {
        active.remove(level);
        forced.remove(level);
    }
}
