# Configuration System Summary

VanillaPlusAdditions features a robust, per-module configuration system integrated with NeoForge's configuration framework.

## Key Features
- **Centralized Config File**: All module settings are kept in `config/vanillaplusadditions-server.toml`.
- **Automatic Generation**: Missing config files or entries are automatically created with default values.
- **Dynamic Reloading**: Changes are picked up when the server/game starts.
- **Hierarchical Structure**: Config is organized by module ID for easy navigation.

## Standard Module Options
Every module (even if it has no custom settings) includes:
- `enabled`: Boolean to turn the module on or off.
- `debug_logging`: Enum (`AUTO`, `ON`, `OFF`) to control the module's log output.

## Advanced Configuration
Some modules support complex configuration types:
- **Lists**: Used for things like `target_structures` or `food_effects`.
- **Formatted Strings**: The `food_effects` module uses a custom string format (`item;effect;duration;amplifier`) for flexible power-user setup.

## Technical Details
The system is built using:
- `AbstractModuleConfig`: Base class for all module configurations.
- `ServerConfig`: Main container class that manages the NeoForge `ModConfigSpec`.
- `ConfigHelper`: Utility for common configuration patterns.

For a detailed list of all available options, see the [Module Configuration Guide](MODULE_CONFIG_GUIDE.md).
