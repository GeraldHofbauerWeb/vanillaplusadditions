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
    private ModConfigSpec.BooleanValue reinitLargeWheels;
    private ModConfigSpec.BooleanValue autoUnstickOnChunkLoad;
    private ModConfigSpec.BooleanValue autoClearPhantomStress;
    private ModConfigSpec.BooleanValue autoFix;
    private ModConfigSpec.BooleanValue clearPhantomStress;
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
                        "Applies to every automatic trigger that may re-initialise. After two full backoff",
                        "rounds without the wheel restarting, it is left alone entirely - a wheel that",
                        "survives that is dry, decorative or unfinished rather than stalled, and only",
                        "/vpaunstuck will try it again.",
                        "On top of this sits a hard floor of one automatic re-init per wheel per minute.",
                        "Every attempt is the same break-and-replace re-init; there is no cheaper first step.")
                .defineInRange("max_fix_attempts", 3, 1, 10);

        hardKick = builder
                .comment("CURRENTLY INERT - nothing reads this key.",
                        "It was meant to allow a cheaper escalation step (detach + re-attach the wheel's",
                        "kinetic network, like wrenching it out and back in) before the break-and-replace",
                        "re-init. That step was never wired up; WaterWheelKinetics.softKick/hardKick exist",
                        "but have no callers. The key is kept so an existing config file does not change",
                        "meaning under the operator's feet.")
                .define("hard_kick", true);

        autoFix = builder
                .comment("Automatically re-initialise stalled wheels during the periodic sweep.",
                        "false (default) = no block changes; fix on demand via the /vpaunstuck command.",
                        "Independent of this, the sweep always settles the stress bookkeeping of an",
                        "overstressed wheel (recompute, plus an orphaned tally per clear_phantom_stress) -",
                        "that only corrects Create's own numbers and changes no blocks.",
                        "The re-init briefly breaks + re-places the wheel (a manual fix, done by code) so",
                        "adjacent water re-flows - the only thing that revives a reload-stalled wheel.")
                .define("auto_fix", false);

        autoUnstickOnChunkLoad = builder
                .comment("Unstick a stalled wheel automatically after its chunk has loaded.",
                        "true (default) = the targeted check that runs post_load_delay_ticks after a chunk",
                        "with wheels loads may apply the full cure, re-initialising the wheel if nothing",
                        "cheaper worked - the same thing /vpaunstuck does by hand.",
                        "This is the situation the module exists for: a wheel loses its flow score across a",
                        "chunk reload. A wheel a player has just placed is NOT treated this way.",
                        "Separate from auto_fix, which governs the periodic sweep.")
                .define("auto_unstick_on_chunk_load", true);

        autoClearPhantomStress = builder
                .comment("Let the automatic path also drop an unloaded-member stress tally, not just a",
                        "self-contradictory one. Requires clear_phantom_stress.",
                        "true (default) = allowed, but only after the tally has stopped changing for ten",
                        "seconds while the network stayed overstressed. That wait matters: Create seeds",
                        "tally with the WHOLE network when a world loads and only counts it down as members",
                        "load, so a tally that is still shrinking means members are still arriving.",
                        "Each such clear is logged as a warning with the numbers it was based on.",
                        "false = the judgement call stays with /vpaunstuck.")
                .define("auto_clear_phantom_stress", true);

        reinitLargeWheels = builder
                .comment("Allow re-initialising large (multiblock) water wheels as well.",
                        "false (default) = large wheels are only detected and logged, never touched.",
                        "A re-init breaks the wheel and places it back a moment later. For a large wheel",
                        "Create rebuilds the multiblock through LargeWaterWheelBlock.tick, which destroys",
                        "the centre block WITHOUT dropping it - so a re-init that goes wrong there costs",
                        "the whole structure rather than one block.")
                .define("reinit_large_wheels", false);

        clearPhantomStress = builder
                .comment("Cure a phantom \"Overstressed\" network: Create keeps a running stress/capacity",
                        "tally for members in unloaded chunks, and a member removed while unloaded never",
                        "subtracts its share again - the network then reports an overload that no existing",
                        "machine causes. Two cases, deliberately treated differently:",
                        " - orphaned tally (stress charged while the network claims ZERO unloaded members):",
                        "   nothing can be behind those numbers, so any caller drops them and logs one line",
                        "   per revived wheel.",
                        " - tally with actual unloaded members, where the loaded members alone would fit the",
                        "   loaded capacity: those might be real machines in unloaded chunks, which Create",
                        "   counts on purpose. /vpaunstuck does this at once; an automatic caller only after",
                        "   auto_clear_phantom_stress's ten-second wait, and logs it as a warning.",
                        "Machines that genuinely are unloaded re-register (with their real numbers) as soon",
                        "as their chunk loads. false = never touch the tally, command included.")
                .define("clear_phantom_stress", true);

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
     * Whether large (multiblock) water wheels may be re-initialised.
     *
     * @return true if large wheels may be broken and re-placed
     */
    public boolean isReinitLargeWheelsEnabled() {
        return reinitLargeWheels != null && reinitLargeWheels.get();
    }

    /**
     * Whether a chunk-load check may apply the full cure, re-init included.
     *
     * @return true if wheels are unstuck automatically after a chunk load
     */
    public boolean isAutoUnstickOnChunkLoadEnabled() {
        return autoUnstickOnChunkLoad == null || autoUnstickOnChunkLoad.get();
    }

    /**
     * Whether the automatic path may drop a settled unloaded-member stress tally.
     *
     * @return true if the automatic path may take the judgement call
     */
    public boolean isAutoClearPhantomStressEnabled() {
        return autoClearPhantomStress == null || autoClearPhantomStress.get();
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
     * Whether a stale unloaded-member stress tally may be dropped to cure a phantom overload - by the
     * sweep when the tally is provably orphaned, by {@code /vpaunstuck} also in the judgement case.
     *
     * @return true if phantom stress may be cleared
     */
    public boolean isClearPhantomStressEnabled() {
        return clearPhantomStress == null || clearPhantomStress.get();
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
