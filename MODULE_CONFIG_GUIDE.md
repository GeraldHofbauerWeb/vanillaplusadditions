# Module Configuration Guide

VanillaPlusAdditions uses a modular configuration system. All settings are stored in a single TOML config file located at:

```
<your-server-or-instance>/config/vanillaplusadditions-server.toml
```

The file is automatically generated on first launch with sensible defaults.

## Config File Structure

The configuration is organized into a top-level global section and a `[modules]` section containing per-module settings:

```toml
# Global settings
globalDebugLogging = false

[modules]

    [modules.hostile_zombified_piglins]
        enabled = true
        debug_logging = "AUTO"

    [modules.food_effects]
        enabled = true
        debug_logging = "AUTO"
        food_effects = [
            "minecraft:cookie;minecraft:speed;160;1",
            ...
        ]
```

## Global Settings

| Setting              | Type    | Default | Description                                      |
|----------------------|---------|---------|--------------------------------------------------|
| `globalDebugLogging` | Boolean | `false` | Enables debug logging for all modules at once.   |

Individual modules can override this — see [Debug Logging Guide](DEBUG_LOGGING_CONFIG.md).

## Per-Module Settings

Every module automatically gets these two standard options:

| Setting         | Type   | Default  | Description                                                                 |
|-----------------|--------|----------|-----------------------------------------------------------------------------|
| `enabled`       | Boolean| varies   | Whether the module is active. Some modules default to `true`, others `false`. |
| `debug_logging` | Enum   | `AUTO`   | Debug logging mode: `AUTO`, `ON`, or `OFF`.                                 |

Some modules add their own settings on top of these (see below).

## Available Modules

### Hostile Zombified Piglins (`hostile_zombified_piglins`)
Makes zombified piglins always hostile in the Nether.

- **Enabled by default**: Yes

### Wither Skeleton Enforcer (`wither_skeleton_enforcer`)
Prevents normal skeletons from spawning in the Nether, replacing them with Wither Skeletons.

- **Enabled by default**: Yes

### MobGlow Command (`mob_glow`)
Adds a `/mobglow` command to make specific mob types glow for easier tracking.

- **Enabled by default**: Yes

### Better Mobs (`better_mobs`)
Enhances mob variety — mobs can spawn with armor and potion effects.

- **Enabled by default**: Yes
- **Extra settings**: `drop_chance`, `max_durability`, `above_zero`, `below_zero`, `nether_end`, `enabled_mobs`, `enabled_mobs_with_armor`

### Death Coords Logger (`death_coordinates`)
Logs player death coordinates in chat. Operators can click to teleport.

- **Enabled by default**: Yes

### Stackables (`stackables`)
Makes normally non-stackable items stackable (stews, potions, etc.).

- **Enabled by default**: Yes

### Haunted House (`haunted_house`)
Creates a spooky experience in configured structures with invisible mobs and fog.

- **Enabled by default**: No (requires Alex's Mobs and Dungeons and Taverns)
- **Extra settings**: `witch_spawn_boost_chance`, `enable_fog_effect`, `fog_effect_amplifier`, `target_mobs`, `target_structures`

### Food Effects (`food_effects`)
Assigns configurable potion effects to food items when consumed.

- **Enabled by default**: Yes
- **Extra settings**: `food_effects` — a list of entries in the format:

```
item_id;effect_id;duration_in_ticks;amplifier
```

**Default entries:**

| Item | Effect | Duration | Amplifier |
|------|--------|----------|-----------|
| `minecraft:cookie` | `minecraft:speed` | 160 (8s) | 1 (Speed II) |
| `minecraft:rabbit_stew` | `toughasnails:heating` | 72000 (60min) | 0 |
| `minecraft:mushroom_stew` | `toughasnails:heating` | 12000 (10min) | 0 |
| `minecraft:beetroot_soup` | `toughasnails:heating` | 12000 (10min) | 0 |
| `rottencreatures:magma_rotten_flesh` | `toughasnails:heating` | 36000 (30min) | 0 |
| `rottencreatures:frozen_rotten_flesh` | `toughasnails:cooling` | 36000 (30min) | 0 |

## Editing the Config

1. Stop the server (or close the game).
2. Open `config/vanillaplusadditions-server.toml` in any text editor.
3. Make your changes and save.
4. Start the server again — the config will be reloaded automatically.

> **Tip:** Duration values are in game ticks. 1 second = 20 ticks.

## Resetting to Defaults

Delete the `vanillaplusadditions-server.toml` file and restart. A fresh config with all defaults will be generated.
