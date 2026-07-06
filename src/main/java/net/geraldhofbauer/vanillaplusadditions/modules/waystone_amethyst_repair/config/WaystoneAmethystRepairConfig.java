package net.geraldhofbauer.vanillaplusadditions.modules.waystone_amethyst_repair.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.waystone_amethyst_repair.WaystoneAmethystRepairModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Configuration for the Waystone Amethyst Repair module.
 * Controls which items count as the repaired Warp Stone, which amethyst-like items repair it,
 * and how much durability each material unit restores.
 */
public class WaystoneAmethystRepairConfig
        extends AbstractModuleConfig<WaystoneAmethystRepairModule, WaystoneAmethystRepairConfig> {

    /** The Waystones item that this module makes repairable. */
    private static final String DEFAULT_TARGET_ITEM = "waystones:warp_stone";

    /** Amethyst is the thematic recharge material for the Warp Stone. */
    private static final List<String> DEFAULT_REPAIR_MATERIALS = List.of(
            "minecraft:amethyst_shard"
    );

    private ModConfigSpec.ConfigValue<String> targetItem;
    private ModConfigSpec.ConfigValue<List<? extends String>> repairMaterials;
    private ModConfigSpec.IntValue repairPercentPerUnit;

    /**
     * Creates a new WaystoneAmethystRepairConfig.
     *
     * @param module The module this configuration belongs to
     */
    public WaystoneAmethystRepairConfig(WaystoneAmethystRepairModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        targetItem = builder
                .comment("The damageable item made repairable by this module (Waystones Warp Stone by default). "
                        + "If the item is not installed, the module stays inert.")
                .define("target_item", DEFAULT_TARGET_ITEM,
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);

        repairMaterials = builder
                .comment("Item ids accepted as repair material in the anvil (amethyst shards by default). "
                        + "Entries whose item is not installed are skipped.")
                .defineList("repair_materials", DEFAULT_REPAIR_MATERIALS,
                        () -> "minecraft:amethyst_shard",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);

        repairPercentPerUnit = builder
                .comment("How much of the item's maximum durability a single material unit restores, in percent. "
                        + "Vanilla material repairs use 25.")
                .defineInRange("repair_percent_per_unit", 25, 1, 100);
    }

    /**
     * The configured repairable target item id.
     *
     * @return the item id string (never null)
     */
    public String getTargetItemValue() {
        return targetItem == null ? DEFAULT_TARGET_ITEM : targetItem.get();
    }

    /**
     * The configured accepted repair material item ids.
     *
     * @return the configured entries (never null)
     */
    public List<? extends String> getRepairMaterialsValue() {
        return repairMaterials == null ? DEFAULT_REPAIR_MATERIALS : repairMaterials.get();
    }

    /**
     * Durability restored per material unit, as a fraction of max durability.
     *
     * @return the percent (1-100)
     */
    public int getRepairPercentPerUnitValue() {
        return repairPercentPerUnit == null ? 25 : repairPercentPerUnit.get();
    }
}
