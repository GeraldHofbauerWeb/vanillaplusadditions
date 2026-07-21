package net.geraldhofbauer.vanillaplusadditions.modules.conduit_attack_range.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.conduit_attack_range.ConduitAttackRangeModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Conduit Attack Range module.
 * Controls the hostile-damage radius divisor and the minimum frame size at which conduits attack.
 */
public class ConduitAttackRangeConfig
        extends AbstractModuleConfig<ConduitAttackRangeModule, ConduitAttackRangeConfig> {

    private ModConfigSpec.IntValue radiusDivisor;
    private ModConfigSpec.IntValue minFrames;

    public ConduitAttackRangeConfig(ConduitAttackRangeModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        radiusDivisor = builder
                .comment("Hostile-damage radius = Conduit Power radius / this divisor. "
                        + "2 (default) = half the effect radius (16 blocks at 16 frames, 48 at 42). "
                        + "1 = full effect radius.")
                .defineInRange("radius_divisor", 2, 1, 16);

        minFrames = builder
                .comment("Minimum frame-block count at which a conduit starts attacking hostiles. "
                        + "Vanilla requires 42 (max frame); default 16 = every active conduit.")
                .defineInRange("min_frames", 16, 1, 96);
    }

    /**
     * Divisor applied to the Conduit Power radius to get the hostile-damage radius.
     *
     * @return the configured radius divisor (default 2)
     */
    public int getRadiusDivisorValue() {
        return radiusDivisor != null ? radiusDivisor.get() : 2;
    }

    /**
     * Minimum frame-block count at which conduits attack hostiles.
     *
     * @return the configured minimum frame count (default 16)
     */
    public int getMinFramesValue() {
        return minFrames != null ? minFrames.get() : 16;
    }
}
