package net.geraldhofbauer.vanillaplusadditions.standalone.death_coordinates;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.death_coordinates.DeathCoordinatesModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Death Coordinates module (jar {@code vpa_death_coordinates}),
 * depending on {@code vpa_core}. All wiring lives in {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_death_coordinates")
public final class DeathCoordinatesStandalone {

    public DeathCoordinatesStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new DeathCoordinatesModule(), modEventBus, modContainer);
    }
}
