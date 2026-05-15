package net.geraldhofbauer.vanillaplusadditions.modules.block_glow.client;

import net.minecraft.world.phys.AABB;

/**
 * A world-space block outline candidate for BlockGlow.
 */
public record BlockGlowHighlight(AABB worldAabb, double distanceSquared) {
}

