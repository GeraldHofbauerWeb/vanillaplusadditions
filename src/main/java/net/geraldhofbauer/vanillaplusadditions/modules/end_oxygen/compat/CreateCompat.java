package net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.compat;

import net.neoforged.fml.ModList;

/**
 * Link-safe Create gate for the End Oxygen module. References no Create types on purpose: asking
 * {@link CreateBacktankCompat} itself would resolve — and therefore link and verify — the class that
 * holds the {@code BacktankUtil} calls, and the JVM verifier may eagerly load Create types appearing
 * in a verified method. A gate must never live in the same class as the optional-mod code it guards
 * (see {@code overpacked_extensions}, where exactly that took the whole mod down).
 */
public final class CreateCompat {

    private static final boolean CREATE_LOADED = ModList.get().isLoaded("create");

    private CreateCompat() {
    }

    /** Whether Create is present at all. */
    public static boolean isLoaded() {
        return CREATE_LOADED;
    }
}
