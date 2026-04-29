package net.geraldhofbauer.vanillaplusadditions.modules.food_effects;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.config.FoodEffectsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoodEffectsModule extends AbstractModule<FoodEffectsModule, FoodEffectsConfig> {

    private final Map<Item, List<MobEffectInstance>> effectCache = new HashMap<>();

    public FoodEffectsModule() {
        super("food_effects",
                "Food Effects",
                "Allows adding potion effects to food items via configuration.",
                FoodEffectsConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    protected void onCommonSetup() {
        reloadEffectCache();
    }

    public void reloadEffectCache() {
        effectCache.clear();
        if (!isModuleEnabled()) {
            return;
        }

        List<String> entries = getConfig().getFoodEffects();
        for (String entry : entries) {
            String[] parts = entry.split(";");
            if (parts.length < 3) {
                continue;
            }

            try {
                ResourceLocation itemRl = ResourceLocation.parse(parts[0]);
                ResourceLocation effectRl = ResourceLocation.parse(parts[1]);
                int duration = Integer.parseInt(parts[2]);
                int amplifier = parts.length >= 4 ? Integer.parseInt(parts[3]) : 0;

                Item item = BuiltInRegistries.ITEM.get(itemRl);
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectRl);

                if (item != Items.AIR && effect != null) {
                    effectCache.computeIfAbsent(item, k -> new ArrayList<>())
                            .add(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier));
                } else {
                    if (getConfig().shouldDebugLog()) {
                        if (item == Items.AIR) {
                            getLogger().warn("Food effect config: Item not found: {}", parts[0]);
                        }
                        if (effect == null) {
                            getLogger().warn("Food effect config: Effect not found: {}", parts[1]);
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().error("Failed to parse food effect entry: {}", entry, e);
            }
        }

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Food effects cache reloaded. {} items configured.", effectCache.size());
        }
    }

    @SubscribeEvent
    public void onFoodFinished(LivingEntityUseItemEvent.Finish event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only process on server side
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        ItemStack stack = event.getItem();
        Item item = stack.getItem();

        if (effectCache.containsKey(item)) {
            LivingEntity entity = event.getEntity();
            List<MobEffectInstance> effects = effectCache.get(item);

            for (MobEffectInstance effect : effects) {
                entity.addEffect(new MobEffectInstance(effect));
            }

            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Applied {} effects to {} after eating {}", effects.size(), entity.getName().getString(), item);
            }
        }
    }
}
