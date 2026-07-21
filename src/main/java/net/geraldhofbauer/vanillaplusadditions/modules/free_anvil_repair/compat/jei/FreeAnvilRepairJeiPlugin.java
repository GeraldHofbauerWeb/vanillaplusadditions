package net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.FreeAnvilRepairModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the module's extra anvil repair materials (e.g. trident + prismarine shard, bow + stick,
 * netherite gear + diamond) as JEI anvil recipes. These repairs are code-only
 * ({@code isExtraRepairMaterial} → {@code AnvilUpdateEvent}); JEI's own anvil list is a hardcoded
 * vanilla one and can never auto-discover them, so we register them explicitly.
 *
 * <p>One recipe per {@code extra_repair_materials} entry whose item and material both resolve and
 * whose item is damageable. Loaded solely by JEI's annotation scan and gated on the module being
 * enabled with material repair active.
 */
@JeiPlugin
public class FreeAnvilRepairJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "free_anvil_repair_jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ModuleManager manager = ModuleManager.getInstance();
        if (!manager.isModuleEnabled("free_anvil_repair")) {
            return;
        }
        if (!(manager.getModule("free_anvil_repair") instanceof FreeAnvilRepairModule module)) {
            return;
        }
        // Material repair (and thus the extra materials) only happens when this is on.
        if (!module.getConfig().isFreeMaterialRepairValue()) {
            return;
        }

        IVanillaRecipeFactory factory = registration.getVanillaRecipeFactory();
        List<IJeiAnvilRecipe> recipes = new ArrayList<>();

        for (String entry : module.getConfig().getExtraRepairMaterialsValue()) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(entry.substring(0, eq).trim());
            ResourceLocation materialId = ResourceLocation.tryParse(entry.substring(eq + 1).trim());
            if (itemId == null || materialId == null) {
                continue;
            }

            Item item = BuiltInRegistries.ITEM.get(itemId);
            Item material = BuiltInRegistries.ITEM.get(materialId);
            if (item == Items.AIR || material == Items.AIR) {
                continue; // item or material not installed → skip (matches runtime behavior)
            }

            ItemStack probe = new ItemStack(item);
            int maxDamage = probe.getMaxDamage();
            if (maxDamage <= 0) {
                continue; // not damageable → nothing to repair
            }

            ItemStack damagedFully = new ItemStack(item);
            damagedFully.setDamageValue(maxDamage);

            ItemStack repairedQuarter = new ItemStack(item);
            repairedQuarter.setDamageValue(maxDamage * 3 / 4); // one material unit restores ~25%

            ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID, "anvil.extra_repair." + itemId.getPath());

            recipes.add(factory.createAnvilRecipe(
                    damagedFully,
                    List.of(new ItemStack(material)),
                    List.of(repairedQuarter),
                    uid));
        }

        registration.addRecipes(RecipeTypes.ANVIL, recipes);
    }
}
