package net.geraldhofbauer.vanillaplusadditions.modules.pet_potions.models;

import java.util.UUID;

/**
 * A short grace period during which a pet must not take a specific player as its target.
 *
 * <p>Set right after a thrown potion calmed the animal down. Without it a goal that already holds a
 * stale reference (or a third-party AI mod re-running its own targeting) would simply re-acquire the
 * thrower on the very next tick.
 *
 * @param player    UUID of the player who is protected from being targeted
 * @param expiresAt game time (see {@code Level#getGameTime()}) at which the protection ends
 */
public record PeaceEntry(UUID player, long expiresAt) {
}
