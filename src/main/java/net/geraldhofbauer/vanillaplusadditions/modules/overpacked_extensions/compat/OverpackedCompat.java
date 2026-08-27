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

    /** Overpacked 2.0 added {@code GiantBackpack.Load}/{@code SetName} and the side-pocket unlocks. */
    private static final int V2_MAJOR = 2;

    private static final boolean OVERPACKED_LOADED = ModList.get().isLoaded("overpacked");

    /**
     * True when the installed Overpacked is 2.x or newer, which decides how the bridge restores the
     * worn backpack onto its helper entity: 2.x has {@code GiantBackpack.Load} / {@code SetName} and
     * stores the side-pocket unlocks on the item, 1.x has neither and needs the older hand-restore.
     *
     * <p>Both paths live in the same method, compiled against 2.x. That is safe because a method
     * reference is resolved when it first <em>executes</em>, not when the class is verified — the 2.x
     * calls are never reached on 1.x, so they never resolve and never throw {@code NoSuchMethodError}.
     */
    private static final boolean OVERPACKED_V2 = OVERPACKED_LOADED && ModList.get()
            .getModContainerById("overpacked")
            .map(container -> container.getModInfo().getVersion().getMajorVersion() >= V2_MAJOR)
            .orElse(false);

    static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");

    private OverpackedCompat() {
    }

    /**
     * True when Overpacked and Curios are both present — Overpacked hard-requires Curios, and since
     * 2.x also Bobo Lib, which it brings along itself. Both Overpacked generations are supported.
     */
    public static boolean isAvailable() {
        return OVERPACKED_LOADED && CURIOS_LOADED;
    }

    /** True when the installed Overpacked is 2.x or newer. See {@link #OVERPACKED_V2}. */
    public static boolean isV2() {
        return OVERPACKED_V2;
    }
}
