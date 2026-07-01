package net.geraldhofbauer.vanillaplusadditions.standalone.haunted_house;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.haunted_house.HauntedHouseModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the haunted_house module (jar {@code vpa_haunted_house}), depending on
 * {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_haunted_house")
public final class HauntedHouseStandalone {

    public HauntedHouseStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new HauntedHouseModule(), modEventBus, modContainer);
    }
}
