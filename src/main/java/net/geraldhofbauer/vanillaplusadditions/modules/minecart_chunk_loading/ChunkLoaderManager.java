package net.geraldhofbauer.vanillaplusadditions.modules.minecart_chunk_loading;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Server-side bookkeeping for active loader rails and their forced chunks.
 *
 * <p>Each loader rail that a minecart recently rode over is "active" with a last-seen game tick.
 * On reconcile, active rails force a (2R+1)x(2R+1) square of chunks (ticking, owner = rail pos)
 * via the NeoForge {@link TicketController}; rails whose timeout elapsed release their chunks.
 * Nothing is persisted — the {@code LoadingValidationCallback} drops all tickets on world load,
 * and active state is re-derived from cart movement.</p>
 */
public final class ChunkLoaderManager {

    private TicketController controller;

    /** level -> (rail pos -> last active game tick). */
    private final Map<ServerLevel, Map<BlockPos, Long>> active = new HashMap<>();
    /** level -> (rail pos -> set of forced chunk longs). */
    private final Map<ServerLevel, Map<BlockPos, Set<Long>>> forced = new HashMap<>();

    public void setController(TicketController controller) {
        this.controller = controller;
    }

    /** Marks a loader rail as active (a minecart is currently on it). */
    public void markActive(ServerLevel level, BlockPos railPos, long gameTime) {
        active.computeIfAbsent(level, k -> new HashMap<>()).put(railPos.immutable(), gameTime);
    }

    /**
     * Forces freshly-active rails' chunks and releases expired ones. Call periodically per level.
     *
     * @param radius        Chebyshev chunk radius around each active rail
     * @param timeoutTicks  ticks of inactivity before a rail releases its chunks
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
        Set<Long> set = lvlForced.remove(railPos);
        if (set != null) {
            for (long packed : set) {
                controller.forceChunk(level, railPos, ChunkPos.getX(packed), ChunkPos.getZ(packed), false, true);
            }
        }
    }

    /** Drops in-memory tracking for an unloading level (tickets vanish with the level). */
    public void forgetLevel(ServerLevel level) {
        active.remove(level);
        forced.remove(level);
    }
}
