package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.compat;

import net.minecraft.world.entity.animal.Wolf;
import org.violetmoon.quark.content.mobs.entity.Foxhound;

/**
 * The only class in this mod that names Quark's {@code Foxhound}. Reached exclusively through
 * {@link QuarkMountCompat}, which checks that Quark is installed first.
 */
final class FoxhoundRest {

    private FoxhoundRest() {
    }

    /** Clears the resting flag if this wolf is a foxhound and is currently lying down. */
    static void wake(Wolf wolf) {
        if (wolf instanceof Foxhound foxhound && foxhound.isResting()) {
            foxhound.setResting(false);
        }
    }
}
