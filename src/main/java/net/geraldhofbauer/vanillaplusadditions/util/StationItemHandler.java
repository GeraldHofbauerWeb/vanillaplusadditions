package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

/**
 * Outward-facing item handler of a feeding station: hoppers, funnels and pipes may push
 * <em>anything</em> in — food ends up in the food chamber, everything else in the loot storage.
 *
 * <p>Slot layout is food first, loot second (0..foodSlots-1 = food, rest = loot), so an inserter
 * that simply walks the slots already fills the food chamber first. The {@link #insertItem}
 * override covers the case where it does not: {@link ItemHandlerHelper#insertItemStacked} looks
 * for slots holding a <em>matching</em> stack before it looks for empty ones, and fish can
 * legitimately sit in the loot storage (cats drop off fish they looted). Without the redirect,
 * piped-in fish would stack there instead of landing in the food chamber.
 *
 * <p>Food that no longer fits the food chamber overflows into the loot storage rather than backing
 * the pipe up.
 */
public class StationItemHandler extends CombinedInvWrapper {

    private final IItemHandlerModifiable food;
    private final int foodSlots;
    private final boolean allowFoodExtraction;

    /**
     * @param allowFoodExtraction whether automation may pull items back out of the food chamber.
     *                            {@code false} for the bottom face, so a hopper underneath keeps
     *                            draining only loot and XP bottles instead of the station's meals.
     */
    public StationItemHandler(ItemStackHandler food, ItemStackHandler loot, boolean allowFoodExtraction) {
        super(food, loot);
        this.food = food;
        this.foodSlots = food.getSlots();
        this.allowFoodExtraction = allowFoodExtraction;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // Food always tries the food chamber first, no matter which slot the inserter aims at;
        // only the surplus falls through into the loot storage.
        if (slot >= foodSlots && !stack.isEmpty() && food.isItemValid(0, stack)) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(food, stack, simulate);
            if (rest.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return super.insertItem(slot, rest, simulate);
        }
        // Food slots keep filtering through their own isItemValid — non-food bounces off them and
        // the inserter moves on to the loot slots.
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!allowFoodExtraction && slot < foodSlots) {
            return ItemStack.EMPTY;
        }
        return super.extractItem(slot, amount, simulate);
    }
}
