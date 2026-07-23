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
    private ModConfigSpec.BooleanValue autoFix;
    private ModConfigSpec.IntValue reinitFloodTicks;

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

        autoFix = builder
                .comment("Automatically re-initialise stalled wheels during the periodic sweep.",
                        "false (default) = detection only; fix on demand via the /vpaunstuck command.",
                        "The re-init briefly breaks + re-places the wheel (a manual fix, done by code) so",
                        "adjacent water re-flows - the only thing that revives a reload-stalled wheel.")
                .define("auto_fix", false);

        reinitFloodTicks = builder
                .comment("Ticks the wheel is removed during a re-init so adjacent water can flood the gap",
                        "and re-establish active flow before the wheel is placed back. Tune if wheels don't",
                        "reliably restart (more ticks = more flood time).")
                .defineInRange("reinit_flood_ticks", 6, 1, 40);
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

    /**
     * Whether stalled wheels are re-initialised automatically during the sweep (vs. command-only).
     *
     * @return true if the periodic sweep should fix stalled wheels itself
     */
    public boolean isAutoFixEnabled() {
        return autoFix != null && autoFix.get();
    }

    /**
     * Ticks the wheel is removed during a re-init so adjacent water can flood the gap.
     *
     * @return the flood duration in ticks
     */
    public int getReinitFloodTicks() {
        return reinitFloodTicks != null ? reinitFloodTicks.get() : 6;
    }
}
