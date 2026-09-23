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

    private ModConfigSpec.IntValue keepAliveIntervalTicks;
    private ModConfigSpec.IntValue graceTicks;
    private ModConfigSpec.IntValue maxHoldTicks;
    private ModConfigSpec.BooleanValue holdUntilNetworkReports;
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
        keepAliveIntervalTicks = builder
                .comment("How often (in ticks) every tracked logistics link is re-stamped in Create's link",
                        "cache. That cache expires 20 ticks - one second - after a link last refreshed it,",
                        "and only the link's own lazyTick() does that, which stops as soon as its chunk",
                        "stops ticking. A disconnecting player takes their chunk tickets with them, so the",
                        "chunks stop ticking at once while staying loaded much longer: every absence beyond",
                        "one second empties the network summary while Create still believes the network is",
                        "complete. This value MUST stay below 20; 5 leaves a comfortable margin.")
                .defineInRange("keepalive_interval_ticks", 5, 1, 19);

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

        holdUntilNetworkReports = builder
                .comment("Keep holding a gauge beyond grace_ticks for as long as its logistics network",
                        "has reported nothing at all - measured as InventorySummary.contributingLinks == 0.",
                        "This is the condition that actually matters, and it is why grace_ticks alone was",
                        "not enough: a fixed wait is always a guess. 60 ticks held on one rejoin and was",
                        "too short on the next, because how long the links need to report back varies with",
                        "how busy the server is. A summary that no link contributed to is not evidence of",
                        "an empty network, even though getLevelInStorage() returns 0 for both cases.",
                        "Bounded by max_hold_ticks so a network that is genuinely gone cannot freeze a gauge.")
                .define("hold_until_network_reports", true);

        maxHoldTicks = builder
                .comment("Hard ceiling on how long a single gauge may be held, counted from its chunk load.",
                        "Only reached if hold_until_network_reports keeps firing - i.e. the network really",
                        "has no contributing links, for instance because its last stock link was removed.",
                        "At that point the gauge is released and Create decides for itself again.")
                .defineInRange("max_hold_ticks", 600, 60, 6000);

        holdOnChunkLoad = builder
                .comment("Hold the panels at all. Turning this off disables the module's only effect;",
                        "it exists so the behaviour can be compared without touching the module list.")
                .define("hold_on_chunk_load", true);
    }

    /**
     * Gets how often tracked logistics links are re-stamped in Create's link cache.
     *
     * @return the refresh cadence in ticks, always below Create's 20-tick expiry
     */
    public int getKeepAliveIntervalTicks() {
        return keepAliveIntervalTicks != null ? keepAliveIntervalTicks.get() : 5;
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
     * Whether a gauge stays held while its network summary has no contributing links.
     *
     * @return true if the condition-based hold is active
     */
    public boolean isHoldUntilNetworkReportsEnabled() {
        return holdUntilNetworkReports == null || holdUntilNetworkReports.get();
    }

    /**
     * Gets the hard ceiling for holding a single gauge.
     *
     * @return maximum hold in ticks, counted from the chunk load
     */
    public int getMaxHoldTicks() {
        return maxHoldTicks != null ? maxHoldTicks.get() : 600;
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
