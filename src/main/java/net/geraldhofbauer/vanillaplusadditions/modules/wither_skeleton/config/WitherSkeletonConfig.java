package net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.WitherSkeletonModule;
import net.neoforged.neoforge.common.ModConfigSpec;

public class WitherSkeletonConfig extends AbstractModuleConfig<WitherSkeletonModule, WitherSkeletonConfig> {

    public WitherSkeletonConfig(WitherSkeletonModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        // No module-specific configuration needed
    }
}
