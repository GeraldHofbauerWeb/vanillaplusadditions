package net.geraldhofbauer.vanillaplusadditions.modules.haunted_house.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.haunted_house.HauntedHouseModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration class for the Haunted House module.
 * This class handles configuration for mob replacements in specific structures.
 */
public class HauntedHouseConfig
        extends AbstractModuleConfig<HauntedHouseModule, HauntedHouseConfig> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HauntedHouseConfig.class);

    private enum SpawnPreset {
        CUSTOM,
        BALANCED,
        STRUCTURE_FOCUSED,
        COURTYARD
    }

    // Configuration values
    private ModConfigSpec.ConfigValue<List<? extends String>> targetMobs;
    private ModConfigSpec.ConfigValue<List<? extends String>> targetStructures;
    private ModConfigSpec.ConfigValue<String> replacementEntityId;
    private ModConfigSpec.ConfigValue<String> spawnPreset;
    private ModConfigSpec.DoubleValue witchSpawnBoostChance;
    private ModConfigSpec.BooleanValue enableFogEffect;
    private ModConfigSpec.IntValue fogEffectAmplifier;
    private ModConfigSpec.IntValue caveDepthTolerance;
    private ModConfigSpec.IntValue skyAccessNearBuildingRadius;
    private ModConfigSpec.DoubleValue skyAccessAroundBuildingChance;
    private ModConfigSpec.IntValue distributionAttempts;
    private ModConfigSpec.IntValue distributionRadius;
    private ModConfigSpec.IntValue minDistanceToOtherMobs;
    private ModConfigSpec.IntValue materialScanHorizontalRadius;
    private ModConfigSpec.IntValue materialScanVerticalRadius;
    private ModConfigSpec.IntValue caveMaterialThreshold;
    private ModConfigSpec.IntValue structureMaterialThreshold;
    private ModConfigSpec.ConfigValue<List<? extends String>> structureMaterialBlocks;
    private ModConfigSpec.ConfigValue<List<? extends String>> undergroundMaterialBlocks;
    private ModConfigSpec.BooleanValue enableDirectAreaSpawning;
    private ModConfigSpec.DoubleValue directSpawnAttemptChance;
    private ModConfigSpec.IntValue directSpawnIntervalTicks;
    private ModConfigSpec.DoubleValue directSpawnReplacementChance;
    private ModConfigSpec.IntValue directSpawnCandidateSamples;
    private ModConfigSpec.IntValue areaScanRadius;
    private ModConfigSpec.IntValue cacheTtlSeconds;
    private ModConfigSpec.IntValue directSpawnMinPlayerDistance;
    private ModConfigSpec.IntValue directSpawnMaxPlayerDistance;
    private ModConfigSpec.IntValue maxCachedSpawnSpotsPerLevel;
    private ModConfigSpec.IntValue fogCacheProximityRadius;
    private ModConfigSpec.IntValue fogIndoorBaseDurationTicks;
    private ModConfigSpec.IntValue fogGardenBaseDurationTicks;
    private ModConfigSpec.IntValue fogTrailMaxTicks;
    private ModConfigSpec.IntValue fogTrailDecayTicks;

    // Cached parsed values
    private final Map<String, Double> mobReplacementRates;
    private final Set<ResourceLocation> configuredStructureMaterialBlockIds;
    private final Set<ResourceLocation> configuredUndergroundMaterialBlockIds;

    /**
     * Creates a new HauntedHouseConfig.
     *
     * @param module The module this configuration belongs to
     */
    public HauntedHouseConfig(HauntedHouseModule module) {
        super(module);
        this.mobReplacementRates = new HashMap<>();
        this.configuredStructureMaterialBlockIds = new HashSet<>();
        this.configuredUndergroundMaterialBlockIds = new HashSet<>();
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        targetMobs = builder
                .comment("List of mobs to replace with the replacement entity in the format 'namespace:mob_id:replacement_rate'",
                        "Example: 'minecraft:witch:10' means 10% of witches will be replaced with murmurs")
                .defineListAllowEmpty("target_mobs",
                        List.of("minecraft:witch:10"),
                        () -> "",
                        obj -> obj instanceof String && isValidMobEntry((String) obj));

        targetStructures = builder
                .comment("List of structure IDs where mob replacements should occur",
                        "Example: 'dungeons_and_taverns:witch_villa'")
                .defineListAllowEmpty("target_structures",
                        List.of("nova_structures:witch_villa", "dungeons_and_taverns:witch_villa"),
                        () -> "",
                        obj -> obj instanceof String && isValidStructureEntry((String) obj));

        replacementEntityId = builder
                .comment("Entity ID spawned as invisible replacement in target structures",
                        "Example: 'alexsmobs:murmur' or 'minecraft:witch'")
                .define("replacement_entity_id", "minecraft:witch",
                        obj -> obj instanceof String && isValidEntityId((String) obj));

        spawnPreset = builder
                .comment("Spawn tuning preset for haunted structures",
                        "Values: custom, balanced, structure_focused, courtyard",
                        "custom = use the individual tuning values below")
                .define("spawn_preset", "structure_focused",
                        obj -> obj instanceof String && isValidPreset((String) obj));

        witchSpawnBoostChance = builder
                .comment("Chance (0-100%) that a mob spawn in target structures will be replaced with a witch",
                        "This helps ensure more witches spawn in witch villas for replacement",
                        "Set to 0 to disable witch spawning boost")
                .defineInRange("witch_spawn_boost_chance", 50.0, 0.0, 100.0);

        enableFogEffect = builder
                .comment("Enable fog/blindness effect for players inside target structures",
                        "Creates a spooky atmosphere in haunted locations")
                .define("enable_fog_effect", true);

        fogEffectAmplifier = builder
                .comment("Amplifier for the fog effect (0 = light fog, 1 = medium fog, 2+ = heavy fog)",
                        "Higher values create denser fog")
                .defineInRange("fog_effect_amplifier", 0, 0, 5);

        caveDepthTolerance = builder
                .comment("How far below terrain surface a spawn is treated as cave-like and blocked")
                .defineInRange("cave_depth_tolerance", 6, 0, 64);

        skyAccessNearBuildingRadius = builder
                .comment("Horizontal search radius (in blocks) to detect nearby covered building areas")
                .defineInRange("sky_access_near_building_radius", 5, 1, 24);

        skyAccessAroundBuildingChance = builder
                .comment("Chance (0-100%) to allow haunted spawns in open-sky spots near a covered area")
                .defineInRange("sky_access_around_building_chance", 15.0, 0.0, 100.0);

        distributionAttempts = builder
                .comment("How many attempts are made to spread haunted spawns away from clusters")
                .defineInRange("distribution_attempts", 8, 1, 64);

        distributionRadius = builder
                .comment("Horizontal offset radius (in blocks) used when distributing haunted spawns")
                .defineInRange("distribution_radius", 4, 0, 24);

        minDistanceToOtherMobs = builder
                .comment("Minimum distance to other living mobs when selecting distributed spawn positions")
                .defineInRange("min_distance_to_other_mobs", 6, 0, 64);

        materialScanHorizontalRadius = builder
                .comment("Horizontal scan radius for block-material based cave/structure detection")
                .defineInRange("material_scan_horizontal_radius", 2, 1, 8);

        materialScanVerticalRadius = builder
                .comment("Vertical scan radius for block-material based cave/structure detection")
                .defineInRange("material_scan_vertical_radius", 1, 0, 4);

        caveMaterialThreshold = builder
                .comment("Minimum number of cave-like blocks near a position to treat it as likely cave")
                .defineInRange("cave_material_threshold", 8, 1, 128);

        structureMaterialThreshold = builder
                .comment("Minimum number of structure-like blocks near a position to treat it as house/garden area")
                .defineInRange("structure_material_threshold", 4, 1, 128);

        structureMaterialBlocks = builder
                .comment("Explicit structure material blocks (namespace:block) used for house/garden detection",
                        "Wood/fence tags are handled separately; this list is for explicit blocks like cobble/stone bricks/paths")
                .defineListAllowEmpty("structure_material_blocks",
                        List.of(
                                "minecraft:cobblestone_stairs",
                                "minecraft:cobblestone_slab",
                                "minecraft:mossy_cobblestone_stairs",
                                "minecraft:mossy_cobblestone_slab",
                                "minecraft:stone_bricks",
                                "minecraft:cracked_stone_bricks",
                                "minecraft:mossy_stone_bricks",
                                "minecraft:chiseled_stone_bricks",
                                "minecraft:stone_brick_stairs",
                                "minecraft:stone_brick_slab",
                                "minecraft:stone_brick_wall",
                                "minecraft:mossy_stone_brick_stairs",
                                "minecraft:mossy_stone_brick_slab",
                                "minecraft:mossy_stone_brick_wall",
                                "minecraft:grass_block",
                                "minecraft:dirt_path",
                                "minecraft:podzol",
                                "minecraft:coarse_dirt",
                                "minecraft:moss_block"
                        ),
                        () -> "",
                        obj -> obj instanceof String && isValidBlockEntry((String) obj));

        undergroundMaterialBlocks = builder
                .comment("Explicit underground/cave material blocks (namespace:block) used for cave detection")
                .defineListAllowEmpty("underground_material_blocks",
                        List.of(
                                "minecraft:stone",
                                "minecraft:deepslate",
                                "minecraft:cobbled_deepslate",
                                "minecraft:andesite",
                                "minecraft:diorite",
                                "minecraft:granite",
                                "minecraft:tuff"
                        ),
                        () -> "",
                        obj -> obj instanceof String && isValidBlockEntry((String) obj));

        enableDirectAreaSpawning = builder
                .comment("Enable direct haunted spawns from cached indoor/garden areas (independent of vanilla spawn generator)")
                .define("enable_direct_area_spawning", true);

        directSpawnAttemptChance = builder
                .comment("Chance (0-100%) per interval to attempt a direct haunted spawn for a player in haunted areas")
                .defineInRange("direct_spawn_attempt_chance", 35.0, 0.0, 100.0);

        directSpawnIntervalTicks = builder
                .comment("Tick interval for direct spawn attempts (20 ticks = 1 second)")
                .defineInRange("direct_spawn_interval_ticks", 40, 20, 1200);

        directSpawnReplacementChance = builder
                .comment("Chance (0-100%) that a direct haunted spawn becomes the configured replacement entity")
                .defineInRange("direct_spawn_replacement_chance", 85.0, 0.0, 100.0);

        directSpawnCandidateSamples = builder
                .comment("How many cached spawn spots to sample when picking a direct haunted spawn")
                .defineInRange("direct_spawn_candidate_samples", 10, 1, 64);

        areaScanRadius = builder
                .comment("Horizontal scan radius around players to discover and cache indoor/garden spawn spots")
                .defineInRange("area_scan_radius", 8, 2, 48);

        cacheTtlSeconds = builder
                .comment("How long cached spawn spots stay valid before being dropped")
                .defineInRange("cache_ttl_seconds", 180, 30, 1800);

        directSpawnMinPlayerDistance = builder
                .comment("Minimum distance to players for direct haunted spawns")
                .defineInRange("direct_spawn_min_player_distance", 8, 0, 64);

        directSpawnMaxPlayerDistance = builder
                .comment("Maximum distance to players for direct haunted spawns")
                .defineInRange("direct_spawn_max_player_distance", 36, 4, 128);

        maxCachedSpawnSpotsPerLevel = builder
                .comment("Maximum number of cached haunted spawn spots per dimension")
                .defineInRange("max_cached_spawn_spots_per_level", 600, 100, 10000);

        fogCacheProximityRadius = builder
                .comment("Radius used to inspect cached spots around the player for fog zone detection")
                .defineInRange("fog_cache_proximity_radius", 3, 1, 16);

        fogIndoorBaseDurationTicks = builder
                .comment("Base darkness duration in ticks for indoor haunted zones")
                .defineInRange("fog_indoor_base_duration_ticks", 120, 20, 1200);

        fogGardenBaseDurationTicks = builder
                .comment("Base darkness duration in ticks for garden/open haunted zones")
                .defineInRange("fog_garden_base_duration_ticks", 50, 10, 600);

        fogTrailMaxTicks = builder
                .comment("Maximum lingering fog trail duration in ticks after indoor exposure")
                .defineInRange("fog_trail_max_ticks", 160, 20, 2400);

        fogTrailDecayTicks = builder
                .comment("How many trail ticks are lost per update when the player is outside")
                .defineInRange("fog_trail_decay_ticks", 20, 1, 1200);

        LOGGER.debug("Built module-specific configuration for Haunted House module");
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec); // Call parent to handle enabled logging
        
        // Parse and cache mob replacement rates
        parseMobReplacementRates();
        parseConfiguredMaterialBlocks();
        
        LOGGER.debug("Module-specific configuration loaded for Haunted House module");
        if (targetMobs != null && targetStructures != null) {
            LOGGER.debug("  - Target mobs: {}", targetMobs.get());
            LOGGER.debug("  - Target structures: {}", targetStructures.get());
            LOGGER.debug("  - Replacement entity ID: {}", replacementEntityId.get());
            LOGGER.debug("  - Spawn preset: {}", spawnPreset.get());
            LOGGER.debug("  - Witch spawn boost chance: {}%", witchSpawnBoostChance.get());
            LOGGER.debug("  - Fog effect enabled: {}", enableFogEffect.get());
            LOGGER.debug("  - Fog effect amplifier: {}", fogEffectAmplifier.get());
            LOGGER.debug("  - Cave depth tolerance: {}", caveDepthTolerance.get());
            LOGGER.debug("  - Sky access near-building radius: {}", skyAccessNearBuildingRadius.get());
            LOGGER.debug("  - Sky access around-building chance: {}%", skyAccessAroundBuildingChance.get());
            LOGGER.debug("  - Distribution attempts: {}", distributionAttempts.get());
            LOGGER.debug("  - Distribution radius: {}", distributionRadius.get());
            LOGGER.debug("  - Min distance to other mobs: {}", minDistanceToOtherMobs.get());
            LOGGER.debug("  - Material scan horizontal radius: {}", materialScanHorizontalRadius.get());
            LOGGER.debug("  - Material scan vertical radius: {}", materialScanVerticalRadius.get());
            LOGGER.debug("  - Cave material threshold: {}", caveMaterialThreshold.get());
            LOGGER.debug("  - Structure material threshold: {}", structureMaterialThreshold.get());
            LOGGER.debug("  - Structure material blocks: {}", configuredStructureMaterialBlockIds);
            LOGGER.debug("  - Underground material blocks: {}", configuredUndergroundMaterialBlockIds);
            LOGGER.debug("  - Enable direct area spawning: {}", enableDirectAreaSpawning.get());
            LOGGER.debug("  - Direct spawn attempt chance: {}%", directSpawnAttemptChance.get());
            LOGGER.debug("  - Direct spawn interval ticks: {}", directSpawnIntervalTicks.get());
            LOGGER.debug("  - Direct spawn replacement chance: {}%", directSpawnReplacementChance.get());
            LOGGER.debug("  - Direct spawn candidate samples: {}", directSpawnCandidateSamples.get());
            LOGGER.debug("  - Area scan radius: {}", areaScanRadius.get());
            LOGGER.debug("  - Cache TTL seconds: {}", cacheTtlSeconds.get());
            LOGGER.debug("  - Direct spawn min player distance: {}", directSpawnMinPlayerDistance.get());
            LOGGER.debug("  - Direct spawn max player distance: {}", directSpawnMaxPlayerDistance.get());
            LOGGER.debug("  - Max cached spawn spots per level: {}", maxCachedSpawnSpotsPerLevel.get());
            LOGGER.debug("  - Fog cache proximity radius: {}", fogCacheProximityRadius.get());
            LOGGER.debug("  - Fog indoor base duration ticks: {}", fogIndoorBaseDurationTicks.get());
            LOGGER.debug("  - Fog garden base duration ticks: {}", fogGardenBaseDurationTicks.get());
            LOGGER.debug("  - Fog trail max ticks: {}", fogTrailMaxTicks.get());
            LOGGER.debug("  - Fog trail decay ticks: {}", fogTrailDecayTicks.get());
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
     * Validates if an entity ID string is in the correct format.
     *
     * @param entry The entity ID string to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidEntityId(String entry) {
        if (entry == null || entry.isEmpty()) {
            return false;
        }
        String[] parts = entry.split(":");
        if (parts.length != 2) {
            LOGGER.warn("Invalid replacement entity ID format: '{}'. Expected format: 'namespace:entity_id'", entry);
            return false;
        }
        return !parts[0].isBlank() && !parts[1].isBlank();
    }

    private boolean isValidPreset(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        try {
            SpawnPreset.valueOf(entry.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Invalid spawn preset '{}'. Expected: custom, balanced, structure_focused, courtyard", entry);
            return false;
        }
    }

    private boolean isValidBlockEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        try {
            ResourceLocation location = ResourceLocation.parse(entry.trim());
            if (!BuiltInRegistries.BLOCK.containsKey(location)) {
                LOGGER.warn("Unknown block ID in material list: '{}'", entry);
                return false;
            }
            return true;
        } catch (Exception ex) {
            LOGGER.warn("Invalid block ID in material list: '{}'", entry);
            return false;
        }
    }

    private void parseConfiguredMaterialBlocks() {
        configuredStructureMaterialBlockIds.clear();
        configuredUndergroundMaterialBlockIds.clear();

        if (structureMaterialBlocks != null) {
            for (String entry : structureMaterialBlocks.get()) {
                try {
                    configuredStructureMaterialBlockIds.add(ResourceLocation.parse(entry));
                } catch (Exception ex) {
                    LOGGER.warn("Skipping invalid structure material block entry '{}': {}", entry, ex.getMessage());
                }
            }
        }

        if (undergroundMaterialBlocks != null) {
            for (String entry : undergroundMaterialBlocks.get()) {
                try {
                    configuredUndergroundMaterialBlockIds.add(ResourceLocation.parse(entry));
                } catch (Exception ex) {
                    LOGGER.warn("Skipping invalid underground material block entry '{}': {}", entry, ex.getMessage());
                }
            }
        }
    }

    private SpawnPreset getSpawnPreset() {
        if (spawnPreset == null) {
            return SpawnPreset.STRUCTURE_FOCUSED;
        }
        try {
            return SpawnPreset.valueOf(spawnPreset.get().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SpawnPreset.STRUCTURE_FOCUSED;
        }
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

    /**
     * Gets the configured replacement entity ID.
     *
     * @return Entity ID in the format namespace:entity_id
     */
    public String getReplacementEntityId() {
        return replacementEntityId != null ? replacementEntityId.get() : "alexsmobs:murmur";
    }

    /**
     * Gets the witch spawn boost chance.
     *
     * @return The chance (0.0 to 1.0) that a mob spawn will be replaced with a witch
     */
    public double getWitchSpawnBoostChance() {
        return switch (getSpawnPreset()) {
            case CUSTOM -> witchSpawnBoostChance != null ? witchSpawnBoostChance.get() / 100.0 : 0.5;
            case BALANCED -> 0.55;
            case STRUCTURE_FOCUSED -> 0.78;
            case COURTYARD -> 0.60;
        };
    }

    /**
     * Checks if fog effect is enabled.
     *
     * @return true if fog effect should be applied, false otherwise
     */
    public boolean isFogEffectEnabled() {
        return enableFogEffect != null && enableFogEffect.get();
    }

    /**
     * Gets the fog effect amplifier level.
     *
     * @return The amplifier level (0-5)
     */
    public int getFogEffectAmplifier() {
        return fogEffectAmplifier != null ? fogEffectAmplifier.get() : 0;
    }

    /**
     * @return Spawn cave depth tolerance in blocks.
     */
    public int getCaveDepthTolerance() {
        return switch (getSpawnPreset()) {
            case CUSTOM -> caveDepthTolerance != null ? caveDepthTolerance.get() : 6;
            case BALANCED -> 6;
            case STRUCTURE_FOCUSED -> 8;
            case COURTYARD -> 5;
        };
    }

    /**
     * @return Radius used to find covered areas near open sky spawn spots.
     */
    public int getSkyAccessNearBuildingRadius() {
        return switch (getSpawnPreset()) {
            case CUSTOM -> skyAccessNearBuildingRadius != null ? skyAccessNearBuildingRadius.get() : 5;
            case BALANCED -> 5;
            case STRUCTURE_FOCUSED -> 7;
            case COURTYARD -> 8;
        };
    }

    /**
     * @return Chance as decimal (0.0 to 1.0) for sky-access spawn allowance near covered areas.
     */
    public double getSkyAccessAroundBuildingChance() {
        return switch (getSpawnPreset()) {
            case CUSTOM -> skyAccessAroundBuildingChance != null ? skyAccessAroundBuildingChance.get() / 100.0 : 0.15;
            case BALANCED -> 0.15;
            case STRUCTURE_FOCUSED -> 0.06;
            case COURTYARD -> 0.28;
        };
    }

    /**
     * @return Number of attempts to find a distributed spawn position.
     */
    public int getDistributionAttempts() {
        return switch (getSpawnPreset()) {
            case CUSTOM -> distributionAttempts != null ? distributionAttempts.get() : 8;
            case BALANCED -> 8;
            case STRUCTURE_FOCUSED -> 16;
            case COURTYARD -> 12;
        };
    }

    /**
     * @return Radius for distributed spawn sampling.
     */
    public int getDistributionRadius() {
        return switch (getSpawnPreset()) {
            case CUSTOM -> distributionRadius != null ? distributionRadius.get() : 4;
            case BALANCED -> 4;
            case STRUCTURE_FOCUSED -> 8;
            case COURTYARD -> 6;
        };
    }

    /**
     * @return Minimum distance to nearby living mobs for distributed spawn sampling.
     */
    public int getMinDistanceToOtherMobs() {
        return switch (getSpawnPreset()) {
            case CUSTOM -> minDistanceToOtherMobs != null ? minDistanceToOtherMobs.get() : 6;
            case BALANCED -> 6;
            case STRUCTURE_FOCUSED -> 8;
            case COURTYARD -> 6;
        };
    }

    /**
     * @return Horizontal scan radius for material-based detection.
     */
    public int getMaterialScanHorizontalRadius() {
        return materialScanHorizontalRadius != null ? materialScanHorizontalRadius.get() : 2;
    }

    /**
     * @return Vertical scan radius for material-based detection.
     */
    public int getMaterialScanVerticalRadius() {
        return materialScanVerticalRadius != null ? materialScanVerticalRadius.get() : 1;
    }

    /**
     * @return Required cave-material matches to classify a location as likely cave.
     */
    public int getCaveMaterialThreshold() {
        return caveMaterialThreshold != null ? caveMaterialThreshold.get() : 8;
    }

    /**
     * @return Required structure-material matches to classify a location as structure-adjacent.
     */
    public int getStructureMaterialThreshold() {
        return structureMaterialThreshold != null ? structureMaterialThreshold.get() : 4;
    }

    public Set<ResourceLocation> getConfiguredStructureMaterialBlockIds() {
        return Set.copyOf(configuredStructureMaterialBlockIds);
    }

    public Set<ResourceLocation> getConfiguredUndergroundMaterialBlockIds() {
        return Set.copyOf(configuredUndergroundMaterialBlockIds);
    }

    public boolean isDirectAreaSpawningEnabled() {
        return enableDirectAreaSpawning != null && enableDirectAreaSpawning.get();
    }

    public double getDirectSpawnAttemptChance() {
        return directSpawnAttemptChance != null ? directSpawnAttemptChance.get() / 100.0 : 0.35;
    }

    public int getDirectSpawnIntervalTicks() {
        return directSpawnIntervalTicks != null ? directSpawnIntervalTicks.get() : 40;
    }

    public double getDirectSpawnReplacementChance() {
        return directSpawnReplacementChance != null ? directSpawnReplacementChance.get() / 100.0 : 0.85;
    }

    public int getDirectSpawnCandidateSamples() {
        return directSpawnCandidateSamples != null ? directSpawnCandidateSamples.get() : 10;
    }

    public int getAreaScanRadius() {
        return areaScanRadius != null ? areaScanRadius.get() : 8;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds != null ? cacheTtlSeconds.get() : 180;
    }

    public int getDirectSpawnMinPlayerDistance() {
        return directSpawnMinPlayerDistance != null ? directSpawnMinPlayerDistance.get() : 6;
    }

    public int getDirectSpawnMaxPlayerDistance() {
        return directSpawnMaxPlayerDistance != null ? directSpawnMaxPlayerDistance.get() : 36;
    }

    public int getMaxCachedSpawnSpotsPerLevel() {
        return maxCachedSpawnSpotsPerLevel != null ? maxCachedSpawnSpotsPerLevel.get() : 600;
    }

    public int getFogCacheProximityRadius() {
        return fogCacheProximityRadius != null ? fogCacheProximityRadius.get() : 3;
    }

    public int getFogIndoorBaseDurationTicks() {
        return fogIndoorBaseDurationTicks != null ? fogIndoorBaseDurationTicks.get() : 120;
    }

    public int getFogGardenBaseDurationTicks() {
        return fogGardenBaseDurationTicks != null ? fogGardenBaseDurationTicks.get() : 50;
    }

    public int getFogTrailMaxTicks() {
        return fogTrailMaxTicks != null ? fogTrailMaxTicks.get() : 160;
    }

    public int getFogTrailDecayTicks() {
        return fogTrailDecayTicks != null ? fogTrailDecayTicks.get() : 20;
    }
}
