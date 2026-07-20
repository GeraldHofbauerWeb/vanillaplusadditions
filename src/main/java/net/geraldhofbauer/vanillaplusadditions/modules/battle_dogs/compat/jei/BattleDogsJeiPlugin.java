package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.BattleDogsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.config.BattleDogsConfig;
import net.geraldhofbauer.vanillaplusadditions.util.JeiMobArmorEnchantments;
import net.geraldhofbauer.vanillaplusadditions.util.JeiMobArmorRepairs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Shows the wolf-armor anvil repairs (armadillo scute) in JEI. Loaded only by JEI's annotation scan;
 * gated on the module being enabled.
 */
@JeiPlugin
public class BattleDogsJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "battle_dogs_jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!ModuleManager.getInstance().isModuleEnabled("battle_dogs")) {
            return;
        }
        List<Item> armors = List.of(
                BattleDogsModule.WOLF_ARMOR_IRON.get(),
                BattleDogsModule.WOLF_ARMOR_GOLD.get(),
                BattleDogsModule.WOLF_ARMOR_DIAMOND.get(),
                BattleDogsModule.WOLF_ARMOR_NETHERITE.get());
        JeiMobArmorRepairs.register(registration, Items.ARMADILLO_SCUTE, armors);

        if (ModuleManager.getInstance().getModule("battle_dogs")
                instanceof BattleDogsModule module) {
            BattleDogsConfig config = module.getConfig();
            JeiMobArmorEnchantments.register(registration, armors,
                    config.getDefaultUnbreakingLevel(),
                    config.getDefaultSharpnessLevel(),
                    config.getDefaultThornsLevel());
        }
    }
}
