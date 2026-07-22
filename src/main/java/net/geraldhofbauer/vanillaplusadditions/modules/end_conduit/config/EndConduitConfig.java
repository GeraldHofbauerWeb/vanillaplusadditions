package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.EndConduitModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the End Conduit module.
 */
public class EndConduitConfig extends AbstractModuleConfig<EndConduitModule, EndConduitConfig> {

    private ModConfigSpec.IntValue minFrames;
    private ModConfigSpec.IntValue effectRadiusDivisor;

    public EndConduitConfig(EndConduitModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        minFrames = builder
                .comment("Minimum frame-block count (Glowstone / End Stone / End Stone Bricks / Sea Lantern) "
                        + "required to activate the End Conduit. Vanilla conduit uses 16.")
                .defineInRange("min_frames", 16, 1, 42);

        effectRadiusDivisor = builder
                .comment("Conduit Power radius = (frames / 7 * 16) / this divisor. "
                        + "1 (default) = full vanilla Conduit Power radius.")
                .defineInRange("effect_radius_divisor", 1, 1, 16);
    }

    public int getMinFrames() {
        return minFrames != null ? minFrames.get() : 16;
    }

    public int getEffectRadiusDivisor() {
        return effectRadiusDivisor != null ? effectRadiusDivisor.get() : 1;
    }
}
