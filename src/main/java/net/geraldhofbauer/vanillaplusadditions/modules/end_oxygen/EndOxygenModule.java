package net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.config.EndOxygenConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

/**
 * Module that removes oxygen from the End dimension.
 * Ported from handleOxygen logic.
 */
public class EndOxygenModule extends AbstractModule<EndOxygenModule, EndOxygenConfig> {
    public static final ResourceKey<DamageType> OUT_OF_OXYGEN = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("create_gravity", "out_of_oxygen")
    );

    public EndOxygenModule() {
        super("end_oxygen",
                "End Oxygen",
                "Removes oxygen from the End dimension, requiring players to hold their breath or use gear.",
                EndOxygenConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLivingBreathe(LivingBreatheEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            // Only affect players in the End
            if (player.level().dimension() == Level.END) {
                // If the player is in creative or spectator mode, they don't need to breathe
                if (player.isCreative() || player.isSpectator()) {
                    return;
                }

                // ForgeEvents logic: if (player.m_20146_() < 1) { player.m_20301_(1); }
                // This keeps them at 1 air if they were at 0, possibly to delay vanilla drowning
                if (player.getAirSupply() < 1) {
                    player.setAirSupply(1);
                }

                // Since we don't have the backtank/diving helmet capability from the original mod,
                // we'll treat them as unable to breathe unless they are in water (which shouldn't happen in the End usually)
                // or we could add specific item checks if they were in the project.
                
                // We treat them as unable to breathe by default in the End
                event.setCanBreathe(false);

                // Consumption logic: instead of recovering air, we reduce the speed of depletion
                int baseInterval = getConfig().getAirConsumptionInterval();
                int effectBonus = 0;
                
                if (player.hasEffect(MobEffects.WATER_BREATHING)) {
                    MobEffectInstance effect = player.getEffect(MobEffects.WATER_BREATHING);
                    if (effect != null) {
                        effectBonus = (effect.getAmplifier() + 1) * getConfig().getWaterBreathingEffectIntervalBonus();
                    }
                }

                int totalInterval = baseInterval + effectBonus;

                // Only consume 1 air every 'totalInterval' ticks
                if (player.tickCount % totalInterval == 0) {
                    event.setConsumeAirAmount(1);
                } else {
                    event.setConsumeAirAmount(0);
                }

                // Damage logic: if air is low, apply damage
                if (player.getAirSupply() <= 1 && player.tickCount % getConfig().getDamageTick() == 0) {
                    DamageSource outOfOxygen = player.level().damageSources().source(OUT_OF_OXYGEN);
                    player.hurt(outOfOxygen, getConfig().getOutOfAirDamage());
                }
            }
        }
    }
}
