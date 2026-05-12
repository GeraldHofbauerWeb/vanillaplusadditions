package net.geraldhofbauer.vanillaplusadditions.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle and registration of all VanillaPlusAdditions modules.
 * This class handles module discovery, initialization, and configuration.
 */
public final class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleManager.class);
    
    private static volatile ModuleManager instance;
    private final Map<String, Module> registeredModules = new ConcurrentHashMap<>();
    private final Map<String, Boolean> moduleEnabledState = new ConcurrentHashMap<>();
    private final Map<String, Boolean> runtimeModuleOverrides = new ConcurrentHashMap<>();
    private final List<Module> enabledModules = new ArrayList<>();
    
    private boolean initialized = false;
    
    private ModuleManager() { }
    
    /**
     * Gets the singleton instance of the ModuleManager.
     * 
     * @return The ModuleManager instance
     */
    public static synchronized ModuleManager getInstance() {
        if (instance == null) {
            instance = new ModuleManager();
        }
        return instance;
    }
    
    /**
     * Registers a module with the manager.
     * 
     * @param module The module to register
     * @throws IllegalArgumentException if a module with the same ID is already registered
     * @throws IllegalStateException if called after initialization
     */
    public void registerModule(Module module) {
        if (initialized) {
            throw new IllegalStateException("Cannot register modules after initialization");
        }
        
        String moduleId = module.getModuleId();
        if (registeredModules.containsKey(moduleId)) {
            throw new IllegalArgumentException("Module with ID '" + moduleId + "' is already registered");
        }
        
        LOGGER.debug("Registering module: {} ({})", module.getDisplayName(), moduleId);
        registeredModules.put(moduleId, module);
        
        // Register module with configuration system
        ModulesConfig.registerModule(module);
        
        // Set initial enabled state based on module default since config isn't ready yet
        // The actual config check will happen later during initialization
        boolean enabled = module.isEnabledByDefault();
        moduleEnabledState.put(moduleId, enabled);
        
        if (enabled) {
            enabledModules.add(module);
        }
    }
    
    /**
     * Initializes all registered and enabled modules.
     * 
     * @param modEventBus The mod event bus
     * @param modContainer The mod container
     */
    public void initializeModules(IEventBus modEventBus, ModContainer modContainer) {
        if (initialized) {
            LOGGER.warn("Module manager already initialized, skipping");
            return;
        }
        
        // Re-check enabled state now that configuration is available
        enabledModules.clear();
        for (Module module : registeredModules.values()) {
            boolean configEnabled = ModulesConfig.isModuleEnabled(module);
            String moduleId = module.getModuleId();
            moduleEnabledState.put(moduleId, configEnabled);
            
            if (configEnabled) {
                enabledModules.add(module);
            }
        }
        
        LOGGER.info("Initializing {} enabled modules out of {} registered", 
                   enabledModules.size(), registeredModules.size());
        
        for (Module module : enabledModules) {
            try {
                LOGGER.debug("Initializing module: {}", module.getDisplayName());
                module.initialize(modEventBus, modContainer);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize module: {}", module.getDisplayName(), e);
                // Continue with other modules
            }
        }
        
        initialized = true;
        LOGGER.info("Module initialization complete");
    }
    
    /**
     * Calls common setup for all enabled modules.
     */
    public void commonSetup() {
        if (!initialized) {
            LOGGER.error("Cannot call common setup before initialization");
            return;
        }
        
        LOGGER.debug("Running common setup for {} modules", enabledModules.size());
        
        for (Module module : enabledModules) {
            try {
                module.commonSetup();
            } catch (Exception e) {
                LOGGER.error("Failed common setup for module: {}", module.getDisplayName(), e);
            }
        }
    }

    /**
     * Calls load complete for all enabled modules.
     */
    public void loadComplete() {
        if (!initialized) {
            LOGGER.error("Cannot call load complete before initialization");
            return;
        }

        LOGGER.debug("Running load complete for {} modules", enabledModules.size());

        for (Module module : enabledModules) {
            try {
                module.loadComplete();
            } catch (Exception e) {
                LOGGER.error("Failed load complete for module: {}", module.getDisplayName(), e);
            }
        }
    }
    
    /**
     * Calls client setup for all enabled modules.
     */
    public void clientSetup() {
        if (!initialized) {
            LOGGER.error("Cannot call client setup before initialization");
            return;
        }
        
        LOGGER.debug("Running client setup for {} modules", enabledModules.size());
        
        for (Module module : enabledModules) {
            try {
                module.clientSetup();
            } catch (Exception e) {
                LOGGER.error("Failed client setup for module: {}", module.getDisplayName(), e);
            }
        }
    }
    
    /**
     * Gets a module by its ID.
     * 
     * @param moduleId The module ID
     * @return The module, or null if not found
     */
    public Module getModule(String moduleId) {
        return registeredModules.get(moduleId);
    }
    
    /**
     * Checks if a module is enabled.
     * 
     * @param moduleId The module ID
     * @return true if the module is enabled
     */
    public boolean isModuleEnabled(String moduleId) {
        Boolean override = runtimeModuleOverrides.get(moduleId);
        if (override != null) {
            return override;
        }
        return moduleEnabledState.getOrDefault(moduleId, false);
    }

    /**
     * Resolves module enabled state using runtime override first, then config state.
     */
    public boolean resolveModuleEnabled(String moduleId, boolean configEnabled) {
        Boolean override = runtimeModuleOverrides.get(moduleId);
        return override != null ? override : configEnabled;
    }

    /**
     * Sets a runtime override for a module's enabled state.
     * This does not persist to config files and applies until restart or clear.
     */
    public boolean setRuntimeModuleEnabled(String moduleId, boolean enabled) {
        if (!registeredModules.containsKey(moduleId)) {
            return false;
        }
        runtimeModuleOverrides.put(moduleId, enabled);
        return true;
    }

    /**
     * Clears runtime override for a module and falls back to config state.
     */
    public boolean clearRuntimeModuleOverride(String moduleId) {
        return runtimeModuleOverrides.remove(moduleId) != null;
    }

    public Boolean getRuntimeModuleOverride(String moduleId) {
        return runtimeModuleOverrides.get(moduleId);
    }

    /**
     * Gets all registered modules.
     * 
     * @return Unmodifiable collection of all registered modules
     */
    public Collection<Module> getAllModules() {
        return Collections.unmodifiableCollection(registeredModules.values());
    }
    
    /**
     * Gets all enabled modules.
     * 
     * @return Unmodifiable list of enabled modules
     */
    public List<Module> getEnabledModules() {
        return Collections.unmodifiableList(enabledModules);
    }
    
    /**
     * Gets module statistics for logging/debugging.
     * 
     * @return A formatted string with module statistics
     */
    public String getModuleStats() {
        return String.format("Modules: %d registered, %d enabled, %d disabled", 
                           registeredModules.size(), 
                           enabledModules.size(), 
                           registeredModules.size() - enabledModules.size());
    }
}