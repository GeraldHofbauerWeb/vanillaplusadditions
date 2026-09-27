package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.compat;

import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.fml.ModList;

/**
 * The gate in front of {@link FoxhoundRest}.
 *
 * <p>Deliberately mentions no Quark type of its own: a class is verified when it is loaded, and a
 * method signature naming a class from a mod that is not installed brings the whole thing down with a
 * {@code NoClassDefFoundError} — the trap that cost a crash in beta.70. Everything that actually
 * names {@code Foxhound} lives in {@link FoxhoundRest}, which is only ever touched once the check
 * below has passed.
 */
public final class QuarkMountCompat {

    private static final boolean QUARK_LOADED = ModList.get().isLoaded("quark");

    private QuarkMountCompat() {
    }

    /**
     * Wakes a foxhound that is being ridden.
     *
     * <p>Quark's Foxhound lies down near a heat source and remembers it in a synced {@code IS_RESTING}
     * flag. Its model reads that flag twice: {@code prepareMobModel} poses the head and body for lying
     * down, and {@code setupAnim} then takes the branch that ignores the look direction entirely and
     * wobbles the head on a timer instead. A ridden foxhound therefore stared in a fixed direction
     * while its legs kept swinging from the walk animation — straight through the posed-down head.
     *
     * <p>The flag is never cleared by itself here, because the goal that would clear it does not run
     * while a passenger is in control.
     *
     * @param wolf the mount, which may or may not be a foxhound
     */
    public static void wakeIfFoxhound(Wolf wolf) {
        if (QUARK_LOADED) {
            FoxhoundRest.wake(wolf);
        }
    }
}
