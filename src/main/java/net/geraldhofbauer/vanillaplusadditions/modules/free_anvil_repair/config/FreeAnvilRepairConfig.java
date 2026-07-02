package net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.FreeAnvilRepairModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration class for the Free Anvil Repair module.
 * Controls which pure-repair operations are free and whether they still bump the
 * prior-work penalty.
 */
public class FreeAnvilRepairConfig
        extends AbstractModuleConfig<FreeAnvilRepairModule, FreeAnvilRepairConfig> {

    private ModConfigSpec.BooleanValue freeMaterialRepair;
    private ModConfigSpec.BooleanValue freeCombineRepair;
    private ModConfigSpec.BooleanValue increasePriorWorkPenalty;

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
}
