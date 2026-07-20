package net.geraldhofbauer.vanillaplusadditions.util;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared JEI helper that surfaces the mob-armor combat enchantments (Unbreaking, Sharpness, Thorns)
 * as anvil recipes: {@code plain armor + matching enchanted book -> enchanted armor}. Complements
 * {@link JeiMobArmorRepairs} (scute repairs) so a player can look up how to enchant their mob armor.
 *
 * <p>Sharpness is a weapon enchant a real vanilla anvil would not apply to armor — the displayed
 * recipe documents the module's innate sharpness behavior rather than a literal anvil action.
 *
 * <p>Lives in {@code util} so each module's {@code @JeiPlugin} can call it, and is only ever reached
 * from within a JEI plugin (client-only, loaded solely by JEI's annotation scan).
 */
public final class JeiMobArmorEnchantments {

    private JeiMobArmorEnchantments() {
    }

    /**
     * Registers, per armor and per enchantment, an anvil recipe applying the enchanted book at the
     * configured default level. Levels {@code <= 0} are skipped, as is the whole call when no world
     * (and therefore no enchantment registry) is available yet.
     *
     * @param registration    JEI's recipe registration
     * @param armors          the armor items to register enchant recipes for (all tiers)
     * @param unbreakingLevel default Unbreaking level to display (skipped when {@code <= 0})
     * @param sharpnessLevel  default Sharpness level to display (skipped when {@code <= 0})
     * @param thornsLevel     default Thorns level to display (skipped when {@code <= 0})
     */
    public static void register(IRecipeRegistration registration, List<Item> armors,
                                int unbreakingLevel, int sharpnessLevel, int thornsLevel) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return; // enchantment registry is unavailable without a joined world — skip gracefully
        }
        RegistryAccess registryAccess = minecraft.level.registryAccess();
        IVanillaRecipeFactory factory = registration.getVanillaRecipeFactory();
        List<IJeiAnvilRecipe> recipes = new ArrayList<>();

        addEnchantRecipes(recipes, factory, registryAccess, armors,
                Enchantments.UNBREAKING, "unbreaking", unbreakingLevel);
        addEnchantRecipes(recipes, factory, registryAccess, armors,
                Enchantments.SHARPNESS, "sharpness", sharpnessLevel);
        addEnchantRecipes(recipes, factory, registryAccess, armors,
                Enchantments.THORNS, "thorns", thornsLevel);

        registration.addRecipes(RecipeTypes.ANVIL, recipes);
    }

    private static void addEnchantRecipes(List<IJeiAnvilRecipe> recipes, IVanillaRecipeFactory factory,
                                          RegistryAccess registryAccess, List<Item> armors,
                                          ResourceKey<Enchantment> key, String path, int level) {
        if (level <= 0) {
            return;
        }
        Holder<Enchantment> holder = registryAccess.registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(key).orElse(null);
        if (holder == null) {
            return;
        }
        for (Item armor : armors) {
            ItemStack base = new ItemStack(armor);

            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            stored.set(holder, level);
            book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());

            ItemStack result = new ItemStack(armor);
            result.enchant(holder, level);

            ResourceLocation armorId = BuiltInRegistries.ITEM.getKey(armor);
            ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID, "anvil.enchant." + path + "." + armorId.getPath());

            recipes.add(factory.createAnvilRecipe(base, List.of(book), List.of(result), uid));
        }
    }
}
