package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.config.AxolotlGuardianConfig;
import net.geraldhofbauer.vanillaplusadditions.util.JeiMobArmorEnchantments;
import net.geraldhofbauer.vanillaplusadditions.util.JeiMobArmorRepairs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Shows the axolotl-armor anvil repairs (turtle scute) in JEI. Loaded only by JEI's annotation scan;
 * gated on the module being enabled.
 */
@JeiPlugin
public class AxolotlGuardianJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "axolotl_guardian_jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!ModuleManager.getInstance().isModuleEnabled("axolotl_guardian")) {
            return;
        }
        List<Item> armors = List.of(
                AxolotlGuardianModule.AXOLOTL_ARMOR_IRON.get(),
                AxolotlGuardianModule.AXOLOTL_ARMOR_GOLD.get(),
                AxolotlGuardianModule.AXOLOTL_ARMOR_DIAMOND.get(),
                AxolotlGuardianModule.AXOLOTL_ARMOR_NETHERITE.get());
        JeiMobArmorRepairs.register(registration, Items.TURTLE_SCUTE, armors);

        if (ModuleManager.getInstance().getModule("axolotl_guardian")
                instanceof AxolotlGuardianModule module) {
            AxolotlGuardianConfig config = module.getConfig();
            JeiMobArmorEnchantments.register(registration, armors,
                    config.getDefaultUnbreakingLevel(),
                    config.getDefaultSharpnessLevel(),
                    config.getDefaultThornsLevel());
        }
    }
}
