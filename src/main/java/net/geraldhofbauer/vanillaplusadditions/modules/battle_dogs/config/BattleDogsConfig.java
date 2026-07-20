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

    private ModConfigSpec.IntValue defaultUnbreakingLevel;
    private ModConfigSpec.IntValue defaultSharpnessLevel;
    private ModConfigSpec.IntValue defaultThornsLevel;
    private ModConfigSpec.DoubleValue thornsReflectFraction;

    public BattleDogsConfig(BattleDogsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        defaultUnbreakingLevel = builder
                .comment("Default Unbreaking level baked onto freshly crafted wolf armor "
                        + "(0 = none). Unbreaking works natively and extends armor durability.")
                .defineInRange("default_unbreaking_level", 3, 0, 10);
        defaultSharpnessLevel = builder
                .comment("Default Sharpness level baked onto freshly crafted wolf armor "
                        + "(0 = none). Adds bonus outgoing damage when the wolf attacks a mob.")
                .defineInRange("default_sharpness_level", 2, 0, 10);
        defaultThornsLevel = builder
                .comment("Default Thorns level baked onto freshly crafted wolf armor "
                        + "(0 = none). Reflects a share of incoming damage back to the attacker.")
                .defineInRange("default_thorns_level", 2, 0, 10);
        thornsReflectFraction = builder
                .comment("Base fraction of absorbed damage reflected back to the attacker, scaled "
                        + "by the armor's Thorns level (0.0 = none, 1.0 = full).")
                .defineInRange("thorns_reflect_fraction", 0.33D, 0.0D, 1.0D);
    }

    public int getDefaultUnbreakingLevel() {
        return defaultUnbreakingLevel != null ? defaultUnbreakingLevel.get() : 3;
    }

    public int getDefaultSharpnessLevel() {
        return defaultSharpnessLevel != null ? defaultSharpnessLevel.get() : 2;
    }

    public int getDefaultThornsLevel() {
        return defaultThornsLevel != null ? defaultThornsLevel.get() : 2;
    }

    public double getThornsReflectFraction() {
        return thornsReflectFraction != null ? thornsReflectFraction.get() : 0.33D;
    }
}
