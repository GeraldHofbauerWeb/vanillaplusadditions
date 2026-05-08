package net.geraldhofbauer.vanillaplusadditions.modules.food_effects.client;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Data for the thirst tooltip.
 */
public record ThirstTooltipData(int amount, float chance) implements TooltipComponent {
}
