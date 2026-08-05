package net.geraldhofbauer.vanillaplusadditions.modules.pet_potions.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.pet_potions.PetPotionsModule;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for the Pet Potions module.
 */
public class PetPotionsConfig extends AbstractModuleConfig<PetPotionsModule, PetPotionsConfig> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PetPotionsConfig.class);

    /** Default effects that make an angry pet forgive the thrower. */
    public static final List<String> DEFAULT_CALMING_EFFECTS = List.of(
            "minecraft:instant_health",
            "minecraft:regeneration"
    );

    /** Default length of the grace period after a pet has been calmed, in ticks. */
    public static final int DEFAULT_PEACE_DURATION_TICKS = 200;

    private ModConfigSpec.BooleanValue allowThrowingAtPets;
    private ModConfigSpec.ConfigValue<List<? extends String>> calmingEffects;
    private ModConfigSpec.IntValue peaceDurationTicks;
    private ModConfigSpec.BooleanValue calmFeedback;

    /**
     * Creates a new PetPotionsConfig.
     *
     * @param module The module this configuration belongs to
     */
    public PetPotionsConfig(PetPotionsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        allowThrowingAtPets = builder
                .comment("Let beneficial splash/lingering potions be thrown while aiming at a tamed animal.",
                        "Vanilla swallows that right-click (the client reports CONSUME for any tamed wolf),",
                        "so without this the potion never leaves your hand.")
                .define("allow_throwing_at_pets", true);

        calmingEffects = builder
                .comment("Effect ids that make an angry owned animal forgive the player who threw the potion.",
                        "Works for splash and lingering potions alike.")
                .defineList("calming_effects", DEFAULT_CALMING_EFFECTS,
                        () -> "minecraft:instant_health", o -> o instanceof String);

        peaceDurationTicks = builder
                .comment("How long (in ticks) a calmed animal refuses to re-target the thrower. 0 disables the",
                        "grace period; the anger is still cleared once, it may just come straight back.")
                .defineInRange("peace_duration_ticks", DEFAULT_PEACE_DURATION_TICKS, 0, 24000);

        calmFeedback = builder
                .comment("Play heart particles and a chime when an animal is calmed, so it is visible that it worked")
                .define("calm_feedback", true);

        LOGGER.debug("Built module-specific configuration for Pet Potions module");
    }

    /**
     * Gets the raw throwing-at-pets configuration value.
     *
     * @return the configuration value, or {@code null} before the spec was built
     */
    public ModConfigSpec.BooleanValue getAllowThrowingAtPets() {
        return allowThrowingAtPets;
    }

    /**
     * Gets the raw calming-effects configuration value.
     *
     * @return the configuration value, or {@code null} before the spec was built
     */
    public ModConfigSpec.ConfigValue<List<? extends String>> getCalmingEffects() {
        return calmingEffects;
    }

    /**
     * Gets the raw peace-duration configuration value.
     *
     * @return the configuration value, or {@code null} before the spec was built
     */
    public ModConfigSpec.IntValue getPeaceDurationTicks() {
        return peaceDurationTicks;
    }

    /**
     * Gets the raw calm-feedback configuration value.
     *
     * @return the configuration value, or {@code null} before the spec was built
     */
    public ModConfigSpec.BooleanValue getCalmFeedback() {
        return calmFeedback;
    }

    /**
     * Whether beneficial thrown potions may be used while aiming at an owned animal.
     *
     * @return {@code true} when the pet interaction should be bypassed
     */
    public boolean isThrowingAtPetsAllowed() {
        return allowThrowingAtPets == null || allowThrowingAtPets.get();
    }

    /**
     * The configured calming effect ids as a lookup set.
     *
     * @return effect ids in {@code namespace:path} form, never {@code null}
     */
    public Set<String> getCalmingEffectIds() {
        List<? extends String> configured = calmingEffects != null ? calmingEffects.get() : DEFAULT_CALMING_EFFECTS;
        return new HashSet<>(configured);
    }

    /**
     * The configured grace period after calming.
     *
     * @return duration in ticks, {@code 0} when disabled
     */
    public int getPeaceDurationTicksValue() {
        return peaceDurationTicks != null ? peaceDurationTicks.get() : DEFAULT_PEACE_DURATION_TICKS;
    }

    /**
     * Whether calming an animal should play particles and a sound.
     *
     * @return {@code true} when feedback is enabled
     */
    public boolean isCalmFeedbackEnabled() {
        return calmFeedback == null || calmFeedback.get();
    }
}
