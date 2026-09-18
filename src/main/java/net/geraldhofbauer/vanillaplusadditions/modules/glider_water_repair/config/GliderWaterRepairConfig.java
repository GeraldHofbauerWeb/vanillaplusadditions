package net.geraldhofbauer.vanillaplusadditions.modules.glider_water_repair.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.glider_water_repair.GliderWaterRepairModule;

/**
 * Configuration for the Glider Water Repair module.
 *
 * <p>Nothing beyond the standard {@code enabled} and {@code debug_logging} — the module does exactly
 * one thing, and whether it does it is the only sensible switch.
 */
public class GliderWaterRepairConfig
        extends AbstractModuleConfig<GliderWaterRepairModule, GliderWaterRepairConfig> {

    public GliderWaterRepairConfig(GliderWaterRepairModule module) {
        super(module);
    }
}
