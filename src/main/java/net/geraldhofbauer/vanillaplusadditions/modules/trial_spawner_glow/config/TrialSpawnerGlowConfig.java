package net.geraldhofbauer.vanillaplusadditions.modules.trial_spawner_glow.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.trial_spawner_glow.TrialSpawnerGlowModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Trial Spawner Glow module.
 */
public class TrialSpawnerGlowConfig
        extends AbstractModuleConfig<TrialSpawnerGlowModule, TrialSpawnerGlowConfig> {

    private ModConfigSpec.IntValue glowDurationSeconds;
    private ModConfigSpec.BooleanValue requireEmptyHand;
    private ModConfigSpec.BooleanValue showParticles;
    private ModConfigSpec.BooleanValue feedbackMessage;

    public TrialSpawnerGlowConfig(TrialSpawnerGlowModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        glowDurationSeconds = builder
                .comment("How long the outline lasts, in seconds. Clicking again re-applies it, but "
                        + "vanilla only ever keeps the longer of two durations - so a second click "
                        + "does not shorten a glow that is still running, it only picks up mobs that "
                        + "were not there the first time.")
                .defineInRange("glow_duration_seconds", 20, 1, 3600);

        requireEmptyHand = builder
                .comment("Only react when the main hand is empty. Off by default, because in a trial "
                        + "chamber you are usually holding a weapon. The interaction is never "
                        + "cancelled either way, so placing a block or changing the spawner with a "
                        + "spawn egg keeps working exactly as it does in vanilla.")
                .define("require_empty_hand", false);

        showParticles = builder
                .comment("Show the swirling particles that come with the effect. Off by default: the "
                        + "outline is the point, and a chamber full of particles hides more than it "
                        + "shows.")
                .define("show_particles", false);

        feedbackMessage = builder
                .comment("Say in the action bar how many mobs were highlighted.")
                .define("feedback_message", true);
    }

    /**
     * How long the outline lasts.
     *
     * @return the duration in seconds (default 20)
     */
    public int getGlowDurationSeconds() {
        return glowDurationSeconds == null ? 20 : glowDurationSeconds.get();
    }

    /**
     * Whether an empty main hand is required.
     *
     * @return true if only an empty hand triggers the glow (default false)
     */
    public boolean requiresEmptyHand() {
        return requireEmptyHand != null && requireEmptyHand.get();
    }

    /**
     * Whether the effect shows its particles.
     *
     * @return true if particles should be visible (default false)
     */
    public boolean showsParticles() {
        return showParticles != null && showParticles.get();
    }

    /**
     * Whether the player gets an action bar message.
     *
     * @return true if the count should be reported (default true)
     */
    public boolean showsFeedbackMessage() {
        return feedbackMessage == null || feedbackMessage.get();
    }
}
