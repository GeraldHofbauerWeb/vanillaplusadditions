package net.geraldhofbauer.vanillaplusadditions.modules.tipped_arrows.recipe;

import net.geraldhofbauer.vanillaplusadditions.modules.tipped_arrows.TippedArrowsModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Replaces vanilla's {@code TippedArrowRecipe}: same 3x3 layout (8 arrows around a center potion,
 * 8 tipped arrows out, potion effect copied via {@link DataComponents#POTION_CONTENTS}), but the
 * center slot also accepts a normal {@link Items#POTION} in addition to
 * {@link Items#LINGERING_POTION} — so tipped arrows no longer require Dragon's Breath.
 */
public class PotionTippedArrowRecipe extends CustomRecipe {

    public PotionTippedArrowRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        for (int i = 0; i < input.height(); i++) {
            for (int j = 0; j < input.width(); j++) {
                ItemStack itemstack = input.getItem(j, i);
                if (itemstack.isEmpty()) {
                    return false;
                }

                if (j == 1 && i == 1) {
                    if (!isPotionSource(itemstack)) {
                        return false;
                    }
                } else if (!itemstack.is(Items.ARROW)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack itemstack = input.getItem(1, 1);
        if (!isPotionSource(itemstack)) {
            return ItemStack.EMPTY;
        }

        ItemStack result = new ItemStack(Items.TIPPED_ARROW, 8);
        result.set(DataComponents.POTION_CONTENTS, itemstack.get(DataComponents.POTION_CONTENTS));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TippedArrowsModule.TIPPED_ARROW_RECIPE_SERIALIZER.get();
    }

    private static boolean isPotionSource(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.LINGERING_POTION);
    }
}
