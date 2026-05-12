package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_slowdown.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_slowdown.OverpackedSlowdownModule;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for the Overpacked Slowdown module.
 * This class handles all configuration options for overriding the Overpacked mod's slowdown effect.
 */
public class OverpackedSlowdownConfig extends AbstractModuleConfig<OverpackedSlowdownModule, OverpackedSlowdownConfig> {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverpackedSlowdownConfig.class);

    private ModConfigSpec.DoubleValue slowdownMultiplier;

    /**
     * Creates a new OverpackedSlowdownConfig.
     *
     * @param module The module this configuration belongs to
     */
    public OverpackedSlowdownConfig(OverpackedSlowdownModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        slowdownMultiplier = builder
                .comment("Multiplier applied to the Overpacked slowdown effect.",
                        "0.0 = no slowdown (completely removes the effect),",
                        "0.5 = half the original slowdown,",
                        "1.0 = original slowdown (no change),",
                        "2.0 = double the slowdown.")
                .defineInRange("slowdown_multiplier", 0.0, 0.0, 10.0);

        LOGGER.debug("Built module-specific configuration for Overpacked Slowdown module");
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec);
        if (shouldDebugLog()) {
            LOGGER.debug("Module-specific configuration loaded for Overpacked Slowdown module");
            if (slowdownMultiplier != null) {
                LOGGER.debug("  - Slowdown multiplier: {}", slowdownMultiplier.get());
            }
        }
    }

    /**
     * Gets the configured slowdown multiplier.
     *
     * @return slowdown multiplier value, or default value if not configured
     */
    public double getSlowdownMultiplierValue() {
        return slowdownMultiplier != null ? slowdownMultiplier.get() : 0.0;
    }

    /**
     * Gets the slowdown multiplier configuration value.
     *
     * @return The slowdown multiplier configuration value
     */
    public ModConfigSpec.DoubleValue getSlowdownMultiplier() {
        return slowdownMultiplier;
    }
}
