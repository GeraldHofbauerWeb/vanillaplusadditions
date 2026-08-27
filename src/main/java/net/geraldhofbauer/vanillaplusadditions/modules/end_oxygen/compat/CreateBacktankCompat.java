package net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.compat;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Optional Create integration for the End Oxygen module: only this class calls
 * {@code BacktankUtil}. Without Create, players simply never have backtank air (empty list / 0 air)
 * and the module falls back to its plain breath-holding behavior. Signatures use only vanilla types,
 * and callers gate on {@link CreateCompat#isLoaded()} — a deliberately Create-free class, so that
 * asking the question never links this one.
 */
public final class CreateBacktankCompat {

    private CreateBacktankCompat() {
    }

    /**
     * All of the player's Create backtanks that still hold air, or an empty list without Create.
     */
    public static List<ItemStack> getBacktanksWithAir(Player player) {
        return CreateCompat.isLoaded() ? BacktankUtil.getAllWithAir(player) : List.of();
    }

    /**
     * Remaining air in the given backtank, or 0 without Create.
     */
    public static int getAir(ItemStack backtank) {
        return CreateCompat.isLoaded() ? BacktankUtil.getAir(backtank) : 0;
    }

    /**
     * Consumes air from the given backtank; no-op without Create.
     */
    public static void consumeAir(Player player, ItemStack backtank, int amount) {
        if (CreateCompat.isLoaded()) {
            BacktankUtil.consumeAir(player, backtank, amount);
        }
    }
}
