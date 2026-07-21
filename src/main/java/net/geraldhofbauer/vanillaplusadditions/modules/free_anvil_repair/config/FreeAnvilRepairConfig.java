package net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.FreeAnvilRepairModule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Configuration class for the Free Anvil Repair module.
 * Controls which pure-repair operations are free, whether they still bump the
 * prior-work penalty, and which extra repair materials (beyond vanilla's) are accepted.
 */
public class FreeAnvilRepairConfig
        extends AbstractModuleConfig<FreeAnvilRepairModule, FreeAnvilRepairConfig> {

    /**
     * Default extra repair materials (Quark-style): netherite gear repairs with diamonds,
     * Create's diving gear with its base material. Entries for missing items (e.g. Create
     * not installed) are skipped at runtime.
     */
    private static final List<String> DEFAULT_EXTRA_REPAIR_MATERIALS = List.of(
            "minecraft:netherite_sword=minecraft:diamond",
            "minecraft:netherite_pickaxe=minecraft:diamond",
            "minecraft:netherite_axe=minecraft:diamond",
            "minecraft:netherite_shovel=minecraft:diamond",
            "minecraft:netherite_hoe=minecraft:diamond",
            "minecraft:netherite_helmet=minecraft:diamond",
            "minecraft:netherite_chestplate=minecraft:diamond",
            "minecraft:netherite_leggings=minecraft:diamond",
            "minecraft:netherite_boots=minecraft:diamond",
            "create:netherite_diving_helmet=minecraft:diamond",
            "create:netherite_diving_boots=minecraft:diamond",
            "create:copper_diving_helmet=minecraft:copper_ingot",
            "create:copper_diving_boots=minecraft:copper_ingot",
            "minecraft:trident=minecraft:prismarine_shard",
            "minecraft:bow=minecraft:stick"
    );

    private ModConfigSpec.BooleanValue freeMaterialRepair;
    private ModConfigSpec.BooleanValue freeCombineRepair;
    private ModConfigSpec.BooleanValue increasePriorWorkPenalty;
    private ModConfigSpec.IntValue repairBoostPercent;
    private ModConfigSpec.ConfigValue<List<? extends String>> extraRepairMaterials;

    /**
     * Creates a new FreeAnvilRepairConfig.
     *
     * @param module The module this configuration belongs to
     */
    public FreeAnvilRepairConfig(FreeAnvilRepairModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        freeMaterialRepair = builder
                .comment("Repairing with the item's repair material (e.g. diamonds) costs no XP levels")
                .define("free_material_repair", true);

        freeCombineRepair = builder
                .comment("Combining two items of the same type costs no XP levels, "
                        + "as long as the sacrifice item is unenchanted (pure durability merge)")
                .define("free_combine_repair", true);

        increasePriorWorkPenalty = builder
                .comment("Whether free repairs still double the hidden prior-work penalty like vanilla does. "
                        + "Default false: repairing doesn't make later enchant operations more expensive.")
                .define("increase_prior_work_penalty", false);

        repairBoostPercent = builder
                .comment("Extra durability restored when repairing an item with its resource material "
                        + "(e.g. diamond sword + diamond), in percent. 0 = vanilla amounts; 50 = each "
                        + "material unit restores 50% more (still capped at full durability, so it just needs "
                        + "fewer materials to fully repair). Does NOT apply to combining two of the same item.")
                .defineInRange("repair_boost_percent", 50, 0, 500);

        extraRepairMaterials = builder
                .comment("Additional anvil repair materials (Quark-style), format item=material "
                        + "(e.g. minecraft:netherite_sword=minecraft:diamond).\n"
                        + "These repairs are handled by this module and are free like regular material repairs. "
                        + "An item may appear in multiple entries (several accepted materials).\n"
                        + "Entries whose item or material is not installed are skipped.")
                .defineList("extra_repair_materials", DEFAULT_EXTRA_REPAIR_MATERIALS,
                        () -> "minecraft:netherite_sword=minecraft:diamond", o -> {
                            if (!(o instanceof String s)) {
                                return false;
                            }
                            int eq = s.indexOf('=');
                            return eq > 0 && eq < s.length() - 1 && s.indexOf('=', eq + 1) < 0;
                        });
    }

    /**
     * Whether material repairs are free.
     *
     * @return true if material repairs cost no XP levels
     */
    public boolean isFreeMaterialRepairValue() {
        return freeMaterialRepair == null || freeMaterialRepair.get();
    }

    /**
     * Whether sacrifice (combine) repairs are free.
     *
     * @return true if combining unenchanted items of the same type costs no XP levels
     */
    public boolean isFreeCombineRepairValue() {
        return freeCombineRepair == null || freeCombineRepair.get();
    }

    /**
     * Whether free repairs still increase the prior-work penalty like vanilla.
     *
     * @return true if the penalty should keep doubling on free repairs
     */
    public boolean isIncreasePriorWorkPenaltyValue() {
        return increasePriorWorkPenalty != null && increasePriorWorkPenalty.get();
    }

    /**
     * Extra durability restored by module-handled repairs, in percent (0 = vanilla amounts).
     *
     * @return the configured repair boost percentage
     */
    public int getRepairBoostPercentValue() {
        return repairBoostPercent == null ? 50 : repairBoostPercent.get();
    }

    /**
     * Additional accepted repair materials in {@code item=material} format.
     *
     * @return the configured entries (never null)
     */
    public List<? extends String> getExtraRepairMaterialsValue() {
        return extraRepairMaterials == null ? DEFAULT_EXTRA_REPAIR_MATERIALS : extraRepairMaterials.get();
    }
}
