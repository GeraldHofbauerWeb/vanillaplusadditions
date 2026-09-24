package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.compat.quark;

import net.neoforged.fml.ModList;

/**
 * Presence gate for the Foxhound armour layer.
 *
 * <p>Deliberately a class of its own, and deliberately free of every Quark type. Merely resolving a
 * static member links the class that holds it, and the verifier then loads the types named in its
 * fields and method signatures - which is how an optional dependency turns into a
 * {@code NoClassDefFoundError} while the mod is still being constructed. Everything that names a
 * Quark type lives in {@link FoxhoundArmorLayers}, which is only ever touched after
 * {@link #isAvailable()} has said yes.</p>
 */
public final class QuarkFoxhoundCompat {

    private QuarkFoxhoundCompat() {
    }

    /**
     * Whether Quark is installed in this instance.
     *
     * @return true if Quark is loaded and its Foxhound types may be resolved
     */
    public static boolean isAvailable() {
        return ModList.get().isLoaded("quark");
    }
}
