package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.BattleDogsModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Battle Dogs module. Adds the shared mob-armor combat-effect keys
 * (default Unbreaking/Sharpness/Thorns levels and the thorns reflect fraction) on top of the
 * inherited {@code enabled} + {@code debug_logging} keys.
 */
public class BattleDogsConfig extends AbstractModuleConfig<BattleDogsModule, BattleDogsConfig> {

    private static final double DEFAULT_BITE_STRENGTH = 1.0D;

    private ModConfigSpec.DoubleValue thornsReflectFraction;
    private ModConfigSpec.DoubleValue fallAbsorbBlocks;
    private ModConfigSpec.BooleanValue biteAnimation;
    private ModConfigSpec.BooleanValue biteAnimationOnlyWhenRidden;
    private ModConfigSpec.DoubleValue biteAnimationStrength;

    public BattleDogsConfig(BattleDogsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        thornsReflectFraction = builder
                .comment("Base fraction of absorbed damage reflected back to the attacker, scaled "
                        + "by the armor's Thorns level (0.0 = none, 1.0 = full).")
                .defineInRange("thorns_reflect_fraction", 0.33D, 0.0D, 1.0D);

        fallAbsorbBlocks = builder
                .comment("An armored wolf takes no fall damage at all - and its armor no wear - for",
                        "falls up to this many blocks. Measured on the fall distance, not on the",
                        "damage, so it reads the way it sounds: 5 means a five-block drop is free.",
                        "0 switches it off and every fall is paid for as before.")
                .defineInRange("fall_absorb_blocks", 5.0D, 0.0D, 256.0D);

        builder.comment("Client-side bite animation. Vanilla wolves have none at all -- their model",
                        "never reads attackAnim, so an attacking wolf deals damage without moving.").push("bite_animation");
        biteAnimation = builder
                .comment("Snap the wolf's head down when it lands a bite.")
                .define("enabled", true);
        biteAnimationOnlyWhenRidden = builder
                .comment("Restrict the animation to a wolf that is being ridden. Off by default: a",
                        "dog that only bites visibly while carrying someone looks stranger than one",
                        "that always does.")
                .define("only_when_ridden", false);
        biteAnimationStrength = builder
                .comment("Scales how far the head swings. 1.0 is about 50 degrees.")
                .defineInRange("strength", DEFAULT_BITE_STRENGTH, 0.0D, 2.0D);
        builder.pop();
    }




    public boolean isBiteAnimation() {
        return biteAnimation == null || biteAnimation.get();
    }

    public boolean isBiteAnimationOnlyWhenRidden() {
        return biteAnimationOnlyWhenRidden != null && biteAnimationOnlyWhenRidden.get();
    }

    public double getBiteAnimationStrength() {
        return biteAnimationStrength != null ? biteAnimationStrength.get() : DEFAULT_BITE_STRENGTH;
    }

    /**
     * Fall distance up to which an armored wolf takes no fall damage and its armor no wear.
     *
     * @return the free fall distance in blocks, 0 to disable
     */
    public double getFallAbsorbBlocks() {
        return fallAbsorbBlocks.get();
    }

    public double getThornsReflectFraction() {
        return thornsReflectFraction != null ? thornsReflectFraction.get() : 0.33D;
    }
}
