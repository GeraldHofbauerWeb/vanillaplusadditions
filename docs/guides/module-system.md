# Module System Overview

VanillaPlusAdditions is built from independent modules. New features can be added as new modules, and each existing one can be configured and switched on or off on its own.

## Architecture

The system consists of three main components:

### 1. `Module` Interface
The base interface (`net.geraldhofbauer.vanillaplusadditions.core.Module`) defines the lifecycle of a module:
- `initialize`: Called during mod construction.
- `commonSetup`: Called for registry-dependent setup.
- `clientSetup`: Called for client-only logic.
- `loadComplete`: Called after all mods are loaded.

### 2. `AbstractModule`
Most modules extend `AbstractModule`, which provides:
- Automatic configuration handling.
- Built-in logging.
- Lifecycle management.
- Convenience methods like `isModuleEnabled()`.

### 3. `ModuleManager`
The central registry that manages all modules. It handles:
- Module registration.
- Lifecycle event distribution.
- Global module status tracking.

## Adding a New Module

To add a new feature:
1. Create a new package under `net.geraldhofbauer.vanillaplusadditions.modules`.
2. Create a configuration class extending `AbstractModuleConfig`.
3. Create a module class extending `AbstractModule`.
4. Register your module in the `VanillaPlusAdditions` main class.

## Benefits
- **Isolation**: Features don't interfere with each other.
- **Configurability**: Every module can be enabled/disabled individually.
- **Clean Code**: Standardized structure for all features.
- **Performance**: Disabled modules have minimal impact on game resources.
