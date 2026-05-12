package net.geraldhofbauer.vanillaplusadditions.modules.custom_crafting_recipes.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.custom_crafting_recipes.CustomCraftingRecipesModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class CustomCraftingRecipesConfig
        extends AbstractModuleConfig<CustomCraftingRecipesModule, CustomCraftingRecipesConfig> {

    private static final String SAMPLE_RECIPE =
            "vanillaplusadditions:giant_backpack;overpacked:giant_backpack;1;CDC|ABA|AAA;"
                    + "A=minecraft:leather,B=create:item_vault,C=minecraft:string,D=create:andesite_alloy";

    private static final String SAMPLE_SHAPELESS_RECIPE =
            "minecraft:leather,minecraft:string->minecraft:bundle;1";

    private static final List<String> DEFAULT_RECIPES = List.of(SAMPLE_RECIPE);

    private static final List<String> DEFAULT_SHAPELESS_RECIPES = List.of(SAMPLE_SHAPELESS_RECIPE);

    private ModConfigSpec.ConfigValue<List<? extends String>> recipeDefinitions;
    private ModConfigSpec.ConfigValue<List<? extends String>> shapelessRecipeDefinitions;

    public CustomCraftingRecipesConfig(CustomCraftingRecipesModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        recipeDefinitions = builder
                .comment("List of custom SHAPED recipes.",
                        "Format: recipe_id;result_item;result_count;pattern;keys",
                        "Pattern supports:",
                        "  - AAA|BBB|AAA",
                        "  - \"AAA\" \"BBB\" \"AAA\"",
                        "Keys format: A=minecraft:green_wool,B=minecraft:chest",
                        "Ingredients can also use tags, e.g. A=#minecraft:planks",
                        "Example: " + SAMPLE_RECIPE)
                .defineList("recipes", DEFAULT_RECIPES, () -> SAMPLE_RECIPE,
                        o -> o instanceof String s && isValidShapedRecipeEntry(s));

        shapelessRecipeDefinitions = builder
                .comment("List of custom SHAPELESS recipes.",
                        "Format: ingredient1,ingredient2,...->result_item[;result_count[;recipe_id]]",
                        "Ingredients can use item IDs or tags (prefix with #)",
                        "Example: " + SAMPLE_SHAPELESS_RECIPE,
                        "Short form: minecraft:leather,minecraft:string->minecraft:bundle")
                .defineList("shapeless_recipes", DEFAULT_SHAPELESS_RECIPES,
                        () -> SAMPLE_SHAPELESS_RECIPE,
                        o -> o instanceof String s && isValidShapelessRecipeEntry(s));
    }

    private boolean isValidShapedRecipeEntry(String entry) {
        String[] parts = entry.split(";", 5);
        if (parts.length != 5) {
            return false;
        }

        try {
            ResourceLocation.parse(parts[0].trim());
            ResourceLocation.parse(parts[1].trim());
            int count = Integer.parseInt(parts[2].trim());
            if (count < 1 || count > 64) {
                return false;
            }
            return !parts[3].trim().isEmpty() && !parts[4].trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidShapelessRecipeEntry(String entry) {
        String[] arrowParts = entry.split("->", 2);
        if (arrowParts.length != 2) {
            return false;
        }

        if (arrowParts[0].trim().isEmpty()) {
            return false;
        }

        String[] resultParts = arrowParts[1].trim().split(";", 2);
        try {
            ResourceLocation.parse(resultParts[0].trim());
            if (resultParts.length == 2) {
                String[] countAndId = resultParts[1].trim().split(";", 2);
                int count = Integer.parseInt(countAndId[0].trim());
                if (count < 1 || count > 64) {
                    return false;
                }
                if (countAndId.length == 2) {
                    ResourceLocation.parse(countAndId[1].trim());
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getRecipeDefinitions() {
        return recipeDefinitions != null
                ? new ArrayList<>(recipeDefinitions.get())
                : new ArrayList<>(DEFAULT_RECIPES);
    }

    public List<String> getShapelessRecipeDefinitions() {
        return shapelessRecipeDefinitions != null
                ? new ArrayList<>(shapelessRecipeDefinitions.get())
                : new ArrayList<>(DEFAULT_SHAPELESS_RECIPES);
    }
}

