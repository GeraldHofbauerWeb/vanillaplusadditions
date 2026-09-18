package net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard.compat;

import net.neoforged.fml.ModList;

/**
 * Tells whether Curios is installed, without naming a single Curios type — see
 * {@link GliderCuriosAccess} for why that separation matters.
 */
public final class CuriosGate {

    private static Boolean loaded;

    private CuriosGate() {
    }

    /**
     * Whether Curios is present in this instance.
     *
     * @return true if the {@code curios} mod is loaded
     */
    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached == null) {
            cached = ModList.get().isLoaded("curios");
            loaded = cached;
        }
        return cached;
    }
}
