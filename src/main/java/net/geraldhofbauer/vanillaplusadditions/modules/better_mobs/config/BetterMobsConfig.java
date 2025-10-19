package net.geraldhofbauer.vanillaplusadditions.modules.better_mobs.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.better_mobs.BetterMobsModule;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BetterMobsConfig extends AbstractModuleConfig<BetterMobsModule, BetterMobsConfig> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BetterMobsConfig.class);

    private static final List<String> DEFAULT_ENABLED_MOBS = List.of(
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:husk",
            "minecraft:stray",
            "minecraft:drowned",
            "minecraft:spider",
            "minecraft:cave_spider",
            "minecraft:witch",
            "minecraft:pillager",
            "minecraft:vindicator",
            "minecraft:evoker",
            "minecraft:illusioner",
            "minecraft:blaze",
            "minecraft:wither_skeleton",
            "minecraft:guardian",
            "minecraft:elder_guardian",
            "minecraft:shulker",
            "minecraft:warden",
            "minecraft:piglin",
            "minecraft:zombified_piglin"
    );

    private static final List<String> DEFAULT_ENABLED_MOBS_WITH_ARMOR = List.of(
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:husk",
            "minecraft:wither_skeleton"
    );

    // Module-specific configuration values
    private ModConfigSpec.IntValue dropChance;
    private ModConfigSpec.IntValue maxDurability;
    private ModConfigSpec.ConfigValue<List<String>> aboveZeroConfig;
    private ModConfigSpec.ConfigValue<List<String>> belowZeroConfig;
    private ModConfigSpec.ConfigValue<List<String>> netherEndConfig;
    private ModConfigSpec.ConfigValue<List<? extends String>> enabledMobs;
    private ModConfigSpec.ConfigValue<List<? extends String>> enabledMobsWithArmor;

    public BetterMobsConfig(BetterMobsModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        dropChance = builder
                .comment("Chance (in percentage) for mobs to drop their enhanced gear upon death (0-100)")
                .defineInRange("drop_chance", 10, 0, 100);

        maxDurability = builder
                .comment("Maximum durability for enhanced gear as percentage (1-100%)")
                .defineInRange("max_durability", 100, 1, 100);

        List<String> aboveZeroDefaultConfig = getAboveZeroConf();

        aboveZeroConfig = builder
                .comment("Configuration for mobs spawned above Y=0")
                .define("above_zero", aboveZeroDefaultConfig);

        List<String> belowZeroDefaultConfig = getBelowZeroConf();

        belowZeroConfig = builder
                .comment("Configuration for mobs spawned below Y=0 or in the Nether/End")
                .define("below_zero", belowZeroDefaultConfig);

        List<String> netherEndDefaultConfig = getNetherEndConf();

        netherEndConfig = builder
                .comment("Configuration for mobs spawned in the Nether or End dimension")
                .define("nether_end", netherEndDefaultConfig);

        enabledMobs = builder
                .comment("""
                        List of mob entity IDs that should receive random equipment.
                        (e.g. minecraft:zombie, minecraft:skeleton, minecraft:husk, minecraft:stray, minecraft:drowned,
                        minecraft:spider, minecraft:cave_spider, minecraft:witch, minecraft:pillager,
                        minecraft:vindicator, minecraft:evoker, minecraft:illusioner, minecraft:blaze,
                        minecraft:wither_skeleton, minecraft:guardian, minecraft:elder_guardian, minecraft:shulker,
                        minecraft:warden)""")
                .defineList("enabled_mobs",
                        DEFAULT_ENABLED_MOBS,
                        () -> "minecraft:zombie",
                        o -> o instanceof String
                );

        enabledMobsWithArmor = builder
                .comment("""
                        List of mob entity IDs that can receive armor
                        (e.g. minecraft:zombie, minecraft:skeleton, minecraft:husk, minecraft:wither_skeleton)
                        """)
                .defineList("enabled_mobs_with_armor",
                        DEFAULT_ENABLED_MOBS_WITH_ARMOR,
                        () -> "minecraft:zombie",
                        o -> o instanceof String
                );

        LOGGER.debug("Built module-specific configuration for Better Mobs module");
    }

    private static @NotNull List<String> getAboveZeroConf() {
        List<String> config = new ArrayList<>();

        // Gear types
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":gold:5");
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":chainmail:10");
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":leather:15");

        // Armor spawn chances
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":helmet:60");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":chestplate:50");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":leggings:45");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":boots:40");

        // Helmet enchantments
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":protection:20");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":fire_protection:10");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":blast_protection:10");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":projectile_protection:10");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":respiration:10");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":aqua_affinity:10");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":thorns:5");

        // Chestplate enchantments
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":protection:20");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":fire_protection:10");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":blast_protection:10");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":projectile_protection:10");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":thorns:5");

        // Leggings enchantments
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":protection:20");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":fire_protection:10");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":blast_protection:10");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":projectile_protection:10");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":thorns:5");

        // Boots enchantments
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":protection:20");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":fire_protection:10");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":blast_protection:10");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":projectile_protection:10");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":feather_falling:10");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":thorns:5");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":depth_strider:5");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":frost_walker:5");

        // Enchantment levels
        config.add(BetterMobsConfigKey.ENCHANTMENT_LEVELS.name() + ":min_level:1");
        config.add(BetterMobsConfigKey.ENCHANTMENT_LEVELS.name() + ":max_level:3");

        // Potion effects
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":speed:5");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":strength:5");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":haste:5");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":fire_resistance:5");

        return config;
    }

    private static @NotNull List<String> getBelowZeroConf() {
        List<String> config = new ArrayList<>();

        // Gear types
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":gold:10");
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":iron:30");
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":leather:10");
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":diamond:30");

        // Armor spawn chances (höhere Chancen unter Y=0)
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":helmet:80");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":chestplate:70");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":leggings:65");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":boots:60");

        // Helmet enchantments
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":protection:30");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":fire_protection:15");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":blast_protection:15");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":projectile_protection:15");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":respiration:20");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":aqua_affinity:15");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":thorns:10");

        // Chestplate enchantments
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":protection:30");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":fire_protection:15");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":blast_protection:15");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":projectile_protection:15");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":thorns:10");

        // Leggings enchantments
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":protection:30");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":fire_protection:15");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":blast_protection:15");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":projectile_protection:15");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":thorns:10");

        // Boots enchantments
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":protection:30");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":fire_protection:15");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":blast_protection:15");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":projectile_protection:15");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":feather_falling:20");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":thorns:10");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":depth_strider:10");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":frost_walker:10");

        // Enchantment levels
        config.add(BetterMobsConfigKey.ENCHANTMENT_LEVELS.name() + ":min_level:2");
        config.add(BetterMobsConfigKey.ENCHANTMENT_LEVELS.name() + ":max_level:4");

        // Potion effects
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":speed:10");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":strength:10");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":haste:10");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":fire_resistance:10");

        return config;
    }

    private static @NotNull List<String> getNetherEndConf() {
        // TODO: Unterschiedliche Konfiguration für Nether/End, falls gewünscht bzw. für andere Dimensionen
        List<String> config = new ArrayList<>();

        // Gear types
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":gold:30");
        config.add(BetterMobsConfigKey.GEAR_TYPES.name() + ":netherite:20");

        // Armor spawn chances (höhere Chancen unter Y=0)
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":helmet:80");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":chestplate:70");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":leggings:65");
        config.add(BetterMobsConfigKey.ARMOR_CHANCES.name() + ":boots:60");

        // Helmet enchantments
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":protection:30");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":fire_protection:15");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":blast_protection:15");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":projectile_protection:15");
        config.add(BetterMobsConfigKey.HELMET_ENCHANTMENTS.name() + ":thorns:10");

        // Chestplate enchantments
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":protection:30");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":fire_protection:15");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":blast_protection:15");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":projectile_protection:15");
        config.add(BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS.name() + ":thorns:10");

        // Leggings enchantments
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":protection:30");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":fire_protection:15");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":blast_protection:15");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":projectile_protection:15");
        config.add(BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS.name() + ":thorns:10");

        // Boots enchantments
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":protection:30");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":fire_protection:15");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":blast_protection:15");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":projectile_protection:15");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":feather_falling:20");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":thorns:10");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":depth_strider:10");
        config.add(BetterMobsConfigKey.BOOTS_ENCHANTMENTS.name() + ":frost_walker:10");

        // Enchantment levels
        config.add(BetterMobsConfigKey.ENCHANTMENT_LEVELS.name() + ":min_level:2");
        config.add(BetterMobsConfigKey.ENCHANTMENT_LEVELS.name() + ":max_level:4");

        // Potion effects
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":speed:10");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":strength:10");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":haste:10");
        config.add(BetterMobsConfigKey.POTION_EFFECTS.name() + ":fire_resistance:10");

        return config;
    }

    @Override
    public void onConfigLoad(ModConfigSpec spec) {
        super.onConfigLoad(spec); // Call parent to handle enabled logging
        // React to module-specific configuration changes if needed
        if (shouldDebugLog()) {
            LOGGER.debug("Module-specific configuration loaded for Better Mobs module");
            if (dropChance != null && maxDurability != null
                    && aboveZeroConfig != null && belowZeroConfig != null && netherEndConfig != null) {
                LOGGER.debug("  - Drop chance: {}%", dropChance.get());
                LOGGER.debug("  - Max durability: {}", maxDurability.get());
                LOGGER.debug("  - Above zero config: {}", aboveZeroConfig.get());
                LOGGER.debug("  - Below zero config: {}", belowZeroConfig.get());
                LOGGER.debug("  - Nether/End config: {}", netherEndConfig.get());
            }
        }
    }

    /**
     * Gets the configured drop chance.
     *
     * @return drop chance in percentage, or default value if not configured
     */
    public int getDropChanceValue() {
        return dropChance != null ? dropChance.get() : 10;
    }

    /**
     * Gets the configured maximum durability.
     *
     * @return maximum durability, or default value if not configured
     */
    public int getMaxDurabilityValue() {
        return maxDurability != null ? maxDurability.get() : 250;
    }

    /**
     * Gets the configuration for mobs spawned above Y=0.
     *
     * @return map of configuration values, or default value if not configured
     */
    public Map<BetterMobsConfigKey, Map<String, Integer>> getAboveZeroConfig() {
        if (aboveZeroConfig == null) {
            return Map.of();
        }
        return convertToKeyMap(aboveZeroConfig.get());
    }

    /**
     * Gets the configuration for mobs spawned below Y=0.
     *
     * @return map of configuration values, or default value if not configured
     */
    public Map<BetterMobsConfigKey, Map<String, Integer>> getBelowZeroConfig() {
        if (belowZeroConfig == null) {
            return Map.of();
        }
        return convertToKeyMap(belowZeroConfig.get());
    }

    /**
     * Gets the configuration for mobs spawned in the Nether or End dimension.
     *
     * @return map of configuration values, or default value if not configured
     */
    public Map<BetterMobsConfigKey, Map<String, Integer>> getNetherEndConfig() {
        if (netherEndConfig == null) {
            return Map.of();
        }
        return convertToKeyMap(netherEndConfig.get());
    }

    /**
     * Converts a list of config entries to a Map with BetterMobsConfigKey keys
     */
    private Map<BetterMobsConfigKey, Map<String, Integer>> convertToKeyMap(List<String> input) {
        Map<BetterMobsConfigKey, Map<String, Integer>> output = new HashMap<>();

        for (String entry : input) {
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                LOGGER.warn("Invalid config entry format: {}", entry);
                continue;
            }

            try {
                BetterMobsConfigKey key = BetterMobsConfigKey.valueOf(parts[0]);
                String property = parts[1];
                int value = Integer.parseInt(parts[2]);

                output.computeIfAbsent(key, k -> new HashMap<>()).put(property, value);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Error parsing config entry: {}", entry, e);
            }
        }

        return output;
    }

    /**
     * Gibt die Liste der Mobs zurück, die zufällige Ausrüstung erhalten sollen.
     */
    public List<String> getEnabledMobs() {
        return enabledMobs != null ? new ArrayList<>(enabledMobs.get()) : new ArrayList<>(DEFAULT_ENABLED_MOBS);
    }

    /**
     * Gibt die Liste der Mobs zurück, die Rüstung erhalten können.
     */
    public List<String> getEnabledMobsWithArmor() {
        return enabledMobsWithArmor != null
                ? new ArrayList<>(enabledMobsWithArmor.get())
                : new ArrayList<>(DEFAULT_ENABLED_MOBS_WITH_ARMOR);
    }

    /**
     * Prüft, ob ein Mob in der Liste der aktivierten Mobs ist.
     */
    public boolean isEntityEnabled(String entityId) {
        return getEnabledMobs().contains(entityId);
    }

    /**
     * Prüft, ob ein Mob Rüstung tragen kann.
     */
    public boolean canEntityWearArmor(String entityId) {
        return getEnabledMobsWithArmor().contains(entityId);
    }

    public Map<BetterMobsConfigKey, List<String>> getRandomEquipmentSetupForMob(ResourceKey<Level> dimension,
                                                                                UUID uuid,
                                                                                int y) {
        List<String> configEntries;
        if (dimension == Level.END || dimension == Level.NETHER) {
            configEntries = netherEndConfig.get();
        } else {
            configEntries = y >= 0 ? aboveZeroConfig.get() : belowZeroConfig.get();
        }
        Random random = new Random(uuid.getLeastSignificantBits()); // Seed basierend auf Y-Koordinate
        Map<BetterMobsConfigKey, List<String>> equipment = new java.util.HashMap<>();

        // Gruppiere Einträge nach ConfigKey
        Map<BetterMobsConfigKey, List<String[]>> groupedEntries = new HashMap<>();
        for (String entry : configEntries) {
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                continue;
            }

            try {
                BetterMobsConfigKey key = BetterMobsConfigKey.valueOf(parts[0]);
                groupedEntries.computeIfAbsent(key, k -> new ArrayList<>()).add(parts);
            } catch (IllegalArgumentException e) {
                continue;
            }
        }

        // Prüfe Rüstungschancen zuerst
        Map<String, Boolean> armorSpawns = new HashMap<>();
        List<String[]> armorChances = groupedEntries.get(BetterMobsConfigKey.ARMOR_CHANCES);
        if (armorChances != null) {
            for (String[] chance : armorChances) {
                armorSpawns.put(chance[1], random.nextInt(100) < Integer.parseInt(chance[2]));
            }
        }

        // Gear Types - wähle einen zufälligen Typ basierend auf der Wahrscheinlichkeit
        List<String[]> gearTypes = groupedEntries.get(BetterMobsConfigKey.GEAR_TYPES);
        if (gearTypes != null) {
            for (String[] gearType : gearTypes) {
                if (random.nextInt(100) < Integer.parseInt(gearType[2])) {
                    equipment.put(BetterMobsConfigKey.GEAR_TYPES, List.of(gearType[1]));
                    break;
                }
            }
        }

        // Enchantments für jedes Ausrüstungsteil (nur wenn das entsprechende Teil spawnen soll)
        Map<BetterMobsConfigKey, String> armorKeyMapping = Map.of(
                BetterMobsConfigKey.HELMET_ENCHANTMENTS, "helmet",
                BetterMobsConfigKey.CHESTPLATE_ENCHANTMENTS, "chestplate",
                BetterMobsConfigKey.LEGGINGS_ENCHANTMENTS, "leggings",
                BetterMobsConfigKey.BOOTS_ENCHANTMENTS, "boots"
        );

        for (Map.Entry<BetterMobsConfigKey, String> mapping : armorKeyMapping.entrySet()) {
            BetterMobsConfigKey key = mapping.getKey();
            String armorPiece = mapping.getValue();

            // Nur wenn das Rüstungsteil spawnen soll
            if (Boolean.TRUE.equals(armorSpawns.get(armorPiece))) {
                List<String[]> enchants = groupedEntries.get(key);
                if (enchants != null) {
                    List<String> selectedEnchants = new ArrayList<>();
                    for (String[] enchant : enchants) {
                        if (random.nextInt(100) < Integer.parseInt(enchant[2])) {
                            selectedEnchants.add(enchant[1]);
                        }
                    }
                    if (!selectedEnchants.isEmpty()) {
                        equipment.put(key, selectedEnchants);
                    }
                }
            }
        }

        // Enchantment Levels
        List<String[]> enchantLevels = groupedEntries.get(BetterMobsConfigKey.ENCHANTMENT_LEVELS);
        if (enchantLevels != null) {
            int minLevel = 1;
            int maxLevel = 1;
            for (String[] level : enchantLevels) {
                if (level[1].equals("min_level")) {
                    minLevel = Integer.parseInt(level[2]);
                } else if (level[1].equals("max_level")) {
                    maxLevel = Integer.parseInt(level[2]);
                }
            }
            int selectedLevel1 = minLevel + random.nextInt(Math.max(1, maxLevel - minLevel + 1));
            int selectedLevel2 = minLevel + random.nextInt(Math.max(1, maxLevel - minLevel + 1));
            equipment.put(BetterMobsConfigKey.ENCHANTMENT_LEVELS, List.of(
                    String.valueOf(selectedLevel1),
                    String.valueOf(selectedLevel2)
            ));
        }

        // Potion Effects
        List<String[]> potionEffects = groupedEntries.get(BetterMobsConfigKey.POTION_EFFECTS);
        if (potionEffects != null) {
            List<String> selectedEffects = new ArrayList<>();
            for (String[] effect : potionEffects) {
                if (random.nextInt(100) < Integer.parseInt(effect[2])) {
                    selectedEffects.add(effect[1]);
                }
            }
            if (!selectedEffects.isEmpty()) {
                equipment.put(BetterMobsConfigKey.POTION_EFFECTS, selectedEffects);
            }
        }

        // Initialize all keys to avoid null checks later
        for (BetterMobsConfigKey key : BetterMobsConfigKey.values()) {
            equipment.putIfAbsent(key, List.of());
        }

        // Speichere die Rüstungschancen auch in der Ausgabe
        equipment.put(BetterMobsConfigKey.ARMOR_CHANCES,
                armorSpawns.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toList()));

        return equipment;
    }
}
