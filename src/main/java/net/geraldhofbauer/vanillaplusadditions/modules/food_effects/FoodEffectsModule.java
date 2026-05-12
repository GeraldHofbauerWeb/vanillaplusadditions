package net.geraldhofbauer.vanillaplusadditions.modules.food_effects;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.config.FoodEffectsConfig;
import net.geraldhofbauer.vanillaplusadditions.util.MessageBroadcaster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.client.ThirstTooltipData;
import net.neoforged.fml.ModList;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.compat.TANIntegration;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoodEffectsModule extends AbstractModule<FoodEffectsModule, FoodEffectsConfig> {

    private static final String HEATING_EFFECT_ID = "toughasnails:internal_warmth";
    private static final String COOLING_EFFECT_ID = "toughasnails:internal_chill";

    private final Map<Item, List<EffectEntry>> effectCache = new HashMap<>();
    private final Map<Item, ThirstEntry> thirstCache = new HashMap<>();
    private boolean isTANLoaded;

    private record EffectEntry(MobEffectInstance effect, float chance) { }
    private record ThirstEntry(int amount, float chance) { }

    public FoodEffectsModule() {
        super("food_effects",
                "Food Effects",
                "Allows adding potion effects to food items via configuration.",
                FoodEffectsConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        isTANLoaded = ModList.get().isLoaded("toughasnails");
        NeoForge.EVENT_BUS.register(this);
        getModEventBus().addListener(this::onModifyDefaultComponents);
    }

    @Override
    protected void onLoadComplete() {
        reloadEffectCache();
    }

    public void reloadEffectCache() {
        effectCache.clear();
        thirstCache.clear();
        if (!isModuleEnabled()) {
            return;
        }

        loadFoodEffects();
        loadThirstEffects();

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Food effects cache reloaded. {} items configured ({} effects, {} thirst).",
                    effectCache.size() + thirstCache.size(), effectCache.size(), thirstCache.size());
        }
    }

    private void loadFoodEffects() {
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
                float chance = parts.length >= 5 ? Float.parseFloat(parts[4]) : 1.0f;

                if (item != Items.AIR && effect != null) {
                    effectCache.computeIfAbsent(item, k -> new ArrayList<>())
                            .add(new EffectEntry(new MobEffectInstance(
                                    BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier), chance));
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
    }

    private void loadThirstEffects() {
        if (!isTANLoaded) {
            return;
        }
        List<String> entries = getConfig().getThirstEffects();
        for (String entry : entries) {
            String[] parts = entry.split(";");
            if (parts.length < 2) {
                continue;
            }

            try {
                ResourceLocation itemRl = ResourceLocation.parse(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                float chance = parts.length >= 3 ? Float.parseFloat(parts[2]) : 1.0f;

                Item item = BuiltInRegistries.ITEM.get(itemRl);

                if (item != Items.AIR) {
                    thirstCache.put(item, new ThirstEntry(amount, chance));
                } else {
                    if (getConfig().shouldDebugLog()) {
                        getLogger().warn("Thirst effect config: Item not found: {}", parts[0]);
                    }
                }
            } catch (Exception e) {
                getLogger().error("Failed to parse thirst effect entry: {}", entry, e);
            }
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

        MessageBroadcaster.broadcastDebug(
                (ServerLevel) event.getEntity().level(),
                getConfig().shouldDebugLog(),
                "Consumed food item: " + item.getDescriptionId(),
                getLogger());

        // Log all food cache entries
        if (getConfig().shouldDebugLog()) {
            MessageBroadcaster.broadcastDebug(
                    (ServerLevel) event.getEntity().level(),
                    getConfig().shouldDebugLog(),
                    "Food cache entries: " + effectCache.size(),
                    getLogger());
            for (Map.Entry<Item, List<EffectEntry>> entry : effectCache.entrySet()) {
                MessageBroadcaster.broadcastDebug(
                        (ServerLevel) event.getEntity().level(),
                        getConfig().shouldDebugLog(),
                        "Food item: " + entry.getKey().getDescriptionId() + ", Effects: " + entry.getValue().size(),
                        getLogger());
            }
        }

        if (effectCache.containsKey(item)) {
            LivingEntity entity = event.getEntity();
            List<EffectEntry> entries = effectCache.get(item);

            for (EffectEntry entry : entries) {
                if (entity.getRandom().nextFloat() <= entry.chance()) {
                    entity.addEffect(new MobEffectInstance(entry.effect()));
                }
            }

            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Applied effects to {} after eating {}", entity.getName().getString(), item);
            }
        }

        if (thirstCache.containsKey(item) && event.getEntity() instanceof Player player) {
            ThirstEntry entry = thirstCache.get(item);
            if (isTANLoaded && player.getRandom().nextFloat() <= entry.chance()) {
                TANIntegration.applyThirst(player, entry.amount());

                if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Restored {} thirst to {} after drinking {}", entry.amount(), player.getName().getString(), item);
                }
            }
        }
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        Item item = event.getItemStack().getItem();
        if (effectCache.containsKey(item)) {
            List<EffectEntry> entries = effectCache.get(item);
            for (EffectEntry entry : entries) {
                MobEffectInstance effect = entry.effect();
                ResourceLocation effectRl = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
                if (effectRl == null) {
                    continue;
                }

                String effectId = effectRl.toString();
                if (effectId.equals(HEATING_EFFECT_ID)) {
                    event.getToolTip().add(Component.literal("\uD83D\uDD25 ")
                            .append(Component.translatable("desc.toughasnails.heating_consumed"))
                            .withStyle(ChatFormatting.GOLD));
                } else if (effectId.equals(COOLING_EFFECT_ID)) {
                    event.getToolTip().add(Component.literal("\u2744 ")
                            .append(Component.translatable("desc.toughasnails.cooling_consumed"))
                            .withStyle(ChatFormatting.AQUA));
                }
            }
        }
    }

    @SubscribeEvent
    public void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        if (!isModuleEnabled() || !isTANLoaded) {
            return;
        }

        Item item = event.getItemStack().getItem();
        if (thirstCache.containsKey(item)) {
            ThirstEntry entry = thirstCache.get(item);
            event.getTooltipElements().add(com.mojang.datafixers.util.Either.right(new ThirstTooltipData(entry.amount(), entry.chance())));
        }
    }

    private void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        List<String> entries = getConfig().getFoodEffects();
        for (String entry : entries) {
            processItemForFoodComponent(event, entry);
        }

        List<String> thirstEntries = getConfig().getThirstEffects();
        for (String entry : thirstEntries) {
            processItemForFoodComponent(event, entry);
        }
    }

    private void processItemForFoodComponent(ModifyDefaultComponentsEvent event, String entry) {
        String[] parts = entry.split(";");
        if (parts.length < 1) {
            return;
        }

        try {
            ResourceLocation itemRl = ResourceLocation.parse(parts[0]);
            Item item = BuiltInRegistries.ITEM.get(itemRl);

            if (item != Items.AIR) {
                event.modify(item, builder -> {
                    ItemStack stack = new ItemStack(item);
                    FoodProperties existingFood = stack.get(DataComponents.FOOD);
                    FoodProperties.Builder foodBuilder;
                    if (existingFood != null) {
                        foodBuilder = new FoodProperties.Builder()
                                .nutrition(existingFood.nutrition())
                                .saturationModifier(existingFood.saturation())
                                .alwaysEdible();
                        // Copy effects if any
                        existingFood.effects().forEach(effect ->
                                foodBuilder.effect(effect::effect, effect.probability()));
                    } else {
                        // If it's not food yet, make it food so it can be eaten
                        foodBuilder = new FoodProperties.Builder()
                                .nutrition(0)
                                .saturationModifier(0)
                                .alwaysEdible();
                    }
                    builder.set(DataComponents.FOOD, foodBuilder.build());
                });
            }
        } catch (Exception e) {
            // Ignore parse errors here
        }
    }
}
