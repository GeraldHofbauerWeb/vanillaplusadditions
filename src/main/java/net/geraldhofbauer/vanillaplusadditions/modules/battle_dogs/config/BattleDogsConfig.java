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

    private ModConfigSpec.DoubleValue thornsReflectFraction;

    public BattleDogsConfig(BattleDogsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        thornsReflectFraction = builder
                .comment("Base fraction of absorbed damage reflected back to the attacker, scaled "
                        + "by the armor's Thorns level (0.0 = none, 1.0 = full).")
                .defineInRange("thorns_reflect_fraction", 0.33D, 0.0D, 1.0D);
    }




    public double getThornsReflectFraction() {
        return thornsReflectFraction != null ? thornsReflectFraction.get() : 0.33D;
    }
}
