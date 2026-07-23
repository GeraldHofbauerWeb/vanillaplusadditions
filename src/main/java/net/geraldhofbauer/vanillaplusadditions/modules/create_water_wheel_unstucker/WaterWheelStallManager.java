package net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Detects stalled water wheels and kicks them back into rotation.
 *
 * <p>A wheel is a stall candidate when its kinetic speed is 0 although it is neither overstressed
 * (a legitimate stop we never fight) nor dry (no water/lava nearby - a decorative wheel). The fix
 * mimics the manual break + re-place players use ({@link #reinitWheel}): it wakes the surrounding
 * fluids so settled water re-flows, then re-runs Create's own flow-score recompute via a scheduled
 * block tick. After {@code max_fix_attempts} consecutive failures the wheel backs off for ~5 minutes
 * with a one-time warning; a wheel seen spinning resets all of its state.</p>
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

    /**
     * A wheel that has been removed for a re-init and must be placed back once the water has flooded.
     *
     * @param wheelState the full block state to restore (preserves orientation / axis)
     * @param material   the visual material to re-apply (may be null)
     * @param dueTick    the game time at which to place the wheel back
     */
    private record PendingReplace(BlockState wheelState, BlockState material, long dueTick) {
    }

    private final CreateWaterWheelUnstuckerModule module;
    private final WaterWheelRegistry registry;

    private final Queue<PendingCheck> incomingPostLoad = new ConcurrentLinkedQueue<>();
    private final Map<ResourceKey<Level>, Map<BlockPos, Long>> pendingPostLoad = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<BlockPos, FixState>> fixStates = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<BlockPos, PendingReplace>> pendingReplace = new HashMap<>();
    /** Wheels awaiting a post-re-init outcome log (pos -> game time to check). */
    private final Map<ResourceKey<Level>, Map<BlockPos, Long>> pendingVerify = new HashMap<>();

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
        processPendingReplaces(server);
        processPendingVerify(server);
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
        if (!hasNearbyFluid(level, pos)) {
            // No water or lava anywhere around the wheel - a dry / decorative wheel; never fight it.
            clearState(level, pos);
            return;
        }

        // Command-only by default: detect but never touch blocks automatically. The /vpaunstuck
        // command drives the fix on demand; set auto_fix = true to let the sweep do it too.
        if (!module.getConfig().isAutoFixEnabled()) {
            return;
        }

        FixState state = fixStates.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .computeIfAbsent(pos, key -> new FixState());
        if (state.backoffUntil > now) {
            return;
        }

        if (module.getConfig().shouldDebugLog()) {
            LOGGER.info("[create_water_wheel_unstucker] STALLED wheel at {} ({}): speed 0, not overstressed,"
                            + " generatedSpeed={} - re-initialising (attempt {})", pos.toShortString(),
                    level.dimension().location(), WaterWheelKinetics.getGeneratedSpeed(be), state.attempts + 1);
        }

        beginReinit(level, pos, be);
        state.attempts++;

        if (state.attempts >= module.getConfig().getMaxFixAttempts()) {
            // Exhausted: back off ~5 min, then allow a fresh re-init cycle.
            state.backoffUntil = now + STALL_BACKOFF_TICKS;
            state.attempts = 0;
            if (!state.warned) {
                state.warned = true;
                LOGGER.warn("[create_water_wheel_unstucker] Water wheel at {} ({}) still stalled after {}"
                                + " re-init attempts; backing off ~5 minutes",
                        pos.toShortString(), level.dimension().location(),
                        module.getConfig().getMaxFixAttempts());
            }
        }
    }

    /**
     * Re-initialises every tracked, stalled, water-driven wheel in loaded chunks - the {@code /vpaunstuck}
     * command entry point. Each qualifying wheel is broken and queued to be placed back (see
     * {@link #beginReinit}); returns the number of wheels a re-init was started for.
     *
     * @param server The running server
     * @return the number of wheels a re-init was started for
     */
    int unstickAll(MinecraftServer server) {
        int started = 0;
        int skippedOverstressed = 0;
        int skippedNoFluid = 0;
        int spinning = 0;
        for (ServerLevel level : server.getAllLevels()) {
            // Copy: beginReinit mutates the world (setBlock), so don't iterate the live registry set.
            for (BlockPos pos : List.copyOf(registry.positionsIfPresent(level))) {
                if (!isFootprintLoaded(level, pos)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null || !WaterWheelKinetics.isWaterWheelBE(be)) {
                    continue;
                }
                if (WaterWheelKinetics.getSpeed(be) != 0.0f) {
                    spinning++;
                    continue;
                }
                if (WaterWheelKinetics.isOverStressed(be)) {
                    skippedOverstressed++;
                    continue;
                }
                if (!hasNearbyFluid(level, pos)) {
                    skippedNoFluid++;
                    continue;
                }
                LOGGER.info("[create_water_wheel_unstucker] /vpaunstuck: re-initialising stalled wheel at {} ({}),"
                        + " generatedSpeed={}", pos.toShortString(), level.dimension().location(),
                        WaterWheelKinetics.getGeneratedSpeed(be));
                beginReinit(level, pos, be);
                clearState(level, pos); // fresh on-demand fix - drop any prior backoff
                started++;
            }
        }
        LOGGER.info("[create_water_wheel_unstucker] /vpaunstuck summary: {} re-initialised, {} already spinning,"
                        + " {} skipped (overstressed), {} skipped (no water nearby). Outcomes logged shortly.",
                started, spinning, skippedOverstressed, skippedNoFluid);
        return started;
    }

    /**
     * Starts a break + re-place re-init: captures the wheel's full state (its orientation / axis) and
     * material, removes it so adjacent water floods the gap and re-establishes ACTIVE flow, then queues
     * the wheel to be placed back after {@code reinit_flood_ticks}. This is the only thing that revives
     * a reload-stalled wheel - Create only reads a non-zero flow score while the water is actually
     * moving, and then keeps it. Sized for small (single-block) wheels.
     *
     * @param level The server level
     * @param pos   The wheel center
     * @param be    The wheel block entity
     */
    private void beginReinit(ServerLevel level, BlockPos pos, BlockEntity be) {
        BlockState wheelState = level.getBlockState(pos);
        BlockState material = WaterWheelKinetics.getMaterial(be);
        // Remove the wheel (no drops) so adjacent water can flood the void.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        long due = level.getGameTime() + module.getConfig().getReinitFloodTicks();
        pendingReplace.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .put(pos.immutable(), new PendingReplace(wheelState, material, due));
    }

    /**
     * Places back wheels whose flood window has elapsed. Runs every tick. A pending replace whose chunk
     * is momentarily unloaded is kept and retried once it reloads, so a removed wheel is never dropped.
     *
     * @param server The running server
     */
    private void processPendingReplaces(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, PendingReplace>> byLevel : pendingReplace.entrySet()) {
            ServerLevel level = server.getLevel(byLevel.getKey());
            if (level == null) {
                continue;
            }
            long now = level.getGameTime();
            Iterator<Map.Entry<BlockPos, PendingReplace>> it = byLevel.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, PendingReplace> entry = it.next();
                if (entry.getValue().dueTick() > now) {
                    continue;
                }
                if (!isFootprintLoaded(level, entry.getKey())) {
                    continue; // wait for the chunk to reload rather than lose the wheel
                }
                it.remove();
                replaceWheel(level, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Places a removed wheel back with its original state (orientation) and material, then triggers
     * Create's own flow recompute (as {@code onPlace} does) while the flooded water is still moving.
     *
     * @param level   The server level
     * @param pos     The wheel center
     * @param pending The captured wheel state
     */
    private void replaceWheel(ServerLevel level, BlockPos pos, PendingReplace pending) {
        BlockState current = level.getBlockState(pos);
        // Only restore into our own placeholder (air, or water that flooded in) - never clobber a
        // block a player may have placed in the gap.
        if (!current.isAir() && current.getFluidState().isEmpty()) {
            return;
        }
        level.setBlock(pos, pending.wheelState(), Block.UPDATE_ALL);
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && WaterWheelKinetics.isWaterWheelBE(be)) {
            WaterWheelKinetics.setMaterial(be, pending.material());
            be.setChanged();
            level.sendBlockUpdated(pos, pending.wheelState(), pending.wheelState(), Block.UPDATE_ALL);
        }
        // Belt-and-suspenders: also schedule Create's flow recompute (onPlace already does this).
        level.scheduleTick(pos, pending.wheelState().getBlock(), 1);
        // Check the outcome ~1s later and log whether the wheel actually restarted.
        pendingVerify.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .put(pos.immutable(), level.getGameTime() + 20L);
    }

    /**
     * Logs, ~1s after a re-init, whether each wheel actually restarted - so a manual {@code /vpaunstuck}
     * during play tells us clearly if the break+replace fix works on a real reload-stalled wheel.
     *
     * @param server The running server
     */
    private void processPendingVerify(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> byLevel : pendingVerify.entrySet()) {
            ServerLevel level = server.getLevel(byLevel.getKey());
            if (level == null) {
                continue;
            }
            long now = level.getGameTime();
            Iterator<Map.Entry<BlockPos, Long>> it = byLevel.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, Long> entry = it.next();
                if (entry.getValue() > now) {
                    continue;
                }
                if (!isFootprintLoaded(level, entry.getKey())) {
                    continue; // wait for the chunk before judging the outcome
                }
                it.remove();
                BlockPos pos = entry.getKey();
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null || !WaterWheelKinetics.isWaterWheelBE(be)) {
                    continue;
                }
                float speed = WaterWheelKinetics.getSpeed(be);
                if (speed != 0.0f) {
                    LOGGER.info("[create_water_wheel_unstucker] re-init RECOVERED wheel at {} ({}): now spinning,"
                            + " speed={}", pos.toShortString(), level.dimension().location(), speed);
                } else {
                    LOGGER.info("[create_water_wheel_unstucker] re-init did NOT restart wheel at {} ({}): still"
                            + " speed 0 (generatedSpeed={}) - likely genuinely no flow (mis-built/drained), not a"
                            + " reload desync", pos.toShortString(), level.dimension().location(),
                            WaterWheelKinetics.getGeneratedSpeed(be));
                }
            }
        }
    }

    /**
     * Whether any fluid (water or lava) sits within the wheel's footprint box - used to skip genuinely
     * dry / decorative wheels instead of fighting them forever.
     *
     * @param level The server level
     * @param pos   The wheel center
     * @return true if a non-empty fluid is present nearby
     */
    private boolean hasNearbyFluid(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            if (!level.getFluidState(p).isEmpty()) {
                return true;
            }
        }
        return false;
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
        pendingReplace.remove(level.dimension());
        pendingVerify.remove(level.dimension());
    }

    /**
     * Clears everything (server stopped).
     */
    void clearAll() {
        incomingPostLoad.clear();
        pendingPostLoad.clear();
        fixStates.clear();
        pendingReplace.clear();
        pendingVerify.clear();
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
