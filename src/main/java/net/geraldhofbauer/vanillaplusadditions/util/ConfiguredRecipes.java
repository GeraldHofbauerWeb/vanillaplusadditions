package net.geraldhofbauer.vanillaplusadditions.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses crafting recipes written as config strings, in the one format the whole mod uses:
 * {@code recipe_id;result_item;result_count;pattern;keys}.
 *
 * <p>This lived inside {@code CustomCraftingRecipesModule} until {@code special_gemstones} needed
 * the same format for its own two recipes. There is deliberately only ONE parser and ONE documented
 * format — a second dialect for the same job would be a second place for typos to mean something
 * different. The format is described in {@code docs/modules/custom_crafting_recipes.md}; the two
 * modules differ only in where the strings come from (a config list there, two single keys here).
 */
public final class ConfiguredRecipes {

    private static final Pattern QUOTED_PATTERN = Pattern.compile("\"([^\"]+)\"");

    private ConfiguredRecipes() {
    }

    /**
     * Resolves one ingredient spec — an item id, or a tag when prefixed with {@code #}.
     *
     * @throws IllegalArgumentException when the item does not exist (a {@link MissingModException}
     *                                  when its mod is merely absent)
     */
    public static Ingredient ingredientFromString(String ingredientString) {
        if (ingredientString.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.parse(ingredientString.substring(1));
            return Ingredient.of(TagKey.create(Registries.ITEM, tagId));
        }

        ResourceLocation itemId = ResourceLocation.parse(ingredientString);
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            throw unknownItem("ingredient item", itemId);
        }

        return Ingredient.of(item);
    }

    /**
     * An unknown item id: a {@link MissingModException} when it belongs to a mod that simply is not
     * installed (the pack dropped Overpacked, Create, …), a plain {@link IllegalArgumentException}
     * when the mod IS there and the id is genuinely wrong. Only the latter deserves an ERROR — the
     * former is the documented way recipe extensions for other mods go inert.
     */
    public static IllegalArgumentException unknownItem(String what, ResourceLocation itemId) {
        if (isModAbsent(itemId)) {
            return new MissingModException(itemId.getNamespace());
        }
        return new IllegalArgumentException("Unknown " + what + ": " + itemId);
    }

    /** True when the id's namespace names a mod that is not loaded. */
    private static boolean isModAbsent(ResourceLocation itemId) {
        String namespace = itemId.getNamespace();
        if ("minecraft".equals(namespace) || "neoforge".equals(namespace)) {
            return false;
        }
        return !ModList.get().isLoaded(namespace);
    }

    /** Marks a recipe that references a mod this installation does not have. */
    public static final class MissingModException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final String namespace;

        private MissingModException(String namespace) {
            super("mod '" + namespace + "' not installed");
            this.namespace = namespace;
        }

        public String namespace() {
            return namespace;
        }
    }

    /** One parsed shaped recipe, ready to be turned into a {@link RecipeHolder}. */
    public record Shaped(ResourceLocation recipeId,
                         ResourceLocation resultItemId,
                         int resultCount,
                         List<String> patternRows,
                         Map<Character, String> keys) {

        /**
         * Parses {@code recipe_id;result_item;result_count;pattern;keys}. The pattern accepts either
         * {@code "AAA" "BBB"} (quoted, keeps leading spaces) or {@code AAA|BBB} / {@code AAA,BBB}.
         */
        public static Shaped parse(String entry) {
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

            return new Shaped(recipeId, resultItemId, resultCount, patternRows, keys);
        }

        /** Builds the recipe. Throws the same way {@link #parse(String)} does for unknown items. */
        public RecipeHolder<ShapedRecipe> toRecipeHolder() {
            Item item = BuiltInRegistries.ITEM.get(resultItemId);
            if (item == Items.AIR) {
                throw unknownItem("result item", resultItemId);
            }

            Map<Character, Ingredient> ingredientsByKey = new LinkedHashMap<>();
            for (Map.Entry<Character, String> keyEntry : keys.entrySet()) {
                ingredientsByKey.put(keyEntry.getKey(), ingredientFromString(keyEntry.getValue()));
            }

            ShapedRecipePattern shapedPattern = ShapedRecipePattern.of(ingredientsByKey, patternRows);
            ItemStack result = new ItemStack(item, resultCount);
            ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, shapedPattern, result);
            return new RecipeHolder<>(recipeId, recipe);
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
}
