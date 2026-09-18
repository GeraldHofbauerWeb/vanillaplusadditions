package net.geraldhofbauer.vanillaplusadditions.standalone.compass_overhaul;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.CompassOverhaulModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Compass Overhaul module (jar
 * {@code vpa_compass_overhaul}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_compass_overhaul")
public final class CompassOverhaulStandalone {

    public CompassOverhaulStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new CompassOverhaulModule(), modEventBus, modContainer);
    }
}
