package net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
                } catch (ReflectiveOperationException | LinkageError ex) {
                    warnOnce("Failed to initialize Create water wheel reflection"
                            + " - the unstucker will be inactive", ex);
                }
                initialized = true;
            }
        }
    }

    private static void warnOnce(String message, Throwable ex) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        LOGGER.warn("[create_water_wheel_unstucker] {}: {}", message, ex.getMessage());
    }
}
