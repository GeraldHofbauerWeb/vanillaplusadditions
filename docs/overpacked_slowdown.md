# Overpacked Slowdown Override

## Overview

Rescales the movement-speed penalty applied by the **Overpacked** mod when a backpack is full,
without needing to patch Overpacked itself. Useful for servers that want full backpacks without
(or with reduced) the speed penalty.

---

## Requirements

- **Overpacked** mod. Without it, this module has nothing to override.

---

## Configuration

| Key                   | Default | Range    | Description                                                        |
|------------------------|---------|----------|------------------------------------------------------------------|
| `slowdown_multiplier` | `0.0`   | 0.0–10.0 | Multiplier applied to Overpacked's slowdown penalty               |

- `0.0` — removes the slowdown entirely
- `0.5` — half of Overpacked's normal slowdown
- `1.0` — unchanged (vanilla Overpacked behaviour)
- `2.0` — double the slowdown

---

## Behaviour

Overpacked computes its own slowdown internally based on backpack item count (`27–53` items
→ `0.1`, `54–80` → `0.2`, `81+` → `0.3`, stacked multiplicatively across multiple backpacks).
This module re-runs that same calculation right after Overpacked's tick handler, then
re-applies the `overpacked:speed` attribute modifier scaled by `slowdown_multiplier`. Server-
side only; client ticks are ignored.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
