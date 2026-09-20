package net.geraldhofbauer.vanillaplusadditions.modules.freecam_sublevel_noclip.compat;

import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Everything this module needs to know about Freecam, asked entirely through reflection.
 *
 * <p>Freecam is not a compile dependency and is not in {@code libs/}, so no type of it may appear in
 * a signature here — see the note on optional-mod gates in {@code docs/guides/module-system.md}.
 * Reflection also survives the mod being updated: a renamed field costs the feature, not the game.
 *
 * <p>Two questions are asked, and both mirror Freecam's own {@code CollisionBehavior.isIgnored}:
 * whether an entity is the camera, and whether the user has asked it to ignore every block. If the
 * user instead ignores only <em>some</em> blocks (transparent ones, doors, a custom list), this
 * module stays out of the way entirely — {@code noPhysics} is all or nothing and cannot reproduce a
 * per-block rule.
 */
public final class FreecamAccess {

    private static final String MOD_ID = "freecam";
    private static final String CAMERA_CLASS = "net.xolt.freecam.util.FreeCamera";
    private static final String CONFIG_CLASS = "net.xolt.freecam.config.ModConfig";
    private static final String VARIANT_CLASS = "net.xolt.freecam.variant.api.BuildVariant";

    private static Boolean loaded;
    private static boolean lookupFailed;
    private static Field instanceField;
    private static Field collisionField;
    private static Field ignoreAllField;
    private static Method variantInstance;
    private static Method cheatsPermitted;

    private FreecamAccess() {
    }

    /**
     * Whether Freecam is present in this instance.
     *
     * @return true if the {@code freecam} mod is loaded
     */
    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached == null) {
            cached = ModList.get().isLoaded(MOD_ID);
            loaded = cached;
        }
        return cached;
    }

    /**
     * Whether this entity is Freecam's camera.
     *
     * @param entity the entity to test, may be null
     * @return true for an instance of Freecam's {@code FreeCamera}
     */
    public static boolean isCamera(Entity entity) {
        return entity != null && CAMERA_CLASS.equals(entity.getClass().getName());
    }

    /**
     * Whether Freecam is currently set to ignore every block.
     *
     * <p>This is the first branch of Freecam's own {@code CollisionBehavior.isIgnored}, including the
     * build-variant check: a variant that forbids cheats refuses to pass through blocks at all, and
     * this module must not quietly hand that back.
     *
     * @return true when the camera is meant to pass through everything
     */
    public static boolean ignoresAllBlocks() {
        if (!resolve()) {
            return false;
        }
        try {
            Object config = instanceField.get(null);
            if (config == null) {
                return false;
            }
            Object collision = collisionField.get(config);
            return collision != null
                    && ignoreAllField.getBoolean(collision)
                    && (boolean) cheatsPermitted.invoke(variantInstance.invoke(null));
        } catch (ReflectiveOperationException | ClassCastException e) {
            lookupFailed = true;
            return false;
        }
    }

    private static boolean resolve() {
        if (lookupFailed) {
            return false;
        }
        if (instanceField != null) {
            return true;
        }
        try {
            Class<?> config = Class.forName(CONFIG_CLASS);
            instanceField = config.getField("INSTANCE");
            collisionField = config.getField("collision");
            ignoreAllField = collisionField.getType().getField("ignoreAll");
            Class<?> variant = Class.forName(VARIANT_CLASS);
            variantInstance = variant.getMethod("getInstance");
            cheatsPermitted = variant.getMethod("cheatsPermitted");
            return true;
        } catch (ReflectiveOperationException e) {
            lookupFailed = true;
            instanceField = null;
            return false;
        }
    }
}
