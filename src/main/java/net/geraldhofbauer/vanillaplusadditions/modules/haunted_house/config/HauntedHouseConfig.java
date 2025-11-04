package net.geraldhofbauer.vanillaplusadditions.modules.haunted_house.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.haunted_house.HauntedHouseModule;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration class for the Haunted House module.
 * This class handles configuration for mob replacements in specific structures.
 */
public class HauntedHouseConfig
        extends AbstractModuleConfig<HauntedHouseModule, HauntedHouseConfig> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HauntedHouseConfig.class);

    // Configuration values
    private ModConfigSpec.ConfigValue<List<? extends String>> targetMobs;
    private ModConfigSpec.ConfigValue<List<? extends String>> targetStructures;
    
    // Cached parsed values
    private final Map<String, Double> mobReplacementRates;

    /**
     * Creates a new HauntedHouseConfig.
     *
     * @param module The module this configuration belongs to
     */
    public HauntedHouseConfig(HauntedHouseModule module) {
        super(module);
        this.mobReplacementRates = new HashMap<>();
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        targetMobs = builder
                .comment("List of mobs to replace with murmurs in the format 'namespace:mob_id:replacement_rate'",
                        "Example: 'minecraft:witch:10' means 10% of witches will be replaced with murmurs")
                .defineList("target_mobs",
                        List.of("minecraft:witch:10"),
                        obj -> obj instanceof String && isValidMobEntry((String) obj));

        targetStructures = builder
                .comment("List of structure IDs where mob replacements should occur",
                        "Example: 'nova_structures:witch_villa'")
                .defineList("target_structures",
                        List.of("nova_structures:witch_villa"),
                        obj -> obj instanceof String && isValidStructureEntry((String) obj));

        LOGGER.debug("Built module-specific configuration for Haunted House module");
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec); // Call parent to handle enabled logging
        
        // Parse and cache mob replacement rates
        parseMobReplacementRates();
        
        LOGGER.debug("Module-specific configuration loaded for Haunted House module");
        if (targetMobs != null && targetStructures != null) {
            LOGGER.debug("  - Target mobs: {}", targetMobs.get());
            LOGGER.debug("  - Target structures: {}", targetStructures.get());
            LOGGER.debug("  - Parsed replacement rates: {}", mobReplacementRates);
        }
    }

    /**
     * Validates if a mob entry string is in the correct format.
     *
     * @param entry The mob entry string to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidMobEntry(String entry) {
        if (entry == null || entry.isEmpty()) {
            return false;
        }
        String[] parts = entry.split(":");
        if (parts.length != 3) {
            LOGGER.warn("Invalid mob entry format: '{}'. Expected format: 'namespace:mob_id:replacement_rate'", entry);
            return false;
        }
        try {
            double rate = Double.parseDouble(parts[2]);
            if (rate < 0 || rate > 100) {
                LOGGER.warn("Invalid replacement rate in entry '{}'. Rate must be between 0 and 100", entry);
                return false;
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid replacement rate in entry '{}'. Must be a number", entry);
            return false;
        }
        return true;
    }

    /**
     * Validates if a structure entry string is in the correct format.
     *
     * @param entry The structure entry string to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidStructureEntry(String entry) {
        if (entry == null || entry.isEmpty()) {
            return false;
        }
        String[] parts = entry.split(":");
        if (parts.length != 2) {
            LOGGER.warn("Invalid structure entry format: '{}'. Expected format: 'namespace:structure_id'", entry);
            return false;
        }
        return true;
    }

    /**
     * Parses the mob replacement rates from the configuration.
     */
    private void parseMobReplacementRates() {
        mobReplacementRates.clear();
        
        if (targetMobs == null) {
            return;
        }

        for (String entry : targetMobs.get()) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                String mobId = parts[0] + ":" + parts[1];
                try {
                    double rate = Double.parseDouble(parts[2]);
                    mobReplacementRates.put(mobId, rate / 100.0); // Convert percentage to decimal
                } catch (NumberFormatException e) {
                    LOGGER.error("Failed to parse replacement rate for mob entry: {}", entry, e);
                }
            }
        }
    }

    /**
     * Gets the replacement rate for a specific mob.
     *
     * @param mobId The mob ID (e.g., "minecraft:witch")
     * @return The replacement rate as a decimal (0.0 to 1.0), or 0.0 if not configured
     */
    public double getReplacementRate(String mobId) {
        return mobReplacementRates.getOrDefault(mobId, 0.0);
    }

    /**
     * Checks if a mob should be replaced based on configuration.
     *
     * @param mobId The mob ID (e.g., "minecraft:witch")
     * @return true if the mob is in the replacement list, false otherwise
     */
    public boolean shouldReplaceMob(String mobId) {
        return mobReplacementRates.containsKey(mobId);
    }

    /**
     * Gets the list of configured target structures.
     *
     * @return List of structure IDs
     */
    public List<String> getTargetStructures() {
        if (targetStructures == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(targetStructures.get());
    }

    /**
     * Checks if a structure is in the target list.
     *
     * @param structureId The structure ID to check
     * @return true if the structure is a target, false otherwise
     */
    public boolean isTargetStructure(String structureId) {
        if (targetStructures == null) {
            return false;
        }
        
        for (String targetStructure : targetStructures.get()) {
            if (structureId.contains(targetStructure)) {
                return true;
            }
        }
        return false;
    }
}
