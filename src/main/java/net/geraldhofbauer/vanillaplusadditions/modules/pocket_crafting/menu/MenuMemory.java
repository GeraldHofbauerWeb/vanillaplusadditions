package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu;

import javax.annotation.Nullable;

/**
 * Implemented on {@code ServerPlayer} by a mixin: the last menu the player opened, and how.
 *
 * <p>Lives outside the mixin package on purpose - classes in a mixin package must never be
 * referenced directly, and this interface is what the module casts to.</p>
 */
public interface MenuMemory {

    /**
     * {@return how the player's most recently opened menu was opened, or null if none was recorded}
     */
    @Nullable
    RememberedMenu vpaGetLastMenu();
}
