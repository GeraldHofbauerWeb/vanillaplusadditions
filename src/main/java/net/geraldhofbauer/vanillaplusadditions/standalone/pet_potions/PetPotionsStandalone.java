package net.geraldhofbauer.vanillaplusadditions.standalone.pet_potions;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.pet_potions.PetPotionsModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the pet_potions module (jar {@code vpa_pet_potions}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_pet_potions")
public final class PetPotionsStandalone {

    public PetPotionsStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new PetPotionsModule(), modEventBus, modContainer);
    }
}
