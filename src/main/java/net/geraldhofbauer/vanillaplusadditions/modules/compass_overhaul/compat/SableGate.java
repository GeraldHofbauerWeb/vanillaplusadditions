package net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.compat;

import net.neoforged.fml.ModList;

/**
 * Tells whether Sable is installed, without naming a single Sable type.
 *
 * <p>This separation is not cosmetic. {@link SableOrientation} references Sable classes directly,
 * so touching it at all — even to ask whether Sable is there — would resolve those classes and
 * throw {@code NoClassDefFoundError} on a pack without Sable. The gate therefore lives in its own
 * class, and {@code SableOrientation} is only ever reached from inside an {@code if} guarded by it.
 */
public final class SableGate {

    private static Boolean loaded;

    private SableGate() {
    }

    /**
     * Whether Sable is present in this instance.
     *
     * @return true if the {@code sable} mod is loaded
     */
    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached == null) {
            cached = ModList.get().isLoaded("sable");
            loaded = cached;
        }
        return cached;
    }
}
