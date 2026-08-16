# Hostile Endermen

## Overview

Endermen in the **End** stop being neutral: they pick a nearby player and attack on their own —
no staring required. Endermen in the Overworld and the Nether are not touched at all and keep
their vanilla behaviour.

Module id: `hostile_endermen` · standalone jar: `vpa_hostile_endermen`

---

## Behaviour

- Every enderman that spawns into (or is loaded in) the End immediately becomes angry at the
  nearest valid player within `detection_range`.
- Hostility is re-evaluated **once per second** per enderman: as long as the current victim stays
  in range, the anger timer is refreshed; otherwise the nearest other player is picked.
- As soon as no valid player is within `detection_range`, the enderman calms down **immediately**
  (within the 1-second check): anger, the player target and the revenge memory are cleared, so
  leaving the area really ends the fight instead of dragging an aggro train along. A hunt for a
  non-player target (endermite) is left untouched.
- Players in **creative** or **spectator** mode are ignored.
- A **carved pumpkin** still protects (see `respect_carved_pumpkin`). The check uses NeoForge's
  own hook, so modded ender masks and mods cancelling `EnderManAngerEvent` keep working too.

Implementation: the module only sets the enderman's *persistent anger target*. Vanilla's
`EndermanLookForPlayerGoal` already targets anyone matching
`isLookingAtMe(player) || isAngryAt(player)`, so the vanilla AI does the actual hunting — including
teleporting closer when the player is more than 16 blocks away. No mixin, no custom AI goals.

Consequences of staying on the vanilla goal (all intentional):

- The goal requires **line of sight** within the enderman's follow range (64 blocks), so an angry
  enderman behind a wall only starts moving once it can see the player.
- Endermen still **freeze while stared at** and still **teleport away** when the player looks at
  them from less than 4 blocks — the classic enderman fight is unchanged.

---

## Enderman Overhaul compatibility

Enderman Overhaul's endermen extend the vanilla `EnderMan` class, so they are covered by this
module as well. Its **End Enderman** additionally teleports its victim away on hit
(`EndEnderman#doHurtTarget` → `ModUtils.teleportTarget`, 50 % per hit by default, a random blink of
roughly ±12 blocks). With this module making every enderman attack unprovoked, that ability would
constantly fling players around for a fight they never picked.

With `suppress_teleport_attack` (default on) the teleport is skipped **as long as the player has
not hit that enderman back**. Vanilla tracks the last attacker for 100 ticks, so during an actual
duel the End Enderman keeps its full moveset; a player who only runs away is no longer displaced.

Deliberately left alone:

- The **Ender Bullet** of the End Islands Enderman (teleports on every hit, and is not covered by
  Enderman Overhaul's own config either) and the Corrupted Shield/Blade teleports.
- Enderman Overhaul's own config: `endEndermanTeleportChance` stays untouched, so without this
  module (or with the option off) the mod behaves exactly as its author intended.

The compat hook is a mixin against a string target — if Enderman Overhaul is not installed, the
mixin is simply disabled (both mixin configs are `"required": false`).

---

## EnhancedAI compatibility

EnhancedAI's **Teleport anti-cheese** (`TeleportAntiCheeseGoal`, entity tag
`enhancedai:mobs/teleport_anti_cheese/can_use` = `minecraft:enderman`) drags the target over to the
enderman whenever the enderman's navigation cannot reach it. That exists to stop players from
bullying endermen from a safe spot — but with this module every enderman in the End hunts
unprovoked, so simply walking away (or dropping off a ledge) yanked the player right back in. In a
diagnosed session this accounted for **every single** unwanted teleport; Enderman Overhaul's melee
teleport never fired once.

With `suppress_anticheese_teleport` (default on) the goal is vetoed in `canUse()` — it never starts,
so there is no teleport and no teleport sound — for **endermen targeting a player in the End that
has not hit them back**. The same self-defence window as above applies, which restores the
feature's original purpose: shooting an enderman from an unreachable spot still gets you dragged
in, only running away no longer does. Overworld/Nether endermen, other mobs in the tag, and
EnhancedAI's own config are untouched.

### Teleport diagnostics

`debug_teleport_tracking` (default off) logs every in-dimension player teleport with its caller
stack via a hook in `ServerPlayer#teleportTo`. Vanilla's `randomTeleport` routes through it, so
chorus fruit, ender pearls, waystones and any mod-side displacement show up with the responsible
class — that is how the anti-cheese goal above was identified. Leave it off in normal play.

---

## Config Reference

All values are in `vanillaplusadditions-common.toml` under `[modules.hostile_endermen]`.

| Key                      | Type    | Default | Range     | Description                                                              |
|--------------------------|---------|---------|-----------|--------------------------------------------------------------------------|
| `enabled`                | boolean | true    | —         | Enables the module (also togglable at runtime via `/vpa`)                |
| `detection_range`        | int     | 16      | 1–128     | Radius in blocks for automatic hostility, and the distance at which endermen calm down again (values above the vanilla follow range of 64 have no extra effect) |
| `anger_duration`         | int     | 600     | -1–MAX    | Ticks the anger lasts (-1 = indefinite); refreshed every second while a player is in range |
| `respect_carved_pumpkin` | boolean | true    | —         | Players wearing a carved pumpkin / ender mask are not attacked automatically |
| `suppress_teleport_attack`| boolean| true    | —         | Enderman Overhaul compat: no teleport-on-hit while the player has not hit that enderman back |
| `suppress_anticheese_teleport`| boolean| true | —      | EnhancedAI compat: endermen in the End no longer drag players over to themselves while the player has not hit them back |
| `debug_teleport_tracking`| boolean| false   | —         | Diagnostics: log every player teleport with the caller stack |

---

## Known Limitations

- Only the End is covered by design; there is no per-dimension list.
- An enderman without line of sight to any player stays idle until it can see one, even while angry.
