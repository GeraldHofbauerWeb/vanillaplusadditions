# Configuration Guide

VanillaPlusAdditions keeps every setting in a single TOML file, organised per module. It is created
on first launch with defaults, so there is nothing to set up before you start.

## Where the file lives

| You installed | File |
|---|---|
| the all-in-one jar | `config/vanillaplusadditions-common.toml` |
| a standalone module jar | `config/vpa_<module>-common.toml`, e.g. `config/vpa_cat_guardian-common.toml` |

Each standalone jar carries its own file with the global settings plus its own module section, so
running three module jars gives you three small config files instead of one large one.

The config is a NeoForge `COMMON` config: on a dedicated server it lives in the server's `config/`
folder, in single player in your instance's `config/` folder. Values are read at startup — the file
is not re-read while the game is running.

## Structure

A top-level block of global settings, then one section per module under `[modules]`:

```toml
#Enable debug logging for all modules (can be overridden by individual module settings)
globalDebugLogging = false

	[modules.minecart_chunk_loading]
		enabled = true
		debug_logging = "AUTO"
		#Chunk radius (Chebyshev) force-loaded around an active loader rail.
		# Default: 2
		# Range: 0 ~ 8
		chunk_load_radius = 2
```

Every key carries its own explanation as a comment, and numeric keys additionally state their
default and their permitted range. The file is self-documenting — you rarely need this guide open
next to it.

## Global settings

| Setting | Type | Default | Description |
|---|---|---|---|
| `globalDebugLogging` | Boolean | `false` | Turns on debug logging for all modules at once. Individual modules can still override it — see the [Debug Logging Guide](debug-logging.md). |
| `worldgenCrashGuardEnabled` | Boolean | `false` | Emergency workaround that suppresses an `IndexOutOfBoundsException` during structure generation. Only meant to keep a server running while you track down an incompatible worldgen mod. |

## Settings every module has

| Setting | Type | Default | Description |
|---|---|---|---|
| `enabled` | Boolean | varies | Whether the module is active. Most default to `true`; a few ship disabled. |
| `debug_logging` | Enum | `AUTO` | `AUTO` follows `globalDebugLogging`, `ON` and `OFF` force it for this module. |

A module whose required mod is missing stays inert regardless of `enabled` — for instance
`copycat_pathfinding` does nothing without Create.

## Value types beyond true/false

* **Numbers with a range.** Rejected values fall back to the default, and the permitted range is
  written into the file as a comment.
* **Lists.** Used where a module takes any number of entries, such as `target_structures` or
  `food_effects`.
* **Formatted strings.** A few modules pack several fields into one string, separated by `;`.
  `food_effects` uses `item_id;effect_id;duration_in_ticks;amplifier`, and the Better Mobs zone
  lists use entries such as `GEAR_TYPES;gold;10` or
  `WEAPON_RANDOMIZER;minecraft:skeleton;sword;12`. The exact format is documented on each module's
  own page.

> **Tip:** durations are in game ticks. 1 second = 20 ticks.

## Editing and resetting

1. Stop the server, or close the game.
2. Edit the file in any text editor.
3. Start again — the new values are read at startup.

To reset, delete the file and restart; a fresh one is generated with all defaults.

## Every key, in one place

The two settings above are the ones shared by all modules. For the complete list of every key in
every module, with defaults, ranges and effects, see the **[Configuration Reference](../reference/config.md)** —
it is generated from the source, so it cannot drift out of date.

Each module's own page documents its keys in context, which is usually the more useful place to
start: see the [module list](../../README.md#modules).

## How it works

* `AbstractModuleConfig` — base class every module's config extends; provides `enabled` and
  `debug_logging` and calls `buildModuleSpecificConfig` for the module's own keys.
* `ModulesConfig` — assembles the `ModConfigSpec` for the bundle, or for a single module in a
  standalone jar (`buildStandaloneSpec`).
* `ConfigHelper` — shared helpers for recurring patterns.

Adding a module means adding its config class; the file layout follows automatically. See the
[Module System](module-system.md) guide.

## See also

* [Debug Logging Guide](debug-logging.md)
* [Module System](module-system.md)
* [Configuration Reference](../reference/config.md) — every key, generated
