# MobGlow Command Guide

The `mob_glow` module adds a powerful utility command `/mobglow` that helps players and server administrators visualize and track entities by making them glow.

## Command Syntax

### Basic Usage
Makes all entities of a specific type glow indefinitely.
```
/mobglow <entity_type>
```

### With Duration
Makes all entities of a specific type glow for a specified duration.
```
/mobglow <entity_type> <duration>
```
The duration can be:
- A number in seconds (e.g., `30`, `60`)
- A number with a time unit (e.g., `10s`, `5m`, `1h`)
- `infinite` for permanent glow

### Clearing Glow Effects
Remove glow effects from specific types or all entities at once.

Clear specific type:
```
/mobglow <entity_type> clear
```

Clear all glowing entities tracked by the mod:
```
/mobglow all clear
```

## Permissions
By default, the `/mobglow` command requires operator permission level 2. This can be changed in the configuration.

## Configuration
You can customize the command behavior in `vanillaplusadditions-server.toml` under the `[modules.mob_glow]` section:

- `enabled`: Enable or disable the module (Default: `true`)
- `require_op`: Whether the command requires OP permissions (Default: `true`)
- `debug_logging`: Enables detailed logging of command executions (Default: `AUTO`)

## Examples
- `/mobglow minecraft:creeper`: Makes all creepers glow forever.
- `/mobglow minecraft:zombie 30s`: Makes all zombies glow for 30 seconds.
- `/mobglow minecraft:skeleton clear`: Stops all skeletons from glowing.
