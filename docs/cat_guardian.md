# Cat Guardian

## Overview

The Cat Guardian module turns tamed cats into defensive companions that autonomously guard a
Feeding Station. An associated cat patrols the area around its station, engages hostile mobs
within a configurable radius, collects loot, absorbs XP from kills, and converts that XP into
Bottles o' Enchanting. Optionally equip a cat with armor for extra durability and attack power.

---

## Quick Setup

1. Tame a cat (vanilla: raw fish).
2. Craft and place a **Feeding Station** anywhere within the home area.
3. **Shift-click** the Feeding Station with the tamed cat nearby — this associates the cat with
   the station (works on all cats within `association_radius` blocks).
4. Fill the station's **fish inventory** (9 slots, top row) with any fish. The cat eats
   automatically and enters the *guarding* state.
5. The cat will now attack hostile mobs within `guard_radius` × `guard_radius_y` blocks of the
   station and deposit loot/XP back at the station when done.

A cat without a bowl continuously tries to auto-associate with the **nearest** Feeding Station
within `auto_associate_radius` blocks.

---

## Systems

### Feeding Station

The Feeding Station (block entity) is the home base for up to `max_cats_per_station` cats.

| Section        | Slots | Purpose                                              |
|----------------|-------|------------------------------------------------------|
| Fish input     | 9     | Fish the cat eats to refill the *fed* timer          |
| Loot output    | 15    | Mob drops deposited by returning cats                |

Fish are consumed one item at a time. Each fish extends the cat's guarding timer by
`fed_duration_ticks` (default 6000, i.e. 5 minutes). The timer ticks down regardless of state;
the cat eats again automatically when the timer runs out.

Up to `max_cats_per_station` cats may share one station. There is no explicit limit on the
number of stations per world.

### Cat State Machine

A cat transitions through four exclusive states. Priority from highest to lowest:

```
FLEEING > RETURNING > FED (combat/idle) > UNFED
```

| State     | Trigger                                        | Behaviour                                             |
|-----------|------------------------------------------------|-------------------------------------------------------|
| UNFED     | `fed_duration_ticks` expires                   | Eats fish at station; auto-associates if no bowl      |
| FED       | Fish consumed                                  | Scans guard zone and attacks hostile mobs             |
| RETURNING | Loot slots full **or** combat finished         | Navigates home to deposit loot; deposits on arrival   |
| FLEEING   | HP < 20 % of max health                        | Abandons all targets, runs home, heals, resumes duty  |

**RETURNING** is interrupted by a new threat in the guard zone *only* if the cat's 5 loot slots
are not all full. A cat with a full inventory finishes the return trip and deposits first.

**FLEEING** is absolute — the cat ignores all mobs until it reaches the station, sits next to
it, and regenerates above 20 % HP.

The fed timer (`fed_duration_ticks`) ticks down even while fleeing or returning.

### Combat & Loot

The cat uses a custom `TargetGoal` (`CatGuardTargetGoal`) that:

- Scans for `Monster` entities within an axis-aligned box of `guard_radius` (XZ) ×
  `guard_radius_y` (Y) centred on the bowl.
- Selects the nearest non-blacklisted mob.
- Drops the target if it leaves the guard zone (+ 4 block buffer) or if the cat itself drifts
  more than `guard_radius + 10` blocks from the station.
- Blacklists unreachable targets (>4 blocks for 6 consecutive 1-second checks) for 60 seconds.
- Retaliates against any mob that directly attacks the cat, bypassing the blacklist.

**Creepers**: killed instantly before the fuse can ignite. No explosion, loot and XP drop
normally.

**Loot**: mob drops are inserted into the cat's 5 loot slots. Items that don't fit remain in
the world. On arrival at the station the full inventory is transferred to the station's 15 loot
slots. Items that don't fit in the station overflow back into the world at the station's
position.

**Stuck recovery**: if the cat's navigation makes less than 1 block of progress in 30 ticks, the
current target is blacklisted and navigation is restarted. A fresh 30-tick window begins from
the new position.

### Water Navigation

Guardian cats use manual movement steering when in water (`steerThroughWater`, runs every tick):

- **Fully submerged**: only upward force (0.42 m/tick) — no horizontal steering — so the cat
  rises straight up without being pinned against walls or cave ceilings. Horizontal navigation
  resumes once the cat is on land.
- **At the water surface**: gentle horizontal push (0.11 m/tick) toward the goal plus light
  buoyancy (0.10 m/tick) to clear same-level banks without over-jumping.
- **Diving for an aquatic target**: full 3D steering toward the target's eye position with
  continuous Water Breathing applied.

The pathfinder's ground navigation is paused during manual water steering.

### Cat Armor

Equip cat armor by **right-clicking** the cat with an armor item in hand
(owner only; shift+right-click removes armor and returns it to inventory).

| Tier        | Durability | Attack bonus |
|-------------|-----------|--------------|
| Iron        | 200        | +1.0 damage  |
| Gold        | 100        | +2.0 damage  |
| Diamond     | 400        | +3.0 damage  |
| Netherite   | 600        | +4.0 damage  |

Armor absorbs 100 % of damage the cat takes (the cat's own HP is unchanged; only the armor
loses durability). When durability reaches 0 the armor breaks and falls off.

The armor slot is visible in the cat's inventory screen (right-click the cat without shift).

### XP & Bottles

Kills redirect their XP into the cat's personal XP buffer instead of spawning orbs:

1. **Cat buffer**: stores up to `cat_xp_capacity` XP points (default 500). Overflow stays in
   the world as normal orbs.
2. **Transfer to station**: when the cat deposits loot, the buffer is also transferred to the
   station's XP counter (up to `station_xp_capacity`, default 5000).
3. **Bottle conversion**: the station converts stored XP into Bottles o' Enchanting at
   `xp_per_bottle` XP each (default 8). Bottles appear in the station's loot inventory; the
   station pauses production if the loot inventory is full.

XP stats (buffer / cap) are displayed in the Goggles overlay.

### Goggles Overlay (requires Create mod)

Equip **Create Goggles** and look at a guardian cat to see a HUD overlay with:

- HP bar (current / max)
- Armor durability bar (if armored)
- XP bar (cat buffer)

Press the **overlay toggle keybind** (default `NumPad +`, rebindable in Controls) to switch to
the extended view:

- Rendered guard-zone bounding box around the associated bowl
- Coloured outline on the cat's current target
- Coloured outline on the cat itself

The keybind can be toggled even without goggles equipped; the extended outlines persist until
toggled off.

---

## Config Reference

All values are in `vanillaplusadditions-common.toml` under `[modules.cat_guardian]`.

| Key                    | Type    | Default | Range         | Description                                                   |
|------------------------|---------|---------|---------------|---------------------------------------------------------------|
| `association_radius`   | double  | 64.0    | 1–128         | Radius (blocks) for shift-click bowl association              |
| `fed_duration_ticks`   | int     | 6000    | 20–144000     | Ticks one fish feeding lasts (6000 = 5 min)                   |
| `guard_radius`         | double  | 32.0    | 2–128         | XZ radius of the guard zone around the bowl                   |
| `guard_radius_y`       | double  | 16.0    | 2–128         | Vertical half-height of the guard zone                        |
| `auto_associate_radius`| double  | 1.5     | 0.5–4.0       | Radius for automatic bowl association (no shift-click needed) |
| `glow_duration_seconds`| int     | 30      | 1–300         | Seconds the Glowing effect lasts when looking at a bowl       |
| `max_cats_per_station` | int     | 8       | 1–64          | Max cats associated with a single station                     |
| `cat_xp_capacity`      | int     | 500     | 0–10000       | Max XP a single cat can hold before overflow drops normally   |
| `station_xp_capacity`  | int     | 5000    | 0–100000      | Max XP the station can store before overflow stays on cats    |
| `xp_per_bottle`        | int     | 8       | 1–64          | XP consumed per Bottle o' Enchanting produced                 |

---

## Known Limitations

- Cats do not pursue aquatic targets *beyond* the guard zone; they only dive for targets that
  entered the zone on land and then moved into water.
- Items dropped when the cat's 5 loot slots are full are permanently lost (not retrieved later).
- XP bottle production pauses when the station's 15 loot slots are full; it resumes on the
  next loot deposit.
- Cat state (fed timer, loot, XP, armor) is stored as entity attachment data and therefore lost
  if the cat entity is killed or replaced.
