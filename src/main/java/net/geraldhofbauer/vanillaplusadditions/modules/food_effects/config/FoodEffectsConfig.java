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
            "minecraft:rabbit_stew;toughasnails:heating;72000;0",
            "minecraft:mushroom_stew;toughasnails:heating;12000;0",
            "minecraft:beetroot_soup;toughasnails:heating;12000;0",
            "rottencreatures:magma_rotten_flesh;toughasnails:heating;36000;0",
            // Cooling effect (Tough As Nails)
            "rottencreatures:frozen_rotten_flesh;toughasnails:cooling;36000;0"
    );

    private ModConfigSpec.ConfigValue<List<? extends String>> foodEffects;

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
                .comment("List of food effects. Format: item_id;effect_id;duration_in_ticks;amplifier\n"
                        + "Example: minecraft:apple;minecraft:speed;200;0")
                .defineList("food_effects", DEFAULT_FOOD_EFFECTS, () -> "minecraft:apple;minecraft:speed;200;0", o -> {
                    if (!(o instanceof String s)) {
                        return false;
                    }
                    String[] parts = s.split(";");
                    if (parts.length < 3 || parts.length > 4) {
                        return false;
                    }
                    try {
                        ResourceLocation.parse(parts[0]);
                        ResourceLocation.parse(parts[1]);
                        int duration = Integer.parseInt(parts[2]);
                        if (duration < 0) {
                            return false;
                        }
                        if (parts.length == 4) {
                            int amplifier = Integer.parseInt(parts[3]);
                            if (amplifier < 0) {
                                return false;
                            }
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
}
