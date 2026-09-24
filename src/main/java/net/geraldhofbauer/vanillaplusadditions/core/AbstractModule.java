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

    /** True once {@link #onInitialize()} has actually run; gates the three setup phases. */
    private boolean setupAllowed;

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
            // Not the same thing as "disabled": the module manager only ever gets here for a module
            // the config enabled. A false shouldInitialize() means the module itself refused - in
            // practice because a mod it needs is not installed.
            logger.info("Module '{}' is unavailable and will not be initialized", displayName);
            return;
        }

        // Call the implementation-specific initialization
        onInitialize();

        setupAllowed = true;
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
        if (skipSetup("common setup")) {
            return;
        }
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
    public void loadComplete() {
        if (skipSetup("load complete")) {
            return;
        }
        logger.debug("Running load complete for module: {}", displayName);
        onLoadComplete();
    }

    /**
     * Override this method to implement logic after all mods are loaded.
     */
    protected void onLoadComplete() {
        // Default empty implementation
    }

    @Override
    public void clientSetup() {
        if (skipSetup("client setup")) {
            return;
        }
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
     * Whether a setup phase must be skipped because {@link #onInitialize()} never ran.
     *
     * <p>The module manager builds its enabled list from the config flag alone and then hands every
     * entry {@code commonSetup()}, {@code loadComplete()} and {@code clientSetup()}. A module that
     * refused to initialise - {@link #shouldInitialize()} false, i.e. a mod it needs is missing -
     * would otherwise still get those calls and touch types that are not there. In the bundle that
     * surfaced as a logged exception per phase; a standalone jar hangs its listeners without a
     * try/catch ({@code StandaloneModuleBootstrap}) and would take the game down with it.</p>
     *
     * @param phase name of the phase, for the log line
     * @return true if the phase should be skipped
     */
    private boolean skipSetup(String phase) {
        if (setupAllowed) {
            return false;
        }
        logger.debug("Skipping {} for module '{}' - it was never initialized", phase, displayName);
        return true;
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
            return ModuleManager.getInstance().resolveModuleEnabled(moduleId, config.isEnabled());
        } catch (Exception e) {
            // Config not readable (too early, or a broken file): treat the module as OFF. Doing the
            // opposite would let a module act while nothing can confirm the operator wants it.
            logger.debug("Config not available during module enabled check: {}", e.getMessage());
            return false;
        }
    }

}