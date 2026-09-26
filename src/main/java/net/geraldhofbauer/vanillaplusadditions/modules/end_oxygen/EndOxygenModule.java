package net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.compat.CreateBacktankCompat;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.compat.CreateCompat;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.config.EndOxygenConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

import java.util.List;

/**
 * Module that removes oxygen from the End dimension.
 * Ported from handleOxygen logic.
 */
public class EndOxygenModule extends AbstractModule<EndOxygenModule, EndOxygenConfig> {
    public static final ResourceKey<DamageType> OUT_OF_OXYGEN = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("vanillaplusadditions", "out_of_oxygen")
    );

    /**
     * Helmets that count as diving gear when {@code backtank.requires_full_set} is on.
     *
     * <p>Shipped as {@code data/vanillaplusadditions/tags/item/diving_helmets.json} — and it has to
     * be, because an item tag nothing defines is simply <b>empty</b>, not absent. Between the module
     * arriving and {@code v1.0.0-beta.97} no file filled this tag, so the condition below could never
     * be true and a Create backtank supplied no air at all on the default config. That is a silent
     * failure with nothing in the log; the entries are {@code required: false} so the tag still loads
     * without Create, and a datapack can add other mods' helmets.</p>
     */
    public static final TagKey<Item> DIVING_HELMETS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("vanillaplusadditions", "diving_helmets")
    );

    private static EndOxygenModule instance;

    public EndOxygenModule() {
        super("end_oxygen",
                "End Oxygen",
                "Removes oxygen from the End dimension, requiring players to hold their breath or use gear.",
                EndOxygenConfig::new
        );
        instance = this;
    }

    /**
     * The module instance, or {@code null} before construction.
     *
     * <p>Resolved module-locally instead of via {@code ModuleManager}: the manager is only filled by the
     * bundle's {@code registerModules()}, so a lookup there returns {@code null} in a standalone module jar.</p>
     *
     * @return the module instance, or {@code null} if it has not been constructed yet
     */
    public static EndOxygenModule getInstance() {
        return instance;
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

                // Conduit Power (e.g. from an End Conduit) grants free breathing in the End:
                // refill air every tick so the player never suffocates while in range.
                if (getConfig().conduitPowerGrantsAir() && player.hasEffect(MobEffects.CONDUIT_POWER)) {
                    event.setCanBreathe(true);
                    event.setRefillAirAmount(player.getMaxAirSupply());
                    return;
                }

                if (player.getAirSupply() < 1) {
                    player.setAirSupply(1);
                }

                // Gate on the Create-free CreateCompat: touching CreateBacktankCompat at all links it.
                List<ItemStack> backtanks = CreateCompat.isLoaded()
                        ? CreateBacktankCompat.getBacktanksWithAir(player)
                        : List.<ItemStack>of();
                boolean hasDivingHelmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(DIVING_HELMETS);

                if (!backtanks.isEmpty() && (!getConfig().requiresFullSet() || hasDivingHelmet)) {
                    event.setCanBreathe(true);
                    event.setRefillAirAmount(1);

                    int depletionRate = getConfig().getBacktankDepletionRate();
                    if (depletionRate > 0 && player.tickCount % depletionRate == 0) {
                        CreateBacktankCompat.consumeAir(player, backtanks.get(0), 1);
                    }
                } else {
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
