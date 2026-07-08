package net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker;

import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelStructuralBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-dimension registry of Create water wheel <em>center</em> positions.
 *
 * <p>Positions are discovered from a chunk's block-entity map on chunk load (covers server start as
 * spawn chunks load - only wheel centers have block entities, structural shell blocks don't) and
 * from placement events; they are dropped on break/unload and lazily validated during the periodic
 * sweep. No persistence: rediscovery on chunk load is free.</p>
 *
 * <p>Only Create's <em>Block</em> classes are referenced here - they are compile-safe, unlike the
 * Ponder-tainted block-entity hierarchy (see {@link WaterWheelKinetics}). Instances of this class
 * must only be created after the Create-loaded gate has passed.</p>
 */
class WaterWheelRegistry {

    /**
     * Tracked wheel centers per dimension. Concurrent because {@code ChunkEvent.Load} can fire off
     * the server thread during world generation.
     */
    private final Map<ResourceKey<Level>, Set<BlockPos>> wheels = new ConcurrentHashMap<>();

    /**
     * Checks whether the given state is a water wheel center block (small wheel, or the large
     * wheel's controller block that owns the block entity).
     *
     * @param state The block state to test
     * @return true for wheel center blocks
     */
    static boolean isWheelBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof WaterWheelBlock || block instanceof LargeWaterWheelBlock;
    }

    /**
     * Scans a freshly loaded chunk's block entities for water wheels and registers them.
     *
     * @param level The server level the chunk belongs to
     * @param chunk The loaded chunk
     * @return the newly discovered wheel positions in this chunk (immutable copies)
     */
    List<BlockPos> discoverChunk(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            if (isWheelBlock(chunk.getBlockState(pos))) {
                BlockPos immutable = pos.immutable();
                positions(level).add(immutable);
                found.add(immutable);
            }
        }
        return found;
    }

    /**
     * Registers a wheel when its center block gets placed.
     *
     * @param level The server level
     * @param pos   The placed position
     * @param state The placed block state
     */
    void onBlockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (isWheelBlock(state)) {
            positions(level).add(pos.immutable());
        }
    }

    /**
     * Unregisters a wheel when its center block - or, for the large wheel, one of its structural
     * shell blocks - gets broken.
     *
     * @param level The server level
     * @param pos   The broken position
     * @param state The broken block state
     */
    void onBlockBroken(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof WaterWheelStructuralBlock) {
            BlockPos master = WaterWheelStructuralBlock.getMaster(level, pos, state);
            if (master != null) {
                positions(level).remove(master);
            }
        } else if (isWheelBlock(state)) {
            positions(level).remove(pos);
        }
    }

    /**
     * Lazy validation: whether the tracked position still holds a wheel center block. Covers
     * removal paths that fire no break event (explosions, {@code /setblock}, Create's sneak-wrench
     * pickup). Callers must ensure the chunk is loaded first - this reads the block state directly.
     *
     * @param level The server level
     * @param pos   The tracked position
     * @return true if a wheel center block is still there
     */
    boolean isStillWheel(ServerLevel level, BlockPos pos) {
        return isWheelBlock(level.getBlockState(pos));
    }

    /**
     * Removes a single tracked position (used when lazy validation fails).
     *
     * @param level The server level
     * @param pos   The position to drop
     */
    void remove(ServerLevel level, BlockPos pos) {
        positions(level).remove(pos);
    }

    /**
     * Drops all tracked positions inside an unloading chunk (rediscovered on re-load).
     *
     * @param level    The server level
     * @param chunkPos The unloading chunk
     */
    void forgetChunk(ServerLevel level, ChunkPos chunkPos) {
        positions(level).removeIf(pos -> chunkPos.x == (pos.getX() >> 4) && chunkPos.z == (pos.getZ() >> 4));
    }

    /**
     * Drops all tracked positions of an unloading level.
     *
     * @param level The server level being unloaded
     */
    void forgetLevel(ServerLevel level) {
        wheels.remove(level.dimension());
    }

    /**
     * Clears everything (server stopped).
     */
    void clearAll() {
        wheels.clear();
    }

    /**
     * The live set of tracked wheel centers for a level (created on demand).
     *
     * @param level The server level
     * @return concurrent set of tracked positions
     */
    Set<BlockPos> positions(ServerLevel level) {
        return wheels.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet());
    }

    /**
     * Read-only view of the tracked positions for a level without creating an empty set.
     *
     * @param level The server level
     * @return the tracked positions, possibly empty
     */
    Set<BlockPos> positionsIfPresent(ServerLevel level) {
        Set<BlockPos> set = wheels.get(level.dimension());
        return set != null ? set : Collections.emptySet();
    }
}
