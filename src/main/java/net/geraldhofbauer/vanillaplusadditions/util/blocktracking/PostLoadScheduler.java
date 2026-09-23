package net.geraldhofbauer.vanillaplusadditions.util.blocktracking;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

/**
 * Schedules a one-off, per-position callback a configurable number of ticks after a chunk load.
 *
 * <p>Both Create companion modules need the same shape: a chunk loads, the interesting block
 * entities in it are known immediately, but acting on them right away is wrong - Create has not
 * finished its own initialization yet. So the positions are parked here and handed back a few ticks
 * later, on the server thread.</p>
 *
 * <p>Positions arrive through {@link #enqueue} (possibly off-thread, from {@code ChunkEvent.Load})
 * and are drained into the due map on the first {@link #runDue} call after that, where the due time
 * is computed from the level's current game time. Re-enqueueing a position that is already pending
 * refreshes nothing - the earliest schedule wins, which keeps a chunk-load storm from pushing a
 * check indefinitely into the future.</p>
 */
public class PostLoadScheduler {

    /** Positions waiting to be assigned a due time; filled off-thread, drained on the server tick. */
    private final Queue<Pending> incoming = new ConcurrentLinkedQueue<>();

    /** Scheduled positions per dimension, mapped to the game time they are due at. */
    private final Map<ResourceKey<Level>, Map<BlockPos, Long>> scheduled = new HashMap<>();

    /**
     * Queues positions for a check that runs {@code delayTicks} after they are drained.
     *
     * <p>Safe to call from chunk-load events off the server thread.</p>
     *
     * @param level     The server level the positions belong to
     * @param positions The positions to check later
     */
    public void enqueue(ServerLevel level, Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            incoming.add(new Pending(level.dimension(), pos.immutable()));
        }
    }

    /**
     * Drains newly queued positions, then runs the action for every position whose delay has
     * elapsed. Call once per server tick.
     *
     * @param server     The running server
     * @param delayTicks Ticks between draining a position and running its check
     * @param action     What to do with a due position, run on the server thread
     */
    public void runDue(MinecraftServer server, int delayTicks, BiConsumer<ServerLevel, BlockPos> action) {
        drainIncoming(server, delayTicks);

        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> byLevel : scheduled.entrySet()) {
            ServerLevel level = server.getLevel(byLevel.getKey());
            if (level == null) {
                continue;
            }
            long now = level.getGameTime();
            List<BlockPos> due = new ArrayList<>();
            Iterator<Map.Entry<BlockPos, Long>> it = byLevel.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, Long> entry = it.next();
                if (entry.getValue() <= now) {
                    due.add(entry.getKey());
                    it.remove();
                }
            }
            for (BlockPos pos : due) {
                action.accept(level, pos);
            }
        }
    }

    /**
     * Moves queued positions into the due map, giving each the configured delay.
     *
     * @param server     The running server
     * @param delayTicks Ticks until the check should run
     */
    private void drainIncoming(MinecraftServer server, int delayTicks) {
        Pending pending;
        while ((pending = incoming.poll()) != null) {
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null) {
                continue;
            }
            long due = level.getGameTime() + delayTicks;
            scheduled.computeIfAbsent(pending.dimension(), key -> new HashMap<>())
                    .merge(pending.pos(), due, Math::min);
        }
    }

    /**
     * Drops scheduled positions inside an unloading chunk.
     *
     * @param level    The server level
     * @param chunkPos The unloading chunk
     */
    public void forgetChunk(ServerLevel level, ChunkPos chunkPos) {
        Map<BlockPos, Long> byLevel = scheduled.get(level.dimension());
        if (byLevel != null) {
            byLevel.keySet().removeIf(pos -> chunkPos.x == (pos.getX() >> 4) && chunkPos.z == (pos.getZ() >> 4));
        }
    }

    /**
     * Drops every scheduled position of an unloading level.
     *
     * @param level The server level being unloaded
     */
    public void forgetLevel(ServerLevel level) {
        scheduled.remove(level.dimension());
    }

    /**
     * Drops a single scheduled position.
     *
     * @param level The server level
     * @param pos   The position to drop
     */
    public void forget(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Long> byLevel = scheduled.get(level.dimension());
        if (byLevel != null) {
            byLevel.remove(pos);
        }
    }

    /**
     * Clears everything (server stopped).
     */
    public void clearAll() {
        incoming.clear();
        scheduled.clear();
    }

    /**
     * A position waiting to be given a due time.
     *
     * @param dimension The level it belongs to
     * @param pos       The block position
     */
    private record Pending(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
