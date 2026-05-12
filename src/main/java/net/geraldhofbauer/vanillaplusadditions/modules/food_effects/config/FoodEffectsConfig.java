package net.geraldhofbauer.vanillaplusadditions.modules.food_effects.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.food_effects.FoodEffectsModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class FoodEffectsConfig extends AbstractModuleConfig<FoodEffectsModule, FoodEffectsConfig> {

    private static final List<String> DEFAULT_FOOD_EFFECTS = List.of(
            // Speed effect (vanilla)
            "minecraft:cookie;minecraft:speed;160;1",
            // Heating effect (Tough As Nails)
            "minecraft:rabbit_stew;toughasnails:internal_warmth;24000;0",
            "minecraft:mushroom_stew;toughasnails:internal_warmth;12000;0",
            "minecraft:beetroot_soup;toughasnails:internal_warmth;12000;0",
            "rottencreatures:magma_rotten_flesh;toughasnails:internal_warmth;6000;0",
            "create:builders_tea;toughasnails:internal_warmth;3600;0",
            "toughasnails:sweet_berry_juice;toughasnails:internal_warmth;3600;0",
            // Cooling effect (Tough As Nails)
            "rottencreatures:frozen_rotten_flesh;toughasnails:internal_chill;6000;0",
            "toughasnails:cactus_juice;toughasnails:internal_chill;3600;0",
            // Thirst effect (Tough As Nails)
            "minecraft:golden_apple;toughasnails:thirst;600;0;0.25",
            "minecraft:enchanted_golden_apple;toughasnails:thirst;600;0;0.25",
            "minecraft:golden_carrot;toughasnails:thirst;600;0;0.25",
            // Other effects
            "minecraft:glow_berries;minecraft:glowing;60;0",
            "toughasnails:melon_juice;minecraft:regeneration;60;0",
            "toughasnails:glow_berry_juice;minecraft:glowing;120;0",
            "toughasnails:chorus_fruit_juice;minecraft:jump_boost;240;1",
            "minecraft:enchanted_golden_apple;toughasnails:climate_clemency;6000;0;1.0",
            // Create effects
            "create:sweet_roll;minecraft:speed;120;0",
            "create:bar_of_chocolate;minecraft:speed;600;0",
            "create:bar_of_chocolate;minecraft:jump_boost;600;0",
            "create:chocolate_glazed_berries;minecraft:speed;60;0",
            "create:chocolate_glazed_berries;minecraft:jump_boost;60;0"
    );

    private static final List<String> DEFAULT_THIRST_EFFECTS = List.of(
            "create:builders_tea;2;",
            "minecraft:beetroot_soup;6;",
            "minecraft:mushroom_stew;2;",
            "minecraft:rabbit_stew;2;",
            "minecraft:melon_slice;2;"
    );

    private ModConfigSpec.ConfigValue<List<? extends String>> foodEffects;
    private ModConfigSpec.ConfigValue<List<? extends String>> thirstEffects;

    public FoodEffectsConfig(FoodEffectsModule module) {
        super(module);
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec);
        getModule().reloadEffectCache();
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        foodEffects = builder
                .comment("List of food effects. Format: item_id;effect_id;duration_in_ticks;amplifier;chance\n"
                        + "Example: minecraft:apple;minecraft:speed;200;0;1.0")
                .defineList("food_effects", DEFAULT_FOOD_EFFECTS, () -> "minecraft:apple;minecraft:speed;200;0;1.0", o -> {
                    if (!(o instanceof String s)) {
                        return false;
                    }
                    String[] parts = s.split(";");
                    if (parts.length < 3 || parts.length > 5) {
                        return false;
                    }
                    try {
                        ResourceLocation.parse(parts[0]);
                        ResourceLocation.parse(parts[1]);
                        int duration = Integer.parseInt(parts[2]);
                        if (duration < 0) {
                            return false;
                        }
                        if (parts.length >= 4) {
                            int amplifier = Integer.parseInt(parts[3]);
                            if (amplifier < 0) {
                                return false;
                            }
                        }
                        if (parts.length == 5) {
                            float chance = Float.parseFloat(parts[4]);
                            if (chance < 0 || chance > 1) {
                                return false;
                            }
                        }
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });

        thirstEffects = builder
                .comment("List of thirst effects. Format: item_id;thirst_amount;chance\n"
                        + "Example: create:builders_tea;2;1.0")
                .defineList("thirst_effects", DEFAULT_THIRST_EFFECTS, () -> "create:builders_tea;2;1.0", o -> {
                    if (!(o instanceof String s)) {
                        return false;
                    }
                    String[] parts = s.split(";");
                    if (parts.length < 2 || parts.length > 3) {
                        return false;
                    }
                    try {
                        ResourceLocation.parse(parts[0]);
                        int amount = Integer.parseInt(parts[1]);
                        if (amount < 0) {
                            return false;
                        }
                        if (parts.length == 3) {
                            float chance = Float.parseFloat(parts[2]);
                            return chance >= 0 && chance <= 1;
                        }
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    public List<String> getFoodEffects() {
        return foodEffects != null ? new ArrayList<>(foodEffects.get()) : new ArrayList<>(DEFAULT_FOOD_EFFECTS);
    }

    public List<String> getThirstEffects() {
        return thirstEffects != null ? new ArrayList<>(thirstEffects.get()) : new ArrayList<>(DEFAULT_THIRST_EFFECTS);
    }
}
