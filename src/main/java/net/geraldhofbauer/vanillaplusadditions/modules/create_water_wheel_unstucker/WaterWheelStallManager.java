package net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker;

import com.mojang.logging.LogUtils;
import net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker.config.CreateWaterWheelUnstuckerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.EnumMap;
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
 * mimics the manual break + re-place players use ({@link #beginReinit}): it wakes the surrounding
 * fluids so settled water re-flows, then re-runs Create's own flow-score recompute via a scheduled
 * block tick. After {@code max_fix_attempts} consecutive failures the wheel backs off for ~5 minutes
 * with a one-time warning; a wheel seen spinning resets all of its state.</p>
 *
 * <p>Checks run in two ways, both restricted to tracked positions in loaded chunks: a targeted
 * check shortly after a chunk with wheels loads (exactly when the reload desync strikes), and a
 * periodic sweep as a safety net. All state is server-thread-only except the post-load hand-off
 * queue, which chunk-load events may fill from worker threads.</p>
 *
 * <p><b>Overstressed wheels</b> report speed 0 because {@code KineticBlockEntity.getSpeed()} returns
 * 0 whenever {@code overStressed} is set, so an overload and a stall look identical from outside.
 * They are told apart in three steps ({@link #resolveOverstress}), only the last of which is a real
 * overload:</p>
 * <ul>
 *   <li><i>stale network numbers</i> — a recompute of the network's own stress/capacity clears the
 *       verdict and the wheel spins again. No blocks touched.</li>
 *   <li><i>phantom unloaded tally</i> — Create keeps a stress/capacity total for members in unloaded
 *       chunks; a member removed while unloaded never subtracts its share, so the network reports an
 *       overload no existing machine causes. An <i>orphaned</i> tally (stress charged while the
 *       network claims zero unloaded members - nothing can be behind it) is dropped by any caller.
 *       A tally with actual unloaded members is a judgement call: {@code /vpaunstuck} takes it at
 *       once, an automatic caller only once the tally has stopped moving for
 *       {@link #TALLY_SETTLE_TICKS} while the network stayed overstressed.</li>
 *   <li><i>genuine overload</i> — survives both, with the wheel's own capacity counted in
 *       (generated speed != 0). Never touched; Create's stress mechanics win.</li>
 * </ul>
 *
 * <p>A wheel that is overstressed <i>and</i> generates nothing is the reload desync wearing an
 * overstressed mask: having lost its flow score it contributes no capacity, tipping its own network
 * over. That one is re-initialised like any other stall.</p>
 */
class WaterWheelStallManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Backoff after exhausting the fix attempts: 6000 ticks = ~5 minutes. */
    private static final long STALL_BACKOFF_TICKS = 6000L;

    /** Wait before retrying a re-place whose gap is occupied: 20 ticks = 1 second. */
    private static final long REPLACE_RETRY_TICKS = 20L;

    /** Retries before a removed wheel is dropped as an item rather than held forever. */
    private static final int REPLACE_MAX_RETRIES = 20;

    /**
     * Wait before the unloaded tally counts as settled: 200 ticks = 10 seconds.
     *
     * <p>Short on purpose - the wheel has been standing since the reload and a player watching it
     * wants it back, not a minute of nothing. The wait exists at all because after a world load
     * every network legitimately carries a tally that counts itself down as members arrive: ten
     * quiet seconds say no further member is coming.</p>
     *
     * <p>That is weaker evidence than a full minute would be, and the failure it buys is bounded:
     * clearing a tally that a genuinely unloaded machine was still behind makes that machine stop
     * counting until its chunk loads, at which point {@code addSilently} registers it again with
     * its real numbers. Every such clear is logged as a warning with the numbers it was based on,
     * so if it ever does fire early, the log says so.</p>
     */
    private static final long TALLY_SETTLE_TICKS = 200L;

    /** Shortest gap between two automatic re-inits of the same wheel: 1200 ticks = ~1 minute. */
    private static final long MIN_REINIT_INTERVAL_TICKS = 1200L;

    /** Exhausted backoff rounds before a wheel is left alone until someone runs the command. */
    private static final int GIVE_UP_AFTER_BACKOFFS = 2;

    /** Wait before retrying a post-load check that could not evaluate the wheel: 20 ticks = 1 second. */
    private static final long POST_LOAD_RETRY_TICKS = 20L;

    /** How long a post-load check may keep deferring before it is dropped: 600 ticks = 30 seconds. */
    private static final long POST_LOAD_MAX_DEFER_TICKS = 600L;

    /** Shortest gap between two large-wheel refusal log lines for the same wheel: 6000 ticks = ~5 min. */
    private static final long REFUSAL_LOG_INTERVAL_TICKS = 6000L;

    /** What triggered a check. Only used to pick a policy. */
    private enum Trigger {
        /** A chunk holding wheels finished loading - the situation the reload stall happens in. */
        CHUNK_LOAD,
        /** A player just placed a wheel. */
        PLACEMENT,
        /** The periodic safety-net sweep. */
        SWEEP,
        /** {@code /vpaunstuck}. */
        COMMAND
    }

    /** How far a stress cure may go. */
    private enum StressMode {
        /** Only the provably safe cure: recompute, plus a self-contradictory tally. */
        SAFE,
        /** Also the judgement call, but only once the tally has stopped moving. */
        DEEP,
        /** {@code /vpaunstuck}: the judgement call immediately, an operator is watching. */
        COMMAND
    }

    /** What a check ended up doing. */
    private enum WheelOutcome {
        NOT_LOADED, REPLACE_PENDING, GONE, NO_BE, SPINNING, STRESS_CURED,
        OVERSTRESSED_GENUINE, NO_FLUID, RATE_LIMITED, GAVE_UP, REINIT_STARTED, REINIT_REFUSED, DETECTED_ONLY
    }

    /**
     * What a given trigger is allowed to do. One place to read the whole matrix.
     *
     * @param trigger     what asked for the check
     * @param stressMode  how far the stress cure may go
     * @param mayReinit   whether blocks may be mutated (break + re-place)
     * @param rateLimited whether the backoff and the minimum re-init interval apply
     */
    private record FixPolicy(Trigger trigger, StressMode stressMode, boolean mayReinit, boolean rateLimited) {

        static FixPolicy of(Trigger trigger, CreateWaterWheelUnstuckerConfig config) {
            boolean deep = config.isAutoClearPhantomStressEnabled();
            return switch (trigger) {
                // The reload desync is exactly what this module exists for, so the targeted check
                // after a chunk load gets the full cure - that is the point of the module.
                case CHUNK_LOAD -> new FixPolicy(trigger, deep ? StressMode.DEEP : StressMode.SAFE,
                        config.isAutoUnstickOnChunkLoadEnabled(), true);
                // A wheel a player just placed has never been through a reload. Settling its stress
                // bookkeeping is fine; breaking it open again a second later is not.
                case PLACEMENT -> new FixPolicy(trigger, StressMode.SAFE, false, true);
                case SWEEP -> new FixPolicy(trigger, deep ? StressMode.DEEP : StressMode.SAFE,
                        config.isAutoFixEnabled(), true);
                case COMMAND -> new FixPolicy(trigger, StressMode.COMMAND, true, false);
            };
        }
    }

    /** What {@link #resolveOverstress} actually did. */
    private enum StressAction {
        NONE,
        RECOMPUTED,
        TALLY_DROPPED
    }

    /** Hand-off entry from a (possibly off-thread) chunk-load event. */
    private record PendingCheck(ResourceKey<Level> dimension, BlockPos pos, Trigger trigger) {
    }

    /**
     * A queued check.
     *
     * @param dueTick  the game time at which to run it
     * @param trigger  what asked for it
     * @param deadline the game time after which it is given up on, even if it never evaluated
     * @param retry    true for a re-queued check, which must not pull a fresh one forward
     */
    private record ScheduledCheck(long dueTick, Trigger trigger, long deadline, boolean retry) {
    }

    /** Per-wheel escalation state. Mutable on purpose - server thread only. */
    private static final class FixState {
        private int attempts;
        private long backoffUntil;
        private boolean warned;
        /** Game time of the last re-init, so an automatic trigger cannot loop on one wheel. */
        private long lastReinitTick;
        /** Set once a wheel has proven it will not come back; only the command clears it. */
        private boolean givenUp;
        /** How often the ~5 minute backoff has already run out for this wheel. */
        private int backoffsExhausted;
        /** Game time of the last large-wheel refusal log, to keep it out of the tick loop. */
        private long lastRefusalLog;
        /** Last seen unloaded tally, and since when it has looked like this. */
        private boolean tallyKnown;
        private int tallyMembers;
        private float tallyStress;
        private float tallyCapacity;
        private long tallySince;
    }

    /**
     * A wheel that has been removed for a re-init and must be placed back once the water has flooded.
     *
     * @param wheelState the full block state to restore (preserves orientation / axis)
     * @param material   the visual material to re-apply (may be null)
     * @param dueTick    the game time at which to place the wheel back
     * @param retries    how often the re-place has already found the gap occupied
     */
    private record PendingReplace(BlockState wheelState, BlockState material, long dueTick, int retries) {
    }

    /** What {@link #replaceWheel} managed to do with a wheel it is holding. */
    private enum ReplaceResult {
        /** The wheel is back in the world (or was already there). */
        RESTORED,
        /** The gap is occupied; keep holding the wheel and try again. */
        RETRY,
        /** Out of retries - the wheel was dropped as an item instead of vanishing. */
        GIVE_UP
    }

    private final CreateWaterWheelUnstuckerModule module;
    private final WaterWheelRegistry registry;

    private final Queue<PendingCheck> incomingPostLoad = new ConcurrentLinkedQueue<>();
    private final Map<ResourceKey<Level>, Map<BlockPos, ScheduledCheck>> pendingPostLoad = new HashMap<>();
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
        enqueue(level, positions, Trigger.CHUNK_LOAD);
    }

    /**
     * Queues a wheel a player has just placed. Deliberately a different entry point from
     * {@link #enqueuePostLoadCheck}: a fresh wheel has never been through a reload, so its stress
     * bookkeeping may be settled but it must never be broken open again a moment after placement.
     *
     * @param level     The server level the wheel is in
     * @param positions The wheel center positions to check
     */
    void enqueuePlacementCheck(ServerLevel level, Collection<BlockPos> positions) {
        enqueue(level, positions, Trigger.PLACEMENT);
    }

    /**
     * Queues wheels for a targeted check, recording what asked for it. The trigger decides how far
     * the check may go, so a chunk that just loaded and a wheel a player just placed must not share
     * one entry - they used to, and the placement would have inherited the reload treatment.
     *
     * @param level     The server level the wheels are in
     * @param positions The wheel center positions to check
     * @param trigger   What asked for the check
     */
    private void enqueue(ServerLevel level, Collection<BlockPos> positions, Trigger trigger) {
        for (BlockPos pos : positions) {
            incomingPostLoad.add(new PendingCheck(level.dimension(), pos, trigger));
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
                    checkWheel(level, pos, FixPolicy.of(Trigger.SWEEP, module.getConfig()));
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
            ScheduledCheck scheduled = new ScheduledCheck(due, pending.trigger(),
                due + POST_LOAD_MAX_DEFER_TICKS, false);
            pendingPostLoad.computeIfAbsent(pending.dimension(), key -> new HashMap<>())
                    .merge(pending.pos(), scheduled, WaterWheelStallManager::mergeScheduled);
        }
    }

    /**
     * Combines two queued checks for the same wheel: the earlier due time wins, and CHUNK_LOAD
     * outranks PLACEMENT - a wheel that was placed and then went through a reload has the reload
     * problem, not the placement one.
     *
     * @param existing the entry already queued
     * @param incoming the entry being added
     * @return the entry to keep
     */
    private static ScheduledCheck mergeScheduled(ScheduledCheck existing, ScheduledCheck incoming) {
        Trigger trigger = existing.trigger() == Trigger.CHUNK_LOAD || incoming.trigger() == Trigger.CHUNK_LOAD
                ? Trigger.CHUNK_LOAD : existing.trigger();
        // A fresh check wins over a retry outright: the post-load delay exists so Create can finish
        // its own reload work, and taking the retry's earlier due time would skip that wait.
        long due = existing.retry() != incoming.retry()
                ? (existing.retry() ? incoming.dueTick() : existing.dueTick())
                : Math.min(existing.dueTick(), incoming.dueTick());
        return new ScheduledCheck(due, trigger, Math.max(existing.deadline(), incoming.deadline()),
                existing.retry() && incoming.retry());
    }

    private void runDuePostLoadChecks(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, ScheduledCheck>> byLevel : pendingPostLoad.entrySet()) {
            ServerLevel level = server.getLevel(byLevel.getKey());
            if (level == null) {
                byLevel.getValue().clear();
                continue;
            }
            long now = level.getGameTime();
            Iterator<Map.Entry<BlockPos, ScheduledCheck>> it = byLevel.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, ScheduledCheck> entry = it.next();
                if (entry.getValue().dueTick() <= now) {
                    ScheduledCheck scheduled = entry.getValue();
                    WheelOutcome outcome = checkWheel(level, entry.getKey(),
                            FixPolicy.of(scheduled.trigger(), module.getConfig()));
                    if (inconclusive(outcome) && now < scheduled.deadline()) {
                        // The check could not evaluate the wheel at all - chunk not fully there, block
                        // entity not built yet, or our own flood window still open. Consuming the one
                        // post-load opportunity here would hand the wheel to the sweep, which does not
                        // re-initialise unless auto_fix is on. Come back shortly instead.
                        entry.setValue(new ScheduledCheck(now + POST_LOAD_RETRY_TICKS, scheduled.trigger(),
                                scheduled.deadline(), true));
                        continue;
                    }
                    it.remove();
                }
            }
        }
    }

    /**
     * The per-wheel state machine, and the only decision chain there is. {@code /vpaunstuck} runs
     * the same chain with {@link Trigger#COMMAND}, which is what keeps the manual and the automatic
     * cure identical instead of two copies that drift apart.
     *
     * @param level  The server level
     * @param pos    The tracked wheel center position
     * @param policy What this trigger is allowed to do
     * @return what the check ended up doing
     */
    private WheelOutcome checkWheel(ServerLevel level, BlockPos pos, FixPolicy policy) {
        if (!isFootprintLoaded(level, pos)) {
            // Never force-load. Also skip when a neighboring footprint chunk is missing: evaluating
            // the flow with the water chunk absent would misread "no flow" and apply a wrong score.
            return WheelOutcome.NOT_LOADED;
        }
        if (hasPendingReplace(level, pos)) {
            // We removed the wheel ourselves and are holding it for the flood window; the air at this
            // position is ours, not a deleted wheel.
            return WheelOutcome.REPLACE_PENDING;
        }
        if (!registry.isStillWheel(level, pos)) {
            registry.remove(level, pos);
            // Hard removal, not clearState: the wheel is provably gone (exploded, /setblock, wrenched -
            // none of which fire a BreakEvent), and a wheel built here later must not inherit its
            // cooldown, its give-up flag or a tally sample from a different network.
            forgetWheel(level, pos);
            return WheelOutcome.GONE;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !WaterWheelKinetics.isWaterWheelBE(be)) {
            // Block entity not materialized yet; the next sweep catches it.
            return WheelOutcome.NO_BE;
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
            return WheelOutcome.SPINNING;
        }

        FixState state = fixStates.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .computeIfAbsent(pos, key -> new FixState());

        if (WaterWheelKinetics.isOverStressed(be)) {
            // Settle the stress bookkeeping first - none of this changes a block, so mayReinit (which
            // gates block mutation) does not apply.
            StressAction action = resolveOverstress(level, pos, be, policy, state, now);
            if (WaterWheelKinetics.getSpeed(be) != 0.0f) {
                if (action != StressAction.NONE) {
                    LOGGER.info("[create_water_wheel_unstucker] Unstuck wheel at {} ({}) [{}]: {} - now spinning at {}",
                            pos.toShortString(), level.dimension().location(), policy.trigger(),
                            action == StressAction.TALLY_DROPPED
                                    ? "dropped a phantom unloaded-member stress tally"
                                    : "recomputed a stale kinetic network",
                            WaterWheelKinetics.getSpeed(be));
                }
                clearState(level, pos);
                return WheelOutcome.STRESS_CURED;
            }
            if (WaterWheelKinetics.isOverStressed(be) && WaterWheelKinetics.getGeneratedSpeed(be) != 0.0f) {
                // Genuine overload, or an unloaded tally that may well be real machines: the wheel's
                // flow is intact and it still contributes its capacity, the network simply demands
                // more. Never fight Create's stress mechanics behind the operator's back.
                clearState(level, pos);
                return WheelOutcome.OVERSTRESSED_GENUINE;
            }
        }
        if (!hasNearbyFluid(level, pos)) {
            // No water or lava anywhere around the wheel - a dry / decorative wheel; never fight it.
            clearState(level, pos);
            return WheelOutcome.NO_FLUID;
        }

        if (!policy.rateLimited()) {
            // An operator is asking in person: drop every brake this wheel has accumulated, the way
            // /vpaunstuck always did. It must not feed the automatic escalation either - see below.
            state.givenUp = false;
            state.backoffUntil = 0L;
            state.attempts = 0;
            state.backoffsExhausted = 0;
            state.warned = false;
        }
        if (!policy.mayReinit()) {
            clearState(level, pos); // drops the entry unless it carries a tally sample or a cooldown
            return WheelOutcome.DETECTED_ONLY;
        }
        if (policy.rateLimited()) {
            if (state.givenUp) {
                // Proven hopeless: a wheel that survived two full backoff rounds is not stalled,
                // it is dry, decorative or half-built. Leave it to /vpaunstuck.
                return WheelOutcome.GAVE_UP;
            }
            if (state.backoffUntil > now) {
                return WheelOutcome.RATE_LIMITED;
            }
            if (state.lastReinitTick != 0L && now - state.lastReinitTick < MIN_REINIT_INTERVAL_TICKS) {
                // Create recomputes a wheel's flow score on its own every 60 ticks; an automatic
                // trigger that fires on every chunk load must not out-run that and keep breaking
                // the same wheel open.
                return WheelOutcome.RATE_LIMITED;
            }
        }

        if (!beginReinit(level, pos, be, state, policy)) {
            return WheelOutcome.REINIT_REFUSED; // large wheel - do not burn an attempt on it
        }
        // The minute floor applies to a manual re-init too - a wheel that was just broken open by
        // hand should not be broken open again by the next chunk load.
        state.lastReinitTick = now;

        if (module.getConfig().shouldDebugLog() && policy.rateLimited()) {
            LOGGER.info("[create_water_wheel_unstucker] STALLED wheel at {} ({}) [{}]: speed 0, not overstressed,"
                            + " generatedSpeed={} - re-initialised (attempt {})", pos.toShortString(),
                    level.dimension().location(), policy.trigger(),
                    WaterWheelKinetics.getGeneratedSpeed(be), state.attempts + 1);
        }
        if (!policy.rateLimited()) {
            // The command drives the fix itself and reports its own summary; it must not arm the
            // automatic escalation, or three manual runs would silence the automatic cure for five
            // minutes and log a warning about an escalation that never happened. That includes the
            // attempt counter, which is why it is incremented below rather than above.
            LOGGER.info("[create_water_wheel_unstucker] /vpaunstuck: re-initialising stalled wheel at {} ({}),"
                            + " generatedSpeed={}, overstressed={}", pos.toShortString(),
                    level.dimension().location(), WaterWheelKinetics.getGeneratedSpeed(be),
                    WaterWheelKinetics.isOverStressed(be));
            return WheelOutcome.REINIT_STARTED;
        }
        state.attempts++;
        if (state.attempts >= module.getConfig().getMaxFixAttempts()) {
            // Exhausted: back off ~5 min, then allow a fresh re-init cycle.
            state.backoffUntil = now + STALL_BACKOFF_TICKS;
            state.attempts = 0;
            state.backoffsExhausted++;
            if (state.backoffsExhausted >= GIVE_UP_AFTER_BACKOFFS) {
                state.givenUp = true;
                LOGGER.warn("[create_water_wheel_unstucker] Water wheel at {} ({}) survived {} full backoff rounds"
                                + " without restarting - it is most likely dry, decorative or unfinished rather than"
                                + " stalled. Leaving it alone; run /vpaunstuck to try again.",
                        pos.toShortString(), level.dimension().location(), state.backoffsExhausted);
            } else if (!state.warned) {
                state.warned = true;
                LOGGER.warn("[create_water_wheel_unstucker] Water wheel at {} ({}) still stalled after {}"
                                + " re-init attempts; backing off ~5 minutes",
                        pos.toShortString(), level.dimension().location(),
                        module.getConfig().getMaxFixAttempts());
            }
        }
        return WheelOutcome.REINIT_STARTED;
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
        FixPolicy policy = FixPolicy.of(Trigger.COMMAND, module.getConfig());
        EnumMap<WheelOutcome, Integer> tally = new EnumMap<>(WheelOutcome.class);
        for (ServerLevel level : server.getAllLevels()) {
            // Copy: a re-init mutates the world (setBlock), so don't iterate the live registry set.
            for (BlockPos pos : List.copyOf(registry.positionsIfPresent(level))) {
                WheelOutcome outcome = checkWheel(level, pos, policy);
                tally.merge(outcome, 1, Integer::sum);
            }
        }
        int started = tally.getOrDefault(WheelOutcome.REINIT_STARTED, 0);
        int stressFixed = tally.getOrDefault(WheelOutcome.STRESS_CURED, 0);
        LOGGER.info("[create_water_wheel_unstucker] /vpaunstuck summary: {} re-initialised, {} revived by clearing a"
                        + " stale stress state, {} already spinning, {} skipped (genuinely overstressed),"
                        + " {} skipped (no water nearby), {} skipped (large wheel, see reinit_large_wheels),"
                        + " {} skipped (not loaded), {} already being re-placed, {} no longer a wheel,"
                        + " {} with no block entity yet. Re-init outcomes logged shortly.",
                started, stressFixed,
                tally.getOrDefault(WheelOutcome.SPINNING, 0),
                tally.getOrDefault(WheelOutcome.OVERSTRESSED_GENUINE, 0),
                tally.getOrDefault(WheelOutcome.NO_FLUID, 0),
                tally.getOrDefault(WheelOutcome.REINIT_REFUSED, 0),
                tally.getOrDefault(WheelOutcome.NOT_LOADED, 0),
                tally.getOrDefault(WheelOutcome.REPLACE_PENDING, 0),
                tally.getOrDefault(WheelOutcome.GONE, 0),
                tally.getOrDefault(WheelOutcome.NO_BE, 0));
        return started + stressFixed;
    }

    /**
     * Tries to clear an "Overstressed" verdict that no loaded machine justifies, without touching a
     * single block. Two escalating steps, both operating purely on Create's own bookkeeping:
     *
     * <ol>
     *   <li><b>Recompute</b> - {@code updateNetwork(); sync();} recalculates stress and capacity from
     *       the network's current members and pushes the result to all of them. Cures a network still
     *       carrying numbers from a state its members have long left.</li>
     *   <li><b>Drop the phantom unloaded tally</b> ({@code clear_phantom_stress}, and for an
     *       automatic caller additionally {@code auto_clear_phantom_stress}) -
     *       Create keeps a running stress/capacity total for members in unloaded chunks and subtracts
     *       a member's share when it loads again. A member removed while unloaded never subtracts,
     *       so its stress haunts the network forever. Only done when the loaded members alone would
     *       fit the loaded capacity - i.e. when the unloaded tally is provably the sole cause. An
     *       automatic caller additionally waits for the tally to stop moving; see
     *       {@link #tallySettled}.</li>
     * </ol>
     *
     * @param level  The server level
     * @param pos    The wheel center
     * @param be     The wheel block entity
     * @param policy What this trigger is allowed to do
     * @param state  The wheel's escalation state, which carries the tally sample
     * @param now    The current game time
     * @return what this call actually did
     */
    private StressAction resolveOverstress(ServerLevel level, BlockPos pos, BlockEntity be,
                                           FixPolicy policy, FixState state, long now) {
        StressMode mode = policy.stressMode();
        WaterWheelKinetics.NetworkStats before = WaterWheelKinetics.readNetworkStats(be);
        if (before == null) {
            return StressAction.NONE; // no network reflection / no network - nothing to reason about
        }
        // Load-bearing order: recompute first, THEN re-read. overloadIsUnloadedOnly() is only exact
        // on freshly recomputed capacity/stress, and an automatic caller now depends on it.
        WaterWheelKinetics.recomputeNetwork(be);
        if (!WaterWheelKinetics.isOverStressed(be)) {
            if (mode == StressMode.COMMAND) {
                LOGGER.info("[create_water_wheel_unstucker] Wheel at {} ({}) was stuck on a stale overstressed state;"
                                + " a network recompute cleared it. Before: {}", pos.toShortString(),
                        level.dimension().location(), before);
            }
            return StressAction.RECOMPUTED;
        }
        if (!module.getConfig().isClearPhantomStressEnabled()) {
            return StressAction.NONE;
        }
        WaterWheelKinetics.NetworkStats current = WaterWheelKinetics.readNetworkStats(be);
        if (current == null) {
            return StressAction.NONE;
        }
        sampleTally(state, current, now);
        if (!isTallyClearable(current, mode, state, now)) {
            return StressAction.NONE; // a real overload, or a tally that is still moving
        }
        if (!WaterWheelKinetics.clearUnloadedStressAccounting(be)) {
            return StressAction.NONE;
        }
        if (mode == StressMode.DEEP && !current.hasOrphanedTally()) {
            // The judgement call, taken without a human: say so loudly, with the numbers it was
            // taken on and how long the tally had stopped moving.
            LOGGER.warn("[create_water_wheel_unstucker] Wheel at {} ({}) [{}]: dropped an unloaded-member stress"
                            + " tally that had not changed for {} ticks while the network stayed overstressed"
                            + " ({}). Set auto_clear_phantom_stress = false to leave this to /vpaunstuck.",
                    pos.toShortString(), level.dimension().location(), policy.trigger(),
                    now - state.tallySince, current);
        } else if (mode == StressMode.COMMAND) {
            LOGGER.info("[create_water_wheel_unstucker] Wheel at {} ({}): dropped a phantom unloaded-member stress"
                            + " tally ({}) - overstressed now {}", pos.toShortString(),
                    level.dimension().location(), current, WaterWheelKinetics.isOverStressed(be));
        }
        return StressAction.TALLY_DROPPED;
    }

    /**
     * Decides whether the unloaded tally may be dropped, and this is where the two modes genuinely
     * differ:
     *
     * <ul>
     *   <li><b>Sweep</b> - only a self-contradictory tally: stress charged while the network claims
     *       zero unloaded members. No machine can be behind those numbers, so dropping them cannot
     *       take anything away from anyone. Safe for every caller.</li>
     *   <li><b>Command</b> - also the judgement call, immediately: a tally with actual unloaded
     *       members, where the loaded members alone would fit the loaded capacity. Those members
     *       might be real machines in unloaded chunks, which Create counts on purpose - but an
     *       operator is watching and the log carries the numbers.</li>
     *   <li><b>Deep</b> - the same judgement call for an automatic caller, but only after
     *       {@link #tallySettled}: the tally is a one-way countdown, so one that is still shrinking
     *       proves members are still arriving and must be left alone.</li>
     * </ul>
     *
     * @param stats The current network snapshot
     * @param mode  How far this trigger may go
     * @param state The wheel's escalation state, carrying the tally sample
     * @param now   The current game time
     * @return true if the tally may be dropped
     */
    private boolean isTallyClearable(WaterWheelKinetics.NetworkStats stats, StressMode mode,
                                     FixState state, long now) {
        if (stats.hasOrphanedTally()) {
            return true;
        }
        if (!stats.hasUnloadedAccounting() || !stats.overloadIsUnloadedOnly()) {
            return false;
        }
        return switch (mode) {
            case SAFE -> false;
            case COMMAND -> true;
            case DEEP -> tallySettled(state, now);
        };
    }

    /**
     * Records the current unloaded tally, restarting the clock whenever it changes.
     *
     * @param state The wheel's escalation state
     * @param stats The freshly recomputed network snapshot
     * @param now   The current game time
     */
    private static void sampleTally(FixState state, WaterWheelKinetics.NetworkStats stats, long now) {
        boolean same = state.tallyKnown
                && state.tallyMembers == stats.unloadedMembers()
                && state.tallyStress == stats.unloadedStress()
                && state.tallyCapacity == stats.unloadedCapacity();
        if (!same) {
            state.tallyKnown = true;
            state.tallyMembers = stats.unloadedMembers();
            state.tallyStress = stats.unloadedStress();
            state.tallyCapacity = stats.unloadedCapacity();
            state.tallySince = now;
        }
    }

    /**
     * Whether the unloaded tally has stopped moving long enough to act on it unattended.
     *
     * <p>This is what makes the automatic judgement call defensible. Create's unloaded tally is a
     * one-way countdown: it is seeded once per network with the whole network's last-known totals -
     * as if every member were unloaded - and from then on only shrinks, once per member that loads
     * ({@code KineticNetwork.addSilently}). Nothing ever raises it again.</p>
     *
     * <p>So right after a world load every network legitimately carries a tally, and
     * {@code overloadIsUnloadedOnly()} is trivially true while the members are still arriving.
     * A tally that is still shrinking therefore proves the network is filling up and must be left
     * alone; one that has not moved for {@link #TALLY_SETTLE_TICKS} while the network stays
     * overstressed proves nobody else is coming.</p>
     *
     * @param state The wheel's escalation state
     * @param now   The current game time
     * @return true if the sample has been unchanged long enough
     */
    private static boolean tallySettled(FixState state, long now) {
        return state.tallyKnown && now - state.tallySince >= TALLY_SETTLE_TICKS;
    }

    /**
     * Starts a break + re-place re-init: captures the wheel's full state (its orientation / axis) and
     * material, removes it so adjacent water floods the gap and re-establishes ACTIVE flow, then queues
     * the wheel to be placed back after {@code reinit_flood_ticks}. This is the only thing that revives
     * a reload-stalled wheel: Create re-reads the flow score every 60 ticks ({@code lazyTick} to
     * {@code determineAndApplyFlowScore}), but it reads the water as it is - standing water scores
     * zero however often it is asked. Flooding the gap is what makes that reading non-zero.
     * Sized for small (single-block) wheels.
     *
     * <p>The pending entry is recorded <i>before</i> the block is removed, never after: between the
     * two statements the wheel exists nowhere else, and anything that ends the tick in between - an
     * exception, a level unload - would take it with it.</p>
     *
     * <p>Large wheels are refused unless {@code reinit_large_wheels} is set. Create rebuilds a large
     * wheel through {@code LargeWaterWheelBlock.tick}, which calls {@code destroyBlock(center, false)}
     * - without drops - so a re-init that goes wrong there costs the whole multiblock, not one block.</p>
     *
     * @param level The server level
     * @param pos   The wheel center
     * @param be    The wheel block entity
     * @param state  The wheel's escalation state, used to keep the refusal log out of the tick loop
     * @param policy What this trigger is allowed to do
     * @return true if the wheel was removed and is now held for re-placement
     */
    private boolean beginReinit(ServerLevel level, BlockPos pos, BlockEntity be, FixState state, FixPolicy policy) {
        BlockState wheelState = level.getBlockState(pos);
        if (WaterWheelRegistry.isLargeWheel(wheelState) && !module.getConfig().isReinitLargeWheelsEnabled()) {
            long now = level.getGameTime();
            // Throttled for automatic triggers, which come round constantly - but never for the
            // operator, who asked this second and deserves an answer.
            if (!policy.rateLimited() || state.lastRefusalLog == 0L
                    || now - state.lastRefusalLog >= REFUSAL_LOG_INTERVAL_TICKS) {
                state.lastRefusalLog = now;
                LOGGER.info("[create_water_wheel_unstucker] Wheel at {} ({}) is a large wheel; not re-initialising"
                                + " it. Set reinit_large_wheels = true to allow it.",
                        pos.toShortString(), level.dimension().location());
            }
            return false;
        }
        BlockState material = WaterWheelKinetics.getMaterial(be);
        long due = level.getGameTime() + module.getConfig().getReinitFloodTicks();
        // Record first, remove second - the wheel must never exist only inside a local variable.
        pendingReplace.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .put(pos.immutable(), new PendingReplace(wheelState, material, due, 0));
        // Remove the wheel (no drops) so adjacent water can flood the void.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return true;
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
                PendingReplace pending = entry.getValue();
                ReplaceResult result = replaceWheel(level, entry.getKey(), pending);
                if (result == ReplaceResult.RETRY) {
                    // Hold on to the wheel and come back. Updating the value in place is allowed
                    // during iteration; a put() would not be.
                    entry.setValue(new PendingReplace(pending.wheelState(), pending.material(),
                            now + REPLACE_RETRY_TICKS, pending.retries() + 1));
                    continue;
                }
                it.remove();
            }
        }
    }

    /**
     * Places a removed wheel back with its original state (orientation) and material, then triggers
     * Create's own flow recompute (as {@code onPlace} does) while the flooded water is still moving.
     *
     * <p>The gap can legitimately be occupied when the wheel is due back - a player builds there
     * during the flood window, a piston pushes into it, a falling block lands. That is not a reason
     * to drop the captured wheel on the floor: the caller keeps holding it and we try again. Only
     * after {@link #REPLACE_MAX_RETRIES} attempts is the wheel dropped as an item, which at least
     * leaves the player something to pick up.</p>
     *
     * @param level   The server level
     * @param pos     The wheel center
     * @param pending The captured wheel state
     * @return what happened - the caller only releases the wheel on {@code RESTORED} or {@code GIVE_UP}
     */
    private ReplaceResult replaceWheel(ServerLevel level, BlockPos pos, PendingReplace pending) {
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() == pending.wheelState().getBlock()) {
            return ReplaceResult.RESTORED; // already back (a player rebuilt it, or we ran twice)
        }
        // Only restore into our own placeholder (air, or water that flooded in) - never clobber a
        // block a player may have placed in the gap.
        if (!current.isAir() && current.getFluidState().isEmpty()) {
            if (pending.retries() < REPLACE_MAX_RETRIES) {
                if (pending.retries() == 0) {
                    LOGGER.warn("[create_water_wheel_unstucker] Cannot put the wheel back at {} ({}): {} is in the"
                                    + " way. Holding the wheel and retrying.", pos.toShortString(),
                            level.dimension().location(), current.getBlock().getName().getString());
                }
                return ReplaceResult.RETRY;
            }
            LOGGER.error("[create_water_wheel_unstucker] Gave up putting the wheel back at {} ({}) after {} tries -"
                            + " {} is still in the way. Dropping {} as an item instead.", pos.toShortString(),
                    level.dimension().location(), pending.retries(),
                    current.getBlock().getName().getString(), pending.wheelState().getBlock().getName().getString());
            Block.popResource(level, pos, new ItemStack(pending.wheelState().getBlock()));
            return ReplaceResult.GIVE_UP;
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
        // Our setBlock fires no EntityPlaceEvent, so the registry would not learn about the wheel
        // again until its chunk next loads.
        registry.onBlockPlaced(level, pos, pending.wheelState());
        // Check the outcome ~1s later and log whether the wheel actually restarted.
        pendingVerify.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .put(pos.immutable(), level.getGameTime() + 20L);
        return ReplaceResult.RESTORED;
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

    /**
     * Resets a wheel's escalation after it recovered - but keeps {@code lastReinitTick}, which is the
     * rate limiter. Create recomputes a wheel's flow score on its own every 60 ticks, so a wheel can
     * look healthy for a moment and stall again; dropping the cooldown here would let an automatic
     * trigger break the same wheel open over and over.
     *
     * @param level The server level
     * @param pos   The wheel center
     */
    private void clearState(ServerLevel level, BlockPos pos) {
        Map<BlockPos, FixState> byPos = fixStates.get(level.dimension());
        if (byPos == null) {
            return;
        }
        FixState state = byPos.get(pos);
        if (state == null) {
            return;
        }
        if (state.lastReinitTick == 0L && !state.tallyKnown) {
            byPos.remove(pos); // nothing worth remembering
            return;
        }
        state.attempts = 0;
        state.backoffUntil = 0L;
        state.warned = false;
        // A wheel that reaches this point is not in a fixable stall right now - it spins, the
        // network is genuinely overloaded, or the water is gone. Any of those disproves the
        // give-up verdict, so the escalation starts from scratch if it stalls again later.
        state.givenUp = false;
        state.backoffsExhausted = 0;
        // The tally sample deliberately survives: it is a measurement of Create's bookkeeping over
        // time, not part of this wheel's escalation. Wiping it here restarted the settle clock on
        // every single check, which made StressMode.DEEP unreachable for the case it exists for.
        // It IS reset in forgetChunk, because a chunk reload rebuilds the network the sample was
        // taken from.
    }

    /**
     * Drops all pending/escalation state inside an unloading chunk.
     *
     * @param level    The server level
     * @param chunkPos The unloading chunk
     */
    void forgetChunk(ServerLevel level, ChunkPos chunkPos) {
        Map<BlockPos, ScheduledCheck> pending = pendingPostLoad.get(level.dimension());
        if (pending != null) {
            pending.keySet().removeIf(pos -> inChunk(pos, chunkPos));
        }
        Map<BlockPos, Long> verify = pendingVerify.get(level.dimension());
        if (verify != null) {
            // Nothing to verify once the chunk is gone; the entry would otherwise sit there forever.
            verify.keySet().removeIf(pos -> inChunk(pos, chunkPos));
        }
        Map<BlockPos, FixState> states = fixStates.get(level.dimension());
        if (states == null) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, FixState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, FixState> entry = it.next();
            if (!inChunk(entry.getKey(), chunkPos)) {
                continue;
            }
            FixState state = entry.getValue();
            // The tally sample must NOT survive: Create tears the kinetic network down with the
            // chunk and re-seeds it on reload, so a sample from before that describes a network
            // that no longer exists - and would let the settle clock read as expired the moment
            // the wheel comes back.
            state.tallyKnown = false;
            // The cooldown, the backoff and the give-up flag must survive: chunk load is precisely
            // what drives the automatic cure, so dropping them here would disarm every brake on
            // the one cycle they exist for.
            boolean worthKeeping = state.givenUp
                    || state.backoffUntil > now
                    || (state.lastReinitTick != 0L && now - state.lastReinitTick < MIN_REINIT_INTERVAL_TICKS);
            if (!worthKeeping) {
                it.remove();
            }
        }
    }

    /**
     * Drops all state of an unloading level.
     *
     * @param level The server level being unloaded
     */
    void forgetLevel(ServerLevel level) {
        flushPendingReplaces(level); // put held wheels back before the level goes away
        pendingPostLoad.remove(level.dimension());
        fixStates.remove(level.dimension());
        pendingReplace.remove(level.dimension());
        pendingVerify.remove(level.dimension());
    }

    /**
     * Puts every wheel this manager is holding for a re-init back into the world at once, ignoring the
     * flood window. Called when a level unloads and when the server stops: six ticks of flooding are
     * worth far less than the wheel, and a held wheel that is never placed back is simply gone.
     *
     * @param server The running server
     */
    void flushPendingReplaces(MinecraftServer server) {
        for (ResourceKey<Level> dimension : List.copyOf(pendingReplace.keySet())) {
            ServerLevel level = server.getLevel(dimension);
            if (level != null) {
                flushPendingReplaces(level);
            }
        }
    }

    /**
     * Puts back every wheel held in one level. A wheel whose chunk is not loaded forces that chunk:
     * we are about to lose the wheel otherwise. Whatever still cannot be placed is logged with its
     * dimension, position and block state, so it can be restored by hand.
     *
     * @param level The server level
     */
    private void flushPendingReplaces(ServerLevel level) {
        Map<BlockPos, PendingReplace> held = pendingReplace.get(level.dimension());
        if (held == null || held.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, PendingReplace> entry : held.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!isFootprintLoaded(level, pos)) {
                level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            }
            PendingReplace pending = entry.getValue();
            // Force the last attempt: a retry would never come.
            ReplaceResult result = replaceWheel(level, pos,
                    new PendingReplace(pending.wheelState(), pending.material(), pending.dueTick(),
                            REPLACE_MAX_RETRIES));
            if (result != ReplaceResult.RESTORED) {
                LOGGER.error("[create_water_wheel_unstucker] Could not restore the wheel held at {} ({}) before"
                                + " shutdown. Its state was {} - restore it with /setblock if it is missing.",
                        pos.toShortString(), level.dimension().location(), pending.wheelState());
            }
        }
        held.clear();
    }

    /**
     * Clears everything (server stopped).
     */
    void clearAll() {
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, PendingReplace>> byLevel : pendingReplace.entrySet()) {
            for (Map.Entry<BlockPos, PendingReplace> entry : byLevel.getValue().entrySet()) {
                LOGGER.error("[create_water_wheel_unstucker] Still holding a removed wheel at {} ({}) when the server"
                                + " stopped: {}. Restore it with /setblock if it is missing.",
                        entry.getKey().toShortString(), byLevel.getKey().location(), entry.getValue().wheelState());
            }
        }
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
        // Hard removal, not clearState: the wheel is gone, and a wheel placed here later must not
        // inherit its cooldown, its give-up flag or a tally sample taken from a different network.
        Map<BlockPos, FixState> states = fixStates.get(level.dimension());
        if (states != null) {
            states.remove(pos);
        }
        Map<BlockPos, ScheduledCheck> pending = pendingPostLoad.get(level.dimension());
        if (pending != null) {
            pending.remove(pos);
        }
    }

    /**
     * Whether a wheel is currently removed and held for re-placement.
     *
     * @param level The server level
     * @param pos   The wheel center
     * @return true while the re-init flood window is open for this position
     */
    private boolean hasPendingReplace(ServerLevel level, BlockPos pos) {
        Map<BlockPos, PendingReplace> held = pendingReplace.get(level.dimension());
        return held != null && held.containsKey(pos);
    }

    /**
     * Whether a check ended without ever getting to look at the wheel, so its queued slot is still
     * worth something.
     *
     * @param outcome what the check returned
     * @return true if nothing was actually evaluated
     */
    private static boolean inconclusive(WheelOutcome outcome) {
        return outcome == WheelOutcome.NOT_LOADED
                || outcome == WheelOutcome.NO_BE
                || outcome == WheelOutcome.REPLACE_PENDING;
    }

    private static boolean inChunk(BlockPos pos, ChunkPos chunkPos) {
        return chunkPos.x == (pos.getX() >> 4) && chunkPos.z == (pos.getZ() >> 4);
    }
}
