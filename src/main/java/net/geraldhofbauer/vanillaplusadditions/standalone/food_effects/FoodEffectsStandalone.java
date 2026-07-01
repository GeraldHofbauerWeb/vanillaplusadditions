package net.geraldhofbauer.vanillaplusadditions.standalone.food_effects;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.FoodEffectsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the food_effects module (jar {@code vpa_food_effects}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_food_effects")
public final class FoodEffectsStandalone {

    public FoodEffectsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new FoodEffectsModule(), modEventBus, modContainer);
    }
}
