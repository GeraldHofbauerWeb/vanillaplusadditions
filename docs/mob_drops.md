# Mob Drops

## Overview

Adds extra, configurable item drops to any mob on death — independent of vanilla/datapack
loot tables. Good for adding rare drop chances without touching loot table JSON.

---

## Configuration

`mob_drops` is a list of strings, one per drop rule:

```
mob_id;item_id;chance[;max_drops]
```

| Field        | Meaning                                                        |
|--------------|-----------------------------------------------------------------|
| `mob_id`     | Entity type ID, e.g. `minecraft:wither_skeleton`                |
| `item_id`    | Item to drop, e.g. `minecraft:wither_skeleton_skull`            |
| `chance`     | Drop probability, `0.0`–`1.0`                                   |
| `max_drops`  | Optional. Drop count is randomized `1`–`max_drops` (default `1`) |

A mob can have multiple drop rules; each is rolled independently.

### Defaults

| Mob              | Item                          | Chance | Max drops |
|-------------------|--------------------------------|--------|-----------|
| Wither Skeleton  | Wither Skeleton Skull          | 12.5%  | 1         |
| Wither Skeleton  | Golden Apple                   | 40%    | 1         |
| Wither Skeleton  | Netherite Scrap                | 10%    | 1         |
| Warden           | Enchanted Golden Apple         | 100%   | 3         |

---

## Behaviour notes

- Invalid entries (wrong field count, out-of-range chance) are skipped and logged, not fatal.
- Enable [debug logging](DEBUG_LOGGING_CONFIG.md) to see every drop roll and assignment.

---

## See also

- [Module Configuration Guide](MODULE_CONFIG_GUIDE.md)
