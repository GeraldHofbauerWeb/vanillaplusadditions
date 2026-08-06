package net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * The ONLY access path to Create's water-wheel {@code BlockEntity} API, implemented entirely via
 * reflection ({@code Class.forName} + cached {@link Method} objects, mirroring
 * {@code ArmBlockEntityReflection}).
 *
 * <p>Why reflection: {@code WaterWheelBlockEntity} extends {@code KineticBlockEntity} extends
 * {@code SmartBlockEntity}, which implements {@code net.createmod.ponder.api.VirtualBlockEntity} -
 * and the Ponder API is not in {@code libs/}, so any compile-time reference to these classes fails
 * to build (see {@code docs/ARM_TARGET_OVERLAY_CASE_STUDY.md}). At runtime, with Create installed,
 * Ponder is present and everything resolves normally.</p>
 *
 * <p>If initialization fails (e.g. a future Create version renamed a method), a warning is logged
 * once, {@link #isAvailable()} turns false and all accessors degrade to neutral no-ops - the module
 * never crashes the server.</p>
 */
final class WaterWheelKinetics {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String KINETIC_BE = "com.simibubi.create.content.kinetics.base.KineticBlockEntity";
    private static final String GENERATING_BE = "com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity";
    private static final String WATER_WHEEL_BE = "com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity";
    private static final String KINETIC_NETWORK = "com.simibubi.create.content.kinetics.KineticNetwork";

    private static volatile boolean initialized;
    private static volatile boolean warningLogged;
    private static Class<?> waterWheelClass;
    private static Method getSpeed;
    private static Method getGeneratedSpeed;
    private static Method isOverStressed;
    private static Method detachKinetics;
    private static Method removeSource;
    private static Method attachKinetics;
    private static Method updateGeneratedRotation;
    private static Method determineAndApplyFlowScore;
    private static Field materialField;

    // Optional network layer - absence must never disable the wheel accessors above.
    private static Method hasNetwork;
    private static Method getOrCreateNetwork;
    private static Method networkUpdate;
    private static Method networkSync;
    private static Method networkCalcCapacity;
    private static Method networkCalcStress;
    private static Field netUnloadedCapacity;
    private static Field netUnloadedStress;
    private static Field netUnloadedMembers;
    private static Field netSources;
    private static Field netMembers;
    private static Field beCapacity;
    private static Field beStress;

    /**
     * A snapshot of the kinetic network a wheel belongs to - the numbers behind Create's
     * "Overstressed" verdict ({@code overStressed = capacity < stress}).
     *
     * <p>The {@code unloaded*} counters are Create's accounting for members in unloaded chunks:
     * {@code calculateStress()/calculateCapacity()} add them on top of the loaded members, and
     * {@code addSilently} subtracts a member's share again when it loads. They are the usual source
     * of a phantom overload - a member removed while its chunk was unloaded is never subtracted, so
     * its stress haunts the network forever.</p>
     *
     * @param capacity         the network's current stress capacity
     * @param stress           the network's current stress demand
     * @param unloadedCapacity the capacity share attributed to unloaded members
     * @param unloadedStress   the stress share attributed to unloaded members
     * @param unloadedMembers  the number of members Create believes are unloaded
     * @param loadedSources    the number of loaded sources in the network
     * @param loadedMembers    the number of loaded members in the network
     * @param unloadedKnown    whether the unloaded tally could be read at all (false = the three
     *                         {@code unloaded*} values are unknown, reported as 0)
     */
    record NetworkStats(float capacity, float stress, float unloadedCapacity, float unloadedStress,
                        int unloadedMembers, int loadedSources, int loadedMembers, boolean unloadedKnown) {

        /**
         * Whether any stress/capacity is currently attributed to unloaded members - the accounting
         * that can go stale and produce a phantom overload.
         *
         * @return true if unloaded members are accounted for
         */
        boolean hasUnloadedAccounting() {
            return unloadedKnown && (unloadedMembers > 0 || unloadedStress != 0.0f || unloadedCapacity != 0.0f);
        }

        /**
         * Whether the unloaded tally is self-contradictory: it charges stress or credits capacity
         * while claiming zero unloaded members. No machine can possibly be behind those numbers, so
         * dropping them is provably correct - which is what makes this case safe to automate.
         *
         * @return true if the tally is orphaned
         */
        boolean hasOrphanedTally() {
            return unloadedKnown && unloadedMembers == 0
                    && (unloadedStress != 0.0f || unloadedCapacity != 0.0f);
        }

        /**
         * Whether the loaded members alone would fit the loaded capacity - i.e. the overload exists
         * only because of the unloaded accounting.
         *
         * @return true if dropping the unloaded accounting would resolve the overload
         */
        boolean overloadIsUnloadedOnly() {
            return (stress - unloadedStress) <= (capacity - unloadedCapacity);
        }

        @Override
        public String toString() {
            String unloaded = unloadedKnown
                    ? String.format("capacity=%.1f stress=%.1f members=%d",
                            unloadedCapacity, unloadedStress, unloadedMembers)
                    : "unreadable";
            return String.format("capacity=%.1f stress=%.1f (of which unloaded: %s), loaded sources=%d members=%d",
                    capacity, stress, unloaded, loadedSources, loadedMembers);
        }
    }

    private WaterWheelKinetics() {
    }

    /**
     * Whether the reflection layer initialized successfully and the accessors are functional.
     *
     * @return true if Create's water-wheel API was resolved
     */
    static boolean isAvailable() {
        ensureInitialized();
        return waterWheelClass != null;
    }

    /**
     * Checks whether the given block entity is a Create water wheel (small or large -
     * {@code LargeWaterWheelBlockEntity} extends {@code WaterWheelBlockEntity}).
     *
     * @param be The block entity to test
     * @return true if it is a water wheel block entity
     */
    static boolean isWaterWheelBE(BlockEntity be) {
        ensureInitialized();
        return waterWheelClass != null && waterWheelClass.isInstance(be);
    }

    /**
     * Reads the wheel's current kinetic speed ({@code KineticBlockEntity.getSpeed()}).
     *
     * @param be The water wheel block entity
     * @return the current speed, or 0 on reflection failure
     */
    static float getSpeed(BlockEntity be) {
        return invokeFloat(getSpeed, be);
    }

    /**
     * Reads the wheel's generated speed ({@code getGeneratedSpeed()}, virtual dispatch applies the
     * water-wheel override based on its flow score).
     *
     * @param be The water wheel block entity
     * @return the generated speed, or 0 on reflection failure
     */
    static float getGeneratedSpeed(BlockEntity be) {
        return invokeFloat(getGeneratedSpeed, be);
    }

    /**
     * Whether the wheel's kinetic network is overstressed ({@code isOverStressed()}).
     *
     * @param be The water wheel block entity
     * @return true if overstressed, false on reflection failure
     */
    static boolean isOverStressed(BlockEntity be) {
        ensureInitialized();
        if (isOverStressed == null || !isWaterWheelBE(be)) {
            return false;
        }
        try {
            return (Boolean) isOverStressed.invoke(be);
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to invoke isOverStressed", ex);
            return false;
        }
    }

    /**
     * Soft kick: {@code determineAndApplyFlowScore()} (recomputes the flow score from the
     * surrounding water - fixes the stale-flow case where a neighbor chunk's water was not loaded
     * when the block entity initialized) followed by {@code updateGeneratedRotation()} (re-announces
     * the generated speed to the kinetic network).
     *
     * @param be The water wheel block entity
     * @return true if both calls succeeded
     */
    static boolean softKick(BlockEntity be) {
        ensureInitialized();
        if (determineAndApplyFlowScore == null || updateGeneratedRotation == null || !isWaterWheelBE(be)) {
            return false;
        }
        try {
            determineAndApplyFlowScore.invoke(be);
            updateGeneratedRotation.invoke(be);
            return true;
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to soft-kick water wheel", ex);
            return false;
        }
    }

    /**
     * Hard kick: {@code detachKinetics(); removeSource(); attachKinetics();} - a full kinetic
     * network detach/re-attach, equivalent to wrenching the wheel out and placing it back
     * ({@code RotationPropagator.handleRemoved/handleAdded} run internally).
     *
     * @param be The water wheel block entity
     * @return true if all three calls succeeded
     */
    static boolean hardKick(BlockEntity be) {
        ensureInitialized();
        if (detachKinetics == null || removeSource == null || attachKinetics == null || !isWaterWheelBE(be)) {
            return false;
        }
        try {
            detachKinetics.invoke(be);
            removeSource.invoke(be);
            attachKinetics.invoke(be);
            return true;
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to hard-kick water wheel", ex);
            return false;
        }
    }

    /**
     * Reads the stress/capacity numbers of the wheel's kinetic network.
     *
     * @param be The water wheel block entity
     * @return the network snapshot, or null if the wheel has no network or the network reflection
     *         is unavailable
     */
    static NetworkStats readNetworkStats(BlockEntity be) {
        Object network = network(be);
        if (network == null) {
            return null;
        }
        try {
            boolean tally = unloadedTallyAvailable();
            // Prefer the numbers the wheel was last told - those are what its overStressed flag was
            // derived from. calculate*() would already answer with the post-recompute values.
            float capacity = tally ? beCapacity.getFloat(be) : (Float) networkCalcCapacity.invoke(network);
            float stress = tally ? beStress.getFloat(be) : (Float) networkCalcStress.invoke(network);
            return new NetworkStats(
                    capacity,
                    stress,
                    tally ? netUnloadedCapacity.getFloat(network) : 0.0f,
                    tally ? netUnloadedStress.getFloat(network) : 0.0f,
                    tally ? netUnloadedMembers.getInt(network) : 0,
                    ((Map<?, ?>) netSources.get(network)).size(),
                    ((Map<?, ?>) netMembers.get(network)).size(),
                    tally);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            warnOnce("Failed to read kinetic network stats", ex);
            return null;
        }
    }

    /**
     * Forces the wheel's kinetic network to recalculate its stress and capacity from its current
     * members and to push the result to every member ({@code updateNetwork(); sync();}).
     *
     * <p>Purely a recompute of Create's own numbers - no world mutation, nothing invented. It clears
     * an overload that only existed because the network was still holding values computed while some
     * member reported a different speed.</p>
     *
     * @param be The water wheel block entity
     * @return true if the recompute ran
     */
    static boolean recomputeNetwork(BlockEntity be) {
        Object network = network(be);
        if (network == null) {
            return false;
        }
        try {
            networkUpdate.invoke(network);
            networkSync.invoke(network); // updateNetwork() only syncs on change - force it either way
            return true;
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to recompute kinetic network", ex);
            return false;
        }
    }

    /**
     * Drops the network's unloaded-member accounting ({@code unloadedMembers/unloadedStress/
     * unloadedCapacity} = 0) and recomputes, curing a phantom overload left behind by members that
     * were removed while their chunk was unloaded and therefore never subtracted their share again.
     *
     * <p>Trade-off: machines that genuinely are unloaded stop counting until their chunk loads, at
     * which point {@code addSilently} registers them again with their real numbers. That is why this
     * is only ever reached from the explicit {@code /vpaunstuck} command.</p>
     *
     * @param be The water wheel block entity
     * @return true if unloaded accounting was present and has been cleared
     */
    static boolean clearUnloadedStressAccounting(BlockEntity be) {
        Object network = network(be);
        if (network == null || !unloadedTallyAvailable()) {
            return false;
        }
        try {
            if (netUnloadedMembers.getInt(network) == 0
                    && netUnloadedStress.getFloat(network) == 0.0f
                    && netUnloadedCapacity.getFloat(network) == 0.0f) {
                return false;
            }
            netUnloadedMembers.setInt(network, 0);
            netUnloadedStress.setFloat(network, 0.0f);
            netUnloadedCapacity.setFloat(network, 0.0f);
            networkUpdate.invoke(network);
            networkSync.invoke(network);
            return true;
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to clear unloaded stress accounting", ex);
            return false;
        }
    }

    private static Object network(BlockEntity be) {
        ensureInitialized();
        if (getOrCreateNetwork == null || !isWaterWheelBE(be)) {
            return null;
        }
        try {
            if (!(Boolean) hasNetwork.invoke(be)) {
                return null; // never create a network just to look at it
            }
            return getOrCreateNetwork.invoke(be);
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to resolve kinetic network", ex);
            return null;
        }
    }

    /**
     * Reads the wheel's applied visual material ({@code WaterWheelBlockEntity.material}).
     *
     * @param be The water wheel block entity
     * @return the material block state, or null if unavailable
     */
    static BlockState getMaterial(BlockEntity be) {
        ensureInitialized();
        if (materialField == null || !isWaterWheelBE(be)) {
            return null;
        }
        try {
            return (BlockState) materialField.get(be);
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to read water wheel material", ex);
            return null;
        }
    }

    /**
     * Restores the wheel's visual material after a re-init break+replace.
     *
     * @param be       The water wheel block entity
     * @param material The material block state to apply (no-op if null)
     */
    static void setMaterial(BlockEntity be, BlockState material) {
        ensureInitialized();
        if (materialField == null || material == null || !isWaterWheelBE(be)) {
            return;
        }
        try {
            materialField.set(be, material);
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to restore water wheel material", ex);
        }
    }

    private static float invokeFloat(Method method, BlockEntity be) {
        ensureInitialized();
        if (method == null || !isWaterWheelBE(be)) {
            return 0.0f;
        }
        try {
            return (Float) method.invoke(be);
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to invoke " + method.getName(), ex);
            return 0.0f;
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (WaterWheelKinetics.class) {
            if (!initialized) {
                try {
                    Class<?> kinetic = Class.forName(KINETIC_BE);
                    Class<?> generating = Class.forName(GENERATING_BE);
                    Class<?> waterWheel = Class.forName(WATER_WHEEL_BE);
                    getSpeed = kinetic.getMethod("getSpeed");
                    getGeneratedSpeed = kinetic.getMethod("getGeneratedSpeed");
                    isOverStressed = kinetic.getMethod("isOverStressed");
                    detachKinetics = kinetic.getMethod("detachKinetics");
                    removeSource = kinetic.getMethod("removeSource");
                    attachKinetics = kinetic.getMethod("attachKinetics");
                    updateGeneratedRotation = generating.getMethod("updateGeneratedRotation");
                    determineAndApplyFlowScore = waterWheel.getMethod("determineAndApplyFlowScore");
                    waterWheelClass = waterWheel;
                    // Optional: the visual material (planks) applied to the wheel. Preserved across a
                    // break+replace re-init. Its absence must NOT disable the whole layer.
                    try {
                        materialField = waterWheel.getField("material");
                    } catch (NoSuchFieldException nsf) {
                        materialField = null;
                    }
                    initNetworkAccess(kinetic);
                } catch (ReflectiveOperationException | LinkageError ex) {
                    warnOnce("Failed to initialize Create water wheel reflection"
                            + " - the unstucker will be inactive", ex);
                }
                initialized = true;
            }
        }
    }

    /**
     * Resolves the optional {@code KineticNetwork} layer. Its absence only costs the stress
     * diagnostics / stale-overload cure, never the wheel accessors, so failures stay silent here.
     *
     * @param kinetic The resolved {@code KineticBlockEntity} class
     */
    private static void initNetworkAccess(Class<?> kinetic) {
        Class<?> network;
        try {
            network = Class.forName(KINETIC_NETWORK);
            hasNetwork = kinetic.getMethod("hasNetwork");
            getOrCreateNetwork = kinetic.getMethod("getOrCreateNetwork");
            networkUpdate = network.getMethod("updateNetwork");
            networkSync = network.getMethod("sync");
            networkCalcCapacity = network.getMethod("calculateCapacity");
            networkCalcStress = network.getMethod("calculateStress");
            netSources = network.getField("sources");
            netMembers = network.getField("members");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            LOGGER.warn("[create_water_wheel_unstucker] Kinetic network reflection unavailable"
                    + " - stale-stress recovery disabled: {}", ex.toString());
            getOrCreateNetwork = null;
            return;
        }
        // The unloaded-member tally is private, as are the numbers the wheel was last told (the ones
        // its overStressed flag was derived from). Losing them only costs the phantom-overload cure
        // and exact diagnostics, so they are resolved separately - setAccessible can also fail with
        // an (unchecked) module exception.
        try {
            netUnloadedCapacity = privateField(network, "unloadedCapacity");
            netUnloadedStress = privateField(network, "unloadedStress");
            netUnloadedMembers = privateField(network, "unloadedMembers");
            beCapacity = privateField(kinetic, "capacity");
            beStress = privateField(kinetic, "stress");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            LOGGER.warn("[create_water_wheel_unstucker] Unloaded-member stress tally not readable"
                    + " - phantom-overload recovery disabled: {}", ex.toString());
            netUnloadedMembers = null;
        }
    }

    private static boolean unloadedTallyAvailable() {
        return netUnloadedMembers != null && netUnloadedStress != null && netUnloadedCapacity != null;
    }

    private static Field privateField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void warnOnce(String message, Throwable ex) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        LOGGER.warn("[create_water_wheel_unstucker] {}: {}", message, ex.getMessage());
    }
}
