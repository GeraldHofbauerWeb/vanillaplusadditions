package net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.EndOxygenModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration class for the End Oxygen module.
 */
public class EndOxygenConfig extends AbstractModuleConfig<EndOxygenModule, EndOxygenConfig> {

    private ModConfigSpec.IntValue airConsumptionInterval;
    private ModConfigSpec.DoubleValue outOfAirDamage;
    private ModConfigSpec.IntValue damageTick;
    private ModConfigSpec.IntValue waterBreathingEffectIntervalBonus;
    private ModConfigSpec.IntValue backtankDepletionRate;
    private ModConfigSpec.BooleanValue requiresFullSet;
    private ModConfigSpec.BooleanValue conduitPowerGrantsAir;

    public EndOxygenConfig(EndOxygenModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        airConsumptionInterval = builder
                .comment("The number of ticks between each air depletion (1 = standard, higher = slower).")
                .defineInRange("air_consumption_interval", 2, 1, 300);

        waterBreathingEffectIntervalBonus = builder
                .comment("Additional ticks added to the consumption interval per level of Water Breathing effect.")
                .defineInRange("water_breathing_effect_interval_bonus", 4, 0, 100);

        outOfAirDamage = builder
                .comment("The amount of damage to apply when the player is out of air in the End.")
                .defineInRange("out_of_air_damage", 2.0, 0.5, 20.0);

        damageTick = builder
                .comment("The interval (in ticks) at which damage is applied when out of air.")
                .defineInRange("damage_tick", 20, 1, 200);

        builder.push("backtank");
        backtankDepletionRate = builder
                .comment("The number of ticks between each backtank air depletion (0 to disable depletion).")
                .defineInRange("backtank_depletion_rate", 20, 0, 1000);

        requiresFullSet = builder
                .comment("Whether a diving helmet is required along with the backtank to breathe.")
                .define("requires_full_set", true);
        builder.pop();

        conduitPowerGrantsAir = builder
                .comment("Whether the Conduit Power effect (e.g. from an End Conduit) lets the player "
                        + "breathe freely in the End, refilling air each tick. Default true.")
                .define("conduit_power_grants_air", true);
    }

    public int getAirConsumptionInterval() {
        return airConsumptionInterval.get();
    }

    public int getWaterBreathingEffectIntervalBonus() {
        return waterBreathingEffectIntervalBonus.get();
    }

    public float getOutOfAirDamage() {
        return outOfAirDamage.get().floatValue();
    }

    public int getDamageTick() {
        return damageTick.get();
    }

    public int getBacktankDepletionRate() {
        return backtankDepletionRate.get();
    }

    public boolean requiresFullSet() {
        return requiresFullSet.get();
    }

    public boolean conduitPowerGrantsAir() {
        return conduitPowerGrantsAir == null || conduitPowerGrantsAir.get();
    }
}
