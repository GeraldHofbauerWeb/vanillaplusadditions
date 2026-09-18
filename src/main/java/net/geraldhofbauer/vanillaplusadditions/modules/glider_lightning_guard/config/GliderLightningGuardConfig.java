package net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard.GliderLightningGuardModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Glider Lightning Guard module.
 */
public class GliderLightningGuardConfig
        extends AbstractModuleConfig<GliderLightningGuardModule, GliderLightningGuardConfig> {

    private ModConfigSpec.DoubleValue durabilityCost;

    public GliderLightningGuardConfig(GliderLightningGuardModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        durabilityCost = builder
                .comment("Share of the glider's maximum durability a lightning strike costs instead of "
                        + "wrecking it outright. 0.25 (default) is a quarter of the bar; 0 lets the strike "
                        + "pass without any cost at all. The glider never breaks from this — it stops one "
                        + "point short of the bar, so there is always something left to repair.")
                .defineInRange("durability_cost", 0.25D, 0.0D, 1.0D);
    }

    /**
     * Share of maximum durability a lightning strike costs.
     *
     * @return the configured share (default 0.25)
     */
    public double getDurabilityCostValue() {
        return durabilityCost != null ? durabilityCost.get() : 0.25D;
    }
}
