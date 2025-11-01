package net.geraldhofbauer.vanillaplusadditions.modules.stackables.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.stackables.StackablesModule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class StackablesConfig extends AbstractModuleConfig<StackablesModule, StackablesConfig> {

    private ModConfigSpec.ConfigValue<List<? extends String>> stackableItems;

    public StackablesConfig(StackablesModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        // Default list of items to make stackable with their default stack sizes (format: namespace:id:stack)
        // NOTE: The toughasnails item IDs below are assumed/guessed; please verify actual IDs in your mod if necessary.
        List<String> defaults = List.of(
                "minecraft:mushroom_stew:64",
                "minecraft:rabbit_stew:64",
                "minecraft:beetroot_soup:64",
                "minecraft:suspicious_stew:64",
                // tough as nails water bottles (keep as 64 by default)
                "toughasnails:dirty_water_bottle:64",
                "toughasnails:purified_water_bottle:64",
                // tough as nails juices / ice cream / charc-os — default to max stack 16 (user asked)
                // These IDs are common guesses; verify and adjust to the mod's actual resource names if needed.
                "toughasnails:juice:16",
                "toughasnails:apple_juice:16",
                "toughasnails:melon_juice:16",
                "toughasnails:ice_cream:16",
                "toughasnails:ice_cream_vanilla:16",
                "toughasnails:charc_os:16"
        );

        stackableItems = builder
                .comment("List of item entries to make stackable. "
                        + "Each entry must use the format namespace:id:stack (e.g. minecraft:mushroom_stew:64).\n"
                        + "The last part is the desired max stack size (1-64). If an item is not present it will be skipped.")
                .defineList("stackable_items", defaults, () -> "minecraft:mushroom_stew:64", o -> {
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
    }

    public java.util.List<String> getStackableItems() {
        // FIXME: temporarily disabled to avoid config system issues
        return /*stackableItems != null ? List.copyOf(stackableItems.get()) :*/ List.of(
                "minecraft:mushroom_stew:64",
                "minecraft:rabbit_stew:64",
                "minecraft:beetroot_soup:64",
                "minecraft:suspicious_stew:64",
                "toughasnails:dirty_water_bottle:64",
                "toughasnails:purified_water_bottle:64",
                "toughasnails:juice:16",
                "toughasnails:apple_juice:16",
                "toughasnails:melon_juice:16",
                "toughasnails:ice_cream:16",
                "toughasnails:ice_cream_vanilla:16",
                "toughasnails:charc_os:16"
        );
    }
}
