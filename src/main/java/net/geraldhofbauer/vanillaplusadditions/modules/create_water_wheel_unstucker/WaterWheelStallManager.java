package net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Detects stalled water wheels and kicks them back into rotation.
 *
 * <p>A wheel is a stall candidate when its kinetic speed is 0 although it is neither overstressed
 * (a legitimate stop we never fight) nor without water flow. The fix escalates per wheel:
 * attempt 1 is a soft kick (flow-score recompute + re-announcing the generated rotation), further
 * attempts are hard kicks (full kinetic detach/re-attach, wrench-equivalent) if enabled. After
 * {@code max_fix_attempts} consecutive failures the wheel backs off for ~5 minutes with a one-time
 * warning; a wheel seen spinning resets all of its state.</p>
 *
 * <p>Checks run in two ways, both restricted to tracked positions in loaded chunks: a targeted
 * check shortly after a chunk with wheels loads (exactly when the reload desync strikes), and a
 * periodic sweep as a safety net. All state is server-thread-only except the post-load hand-off
 * queue, which chunk-load events may fill from worker threads.</p>
 */
class WaterWheelStallManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Backoff after exhausting the fix attempts: 6000 ticks = ~5 minutes. */
    private static final long STALL_BACKOFF_TICKS = 6000L;

    /** Hand-off entry from a (possibly off-thread) chunk-load event. */
    private record PendingCheck(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** Per-wheel escalation state. Mutable on purpose - server thread only. */
    private static final class FixState {
        private int attempts;
        private long backoffUntil;
        private boolean warned;
    }

    private final CreateWaterWheelUnstuckerModule module;
    private final WaterWheelRegistry registry;

    private final Queue<PendingCheck> incomingPostLoad = new ConcurrentLinkedQueue<>();
    private final Map<ResourceKey<Level>, Map<BlockPos, Long>> pendingPostLoad = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<BlockPos, FixState>> fixStates = new HashMap<>();

    WaterWheelStallManager(CreateWaterWheelUnstuckerModule module, WaterWheelRegistry registry) {
        this.module = module;
        this.registry = registry;
    }

    /**
     * Queues freshly discovered (or placed) wheels for a targeted check after the configured
     * post-load delay. Safe to call from chunk-load events off the server thread; due times are
     * assigned on the server thread while draining.
     *
     * @param level     The server level the wheels are in
     * @param positions The wheel center positions to check
     */
    void enqueuePostLoadCheck(ServerLevel level, Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            incomingPostLoad.add(new PendingCheck(level.dimension(), pos));
        }
    }

    /**
     * Per-tick driver: drains the post-load queue, runs due targeted checks every tick, and runs
     * the full sweep over all tracked wheels at the configured interval.
     *
     * @param server The running server
     */
    void tick(MinecraftServer server) {
        drainIncoming(server);
        runDuePostLoadChecks(server);

        if (server.getTickCount() % module.getConfig().getCheckIntervalTicks() == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (BlockPos pos : registry.positionsIfPresent(level)) {
                    checkWheel(level, pos);
                }
            }
        }
    }

    private void drainIncoming(MinecraftServer server) {
        PendingCheck pending;
        while ((pending = incomingPostLoad.poll()) != null) {
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null) {
                continue;
            }
            long due = level.getGameTime() + module.getConfig().getPostLoadDelayTicks();
            pendingPostLoad.computeIfAbsent(pending.dimension(), key -> new HashMap<>())
                    .putIfAbsent(pending.pos(), due);
        }
    }

    private void runDuePostLoadChecks(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> byLevel : pendingPostLoad.entrySet()) {
            ServerLevel level = server.getLevel(byLevel.getKey());
            if (level == null) {
                byLevel.getValue().clear();
                continue;
            }
            long now = level.getGameTime();
            Iterator<Map.Entry<BlockPos, Long>> it = byLevel.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, Long> entry = it.next();
                if (entry.getValue() <= now) {
                    it.remove();
                    checkWheel(level, entry.getKey());
                }
            }
        }
    }

    /**
     * The per-wheel state machine. Server thread only.
     *
     * @param level The server level
     * @param pos   The tracked wheel center position
     */
    private void checkWheel(ServerLevel level, BlockPos pos) {
        if (!isFootprintLoaded(level, pos)) {
            // Never force-load. Also skip when a neighboring footprint chunk is missing: evaluating
            // the flow with the water chunk absent would misread "no flow" and apply a wrong score.
            return;
        }
        if (!registry.isStillWheel(level, pos)) {
            registry.remove(level, pos);
            clearState(level, pos);
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !WaterWheelKinetics.isWaterWheelBE(be)) {
            // Block entity not materialized yet; the next sweep catches it.
            return;
        }

        long now = level.getGameTime();
        float speed = WaterWheelKinetics.getSpeed(be);
        if (speed != 0.0f) {
            // Healthy. Also self-heals shared networks: kicking one wheel revives all, the others
            // reset their counters here on their next check.
            FixState state = peekState(level, pos);
            if (state != null && state.attempts > 0 && module.getConfig().shouldDebugLog()) {
                LOGGER.info("[create_water_wheel_unstucker] Wheel at {} ({}) recovered, speed {}",
                        pos.toShortString(), level.dimension().location(), speed);
            }
            clearState(level, pos);
            return;
        }
        if (WaterWheelKinetics.isOverStressed(be)) {
            // Legitimate stop - never fight Create's stress mechanics.
            clearState(level, pos);
            return;
        }

        FixState state = fixStates.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .computeIfAbsent(pos, key -> new FixState());
        if (state.backoffUntil > now) {
            return;
        }

        if (state.attempts == 0) {
            WaterWheelKinetics.softKick(be);
            if (WaterWheelKinetics.getGeneratedSpeed(be) == 0.0f) {
                // Genuinely no water flow - not a desync. Leave the wheel alone, no attempt counted.
                clearState(level, pos);
                return;
            }
            state.attempts = 1;
            debugLog("Soft-kicked stalled wheel at {} ({}), generated speed {}",
                    pos, level, WaterWheelKinetics.getGeneratedSpeed(be));
            return;
        }

        if (state.attempts < module.getConfig().getMaxFixAttempts()) {
            if (module.getConfig().isHardKickEnabled()) {
                WaterWheelKinetics.hardKick(be);
                debugLog("Hard-kicked stalled wheel at {} ({}), attempt " + (state.attempts + 1),
                        pos, level, null);
            } else {
                WaterWheelKinetics.softKick(be);
                debugLog("Soft-kicked stalled wheel at {} ({}) again, attempt " + (state.attempts + 1),
                        pos, level, null);
            }
            state.attempts++;
            return;
        }

        // Attempts exhausted: back off, allow one fresh soft+hard cycle per backoff window.
        state.backoffUntil = now + STALL_BACKOFF_TICKS;
        state.attempts = 0;
        if (!state.warned) {
            state.warned = true;
            LOGGER.warn("[create_water_wheel_unstucker] Water wheel at {} ({}) appears permanently"
                            + " stalled despite {} fix attempts; retrying every ~5 minutes",
                    pos.toShortString(), level.dimension().location(),
                    module.getConfig().getMaxFixAttempts());
        }
    }

    /**
     * Checks that the wheel's own chunk and every chunk overlapping its water footprint
     * (center +/-2 blocks on X/Z covers the large wheel) are loaded - at most four distinct chunks.
     *
     * @param level The server level
     * @param pos   The wheel center
     * @return true if all footprint chunks are loaded
     */
    private boolean isFootprintLoaded(ServerLevel level, BlockPos pos) {
        int minChunkX = (pos.getX() - 2) >> 4;
        int maxChunkX = (pos.getX() + 2) >> 4;
        int minChunkZ = (pos.getZ() - 2) >> 4;
        int maxChunkZ = (pos.getZ() + 2) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private FixState peekState(ServerLevel level, BlockPos pos) {
        Map<BlockPos, FixState> byPos = fixStates.get(level.dimension());
        return byPos != null ? byPos.get(pos) : null;
    }

    private void clearState(ServerLevel level, BlockPos pos) {
        Map<BlockPos, FixState> byPos = fixStates.get(level.dimension());
        if (byPos != null) {
            byPos.remove(pos);
        }
    }

    private void debugLog(String message, BlockPos pos, ServerLevel level, Float value) {
        if (!module.getConfig().shouldDebugLog()) {
            return;
        }
        if (value != null) {
            LOGGER.info("[create_water_wheel_unstucker] " + message,
                    pos.toShortString(), level.dimension().location(), value);
        } else {
            LOGGER.info("[create_water_wheel_unstucker] " + message,
                    pos.toShortString(), level.dimension().location());
        }
    }

    /**
     * Drops all pending/escalation state inside an unloading chunk.
     *
     * @param level    The server level
     * @param chunkPos The unloading chunk
     */
    void forgetChunk(ServerLevel level, ChunkPos chunkPos) {
        Map<BlockPos, Long> pending = pendingPostLoad.get(level.dimension());
        if (pending != null) {
            pending.keySet().removeIf(pos -> inChunk(pos, chunkPos));
        }
        Map<BlockPos, FixState> states = fixStates.get(level.dimension());
        if (states != null) {
            states.keySet().removeIf(pos -> inChunk(pos, chunkPos));
        }
    }

    /**
     * Drops all state of an unloading level.
     *
     * @param level The server level being unloaded
     */
    void forgetLevel(ServerLevel level) {
        pendingPostLoad.remove(level.dimension());
        fixStates.remove(level.dimension());
    }

    /**
     * Clears everything (server stopped).
     */
    void clearAll() {
        incomingPostLoad.clear();
        pendingPostLoad.clear();
        fixStates.clear();
    }

    /**
     * Drops the escalation/pending state for a single removed wheel.
     *
     * @param level The server level
     * @param pos   The removed wheel center
     */
    void forgetWheel(ServerLevel level, BlockPos pos) {
        clearState(level, pos);
        Map<BlockPos, Long> pending = pendingPostLoad.get(level.dimension());
        if (pending != null) {
            pending.remove(pos);
        }
    }

    private static boolean inChunk(BlockPos pos, ChunkPos chunkPos) {
        return chunkPos.x == (pos.getX() >> 4) && chunkPos.z == (pos.getZ() >> 4);
    }
}
