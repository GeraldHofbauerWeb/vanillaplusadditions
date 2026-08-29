package net.geraldhofbauer.vanillaplusadditions.modules.tipped_arrows.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows the potion-based tipped arrow recipe in JEI. The real recipe
 * ({@code PotionTippedArrowRecipe}) is a special recipe like vanilla's (empty
 * {@code getResultItem}), so JEI cannot auto-discover it — one plain display-only
 * {@link ShapedRecipe} per registered potion is generated instead, never registered with the
 * {@code RecipeManager}. The center slot accepts either a normal or a lingering potion, matching
 * what the real recipe allows.
 */
@JeiPlugin
public class TippedArrowsJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "tipped_arrows_jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!ModuleManager.getInstance().isModuleEnabled("tipped_arrows")) {
            return;
        }

        List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();
        for (Potion potion : BuiltInRegistries.POTION) {
            ResourceLocation potionId = BuiltInRegistries.POTION.getKey(potion);
            if (potionId == null) {
                continue;
            }
            Holder<Potion> potionHolder = BuiltInRegistries.POTION.wrapAsHolder(potion);

            ItemStack potionIngredientStack = PotionContents.createItemStack(Items.POTION, potionHolder);
            ItemStack lingeringIngredientStack =
                    PotionContents.createItemStack(Items.LINGERING_POTION, potionHolder);
            Ingredient centerIngredient = Ingredient.of(potionIngredientStack, lingeringIngredientStack);

            ItemStack result = PotionContents.createItemStack(Items.TIPPED_ARROW, potionHolder);
            result.setCount(8);

            Map<Character, Ingredient> key = new LinkedHashMap<>();
            key.put('A', Ingredient.of(Items.ARROW));
            key.put('P', centerIngredient);
            ShapedRecipePattern pattern = ShapedRecipePattern.of(key, List.of("AAA", "APA", "AAA"));
            ShapedRecipe displayRecipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, result);

            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID, "tipped_arrow_display/" + potionId.getPath());
            recipes.add(new RecipeHolder<>(recipeId, displayRecipe));
        }

        registration.addRecipes(RecipeTypes.CRAFTING, recipes);
    }
}
