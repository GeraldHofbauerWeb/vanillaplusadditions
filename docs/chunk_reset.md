# Chunk Reset Command

## Overview

An admin command to permanently delete chunks so they regenerate from scratch the next time
they're loaded — useful for resetting a test area, regenerating after a worldgen change, or
clearing a builder's plot back to vanilla terrain. Destructive, so it's gated behind a
two-step confirmation.

---

## Commands

Requires **permission level 4** (operator).

| Command                     | Description                                                          |
|------------------------------|------------------------------------------------------------------------|
| `/chunkreset`                | Queue the player's current chunk for reset and show a confirm prompt  |
| `/chunkreset <radius>`       | Queue a square of chunks around the player (radius 0–5, i.e. up to 11×11 = 121 chunks) |
| `/chunkreset confirm`        | Execute the pending reset                                              |
| `/chunkreset cancel`         | Cancel the pending reset                                                |

Running `/chunkreset` (with or without a radius) shows a chat message with clickable
`[✓ Confirm]` / `[✗ Cancel]` buttons — nothing is deleted until confirmed.

---

## Behaviour

- Players standing in an affected chunk are teleported to a safe location before deletion, so
  no one is lost in the void mid-reset.
- Forced-loading tickets on affected chunks are removed before the chunk data is erased.
- Chunks regenerate normally (current world seed + datapacks) the next time something loads
  them — walking back in, or any forced load.
- Per-chunk success/failure is logged.

---

## Configuration

None — this module has no configurable options.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
