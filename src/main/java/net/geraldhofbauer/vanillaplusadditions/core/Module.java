package net.geraldhofbauer.vanillaplusadditions.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/**
 * Base interface for all VanillaPlusAdditions modules.
 * Each module represents a self-contained feature or group of related features.
 */
public interface Module {

    /**
     * Gets the unique identifier for this module.
     * Should be lowercase and use underscores for separation.
     *
     * @return The module ID
     */
    String getModuleId();

    /**
     * Gets the display name for this module.
     * Used in logs and configuration.
     *
     * @return The module display name
     */
    String getDisplayName();

    /**
     * Gets the description of what this module does.
     *
     * @return The module description
     */
    String getDescription();

    /**
     * Called during the mod construction phase.
     * Use this to register deferred registers, event listeners, etc.
     *
     * @param modEventBus  The mod event bus
     * @param modContainer The mod container
     */
    void initialize(IEventBus modEventBus, ModContainer modContainer);

    /**
     * Called during the common setup phase.
     * Use this for setup that needs to happen after registries are populated.
     */
    default void commonSetup() {
        // Default empty implementation
    }

    /**
     * Called when the mod loading process is complete and all mods are registered.
     */
    default void loadComplete() {
        // Default empty implementation
    }

    /**
     * Called during client setup phase.
     * Only called on the client side.
     */
    default void clientSetup() {
        // Default empty implementation
    }

    /**
     * Whether this module is enabled by default.
     * Can be overridden by configuration.
     *
     * @return true if enabled by default
     */
    default boolean isEnabledByDefault() {
        return true;
    }

    /**
     * Whether this module can be disabled via configuration.
     * Some core modules might not be disableable.
     *
     * @return true if the module can be disabled
     */
    default boolean isConfigurable() {
        return true;
    }

    /**
     * Gets the configuration instance for this module.
     * All modules must have a configuration instance, even if it only contains
     * the standard "enabled" property.
     *
     * @return The module's configuration instance (never null)
     */
    ModuleConfig getConfig();
}
