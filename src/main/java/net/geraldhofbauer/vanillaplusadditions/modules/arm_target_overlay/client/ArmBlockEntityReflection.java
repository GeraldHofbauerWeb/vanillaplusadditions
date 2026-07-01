package net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.client;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.geraldhofbauer.vanillaplusadditions.core.Vpa;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/**
 * Accesses ArmBlockEntity.inputs and ArmBlockEntity.outputs via reflection.
 * Both fields are package-private in Create, so direct access is not possible.
 * Fields are resolved once and cached for performance.
 */
final class ArmBlockEntityReflection {
    private static volatile boolean initialized;
    private static volatile boolean warningLogged;
    private static Field inputsField;
    private static Field outputsField;

    private ArmBlockEntityReflection() {
    }

    @SuppressWarnings("unchecked")
    static List<ArmInteractionPoint> getInputs(ArmBlockEntity be) {
        ensureInitialized();
        if (inputsField == null) {
            return Collections.emptyList();
        }
        try {
            return (List<ArmInteractionPoint>) inputsField.get(be);
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to read ArmBlockEntity.inputs", ex);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    static List<ArmInteractionPoint> getOutputs(ArmBlockEntity be) {
        ensureInitialized();
        if (outputsField == null) {
            return Collections.emptyList();
        }
        try {
            return (List<ArmInteractionPoint>) outputsField.get(be);
        } catch (ReflectiveOperationException ex) {
            warnOnce("Failed to read ArmBlockEntity.outputs", ex);
            return Collections.emptyList();
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (ArmBlockEntityReflection.class) {
            if (!initialized) {
                try {
                    Field f1 = ArmBlockEntity.class.getDeclaredField("inputs");
                    f1.setAccessible(true);
                    Field f2 = ArmBlockEntity.class.getDeclaredField("outputs");
                    f2.setAccessible(true);
                    inputsField = f1;
                    outputsField = f2;
                } catch (ReflectiveOperationException ex) {
                    warnOnce("Failed to initialize ArmBlockEntity reflection", ex);
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
        Vpa.LOGGER.warn("{}: {}", message, ex.getMessage());
    }
}
