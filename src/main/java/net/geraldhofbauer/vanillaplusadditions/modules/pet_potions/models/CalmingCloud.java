package net.geraldhofbauer.vanillaplusadditions.modules.pet_potions.models;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * A tracked lingering-potion cloud that carries at least one calming effect.
 *
 * <p>Lingering potions keep applying their effects for up to half a minute, so a pet that only walks
 * into the cloud later must be calmed too. The dimension is stored alongside the thrower so the
 * periodic sweep can resolve the cloud with a single {@code ServerLevel#getEntity(UUID)} lookup
 * instead of scanning every loaded level.
 *
 * @param dimension the level the cloud was spawned in
 * @param thrower   UUID of the player who threw the lingering potion
 */
public record CalmingCloud(ResourceKey<Level> dimension, UUID thrower) {
}
