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
    private ModConfigSpec.BooleanValue trainsEnabled;
    private ModConfigSpec.IntValue trackSearchDistance;
    private ModConfigSpec.DoubleValue trainSeatSearchRadius;

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

        builder.push("create_trains");
        trainsEnabled = builder
                .comment("Also load/unload mobs into the seats of Create train carriages.",
                        "The relevant face must point at a Create track block: the loader's output face,",
                        "the unloader's input face. Only standing (not moving) trains are served.",
                        "Inert without Create; minecart handling is unaffected either way.")
                .define("enabled", true);
        trackSearchDistance = builder
                .comment("How many blocks along its facing direction the block looks for a Create track.",
                        "1 = the block must sit directly in the track bed; higher values let it stand a few",
                        "blocks off to the side, next to the carriage body. The scan stops at the first solid",
                        "block, so it never reaches through a wall.")
                .defineInRange("track_search_distance", 3, 1, 8);
        trainSeatSearchRadius = builder
                .comment("How far (in blocks) a carriage seat may be from the track block to still count.",
                        "Measured from the centre of the track block to the seat's world position; the",
                        "nearest matching seat wins. Seats sit above the track, so this needs some slack.")
                .defineInRange("seat_search_radius", 4.0, 1.0, 16.0);
        builder.pop();
    }

    /**
     * Gets the block-entity scan cadence in ticks.
     *
     * @return ticks between scans (default 5)
     */
    public int getCheckIntervalTicks() {
        return checkIntervalTicks != null ? checkIntervalTicks.get() : 5;
    }

    /**
     * Whether the blocks may also serve Create train carriage seats.
     *
     * @return true if train support is enabled (default true)
     */
    public boolean isTrainSupportEnabled() {
        return trainsEnabled == null || trainsEnabled.get();
    }

    /**
     * Gets how far along its facing direction a block looks for a Create track.
     *
     * @return the search distance in blocks (default 3)
     */
    public int getTrackSearchDistance() {
        return trackSearchDistance != null ? trackSearchDistance.get() : 3;
    }

    /**
     * Gets the maximum distance between the track block and a carriage seat.
     *
     * @return the search radius in blocks (default 4.0)
     */
    public double getTrainSeatSearchRadius() {
        return trainSeatSearchRadius != null ? trainSeatSearchRadius.get() : 4.0;
    }
}
