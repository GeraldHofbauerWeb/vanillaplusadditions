# Arm Target Overlay

## Overview

Shows the input (TAKE) and output (DEPOSIT) positions of a Create **Mechanical Arm** as
X-ray outlines while looking at it and wearing compatible goggles. Helps you debug arm
configurations without breaking the block or opening its GUI.

---

## Requirements

- **Create** mod (the Mechanical Arm itself)
- A pair of goggles in the `vanillaplusadditions:arm_goggles` item tag, worn in the head slot
  (or a Curios slot, if supported by the item). By default this includes:
  - Create's **Engineering Goggles**
  - Aeronautics' **Aviation Goggles** (if Aeronautics is installed)

---

## Behaviour

While wearing compatible goggles and looking directly at a placed Mechanical Arm:

- Each configured **input** position is outlined in the input color.
- Each configured **output** position is outlined in the output color.
- Outlines render through solid blocks (X-ray style) so you can see targets behind walls.

The arm's input/output lists are internal to Create and not exposed via public API, so this
module reads them via reflection (see `docs/ARM_TARGET_OVERLAY_CASE_STUDY.md` for the
implementation story).

---

## Configuration

| Key                  | Default | Description                              |
|----------------------|---------|-------------------------------------------|
| `input_color.red`    | `1.0`   | Input outline color, red channel (0–1)    |
| `input_color.green`  | `0.6`   | Input outline color, green channel (0–1)  |
| `input_color.blue`   | `0.1`   | Input outline color, blue channel (0–1)   |
| `input_color.alpha`  | `0.8`   | Input outline opacity (0–1)               |
| `output_color.red`   | `0.1`   | Output outline color, red channel (0–1)   |
| `output_color.green` | `0.9`   | Output outline color, green channel (0–1) |
| `output_color.blue`  | `0.7`   | Output outline color, blue channel (0–1)  |
| `output_color.alpha` | `0.8`   | Output outline opacity (0–1)              |

Default colors: orange-ish for inputs, cyan-ish for outputs.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
- [Arm Target Overlay Case Study](ARM_TARGET_OVERLAY_CASE_STUDY.md) — implementation deep-dive
