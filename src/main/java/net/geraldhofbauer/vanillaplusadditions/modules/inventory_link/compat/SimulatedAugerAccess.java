package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * The ONLY access path to Create: Aeronautics' ({@code simulated}) auger inventories.
 *
 * <p>Why this exists: an Auger Shaft exposes its item handler capability only on the faces
 * perpendicular to its axis, and an Auger <em>Cog</em> exposes none at all
 * ({@code SimBlockEntityTypes.AUGER_SHAFT} returns {@code null} for {@code dir == null}, for both
 * axial faces, and for every cog blockstate). Its inventory is still a public field, and the mod's
 * own {@code ContainerWrapper} adapts it to {@link IItemHandler} — so a link can use a cog as an
 * endpoint even though nothing else in the game can.</p>
 *
 * <p>Why reflection: the {@code simulated} mod ships jar-in-jar inside
 * {@code create-aeronautics-bundled} and is not on the compile classpath at all. If initialization
 * fails (mod absent, classes renamed), a warning is logged once and every call returns {@code null}
 * — auger cogs then simply cannot be link endpoints.</p>
 */
public final class SimulatedAugerAccess {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String AUGER_BE =
            "dev.simulated_team.simulated.content.blocks.auger_shaft.AugerShaftBlockEntity";
    private static final String CONTAINER_WRAPPER =
            "dev.simulated_team.simulated.multiloader.inventory.neoforge.ContainerWrapper";

    private static volatile boolean initialized;
    private static volatile boolean warningLogged;
    private static Class<?> augerBeClass;
    private static Field inventoryField;
    private static Constructor<?> wrapperCtor;

    private SimulatedAugerAccess() {
    }

    /**
     * Wraps an auger block entity's internal inventory in an {@link IItemHandler}.
     *
     * @param blockEntity any block entity, may be null or of an unrelated type
     * @return a handler for the auger's own single-slot inventory, or null if this is not an auger
     *         or the reflection is unavailable
     */
    public static IItemHandler wrap(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        ensureInitialized();
        if (augerBeClass == null || !augerBeClass.isInstance(blockEntity)) {
            return null;
        }
        try {
            Object inventory = inventoryField.get(blockEntity);
            if (inventory == null) {
                return null;
            }
            Object wrapper = wrapperCtor.newInstance(inventory);
            return wrapper instanceof IItemHandler handler ? handler : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warnOnce("Failed to wrap a simulated auger inventory", ex);
            return null;
        }
    }

    public static boolean isAvailable() {
        ensureInitialized();
        return augerBeClass != null;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (SimulatedAugerAccess.class) {
            if (initialized) {
                return;
            }
            try {
                Class<?> beClass = Class.forName(AUGER_BE);
                Field field = beClass.getField("inventory");
                Constructor<?> ctor = findWrapperConstructor(field.getType());
                if (ctor != null) {
                    augerBeClass = beClass;
                    inventoryField = field;
                    wrapperCtor = ctor;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                warnOnce("Create: Aeronautics auger reflection unavailable — auger cogs cannot be link endpoints",
                        ex);
            }
            initialized = true;
        }
    }

    /**
     * Finds {@code ContainerWrapper}'s single-argument constructor. The parameter is the
     * {@code AbstractContainer} supertype, whose name we deliberately do not hardcode — matching by
     * assignability survives a package move of the interface.
     */
    private static Constructor<?> findWrapperConstructor(Class<?> inventoryType)
            throws ReflectiveOperationException {
        Class<?> wrapperClass = Class.forName(CONTAINER_WRAPPER);
        for (Constructor<?> candidate : wrapperClass.getConstructors()) {
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 1 && params[0].isAssignableFrom(inventoryType)) {
                return candidate;
            }
        }
        warnOnce("simulated ContainerWrapper has no single-argument constructor accepting "
                + inventoryType.getName(), null);
        return null;
    }

    private static void warnOnce(String message, Throwable ex) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        if (ex != null) {
            LOGGER.warn("[inventory_link] {}: {}", message, ex.toString());
        } else {
            LOGGER.warn("[inventory_link] {}", message);
        }
    }
}
