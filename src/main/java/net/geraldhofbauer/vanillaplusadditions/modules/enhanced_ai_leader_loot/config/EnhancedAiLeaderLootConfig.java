package net.geraldhofbauer.vanillaplusadditions.modules.enhanced_ai_leader_loot.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.enhanced_ai_leader_loot.EnhancedAiLeaderLootModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class EnhancedAiLeaderLootConfig
        extends AbstractModuleConfig<EnhancedAiLeaderLootModule, EnhancedAiLeaderLootConfig> {

    private static final List<String> DEFAULT_LEADER_BONUS_LOOT = List.of(
            "minecraft:golden_carrot;50;3;9",
            "minecraft:golden_apple;49;1;3",
            "minecraft:enchanted_golden_apple;1;1;1"
    );

    private ModConfigSpec.ConfigValue<List<? extends String>> leaderBonusLoot;

    public EnhancedAiLeaderLootConfig(EnhancedAiLeaderLootModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        leaderBonusLoot = builder
                .comment("Bonus loot rolled (in addition to normal mob loot) when an Enhanced AI "
                                + "\"leader\" mob dies. One entry is picked per kill, weighted — weights need "
                                + "not sum to 100, they're relative.",
                        "Format: item_id;weight;min_count;max_count",
                        "Example: " + DEFAULT_LEADER_BONUS_LOOT.get(0))
                .defineList("leader_bonus_loot", DEFAULT_LEADER_BONUS_LOOT,
                        () -> DEFAULT_LEADER_BONUS_LOOT.get(0), o -> {
                            if (!(o instanceof String s)) {
                                return false;
                            }
                            String[] parts = s.split(";");
                            if (parts.length != 4) {
                                return false;
                            }
                            try {
                                ResourceLocation.parse(parts[0]);
                                int weight = Integer.parseInt(parts[1]);
                                int minCount = Integer.parseInt(parts[2]);
                                int maxCount = Integer.parseInt(parts[3]);
                                return weight >= 1 && minCount >= 1 && maxCount >= minCount;
                            } catch (Exception e) {
                                return false;
                            }
                        });
    }

    public List<String> getLeaderBonusLoot() {
        return leaderBonusLoot != null
                ? new ArrayList<>(leaderBonusLoot.get())
                : new ArrayList<>(DEFAULT_LEADER_BONUS_LOOT);
    }
}
