package net.geraldhofbauer.vanillaplusadditions.modules.mob_drops.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_drops.MobDropsModule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the Mob Drops module.
 *
 * Configuration format: mob;item;chance[;max_drops]
 * Example: minecraft:wither_skeleton;minecraft:wither_skeleton_skull;0.5;2
 */
public class MobDropsConfig extends AbstractModuleConfig<MobDropsModule, MobDropsConfig> {

    private static final List<String> DEFAULT_MOB_DROPS = List.of(
            "minecraft:wither_skeleton;minecraft:wither_skeleton_skull;0.125",
            "minecraft:wither_skeleton;minecraft:golden_apple;0.4",
            "minecraft:wither_skeleton;minecraft:netherite_scrap;0.1",
            "minecraft:warden;minecraft:enchanted_golden_apple;1;3"
    );

    private ModConfigSpec.ConfigValue<List<? extends String>> mobDrops;

    public MobDropsConfig(MobDropsModule module) {
        super(module);
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec);
        getModule().reloadMobDropsCache();
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        mobDrops = builder
                .comment("List of additional mob drops. Format: mob_id;item_id;chance[;max_drops]\n"
                        + "Example: minecraft:wither_skeleton;minecraft:wither_skeleton_skull;0.5;2")
                .defineList("mob_drops", DEFAULT_MOB_DROPS, () -> "minecraft:wither_skeleton;minecraft:wither_skeleton_skull;0.5;1", o -> {
                    if (!(o instanceof String s)) {
                        return false;
                    }
                    String[] parts = s.split(";");
                    if (parts.length < 3 || parts.length > 4) {
                        return false;
                    }
                    try {
                        float chance = Float.parseFloat(parts[2]);
                        if (chance < 0.0f || chance > 1.0f) {
                            return false;
                        }
                        if (parts.length == 4) {
                            Integer.parseInt(parts[3]); // Validate max_drops is a number
                        }
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
    }

    public List<String> getMobDrops() {
        return mobDrops != null ? new ArrayList<>(mobDrops.get()) : new ArrayList<>(DEFAULT_MOB_DROPS);
    }
}
