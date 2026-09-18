package net.geraldhofbauer.vanillaplusadditions.standalone.glider_water_repair;

import net.geraldhofbauer.vanillaplusadditions.core.StandaloneModuleBootstrap;
import net.geraldhofbauer.vanillaplusadditions.modules.glider_water_repair.GliderWaterRepairModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Standalone {@code @Mod} entrypoint for the Glider Water Repair module (jar
 * {@code vpa_glider_water_repair}), depending on {@code vpa_core}. All wiring lives in
 * {@link StandaloneModuleBootstrap}.
 */
@Mod("vpa_glider_water_repair")
public final class GliderWaterRepairStandalone {

    public GliderWaterRepairStandalone(IEventBus modEventBus, ModContainer modContainer) {
        StandaloneModuleBootstrap.boot(new GliderWaterRepairModule(), modEventBus, modContainer);
    }
}
