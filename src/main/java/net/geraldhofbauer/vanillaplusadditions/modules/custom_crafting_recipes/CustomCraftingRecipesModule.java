package net.geraldhofbauer.vanillaplusadditions.modules.custom_crafting_recipes;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.custom_crafting_recipes.config.CustomCraftingRecipesConfig;
import net.geraldhofbauer.vanillaplusadditions.util.ConfiguredRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CustomCraftingRecipesModule
        extends AbstractModule<CustomCraftingRecipesModule, CustomCraftingRecipesConfig> {

    public CustomCraftingRecipesModule() {
        super("custom_crafting_recipes",
                "Custom Crafting Recipes",
                "Adds configurable shaped and shapeless crafting recipes from the module config.",
                CustomCraftingRecipesConfig::new);
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
    }

    // TODO: Migrate from code-injected recipes to proper JSON datapack recipes
    //       (data/<ns>/recipe/*.json — singular folder since MC 1.21). This RecipeManager
    //       injection is a workaround because the old plural recipes/ folder never loaded.
    //       See docs/modules/custom_crafting_recipes.md ("TODO / Roadmap") for the correct format.
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        event.addListener(new CustomRecipeReloadListener(event.getServerResources().getRecipeManager()));
    }

    private void applyConfiguredRecipes(RecipeManager recipeManager) {
        List<RecipeHolder<?>> configuredRecipes = parseConfiguredRecipes();
        if (configuredRecipes.isEmpty()) {
            if (getConfig().shouldDebugLog()) {
                getLogger().debug("No custom crafting recipes configured.");
            }
            return;
        }

        Map<ResourceLocation, RecipeHolder<?>> mergedRecipes = new LinkedHashMap<>();
        Collection<RecipeHolder<?>> existingRecipes = recipeManager.getRecipes();
        for (RecipeHolder<?> recipeHolder : existingRecipes) {
            mergedRecipes.put(recipeHolder.id(), recipeHolder);
        }

        int added = 0;
        int replaced = 0;
        for (RecipeHolder<?> configuredRecipe : configuredRecipes) {
            if (mergedRecipes.put(configuredRecipe.id(), configuredRecipe) == null) {
                added++;
            } else {
                replaced++;
            }
        }

        recipeManager.replaceRecipes(mergedRecipes.values());
        getLogger().info("Applied {} custom recipes ({} added, {} replaced).",
                configuredRecipes.size(), added, replaced);
    }

    private List<RecipeHolder<?>> parseConfiguredRecipes() {
        List<RecipeHolder<?>> parsedRecipes = new ArrayList<>();
        Set<ResourceLocation> seenRecipeIds = new LinkedHashSet<>();
        // Namespaces we skipped because their mod is not installed — summarized as one INFO line at
        // the end, so a typo'd namespace stays visible without an ERROR per absent-mod recipe.
        Set<String> skippedMods = new LinkedHashSet<>();

        // Parse shaped recipes
        for (String entry : getConfig().getRecipeDefinitions()) {
            try {
                ConfiguredRecipes.Shaped definition = ConfiguredRecipes.Shaped.parse(entry);
                RecipeHolder<ShapedRecipe> recipeHolder = definition.toRecipeHolder();

                if (!seenRecipeIds.add(definition.recipeId())) {
                    getLogger().warn("Duplicate custom recipe id in config. Last one wins: {}", definition.recipeId());
                }

                parsedRecipes.removeIf(existing -> existing.id().equals(definition.recipeId()));
                parsedRecipes.add(recipeHolder);
            } catch (ConfiguredRecipes.MissingModException exception) {
                // A recipe extension for a mod this pack does not have — expected, not a defect.
                skippedMods.add(exception.namespace());
                getLogger().debug("Skipping custom crafting recipe (shaped), {}: {}",
                        exception.getMessage(), entry);
            } catch (IllegalArgumentException exception) {
                getLogger().error("Invalid custom crafting recipe definition (shaped): {}", entry);
                getLogger().error("Reason: {}", exception.getMessage());
            } catch (Exception exception) {
                getLogger().error("Failed to parse custom crafting recipe (shaped): {}", entry, exception);
            }
        }

        // Parse shapeless recipes
        for (String entry : getConfig().getShapelessRecipeDefinitions()) {
            try {
                ShapelessRecipeDefinition definition = ShapelessRecipeDefinition.parse(entry);
                RecipeHolder<ShapelessRecipe> recipeHolder = createShapelessRecipe(definition);

                if (!seenRecipeIds.add(definition.recipeId())) {
                    getLogger().warn("Duplicate custom recipe id in config. Last one wins: {}", definition.recipeId());
                }

                parsedRecipes.removeIf(existing -> existing.id().equals(definition.recipeId()));
                parsedRecipes.add(recipeHolder);
            } catch (ConfiguredRecipes.MissingModException exception) {
                skippedMods.add(exception.namespace());
                getLogger().debug("Skipping custom crafting recipe (shapeless), {}: {}",
                        exception.getMessage(), entry);
            } catch (IllegalArgumentException exception) {
                getLogger().error("Invalid custom crafting recipe definition (shapeless): {}", entry);
                getLogger().error("Reason: {}", exception.getMessage());
            } catch (Exception exception) {
                getLogger().error("Failed to parse custom crafting recipe (shapeless): {}", entry, exception);
            }
        }

        if (!skippedMods.isEmpty()) {
            getLogger().info("Skipped custom crafting recipes for mods that are not installed: {}",
                    String.join(", ", skippedMods));
        }

        return parsedRecipes;
    }

    private RecipeHolder<ShapelessRecipe> createShapelessRecipe(ShapelessRecipeDefinition definition) {
        Item resultItem = BuiltInRegistries.ITEM.get(definition.resultItemId());
        if (resultItem == Items.AIR) {
            throw ConfiguredRecipes.unknownItem("result item", definition.resultItemId());
        }

        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (String ingredientSpec : definition.ingredients()) {
            ingredients.add(ConfiguredRecipes.ingredientFromString(ingredientSpec));
        }

        ItemStack result = new ItemStack(resultItem, definition.resultCount());
        ShapelessRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, result, ingredients);
        return new RecipeHolder<>(definition.recipeId(), recipe);
    }

    private final class CustomRecipeReloadListener implements PreparableReloadListener {
        private final RecipeManager recipeManager;

        private CustomRecipeReloadListener(RecipeManager recipeManager) {
            this.recipeManager = recipeManager;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier,
                                              ResourceManager resourceManager,
                                              ProfilerFiller preparationsProfiler,
                                              ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor,
                                              Executor gameExecutor) {
            return preparationBarrier.wait(Unit.INSTANCE)
                    .thenRunAsync(() -> applyConfiguredRecipes(recipeManager), gameExecutor);
        }

        @Override
        public String getName() {
            return "vanillaplusadditions_custom_crafting_recipes";
        }
    }

    private record ShapelessRecipeDefinition(ResourceLocation recipeId,
                                             ResourceLocation resultItemId,
                                             int resultCount,
                                             List<String> ingredients) {

        private static ShapelessRecipeDefinition parse(String entry) {
            String[] arrowParts = entry.split("->", 2);
            if (arrowParts.length != 2) {
                throw new IllegalArgumentException("Expected format: ingredient1,ingredient2,...->result_item;result_count");
            }

            String ingredientsPart = arrowParts[0].trim();
            String resultPart = arrowParts[1].trim();

            String[] resultParts = resultPart.split(";", 2);
            if (resultParts.length < 1) {
                throw new IllegalArgumentException("Result part must contain at least result_item;result_count");
            }

            ResourceLocation resultItemId = ResourceLocation.parse(resultParts[0].trim());
            int resultCount = 1;
            ResourceLocation recipeId;

            if (resultParts.length == 2) {
                String[] countAndId = resultParts[1].trim().split(";", 2);
                resultCount = Integer.parseInt(countAndId[0].trim());
                recipeId = countAndId.length > 1
                        ? ResourceLocation.parse(countAndId[1].trim())
                        : ResourceLocation.withDefaultNamespace("shapeless_" + resultItemId.getPath());
            } else {
                recipeId = ResourceLocation.withDefaultNamespace("shapeless_" + resultItemId.getPath());
            }

            if (resultCount < 1 || resultCount > 64) {
                throw new IllegalArgumentException("result_count must be between 1 and 64");
            }

            List<String> ingredients = new ArrayList<>();
            for (String ingredient : ingredientsPart.split(",")) {
                ingredients.add(ingredient.trim());
            }

            if (ingredients.isEmpty()) {
                throw new IllegalArgumentException("At least one ingredient is required");
            }

            return new ShapelessRecipeDefinition(recipeId, resultItemId, resultCount, ingredients);
        }
    }
}
