package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client.compat;

import net.geraldhofbauer.vanillaplusadditions.core.Vpa;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Maps a link endpoint's box from Sable plot space into the world position where the player
 * actually sees it.
 *
 * <p>Blocks of a physics platform live at plot coordinates around 20.48 million but are
 * <em>rendered</em> at the platform's current pose. Drawing an outline at the raw block position
 * would put it 20 million blocks away — so any endpoint that sits inside a sub level gets its box
 * transformed by that sub level's logical pose first.</p>
 *
 * <p>Client-only (references the level's sub-level container through Sable) and reflection-based,
 * exactly like {@code BlockGlowSableIntegration}: without Sable installed every call is a no-op
 * that returns the box unchanged.</p>
 */
public final class InventoryLinkSableRender {

    private static final String SABLE_MODID = "sable";

    private static volatile SableApi api;
    private static volatile boolean initialized;
    private static volatile boolean warningLogged;

    private InventoryLinkSableRender() {
    }

    /**
     * @param level    the client level the endpoint belongs to
     * @param anchor   any block position inside the endpoint's structure
     * @param plotBox  the endpoint's box in block coordinates
     * @return the box in visible world coordinates; {@code plotBox} itself for ordinary world blocks
     */
    public static AABB toRenderBox(Level level, BlockPos anchor, AABB plotBox) {
        if (level == null || !ModList.get().isLoaded(SABLE_MODID)) {
            return plotBox;
        }
        SableApi reflection = reflection();
        if (reflection == null) {
            return plotBox;
        }
        try {
            Object subLevel = reflection.getContaining.invoke(reflection.helper, level, anchor);
            if (subLevel == null) {
                return plotBox;
            }
            Object pose = reflection.logicalPose.invoke(subLevel);
            // BoundingBox3d.transform mutates in place, so build a fresh one per call.
            Object box = reflection.boundingBoxFromAabb.newInstance(plotBox);
            Object transformed = reflection.transform.invoke(box, pose);
            return (AABB) reflection.toMojang.invoke(transformed);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnOnce("Failed to map an inventory link endpoint out of Sable plot space", ex);
            return plotBox;
        }
    }

    private static SableApi reflection() {
        if (initialized) {
            return api;
        }
        synchronized (InventoryLinkSableRender.class) {
            if (!initialized) {
                try {
                    api = new SableApi();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                    warnOnce("Failed to initialize Sable rendering support for inventory links", ex);
                    api = null;
                }
                initialized = true;
            }
            return api;
        }
    }

    private static void warnOnce(String message, Throwable ex) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        Vpa.LOGGER.warn("{}: {}", message, ex.getMessage());
    }

    private static final class SableApi {
        private final Object helper;
        private final Method getContaining;
        private final Method logicalPose;
        private final Constructor<?> boundingBoxFromAabb;
        private final Method transform;
        private final Method toMojang;

        private SableApi() throws ReflectiveOperationException {
            Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
            Field helperField = sableClass.getField("HELPER");
            this.helper = helperField.get(null);

            this.getContaining = helper.getClass().getMethod("getContaining", Level.class, Vec3i.class);

            Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            this.logicalPose = subLevelClass.getMethod("logicalPose");

            Class<?> boundingBox3dClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d");
            Class<?> boundingBox3dcClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
            Class<?> pose3dcClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
            this.boundingBoxFromAabb = boundingBox3dClass.getConstructor(AABB.class);
            this.transform = boundingBox3dClass.getMethod("transform", pose3dcClass);
            this.toMojang = boundingBox3dcClass.getMethod("toMojang");
        }
    }
}
