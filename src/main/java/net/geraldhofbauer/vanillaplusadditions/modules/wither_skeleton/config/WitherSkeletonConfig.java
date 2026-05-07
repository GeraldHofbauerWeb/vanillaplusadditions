package net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.WitherSkeletonModule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class WitherSkeletonConfig extends AbstractModuleConfig<WitherSkeletonModule, WitherSkeletonConfig> {

    private static final List<String> DEFAULT_ADDITIONAL_DROPS = List.of(
            "minecraft:wither_skeleton_skull;0.125",
            "minecraft:golden_apple;0.4",
            "minecraft:netherite_scrap;0.1"
    );

    private ModConfigSpec.ConfigValue<List<? extends String>> additionalDrops;

    public WitherSkeletonConfig(WitherSkeletonModule module) {
        super(module);
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec);
        getModule().reloadAdditionalDropsCache();
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        additionalDrops = builder
                .comment("List of additional drops for Wither Skeletons. Format: item_id;chance\n"
                        + "Example: minecraft:golden_apple;0.4")
                .defineList("additional_drops", DEFAULT_ADDITIONAL_DROPS, () -> "minecraft:golden_apple;0.4", o -> {
                    if (!(o instanceof String s)) {
                        return false;
                    }
                    String[] parts = s.split(";");
                    if (parts.length != 2) {
                        return false;
                    }
                    try {
                        float chance = Float.parseFloat(parts[1]);
                        return chance >= 0.0f && chance <= 1.0f;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
    }

    public List<String> getAdditionalDrops() {
        return additionalDrops != null ? new ArrayList<>(additionalDrops.get()) : new ArrayList<>(DEFAULT_ADDITIONAL_DROPS);
    }
}
