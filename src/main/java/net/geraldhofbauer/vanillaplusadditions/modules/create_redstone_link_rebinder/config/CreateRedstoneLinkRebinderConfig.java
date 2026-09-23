package net.geraldhofbauer.vanillaplusadditions.modules.create_redstone_link_rebinder.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.create_redstone_link_rebinder.CreateRedstoneLinkRebinderModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Create Redstone Link Rebinder: how soon after a chunk load the links in it
 * are checked, how often all tracked links are swept afterwards, and whether the module repairs on
 * its own or only reports and waits for {@code /vparelink}.
 */
public class CreateRedstoneLinkRebinderConfig
        extends AbstractModuleConfig<CreateRedstoneLinkRebinderModule, CreateRedstoneLinkRebinderConfig> {

    private ModConfigSpec.IntValue postLoadDelayTicks;
    private ModConfigSpec.IntValue checkIntervalTicks;
    private ModConfigSpec.BooleanValue autoRebindOnChunkLoad;
    private ModConfigSpec.BooleanValue autoRebindDuringSweep;

    /**
     * Creates the config for the given module instance.
     *
     * @param module The owning module
     */
    public CreateRedstoneLinkRebinderConfig(CreateRedstoneLinkRebinderModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        postLoadDelayTicks = builder
                .comment("Delay (in ticks) between a chunk with redstone links loading and the targeted",
                        "check of those links. This is the EARLY net, for links that never register at all.",
                        "MEASURED 2026-09-23 on games2: after a cold load (empty server, chunks unloaded,",
                        "first join) ten links were missing from their network - and not one of them was",
                        "caught here. At 20 ticks they all still looked fine; they dropped out afterwards.",
                        "So do not tune this hoping to catch that case - the sweep below is what catches it.")
                .defineInRange("post_load_delay_ticks", 20, 0, 600);

        checkIntervalTicks = builder
                .comment("How often (in ticks) all tracked redstone links are swept.",
                        "Only remembered link positions in loaded chunks are checked - never a world scan.",
                        "THIS is the value that matters. It was built as a mere safety net, but the",
                        "measurement of 2026-09-23 showed it doing all the work: all ten links repaired",
                        "after a cold load were found here, none by the post-load check. Links lose their",
                        "registration some time AFTER they were correctly registered, so a repeating check",
                        "is the only thing that sees it. Lowering this shortens the window in which a door",
                        "stays dead; raising it costs nothing but reaction time.")
                .defineInRange("check_interval_ticks", 100, 20, 1200);

        autoRebindOnChunkLoad = builder
                .comment("Re-register links that are missing from their network right after a chunk load.",
                        "This is the whole point of the module - turning it off leaves only /vparelink.")
                .define("auto_rebind_on_chunk_load", true);

        autoRebindDuringSweep = builder
                .comment("Also re-register missing links during the periodic sweep, not just after a",
                        "chunk load. Cheap, because the sweep walks a remembered list of positions.")
                .define("auto_rebind_during_sweep", true);
    }

    /**
     * Gets the delay between a chunk load and the targeted link check.
     *
     * @return delay in ticks
     */
    public int getPostLoadDelayTicks() {
        return postLoadDelayTicks != null ? postLoadDelayTicks.get() : 20;
    }

    /**
     * Gets the periodic sweep cadence in ticks.
     *
     * @return ticks between full sweeps over tracked links
     */
    public int getCheckIntervalTicks() {
        return checkIntervalTicks != null ? checkIntervalTicks.get() : 100;
    }

    /**
     * Whether the post-load check may re-register a missing link itself.
     *
     * @return true if links are rebound automatically after a chunk load
     */
    public boolean isAutoRebindOnChunkLoadEnabled() {
        return autoRebindOnChunkLoad == null || autoRebindOnChunkLoad.get();
    }

    /**
     * Whether the periodic sweep may re-register a missing link itself.
     *
     * @return true if links are rebound automatically during the sweep
     */
    public boolean isAutoRebindDuringSweepEnabled() {
        return autoRebindDuringSweep == null || autoRebindDuringSweep.get();
    }
}
