package net.geraldhofbauer.vanillaplusadditions.core;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig.DefaultModuleConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Abstract base class for modules that provides common functionality.
 * Extends this class to create new modules with less boilerplate.
 */
public abstract class AbstractModule<M extends Module, C extends ModuleConfig> implements Module {
    private final Logger logger;
    private final String moduleId;
    private final String displayName;
    private final String description;
    private final C config;

    private IEventBus modEventBus;
    private ModContainer modContainer;

    /**
     * Creates a new abstract module.
     *
     * @param moduleId    The unique module identifier
     * @param displayName The human-readable module name
     * @param description The module description
     * @param initConfig  A function to create the module config instance
     */
    protected AbstractModule(String moduleId, String displayName, String description, Function<M, C> initConfig) {
        this.moduleId = moduleId;
        this.displayName = displayName;
        this.description = description;
        this.config = initConfig.apply(self());
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    protected M self() {
        try {
            @SuppressWarnings("unchecked")
            M self = (M) this;
            return self;
        } catch (ClassCastException e) {
            throw new IllegalStateException("Module class does not match generic type", e);
        }
    }

    protected Function<M, DefaultModuleConfig<M>> getDefaultConfigInitializer() {
        return AbstractModuleConfig::createDefault;
    }

    /**
     * Gets the logger instance for this module.
     * 
     * @return The logger instance
     */
    protected final Logger getLogger() {
        return logger;
    }

    /**
     * Gets the configuration instance for this module.
     * 
     * @return The configuration instance
     */
    @Override
    public final C getConfig() {
        return config;
    }

    @Override
    public String getModuleId() {
        return moduleId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void initialize(IEventBus modEventBus, ModContainer modContainer) {
        this.modEventBus = modEventBus;
        this.modContainer = modContainer;

        logger.debug("Initializing module: {}", displayName);

        if (!shouldInitialize()) {
            logger.info("Module '{}' is disabled and will not be initialized", displayName);
            return;
        }

        // Call the implementation-specific initialization
        onInitialize();

        logger.debug("Module initialized: {}", displayName);
    }

    /**
     * Determines whether this module should be initialized.
     *
     * @return true if the module should be initialized, false otherwise
     */
    protected boolean shouldInitialize() {
        return true;
    }

    /**
     * Override this method to implement module-specific initialization logic.
     * The modEventBus and modContainer fields will be available at this point.
     */
    protected abstract void onInitialize();

    @Override
    public void commonSetup() {
        logger.debug("Running common setup for module: {}", displayName);
        onCommonSetup();
    }

    /**
     * Override this method to implement common setup logic.
     * This is called after all registries are populated.
     */
    protected void onCommonSetup() {
        // Default empty implementation
    }

    @Override
    public void clientSetup() {
        logger.debug("Running client setup for module: {}", displayName);
        onClientSetup();
    }

    /**
     * Override this method to implement client-side setup logic.
     * This is only called on the client side.
     */
    protected void onClientSetup() {
        // Default empty implementation
    }

    /**
     * Utility method to check if we're in the initialization phase.
     *
     * @return true if modEventBus is available
     */
    protected boolean isInitialized() {
        return modEventBus != null;
    }

    /**
     * Gets the mod event bus. Only available after initialization.
     *
     * @return The mod event bus
     * @throws IllegalStateException if called before initialization
     */
    protected IEventBus getModEventBus() {
        if (modEventBus == null) {
            throw new IllegalStateException("Mod event bus not available before initialization");
        }
        return modEventBus;
    }

    /**
     * Gets the mod container. Only available after initialization.
     *
     * @return The mod container
     * @throws IllegalStateException if called before initialization
     */
    protected ModContainer getModContainer() {
        if (modContainer == null) {
            throw new IllegalStateException("Mod container not available before initialization");
        }
        return modContainer;
    }


    /**
     * Helper method to check if this specific module is enabled.
     */
    public boolean isModuleEnabled() {
        // During initialization, assume enabled if isInitialized is true
        // After initialization, check configuration
        if (!isInitialized()) {
            return true;
        }

        try {
            return config.isEnabled();
        } catch (Exception e) {
            // If config not available yet, return true to allow initialization
            logger.debug("Config not available during module enabled check: {}", e.getMessage());
            return false;
        }
    }

}