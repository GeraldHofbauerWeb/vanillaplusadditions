package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game;

/**
 * Lifecycle state of a running {@link GameSession}.
 */
public enum GameOutcome {
    /** The game is still in progress. */
    RUNNING,
    /** The player met the win condition. */
    WON,
    /** The player failed (wrong answer, mob died, etc.); timeouts are handled separately. */
    LOST
}
