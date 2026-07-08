package net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.create_water_wheel_unstucker.CreateWaterWheelUnstuckerModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Create Water Wheel Unstucker module: how often tracked wheels are
 * swept, how long after a chunk load the targeted re-check runs, and how the fix escalation
 * (soft flow-recompute vs. hard kinetic re-attach) behaves.
 */
public class CreateWaterWheelUnstuckerConfig
        extends AbstractModuleConfig<CreateWaterWheelUnstuckerModule, CreateWaterWheelUnstuckerConfig> {

    private ModConfigSpec.IntValue checkIntervalTicks;
    private ModConfigSpec.IntValue postLoadDelayTicks;
    private ModConfigSpec.IntValue maxFixAttempts;
    private ModConfigSpec.BooleanValue hardKick;

    /**
     * Creates the config for the given module instance.
     *
     * @param module The owning module
     */
    public CreateWaterWheelUnstuckerConfig(CreateWaterWheelUnstuckerModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        checkIntervalTicks = builder
                .comment("How often (in ticks) all tracked water wheels are swept for stalls.",
                        "Only remembered wheel positions in loaded chunks are checked - never a global scan.")
                .defineInRange("check_interval_ticks", 100, 20, 1200);

        postLoadDelayTicks = builder
                .comment("Delay (in ticks) between a chunk with water wheels loading and the targeted",
                        "stall check for those wheels. The reload desync happens right at chunk load,",
                        "so this check catches it early; the delay lets Create finish its own init first.")
                .defineInRange("post_load_delay_ticks", 60, 0, 600);

        maxFixAttempts = builder
                .comment("Consecutive failed fix attempts per wheel before backing off for ~5 minutes.",
                        "Attempt 1 is always the soft kick (flow recompute); further attempts use the",
                        "hard kick if enabled.")
                .defineInRange("max_fix_attempts", 3, 1, 10);

        hardKick = builder
                .comment("Allow the hard fix escalation: detach + re-attach the wheel's kinetic network",
                        "(equivalent to wrenching the wheel out and back in). false = soft kicks only.")
                .define("hard_kick", true);
    }

    /**
     * Gets the periodic sweep cadence in ticks.
     *
     * @return ticks between full sweeps over tracked wheels
     */
    public int getCheckIntervalTicks() {
        return checkIntervalTicks != null ? checkIntervalTicks.get() : 100;
    }

    /**
     * Gets the delay between a chunk load and the targeted wheel check.
     *
     * @return delay in ticks
     */
    public int getPostLoadDelayTicks() {
        return postLoadDelayTicks != null ? postLoadDelayTicks.get() : 60;
    }

    /**
     * Gets how many consecutive failed fix attempts are made before backing off.
     *
     * @return maximum fix attempts per backoff window
     */
    public int getMaxFixAttempts() {
        return maxFixAttempts != null ? maxFixAttempts.get() : 3;
    }

    /**
     * Whether the hard detach/re-attach escalation is allowed.
     *
     * @return true if hard kicks are enabled
     */
    public boolean isHardKickEnabled() {
        return hardKick == null || hardKick.get();
    }
}
