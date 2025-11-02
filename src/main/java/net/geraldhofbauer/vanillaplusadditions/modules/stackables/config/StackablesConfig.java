package net.geraldhofbauer.vanillaplusadditions.modules.stackables.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.stackables.StackablesModule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class StackablesConfig extends AbstractModuleConfig<StackablesModule, StackablesConfig> {

    public static final List<String> DEFAULT_STACKABLES = List.of(
            "minecraft:mushroom_stew:64",
            "minecraft:rabbit_stew:64",
            "minecraft:beetroot_soup:64",
            "minecraft:suspicious_stew:64",
            // tough as nails water bottles (keep as 64 by default)
            "toughasnails:dirty_water_bottle:64",
            "toughasnails:purified_water_bottle:64",
            // tough as nails juices / ice cream / charc-os — default to max stack 64
            "toughasnails:apple_juice:64",
            "toughasnails:cactus_juice:64",
            "toughasnails:chorus_fruit_juice:64",
            "toughasnails:glow_berry_juice:64",
            "toughasnails:melon_juice:64",
            "toughasnails:pumpkin_juice:64",
            "toughasnails:sweet_berry_juice:64",
            "toughasnails:ice_cream:64",
            "toughasnails:charc_os:64"
    );

    private ModConfigSpec.ConfigValue<List<? extends String>> stackableItems;
    private ModConfigSpec.IntValue defaultPotionStackSize;

    public StackablesConfig(StackablesModule module) {
        super(module);
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec);
        getModule().getModuleLogger().info("StackablesConfig loaded - potion stack size: {}, stackable items count: {}",
                getDefaultPotionStackSize(), getStackableItems().size());
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        // Default list of items to make stackable with their default stack sizes (format: namespace:id:stack)
        // NOTE: The toughasnails item IDs below are assumed/guessed; please verify actual IDs in your mod if necessary.

        stackableItems = builder
                .comment("List of item entries to make stackable. "
                        + "Each entry must use the format namespace:id:stack (e.g. minecraft:mushroom_stew:64).\n"
                        + "The last part is the desired max stack size (1-64). If an item is not present it will be skipped.")
                .defineList("stackable_items", DEFAULT_STACKABLES, () -> "minecraft:mushroom_stew:64", o -> {
                    if (!(o instanceof String s)) {
                        return false;
                    }
                    int lastColon = s.lastIndexOf(':');
                    if (lastColon <= 0 || lastColon == s.length() - 1) {
                        return false;
                    }
                    String resourcePart = s.substring(0, lastColon);
                    // resourcePart must itself contain exactly one colon separating namespace and id
                    int midColon = resourcePart.indexOf(':');
                    if (midColon <= 0 || midColon == resourcePart.length() - 1) {
                        return false;
                    }
                    String stackPart = s.substring(lastColon + 1);
                    try {
                        int v = Integer.parseInt(stackPart);
                        return v >= 1 && v <= 64;
                    } catch (NumberFormatException ex) {
                        return false;
                    }
                });

        defaultPotionStackSize = builder
                .comment("Default stack size for potions, splash potions, lingering potions, and tipped arrows.")
                .defineInRange("default_potion_stack_size", 16, 1, 64);
    }

    public java.util.List<String> getStackableItems() {
        if (stackableItems != null) {
            try {
                return List.copyOf(stackableItems.get());
            } catch (Exception e) {
                getModule().getModuleLogger().debug("Config not yet loaded for stackable_items, using defaults;\n"
                        + "Error: {}", e.getMessage());
            }
        }
        // Return hardcoded defaults if config not available
        return DEFAULT_STACKABLES;
    }

    public int getDefaultPotionStackSize() {
        if (defaultPotionStackSize != null) {
            try {
                return defaultPotionStackSize.get();
            } catch (Exception e) {
                getModule().getModuleLogger().debug("Config not yet loaded for default_potion_stack_size, using default: 16 - {}", e.getMessage());
            }
        }
        return 16;
    }
}
