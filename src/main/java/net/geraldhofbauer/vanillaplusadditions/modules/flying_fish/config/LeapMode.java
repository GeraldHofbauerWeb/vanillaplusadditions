package net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.config;

/**
 * Controls when and how the Flying Fish Boots trigger automatic water leaps.
 */
public enum LeapMode {
    /**
     * Default behaviour: automatic leaps when sprinting near the water surface,
     * governed by {@code leap_cooldown_ticks}.
     */
    DEFAULT,

    /**
     * Arcade behaviour: leaps trigger whenever the player touches water while
     * sprinting (not only near the surface), with a shorter effective cooldown
     * (half of {@code leap_cooldown_ticks}).  Produces a fast, bouncy dolphin-like feel.
     */
    ARCADE,

    /**
     * Realistic behaviour: no automatic leaps at all.  Only the horizontal
     * water-skim speed boost is applied, letting the player glide swiftly
     * across the surface without leaving it.
     */
    REALISTIC
}

