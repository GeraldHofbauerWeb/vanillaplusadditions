package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.compat;

import net.neoforged.fml.ModList;

/**
 * Link-safe availability gate for the Overpacked backpack features.
 *
 * <p>This class exists <b>only</b> to answer "are Overpacked and Curios installed?" and deliberately
 * references no Overpacked/Curios types at all. That separation is load-bearing: asking
 * {@link OverpackedGuiBridge} itself would resolve (and therefore link + verify) that class, and the
 * JVM verifier eagerly loads the Overpacked types appearing in its method bodies — which throws
 * {@code NoClassDefFoundError: net/nycto_team/overpacked/menu/GiantBackpackMenu} on a pack without
 * Overpacked, taking the whole mod down during construction. A gate must never live in the same
 * class as the optional-mod code it guards (same pattern as {@code bluemap_signs}).
 */
public final class OverpackedCompat {

    private static final boolean OVERPACKED_LOADED = ModList.get().isLoaded("overpacked");
    static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");

    private OverpackedCompat() {
    }

    /** True when both Overpacked and Curios are present (Overpacked hard-requires Curios). */
    public static boolean isAvailable() {
        return OVERPACKED_LOADED && CURIOS_LOADED;
    }
}
