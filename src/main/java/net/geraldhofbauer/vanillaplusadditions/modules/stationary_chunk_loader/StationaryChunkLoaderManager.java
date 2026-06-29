package net.geraldhofbauer.vanillaplusadditions.modules.stationary_chunk_loader;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side bookkeeping for stationary Chunk Anchors and their forced chunks.
 *
 * <p>Each active (redstone-powered) anchor forces a (2R+1)x(2R+1) square of ticking chunks
 * (owner = anchor pos) via the NeoForge {@link TicketController}. Active anchor positions are
 * persisted in {@link ChunkAnchorData}; the {@code LoadingValidationCallback} drops all tickets on
 * world load and they are rebuilt from the persisted set on {@link #resume}.</p>
 */
public final class StationaryChunkLoaderManager {

    private TicketController controller;

    /** level -> (anchor pos -> set of forced chunk longs). */
    private final Map<ServerLevel, Map<BlockPos, Set<Long>>> forced = new HashMap<>();

    public void setController(TicketController controller) {
        this.controller = controller;
    }

    /**
     * Records a freshly-active anchor (always persisted) and force-loads its chunks immediately when
     * force-loading is currently enabled (a player is online).
     */
    public void addAnchor(ServerLevel level, BlockPos anchorPos, int radius, boolean forceNow) {
        ChunkAnchorData.get(level).add(anchorPos.asLong());
        if (forceNow && controller != null) {
            Map<BlockPos, Set<Long>> lvlForced = forced.computeIfAbsent(level, k -> new HashMap<>());
            if (!lvlForced.containsKey(anchorPos.immutable())) {
                forceChunksFor(level, lvlForced, anchorPos.immutable(), radius);
            }
        }
    }

    /** Removes an inactive/broken anchor: forgets it persistently and releases its forced chunks. */
    public void removeAnchor(ServerLevel level, BlockPos anchorPos) {
        ChunkAnchorData.get(level).remove(anchorPos.asLong());
        Map<BlockPos, Set<Long>> lvlForced = forced.get(level);
        if (lvlForced != null) {
            unforceChunksFor(level, lvlForced, anchorPos.immutable());
        }
    }

    private void forceChunksFor(ServerLevel level, Map<BlockPos, Set<Long>> lvlForced, BlockPos anchorPos,
                                int radius) {
        int cx = anchorPos.getX() >> 4;
        int cz = anchorPos.getZ() >> 4;
        Set<Long> set = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                controller.forceChunk(level, anchorPos, x, z, true, true);
                set.add(ChunkPos.asLong(x, z));
            }
        }
        lvlForced.put(anchorPos, set);
    }

    private void unforceChunksFor(ServerLevel level, Map<BlockPos, Set<Long>> lvlForced, BlockPos anchorPos) {
        Set<Long> set = lvlForced.remove(anchorPos);
        if (set != null && controller != null) {
            for (long packed : set) {
                controller.forceChunk(level, anchorPos, ChunkPos.getX(packed), ChunkPos.getZ(packed), false, true);
            }
        }
    }

    /**
     * Re-forces every anchor recorded in {@link ChunkAnchorData} (server start / first player join),
     * so anchored chunks load again.
     */
    public void resume(ServerLevel level, int radius) {
        if (controller == null) {
            return;
        }
        ChunkAnchorData data = ChunkAnchorData.get(level);
        if (data.anchors().isEmpty()) {
            return;
        }
        Map<BlockPos, Set<Long>> lvlForced = forced.computeIfAbsent(level, k -> new HashMap<>());
        for (long packed : new ArrayList<>(data.anchors())) {
            BlockPos anchorPos = BlockPos.of(packed);
            if (!lvlForced.containsKey(anchorPos)) {
                forceChunksFor(level, lvlForced, anchorPos, radius);
            }
        }
    }

    /**
     * Releases ALL currently forced chunks for a level (e.g. last player left), but KEEPS the
     * persistent {@link ChunkAnchorData} set so the anchors can be resumed later.
     */
    public void releaseAll(ServerLevel level) {
        Map<BlockPos, Set<Long>> lvlForced = forced.get(level);
        if (lvlForced != null && controller != null) {
            for (Map.Entry<BlockPos, Set<Long>> entry : lvlForced.entrySet()) {
                BlockPos owner = entry.getKey();
                for (long packed : entry.getValue()) {
                    controller.forceChunk(level, owner, ChunkPos.getX(packed), ChunkPos.getZ(packed), false, true);
                }
            }
            lvlForced.clear();
        }
    }

    /** Drops in-memory tracking for an unloading level (tickets vanish with the level). */
    public void forgetLevel(ServerLevel level) {
        forced.remove(level);
    }
}
