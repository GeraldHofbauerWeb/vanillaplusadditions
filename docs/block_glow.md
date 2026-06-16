# Block Glow

## Overview

A client-side `/blockglow` command that highlights every block of a chosen type within a
search radius, outlined in a configurable color, for a configurable duration. Useful for
locating ores, scattered loot blocks, or anything else by ID.

---

## Commands

| Command                                       | Description                                          |
|------------------------------------------------|-------------------------------------------------------|
| `/blockglow <block_id>`                       | Highlight all `<block_id>` blocks within the default radius/duration |
| `/blockglow <block_id> <radius>`              | Highlight with a custom search radius                |
| `/blockglow <block_id> <radius> <duration_seconds>` | Highlight with custom radius and duration (`0` = infinite) |
| `/blockglow clear`                            | Clear the current highlight                          |

`block_id` supports full tab-completion. The command runs entirely client-side — no server
permission is required.

---

## Configuration

| Key                       | Default   | Range      | Description                                              |
|----------------------------|-----------|------------|------------------------------------------------------------|
| `default_radius`          | `24`      | 1–128      | Search radius (blocks) used when not specified            |
| `max_radius`              | `64`      | 1–256      | Hard cap on the radius argument                            |
| `default_duration_seconds`| `60`      | 0–∞        | Default highlight duration; `0` = infinite                |
| `max_highlights_per_frame`| `512`     | 16–8192    | Outline render budget per frame (performance tuning)       |
| `selection_mode`          | `nearest` | `nearest` \| `scan_order` | How to pick which blocks to render when the per-frame limit is exceeded |
| `outline_color.red`       | `0.0`     | 0–1        | Outline color, red channel                                 |
| `outline_color.green`     | `1.0`     | 0–1        | Outline color, green channel                                |
| `outline_color.blue`      | `1.0`     | 0–1        | Outline color, blue channel                                 |
| `outline_color.alpha`     | `1.0`     | 0–1        | Outline opacity                                              |

Default outline color is cyan.

---

## Compatibility

- **Sable**: when installed, block glow also works across dimensions linked via Sable.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
