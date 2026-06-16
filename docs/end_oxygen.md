# End Oxygen

## Overview

Removes breathable air from the End dimension. Players' air supply depletes like underwater,
forcing the use of Water Breathing potions or Create backtanks + diving helmet to survive
extended exploration. Adds a survival/logistics layer to End exploration beyond combat.

---

## Behaviour

- Applies only in the **End** dimension; ignored in Creative/Spectator mode.
- Air depletes on a tick interval instead of instantly, and damages the player once it
  reaches zero (custom damage type `vanillaplusadditions:out_of_oxygen`).
- The air bubble HUD is force-rendered in the End even at full air, as a reminder.
- Water Breathing potions slow the depletion rate (more effect levels = slower).
- Create **backtanks** + a diving helmet supply air independently of the vanilla bar, with
  their own depletion rate and an on-screen timer (`MM:SS`, turns red under 1 minute).

---

## Configuration

| Key                                          | Default | Range      | Description                                                               |
|-----------------------------------------------|---------|------------|------------------------------------------------------------------------------|
| `air_consumption_interval`                   | `2`     | 1–300      | Ticks between each air-depletion step (`1` = vanilla underwater speed)    |
| `water_breathing_effect_interval_bonus`      | `4`     | 0–100      | Extra ticks added per Water Breathing level, slowing depletion             |
| `out_of_air_damage`                          | `2.0`   | 0.5–20.0   | Damage dealt per hit once air is fully depleted                            |
| `damage_tick`                                | `20`    | 1–200      | Ticks between damage hits while out of air                                |
| `backtank.backtank_depletion_rate`           | `20`    | 0–1000     | Ticks between backtank air consumption; `0` disables backtank depletion   |
| `backtank.requires_full_set`                 | `true`  | —          | If `true`, a diving helmet is required alongside the backtank to breathe; if `false`, the backtank alone suffices |

---

## Requirements

- **Create** mod, for backtank integration (`vanillaplusadditions:backtanks` and
  `vanillaplusadditions:diving_helmets` item tags control which items count).

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
