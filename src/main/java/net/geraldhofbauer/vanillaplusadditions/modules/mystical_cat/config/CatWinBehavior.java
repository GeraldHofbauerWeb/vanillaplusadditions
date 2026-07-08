package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.config;

/**
 * What a Mystical Cat does after a player wins its mini-game.
 */
public enum CatWinBehavior {
    /** The cat stays but refuses to start a new game until a cooldown elapses. */
    COOLDOWN,
    /** The cat vanishes in a puff of particles (single-use collectible spot). */
    DESPAWN
}
