package net.geraldhofbauer.vanillaplusadditions.util.blocktracking;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * In-memory, per-dimension cache of block positions matching a caller-supplied predicate.
 *
 * <p>Positions are discovered from a chunk's block-entity map on chunk load (which also covers
 * server start, as the spawn chunks load), and from placement events; they are dropped on break and
 * on chunk unload. Nothing is persisted - rediscovery on chunk load is free, because reading
 * {@code chunk.getBlockEntities()} costs nothing compared to a block scan.</p>
 *
 * <p>This is the shared scaffolding behind the Create companion modules
 * ({@code create_redstone_link_rebinder}, {@code create_stock_link_keepalive}), all of which need
 * the same thing: know where the interesting block entities are, so a periodic sweep never has to
 * scan the world. Pair it with a {@link PostLoadScheduler} for the targeted check right after a
 * chunk load.</p>
 *
 * <p>Thread safety: {@code ChunkEvent.Load} can fire off the server thread during world generation,
 * so the backing collections are concurrent.</p>
 */
public class TrackedPositionRegistry {

    /** Decides whether a block state is worth remembering. */
    private final Predicate<BlockState> matcher;

    /** Tracked positions per dimension. */
    private final Map<ResourceKey<Level>, Set<BlockPos>> tracked = new ConcurrentHashMap<>();

    /**
     * Creates a registry for the block states accepted by the given predicate.
     *
     * @param matcher Predicate deciding which block states are tracked
     */
    public TrackedPositionRegistry(Predicate<BlockState> matcher) {
        this.matcher = matcher;
    }

    /**
     * Whether the given state is one this registry tracks.
     *
     * @param state The block state to test
     * @return true if the state matches
     */
    public boolean matches(BlockState state) {
        return matcher.test(state);
    }

    /**
     * Scans a freshly loaded chunk's block entities and registers the matching positions. Only the
     * block-entity map is read - never the block storage - so this stays cheap even on chunk-load
     * storms.
     *
     * @param level The server level the chunk belongs to
     * @param chunk The loaded chunk
     * @return the newly discovered positions in this chunk (immutable copies)
     */
    public List<BlockPos> discoverChunk(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            if (matcher.test(chunk.getBlockState(pos))) {
                BlockPos immutable = pos.immutable();
                positions(level).add(immutable);
                found.add(immutable);
            }
        }
        return found;
    }

    /**
     * Registers a position when a matching block gets placed.
     *
     * @param level The server level
     * @param pos   The placed position
     * @param state The placed block state
     * @return true if the position is now tracked
     */
    public boolean onBlockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (!matcher.test(state)) {
            return false;
        }
        positions(level).add(pos.immutable());
        return true;
    }

    /**
     * Unregisters a position when a matching block gets broken.
     *
     * @param level The server level
     * @param pos   The broken position
     * @param state The broken block state
     * @return true if a tracked position was dropped
     */
    public boolean onBlockBroken(ServerLevel level, BlockPos pos, BlockState state) {
        if (!matcher.test(state)) {
            return false;
        }
        return positions(level).remove(pos);
    }

    /**
     * Lazy validation: whether the tracked position still holds a matching block. Covers removal
     * paths that fire no break event (explosions, {@code /setblock}, Create's wrench pickup).
     * Callers must ensure the chunk is loaded first - this reads the block state directly.
     *
     * @param level The server level
     * @param pos   The tracked position
     * @return true if a matching block is still there
     */
    public boolean isStillTracked(ServerLevel level, BlockPos pos) {
        return matcher.test(level.getBlockState(pos));
    }

    /**
     * Removes a single tracked position (used when lazy validation fails).
     *
     * @param level The server level
     * @param pos   The position to drop
     */
    public void remove(ServerLevel level, BlockPos pos) {
        positions(level).remove(pos);
    }

    /**
     * Drops all tracked positions inside an unloading chunk (rediscovered on re-load).
     *
     * @param level    The server level
     * @param chunkPos The unloading chunk
     */
    public void forgetChunk(ServerLevel level, ChunkPos chunkPos) {
        positions(level).removeIf(pos -> chunkPos.x == (pos.getX() >> 4) && chunkPos.z == (pos.getZ() >> 4));
    }

    /**
     * Drops all tracked positions of an unloading level.
     *
     * @param level The server level being unloaded
     */
    public void forgetLevel(ServerLevel level) {
        tracked.remove(level.dimension());
    }

    /**
     * Clears everything (server stopped).
     */
    public void clearAll() {
        tracked.clear();
    }

    /**
     * The live set of tracked positions for a level (created on demand).
     *
     * @param level The server level
     * @return concurrent set of tracked positions
     */
    public Set<BlockPos> positions(ServerLevel level) {
        return tracked.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet());
    }

    /**
     * Read-only view of the tracked positions for a level without creating an empty set.
     *
     * @param level The server level
     * @return the tracked positions, possibly empty
     */
    public Set<BlockPos> positionsIfPresent(ServerLevel level) {
        Set<BlockPos> set = tracked.get(level.dimension());
        return set != null ? set : Collections.emptySet();
    }
}
