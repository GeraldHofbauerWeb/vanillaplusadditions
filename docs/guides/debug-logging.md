# Debug Logging Configuration

VanillaPlusAdditions includes a flexible debug logging system with both global and per-module control. This is useful for troubleshooting issues, verifying that modules are working correctly, or reporting bugs.

## How It Works

Debug logging operates on two levels:

1. **Global** — a single toggle that enables debug output for all modules.
2. **Per-module** — each module has its own `debug_logging` setting that can override the global value.

### Per-Module Modes

| Mode   | Behaviour                                                        |
|--------|------------------------------------------------------------------|
| `AUTO` | Follows the global `globalDebugLogging` setting (this is the default). |
| `ON`   | Forces debug logging **on** for this module, regardless of the global setting. |
| `OFF`  | Forces debug logging **off** for this module, regardless of the global setting. |

## Configuration

All settings live in:

```
config/vanillaplusadditions-server.toml
```

### Enable Debug Logging Globally

```toml
# At the top of the file
globalDebugLogging = true
```

This turns on debug output for every module whose `debug_logging` is set to `AUTO` (the default).

### Enable Debug Logging for a Single Module

Leave the global setting off and override only the module you're interested in:

```toml
globalDebugLogging = false

[modules.food_effects]
    enabled = true
    debug_logging = "ON"
```

### Disable Debug Logging for a Noisy Module

If you have global logging enabled but one module is too verbose:

```toml
globalDebugLogging = true

[modules.better_mobs]
    enabled = true
    debug_logging = "OFF"
```

## What Gets Logged

When debug logging is active for a module, you'll see messages like:

- Configuration load/reload events
- Cache rebuilds (e.g., Food Effects reloading its item → effect map)
- Per-event processing details (e.g., which effects were applied to a player)
- Warnings for missing items or effects in the config
- Module enable/disable state changes

All debug messages are written to the standard server log (`logs/latest.log`).

## Example: Debugging Food Effects

1. Open `config/vanillaplusadditions-server.toml`.
2. Set the Food Effects module to verbose:
   ```toml
   [modules.food_effects]
       enabled = true
       debug_logging = "ON"
   ```
3. Restart the server.
4. Eat a configured food item and check `logs/latest.log` for output like:
   ```
   [DEBUG] Food effects cache reloaded. 5 items configured.
   [DEBUG] Applied 1 effects to Steve after eating cookie
   ```

## Tips

- **Don't leave global debug logging on in production** — it can generate a lot of output.
- Use `debug_logging = "ON"` on individual modules when investigating a specific issue.
- If an item or effect ID is wrong, debug logging will show warnings about missing registry entries.
- To reset everything, delete the config file and restart — defaults will be regenerated.
