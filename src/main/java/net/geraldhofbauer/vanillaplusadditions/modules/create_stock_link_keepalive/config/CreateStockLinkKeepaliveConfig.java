package net.geraldhofbauer.vanillaplusadditions.modules.create_stock_link_keepalive.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.create_stock_link_keepalive.CreateStockLinkKeepaliveModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Create Stock Link Keepalive: how long after a chunk load the factory panels
 * in it are kept from ordering, while Create's logistics links re-register themselves.
 */
public class CreateStockLinkKeepaliveConfig
        extends AbstractModuleConfig<CreateStockLinkKeepaliveModule, CreateStockLinkKeepaliveConfig> {

    private ModConfigSpec.IntValue graceTicks;
    private ModConfigSpec.BooleanValue holdOnChunkLoad;

    /**
     * Creates the config for the given module instance.
     *
     * @param module The owning module
     */
    public CreateStockLinkKeepaliveConfig(CreateStockLinkKeepaliveModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        graceTicks = builder
                .comment("How long (in ticks) factory panels are kept from placing orders after their",
                        "chunk loads. Measured on the live server, the blind window is about 20 ticks:",
                        "for one sample every panel reported a stock level of 0 while its links had not",
                        "re-registered yet, and ordered against that. 60 ticks covers it with room to",
                        "spare. Cost of a larger value: a genuinely needed order is delayed by that much",
                        "(plus up to one Create factoryGaugeTimer interval) after a chunk load.",
                        "VERIFIED 2026-09-23 at this value: on a cold load all 14 gauges of the base",
                        "reported real stock and 'satisfied' on every panel when their window closed, and",
                        "nothing was re-ordered. Before the module the same load had driven all four panels",
                        "of one gauge to a reported level of 0 and left 42 surplus barrels behind.")
                .defineInRange("grace_ticks", 60, 0, 600);

        holdOnChunkLoad = builder
                .comment("Hold the panels at all. Turning this off disables the module's only effect;",
                        "it exists so the behaviour can be compared without touching the module list.")
                .define("hold_on_chunk_load", true);
    }

    /**
     * Gets how long panels are held after a chunk load.
     *
     * @return grace window in ticks
     */
    public int getGraceTicks() {
        return graceTicks != null ? graceTicks.get() : 60;
    }

    /**
     * Whether panels are held after a chunk load.
     *
     * @return true if the grace window is applied
     */
    public boolean isHoldOnChunkLoadEnabled() {
        return holdOnChunkLoad == null || holdOnChunkLoad.get();
    }
}
