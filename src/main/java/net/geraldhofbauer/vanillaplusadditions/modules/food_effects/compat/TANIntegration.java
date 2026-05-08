package net.geraldhofbauer.vanillaplusadditions.modules.food_effects.compat;

import net.minecraft.world.entity.player.Player;
import toughasnails.api.thirst.IThirst;
import toughasnails.api.thirst.ThirstHelper;

/**
 * Compatibility class for Tough As Nails.
 * All direct calls to Tough As Nails API must be placed in this class
 * to avoid ClassNotFoundException when the mod is not present.
 */
public final class TANIntegration {
    private TANIntegration() {
    }

    public static void applyThirst(Player player, int amount) {
        IThirst thirst = ThirstHelper.getThirst(player);
        thirst.setThirst(Math.min(thirst.getThirst() + amount, 20));
    }
}
