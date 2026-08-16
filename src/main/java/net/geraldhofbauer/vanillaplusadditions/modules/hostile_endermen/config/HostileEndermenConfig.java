package net.geraldhofbauer.vanillaplusadditions.modules.hostile_endermen.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.hostile_endermen.HostileEndermenModule;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for the Hostile Endermen module.
 */
public class HostileEndermenConfig
        extends AbstractModuleConfig<HostileEndermenModule, HostileEndermenConfig> {
    /** Sentinel for {@code anger_duration}: never let the anger time run out on its own. */
    public static final int INDEFINITE_ANGER = -1;

    private static final Logger LOGGER = LoggerFactory.getLogger(HostileEndermenConfig.class);

    private static final int DEFAULT_DETECTION_RANGE = 16;
    private static final int DEFAULT_ANGER_DURATION = 600;

    // enabled and debugLogging are handled by AbstractModuleConfig
    private ModConfigSpec.IntValue detectionRange;
    private ModConfigSpec.IntValue angerDuration;
    private ModConfigSpec.BooleanValue respectCarvedPumpkin;
    private ModConfigSpec.BooleanValue suppressTeleportAttack;
    private ModConfigSpec.BooleanValue suppressAntiCheeseTeleport;
    private ModConfigSpec.BooleanValue debugTeleportTracking;

    /**
     * Creates a new HostileEndermenConfig.
     *
     * @param module The module this configuration belongs to
     */
    public HostileEndermenConfig(HostileEndermenModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        detectionRange = builder
                .comment("Range in blocks in which endermen in the End automatically become hostile,",
                        "and beyond which they calm down again. Hard ceiling is the vanilla enderman",
                        "follow range of 64 - larger values are capped by vanilla targeting anyway")
                .defineInRange("detection_range", DEFAULT_DETECTION_RANGE, 1, 128);

        angerDuration = builder
                .comment("How long endermen stay angry in ticks (-1 for indefinite).",
                        "Refreshed every second while a player stays within detection_range")
                .defineInRange("anger_duration", DEFAULT_ANGER_DURATION, INDEFINITE_ANGER, Integer.MAX_VALUE);

        respectCarvedPumpkin = builder
                .comment("Keep the vanilla carved pumpkin protection: players wearing a carved pumpkin",
                        "(or a modded ender mask) are not attacked automatically")
                .define("respect_carved_pumpkin", true);

        suppressTeleportAttack = builder
                .comment("Enderman Overhaul compat: stop its End Enderman from teleporting the player away",
                        "on hit as long as the player has not hit that enderman back (5 s window).",
                        "No effect without Enderman Overhaul installed")
                .define("suppress_teleport_attack", true);

        suppressAntiCheeseTeleport = builder
                .comment("EnhancedAI compat: stop its \"Teleport anti-cheese\" goal from dragging players",
                        "over to an enderman in the End as long as the player has not hit that enderman",
                        "back (5 s window). The goal fires whenever the enderman cannot path to its target,",
                        "so with this module simply walking away would pull the player back in.",
                        "No effect without EnhancedAI installed")
                .define("suppress_anticheese_teleport", true);

        debugTeleportTracking = builder
                .comment("Diagnostics: log every player teleport together with the caller stack, which",
                        "identifies the mod/feature that moved the player. Off by default")
                .define("debug_teleport_tracking", false);

        LOGGER.debug("Built module-specific configuration for Hostile Endermen module");
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec);
        if (detectionRange != null && angerDuration != null && respectCarvedPumpkin != null) {
            LOGGER.debug("  - Detection range: {} blocks", detectionRange.get());
            LOGGER.debug("  - Anger duration: {} ticks", angerDuration.get());
            LOGGER.debug("  - Respect carved pumpkin: {}", respectCarvedPumpkin.get());
            LOGGER.debug("  - Suppress teleport attack: {}", suppressTeleportAttack.get());
        }
    }

    /**
     * Gets the detection range configuration value.
     *
     * @return The detection range configuration value
     */
    public ModConfigSpec.IntValue getDetectionRange() {
        return detectionRange;
    }

    /**
     * Gets the anger duration configuration value.
     *
     * @return The anger duration configuration value
     */
    public ModConfigSpec.IntValue getAngerDuration() {
        return angerDuration;
    }

    /**
     * Gets the carved pumpkin protection configuration value.
     *
     * @return The carved pumpkin protection configuration value
     */
    public ModConfigSpec.BooleanValue getRespectCarvedPumpkin() {
        return respectCarvedPumpkin;
    }

    /**
     * Gets the configured detection range.
     *
     * @return detection range in blocks, or the default value if not configured
     */
    public int getDetectionRangeValue() {
        return detectionRange != null ? detectionRange.get() : DEFAULT_DETECTION_RANGE;
    }

    /**
     * Gets the configured anger duration.
     *
     * @return anger duration in ticks ({@link #INDEFINITE_ANGER} for indefinite), or the default
     *         value if not configured
     */
    public int getAngerDurationValue() {
        return angerDuration != null ? angerDuration.get() : DEFAULT_ANGER_DURATION;
    }

    /**
     * Whether the vanilla carved pumpkin protection stays in effect.
     *
     * @return true if players wearing a carved pumpkin are exempt from auto-aggression
     */
    public boolean respectCarvedPumpkin() {
        return respectCarvedPumpkin == null || respectCarvedPumpkin.get();
    }

    /**
     * Gets the Enderman Overhaul teleport suppression configuration value.
     *
     * @return The teleport suppression configuration value
     */
    public ModConfigSpec.BooleanValue getSuppressTeleportAttack() {
        return suppressTeleportAttack;
    }

    /**
     * Whether Enderman Overhaul's End Enderman teleport attack is suppressed for players who have
     * not hit the enderman themselves.
     *
     * @return true if the unprovoked teleport attack should be skipped
     */
    public boolean suppressTeleportAttack() {
        return suppressTeleportAttack == null || suppressTeleportAttack.get();
    }

    /**
     * Whether EnhancedAI's teleport anti-cheese goal is vetoed for players in the End.
     *
     * @return true if the anti-cheese teleport should be blocked
     */
    public boolean suppressAntiCheeseTeleport() {
        return suppressAntiCheeseTeleport == null || suppressAntiCheeseTeleport.get();
    }

    /**
     * Whether player teleports are logged with their caller stack (diagnostics).
     *
     * @return true if teleport tracking should log
     */
    public boolean debugTeleportTracking() {
        return debugTeleportTracking != null && debugTeleportTracking.get();
    }
}
