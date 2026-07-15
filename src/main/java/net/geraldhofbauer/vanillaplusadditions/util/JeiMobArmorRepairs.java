package net.geraldhofbauer.vanillaplusadditions.util;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared JEI helper for the mob-armor items (wolf / cat / axolotl body armor). Vanilla anvil repair is
 * code-only ({@code Item.isValidRepairItem}) — it is not a registered recipe, and JEI's own anvil
 * recipes come from a hardcoded vanilla list ({@code AnvilRecipeMaker.getRepairData}), so JEI can never
 * auto-discover our scute repairs. This registers them explicitly as anvil recipes.
 *
 * <p>Lives in {@code util} (→ {@code vpa_core}) so each module's {@code @JeiPlugin} can call it. Only
 * ever reached from within a JEI plugin (loaded solely by JEI's annotation scan), so the JEI-API types
 * referenced here are never linked on a client/server without JEI installed.
 */
public final class JeiMobArmorRepairs {

    private JeiMobArmorRepairs() {
    }

    /**
     * Registers one anvil repair recipe per armor: a fully-damaged piece plus the repair material,
     * yielding the piece repaired by 25% — exactly one material unit, matching vanilla anvil repair.
     *
     * @param registration  JEI's recipe registration
     * @param repairMaterial the item that repairs this family (armadillo/turtle scute)
     * @param armors         the armor items to register repairs for (all tiers)
     */
    public static void register(IRecipeRegistration registration, Item repairMaterial, List<Item> armors) {
        IVanillaRecipeFactory factory = registration.getVanillaRecipeFactory();
        List<IJeiAnvilRecipe> recipes = new ArrayList<>();

        for (Item armor : armors) {
            ItemStack probe = new ItemStack(armor);
            int maxDamage = probe.getMaxDamage();
            if (maxDamage <= 0) {
                continue;
            }

            ItemStack damagedFully = new ItemStack(armor);
            damagedFully.setDamageValue(maxDamage);

            ItemStack repairedQuarter = new ItemStack(armor);
            repairedQuarter.setDamageValue(maxDamage * 3 / 4); // one material unit restores 25%

            ResourceLocation armorId = BuiltInRegistries.ITEM.getKey(armor);
            ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID, "anvil.materials_repair." + armorId.getPath());

            recipes.add(factory.createAnvilRecipe(
                    damagedFully,
                    List.of(new ItemStack(repairMaterial)),
                    List.of(repairedQuarter),
                    uid));
        }

        registration.addRecipes(RecipeTypes.ANVIL, recipes);
    }
}
