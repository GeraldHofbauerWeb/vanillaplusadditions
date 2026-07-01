package net.geraldhofbauer.vanillaplusadditions.standalone.custom_crafting_recipes;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.custom_crafting_recipes.CustomCraftingRecipesModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the custom_crafting_recipes module (jar {@code vpa_custom_crafting_recipes}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_custom_crafting_recipes")
public final class CustomCraftingRecipesStandalone {

    public CustomCraftingRecipesStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new CustomCraftingRecipesModule(), modEventBus, modContainer);
    }
}
