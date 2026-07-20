package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.config.CatGuardianConfig;
import net.geraldhofbauer.vanillaplusadditions.util.JeiMobArmorEnchantments;
import net.geraldhofbauer.vanillaplusadditions.util.JeiMobArmorRepairs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Shows the cat-armor anvil repairs (armadillo scute) in JEI. Loaded only by JEI's annotation scan;
 * gated on the module being enabled.
 */
@JeiPlugin
public class CatGuardianJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "cat_guardian_jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!ModuleManager.getInstance().isModuleEnabled("cat_guardian")) {
            return;
        }
        List<Item> armors = List.of(
                CatGuardianModule.CAT_ARMOR_IRON.get(),
                CatGuardianModule.CAT_ARMOR_GOLD.get(),
                CatGuardianModule.CAT_ARMOR_DIAMOND.get(),
                CatGuardianModule.CAT_ARMOR_NETHERITE.get());
        JeiMobArmorRepairs.register(registration, Items.ARMADILLO_SCUTE, armors);

        if (ModuleManager.getInstance().getModule("cat_guardian")
                instanceof CatGuardianModule module) {
            CatGuardianConfig config = module.getConfig();
            JeiMobArmorEnchantments.register(registration, armors,
                    config.getDefaultUnbreakingLevel(),
                    config.getDefaultSharpnessLevel(),
                    config.getDefaultThornsLevel());
        }
    }
}
