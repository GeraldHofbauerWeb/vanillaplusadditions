package net.geraldhofbauer.vanillaplusadditions.core;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration manager for all VanillaPlusAdditions modules.
 * This class handles the creation and management of configuration options
 * for modules, including the ability to enable/disable them.
 */
@EventBusSubscriber(modid = "vanillaplusadditions", bus = EventBusSubscriber.Bus.MOD)
public final class ModulesConfig {
    private ModulesConfig() {
        // Utility class - prevent instantiation
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(ModulesConfig.class);

    // Storage for registered modules
    private static final List<Module> REGISTERED_MODULES = new ArrayList<>();
    private static final Map<String, ModuleConfig> MODULE_CONFIGS = new HashMap<>();

    // Global debug logging configuration
    private static ModConfigSpec.BooleanValue globalDebugLogging;
    private static ModConfigSpec.BooleanValue worldgenCrashGuardEnabled;

    // The configuration specification - built dynamically
    private static ModConfigSpec spec = null;
    private static boolean configBuilt = false;

    /**
     * Gets the configuration specification, building it if necessary.
     */
    public static ModConfigSpec getSpec() {
        if (!configBuilt) {
            buildConfig();
        }
        return spec;
    }

    /**
     * Builds the configuration specification dynamically from all registered modules.
     */
    private static synchronized void buildConfig() {
        if (configBuilt) {
            return; // Already built
        }
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("VanillaPlusAdditions Module Configuration");
        
        // Add global debug logging option
        globalDebugLogging = builder
                .comment("Enable debug logging for all modules (can be overridden by individual module settings)")
                .define("globalDebugLogging", false);

        worldgenCrashGuardEnabled = builder
                .comment("Emergency workaround: suppress IndexOutOfBoundsException during structure start generation.",
                        "Use only to keep servers running while isolating incompatible worldgen mods.")
                .define("worldgenCrashGuardEnabled", false);

        builder.push("modules");

        // Build configuration for each registered module
        for (Module module : REGISTERED_MODULES) {
            ModuleConfig config = module.getConfig();
            if (config != null) {
                LOGGER.debug("Building configuration for module: {}", module.getModuleId());
                config.buildConfig(builder);
                MODULE_CONFIGS.put(module.getModuleId(), config);
            } else {
                // Module has no configuration, add basic enabled/disabled option
                LOGGER.debug("Adding basic enabled config for module: {}", module.getModuleId());
                if (module.isConfigurable()) {
                    builder.comment(String.format("Configuration for %s module", module.getDisplayName()))
                            .push(module.getModuleId());

                    builder.comment(String.format("Whether the %s module is enabled", module.getDisplayName()))
                            .define("enabled", module.isEnabledByDefault());

                    builder.pop();
                }
            }
        }

        builder.pop(); // Close modules section

        spec = builder.build();
        configBuilt = true;
        LOGGER.debug("Built dynamic configuration with {} modules", REGISTERED_MODULES.size());
    }

    /**
     * Registers a module for configuration building.
     * This must be called before the configuration is built (during mod construction).
     *
     * @param module The module to register
     */
    public static void registerModule(Module module) {
        if (!REGISTERED_MODULES.contains(module)) {
            REGISTERED_MODULES.add(module);
            LOGGER.debug("Registered module for configuration: {}", module.getModuleId());
        }
    }

    /**
     * Gets the configuration instance for a specific module.
     *
     * @param moduleId The module ID
     * @return The module's config instance, or null if not found
     */
    public static ModuleConfig getModuleConfig(String moduleId) {
        return MODULE_CONFIGS.get(moduleId);
    }

    /**
     * Checks if a module is enabled according to configuration.
     * Falls back to the module's default enabled state if no config exists.
     *
     * @param module The module to check
     * @return true if the module should be enabled
     */
    public static boolean isModuleEnabled(Module module) {
        String moduleId = module.getModuleId();

        // First check if the module has its own configuration
        ModuleConfig moduleConfig = module.getConfig();
        if (moduleConfig != null) {
            try {
                boolean enabled = moduleConfig.isEnabled();
                LOGGER.debug("Module {} enabled state: {} (from custom config)", moduleId, enabled);
                return enabled;
            } catch (Exception e) {
                LOGGER.debug("Error checking custom config for module {}: {} - falling back to default",
                        moduleId, e.getMessage());
            }
        }

        // Fall back to module default
        boolean defaultEnabled = module.isEnabledByDefault();
        LOGGER.debug("Module {} enabled state: {} (default fallback)", moduleId, defaultEnabled);
        return defaultEnabled;
    }
    
    /**
     * Checks if global debug logging is enabled.
     * 
     * @return true if global debug logging should be enabled
     */
    public static boolean isGlobalDebugLoggingEnabled() {
        return globalDebugLogging != null && globalDebugLogging.get();
    }

    /**
     * Checks if the worldgen crash guard is enabled.
     *
     * @return true if IndexOutOfBoundsExceptions in structure-start generation should be suppressed
     */
    public static boolean isWorldgenCrashGuardEnabled() {
        if (worldgenCrashGuardEnabled == null) {
            // Fail-open for early worldgen phases before config values are wired.
            return true;
        }

        try {
            return worldgenCrashGuardEnabled.get();
        } catch (Exception ignored) {
            // Keep servers alive in emergency situations if config access fails unexpectedly.
            return true;
        }
    }

    /**
     * Handles module configuration events.
     * Called when configuration is loaded or reloaded.
     */
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (!event.getConfig().getModId().equals("vanillaplusadditions")) {
            return;
        }

        LOGGER.debug("VanillaPlusAdditions module configuration loaded: {}", event.getConfig().getFileName());

        // Notify all configurable modules about the config load
        for (Module module : REGISTERED_MODULES) {
            ModuleConfig config = module.getConfig();
            if (config != null) {
                try {
                    config.onConfigLoad(getSpec());
                } catch (Exception e) {
                    LOGGER.error("Error loading configuration for module {}: {}",
                            module.getModuleId(), e.getMessage());
                }
            }
        }

        // Log the current module states
        LOGGER.info("Module configuration reloaded. Current states:");
        for (Module module : REGISTERED_MODULES) {
            boolean enabled = isModuleEnabled(module);
            LOGGER.info("  - {}: {}", module.getModuleId(), enabled ? "ENABLED" : "DISABLED");
        }
    }
}
