package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Mob Cart Loader module. Beyond the standard enabled/debug options it only
 * exposes how often the loader/unloader block entities scan for carts and mobs (a cost/latency
 * trade-off).
 */
public class MobCartLoaderConfig
        extends AbstractModuleConfig<MobCartLoaderModule, MobCartLoaderConfig> {

    private ModConfigSpec.IntValue checkIntervalTicks;

    /**
     * Creates the config for the given module instance.
     *
     * @param module The owning module
     */
    public MobCartLoaderConfig(MobCartLoaderModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        checkIntervalTicks = builder
                .comment("How often (in ticks) each loader/unloader block scans the adjacent rail and pen.",
                        "Lower = more responsive, higher = cheaper. Only tracked blocks in loaded chunks tick.")
                .defineInRange("check_interval_ticks", 5, 1, 40);
    }

    /**
     * Gets the block-entity scan cadence in ticks.
     *
     * @return ticks between scans (default 5)
     */
    public int getCheckIntervalTicks() {
        return checkIntervalTicks != null ? checkIntervalTicks.get() : 5;
    }
}
