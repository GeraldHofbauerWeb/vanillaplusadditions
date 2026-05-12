package net.geraldhofbauer.vanillaplusadditions.modules.custom_crafting_recipes;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.custom_crafting_recipes.config.CustomCraftingRecipesConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.item.crafting.ShapedRecipePattern;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomCraftingRecipesModule
        extends AbstractModule<CustomCraftingRecipesModule, CustomCraftingRecipesConfig> {

    private static final Pattern QUOTED_PATTERN = Pattern.compile("\"([^\"]+)\"");

    public CustomCraftingRecipesModule() {
        super("custom_crafting_recipes",
                "Custom Crafting Recipes",
                "Adds configurable shaped crafting recipes from the module config.",
                CustomCraftingRecipesConfig::new);
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
    }

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

        // Parse shaped recipes
        for (String entry : getConfig().getRecipeDefinitions()) {
            try {
                CustomRecipeDefinition definition = CustomRecipeDefinition.parse(entry);
                RecipeHolder<ShapedRecipe> recipeHolder = createShapedRecipe(definition);

                if (!seenRecipeIds.add(definition.recipeId())) {
                    getLogger().warn("Duplicate custom recipe id in config. Last one wins: {}", definition.recipeId());
                }

                parsedRecipes.removeIf(existing -> existing.id().equals(definition.recipeId()));
                parsedRecipes.add(recipeHolder);
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
            } catch (IllegalArgumentException exception) {
                getLogger().error("Invalid custom crafting recipe definition (shapeless): {}", entry);
                getLogger().error("Reason: {}", exception.getMessage());
            } catch (Exception exception) {
                getLogger().error("Failed to parse custom crafting recipe (shapeless): {}", entry, exception);
            }
        }

        return parsedRecipes;
    }

    private RecipeHolder<ShapedRecipe> createShapedRecipe(CustomRecipeDefinition definition) {
        Item resultItem = BuiltInRegistries.ITEM.get(definition.resultItemId());
        if (resultItem == Items.AIR) {
            throw new IllegalArgumentException("Unknown result item: " + definition.resultItemId());
        }

        Map<Character, Ingredient> key = new LinkedHashMap<>();
        for (Map.Entry<Character, String> keyEntry : definition.keys().entrySet()) {
            key.put(keyEntry.getKey(), ingredientFromString(keyEntry.getValue()));
        }

        ShapedRecipePattern shapedPattern = ShapedRecipePattern.of(key, definition.patternRows());
        ItemStack result = new ItemStack(resultItem, definition.resultCount());
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, shapedPattern, result);
        return new RecipeHolder<>(definition.recipeId(), recipe);
    }

    private RecipeHolder<ShapelessRecipe> createShapelessRecipe(ShapelessRecipeDefinition definition) {
        Item resultItem = BuiltInRegistries.ITEM.get(definition.resultItemId());
        if (resultItem == Items.AIR) {
            throw new IllegalArgumentException("Unknown result item: " + definition.resultItemId());
        }

        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (String ingredientSpec : definition.ingredients()) {
            ingredients.add(ingredientFromString(ingredientSpec));
        }

        ItemStack result = new ItemStack(resultItem, definition.resultCount());
        ShapelessRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, result, ingredients);
        return new RecipeHolder<>(definition.recipeId(), recipe);
    }

    private Ingredient ingredientFromString(String ingredientString) {
        if (ingredientString.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.parse(ingredientString.substring(1));
            return Ingredient.of(TagKey.create(Registries.ITEM, tagId));
        }

        ResourceLocation itemId = ResourceLocation.parse(ingredientString);
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            throw new IllegalArgumentException("Unknown ingredient item: " + itemId);
        }

        return Ingredient.of(item);
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

    private record CustomRecipeDefinition(ResourceLocation recipeId,
                                          ResourceLocation resultItemId,
                                          int resultCount,
                                          List<String> patternRows,
                                          Map<Character, String> keys) {

        private static CustomRecipeDefinition parse(String entry) {
            String[] parts = entry.split(";", 5);
            if (parts.length != 5) {
                throw new IllegalArgumentException(
                        "Expected 5 parts: recipe_id;result_item;result_count;pattern;keys");
            }

            ResourceLocation recipeId = ResourceLocation.parse(parts[0].trim());
            ResourceLocation resultItemId = ResourceLocation.parse(parts[1].trim());
            int resultCount = Integer.parseInt(parts[2].trim());
            if (resultCount < 1 || resultCount > 64) {
                throw new IllegalArgumentException("result_count must be between 1 and 64");
            }

            List<String> patternRows = parsePatternRows(parts[3].trim());
            if (patternRows.isEmpty()) {
                throw new IllegalArgumentException("pattern must define at least one row");
            }

            Map<Character, String> keys = parseKeys(parts[4].trim());
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("keys must define at least one symbol");
            }

            return new CustomRecipeDefinition(recipeId, resultItemId, resultCount, patternRows, keys);
        }

        private static List<String> parsePatternRows(String patternSpec) {
            List<String> rows = new ArrayList<>();
            Matcher matcher = QUOTED_PATTERN.matcher(patternSpec);
            while (matcher.find()) {
                rows.add(matcher.group(1));
            }

            if (!rows.isEmpty()) {
                return rows;
            }

            String[] splitRows = patternSpec.contains("|")
                    ? patternSpec.split("\\|")
                    : patternSpec.split(",");

            for (String row : splitRows) {
                rows.add(row.trim());
            }

            return rows;
        }

        private static Map<Character, String> parseKeys(String keysSpec) {
            Map<Character, String> keys = new LinkedHashMap<>();
            String[] assignments = keysSpec.split(",");

            for (String assignment : assignments) {
                String[] pair = assignment.trim().split("=", 2);
                if (pair.length != 2) {
                    throw new IllegalArgumentException("Invalid key assignment: " + assignment);
                }

                String symbolText = pair[0].trim();
                if (symbolText.length() != 1) {
                    throw new IllegalArgumentException("Key symbol must be exactly one character: " + symbolText);
                }

                char symbol = symbolText.charAt(0);
                if (symbol == ' ') {
                    throw new IllegalArgumentException("Space cannot be used as a key symbol");
                }

                String ingredientText = pair[1].trim();
                if (ingredientText.startsWith("#")) {
                    ResourceLocation.parse(ingredientText.substring(1));
                    keys.put(symbol, ingredientText);
                } else {
                    keys.put(symbol, ResourceLocation.parse(ingredientText).toString());
                }
            }

            return keys;
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

