package net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills.recipe;

import net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills.PathfinderQuillsModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.violetmoon.quark.content.tools.item.PathfindersQuillItem;

/**
 * Feather + Eye of Ender + a biome-appropriate block (see {@link PathfinderQuillRecipes}) →
 * a Pathfinder's Quill pre-targeted at that biome, via Quark's own
 * {@link PathfindersQuillItem#forBiome(String, int)}. Deliberately a plain, non-special
 * {@link CraftingRecipe} (unlike {@code PotionTippedArrowRecipe}, which had to override an
 * already-special vanilla recipe) — real {@link #getIngredients()} and {@link #getResultItem}
 * mean this shows up in the recipe book and JEI without any extra plugin.
 */
public class PathfinderQuillRecipe implements CraftingRecipe {

    private final CraftingBookCategory category;
    private final String biomeId;

    public PathfinderQuillRecipe(CraftingBookCategory category, String biomeId) {
        this.category = category;
        this.biomeId = biomeId;
    }

    public String biomeId() {
        return biomeId;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        PathfinderQuillRecipes.BiomeEntry entry = PathfinderQuillRecipes.BY_BIOME.get(biomeId);
        if (entry == null || input.ingredientCount() != 3) {
            return false;
        }

        boolean hasFeather = false;
        boolean hasEye = false;
        boolean hasBlock = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getCount() != 1) {
                return false;
            }
            if (!hasFeather && stack.is(Items.FEATHER)) {
                hasFeather = true;
            } else if (!hasEye && stack.is(Items.ENDER_EYE)) {
                hasEye = true;
            } else if (!hasBlock && stack.is(entry.block())) {
                hasBlock = true;
            } else {
                return false;
            }
        }
        return hasFeather && hasEye && hasBlock;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        PathfinderQuillRecipes.BiomeEntry entry = PathfinderQuillRecipes.BY_BIOME.get(biomeId);
        return entry == null ? ItemStack.EMPTY : PathfindersQuillItem.forBiome(biomeId, entry.color());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        PathfinderQuillRecipes.BiomeEntry entry = PathfinderQuillRecipes.BY_BIOME.get(biomeId);
        return entry == null ? ItemStack.EMPTY : PathfindersQuillItem.forBiome(biomeId, entry.color());
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        PathfinderQuillRecipes.BiomeEntry entry = PathfinderQuillRecipes.BY_BIOME.get(biomeId);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Items.FEATHER));
        ingredients.add(Ingredient.of(Items.ENDER_EYE));
        ingredients.add(entry == null ? Ingredient.EMPTY : Ingredient.of(entry.block()));
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PathfinderQuillsModule.QUILL_RECIPE_SERIALIZER.get();
    }
}
