# Trial Spawner Glow

> **TL;DR** — Click a trial spawner and every mob it currently has out is outlined for twenty seconds, straight through the walls of the chamber - so you can see at a glance whether the wave is finished or one straggler is still hiding in a corner.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `trial_spawner_glow` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_trial_spawner_glow.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_trial_spawner_glow.jar) · also needs `vpa_core` |
| **Config section** | `[modules.trial_spawner_glow]` |
| **Since** | the next release |
<!-- vpa:meta:end -->

## What it does

Right-click a trial spawner and the mobs it currently has out light up — outlined, straight through
the walls of the chamber — for twenty seconds.

That is the whole feature. No item, no recipe, no block. You click the spawner you are fighting and
you can see where its mobs are.

The player who clicked gets a short note in the action bar (`4 mobs highlighted`). A click on a
spawner that has nothing out stays silent.

## Why it exists

A trial chamber is a dark room full of corners, and the spawner tells you nothing useful. Its display
shows the next mob, not the ones already loose. So the awkward part of a trial is rarely the fight —
it is standing in an apparently empty room wondering whether the wave is finished or whether one
stray husk is standing behind a wall, keeping the spawner from moving on.

The spawner already knows the answer. It tracks the mobs it spawned, because that is exactly how it
decides when the wave is over:

```java
public boolean haveAllCurrentMobsDied() {
    return this.currentMobs.isEmpty();
}
```

This module does not compute anything — it just makes that set visible for a moment.

## In detail

### What counts as "its mobs"

Exactly what the spawner is tracking, nothing more. Mobs that wandered in from elsewhere, mobs from
a second spawner and the ominous item spawners an ominous trial drops are all left alone.

Vanilla itself stops tracking a mob once it is dead, has changed dimension, or is more than **47
blocks** from the spawner. A mob that ran off therefore keeps glowing until its twenty seconds are up,
but a second click will no longer find it.

### The twenty seconds, and clicking again

`glow_duration_seconds` sets the length. Clicking a second time re-applies the effect, but Minecraft
only ever keeps the **longer** of two equal-strength effects — so a second click never cuts a glow
short. What it does do is pick up mobs that were not there the first time.

Which is the way to use it: **the glow does not follow the wave.** Mobs the spawner releases after
your click do not light up on their own. Click again when the next batch appears. This is a
deliberate limit — it keeps the module to a single event handler with nothing running in the
background between clicks.

### Your hand stays free

The interaction is never cancelled, so everything you could do to a trial spawner before, you can
still do:

* holding a block, you place the block **and** the mobs light up;
* holding a spawn egg, vanilla rewrites what the spawner spawns, exactly as before.

If you would rather the glow only happened with an empty hand, set `require_empty_hand = true`. It is
off by default because in a trial chamber you are usually holding a weapon, and a feature that only
works when you put your sword away is a feature you will not use.

### Ominous spawners and the cooldown

Both work, and neither needed a special case. An ominous spawner only swaps which configuration
applies. A spawner in its cooldown has nothing tracked, so clicking it does nothing of its own
accord — no state check required.

<!-- vpa:config:start -->
## Configuration

Section `[modules.trial_spawner_glow]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_trial_spawner_glow-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `feedback_message` | boolean | `true` | — | Tell the clicking player in the action bar how many mobs were highlighted. Only sent when at least one mob was reached, so a click on a spawner in cooldown stays silent. |
| `glow_duration_seconds` | int | `20` | 1 ~ 3600 | How long the outline lasts. Clicking again re-applies it, but MobEffectInstance.update only takes the new duration when it is longer at equal amplifier - so a second click never shortens a glow that is still running; it only picks up mobs that were not there the first time. |
| `require_empty_hand` | boolean | `false` | — | Only react when the main hand is empty. Off by default because in a trial chamber you are usually holding a weapon. The interaction is never cancelled either way, so placing a block against the spawner or changing its mob with a spawn egg keeps working exactly as in vanilla. |
| `show_particles` | boolean | `false` | — | Show the effect's swirling particles. Off by default: the outline is the point, and a chamber full of particles hides more than it shows. Passed as the visible flag of the MobEffectInstance, which also drives showIcon. |
<!-- vpa:config:end -->

## Compatibility and known limits

* **Vanilla clients see the glow.** The effect lives on the mob, so the outline is drawn by any
  client — nothing needs to be installed on the player's side. This is also why the module is marked
  server only.
* **Everyone nearby sees it**, not just whoever clicked. That is the point on a shared server; if you
  wanted a private marker, this is not it.
* **It is a real status effect**, so it can be washed off with milk and it shows up wherever effects
  are listed for that mob. It also survives a restart, minus the time that passed.
* The outline needs the entity-outline post-processing chain, the same one spectral arrows and
  `/effect` glowing use. If that is unavailable — some shader packs and the panorama screen — nothing
  glows, for any source.
* Mobs outside your render distance are not drawn at all, so they cannot be outlined either. Through
  walls: yes. Across the map: no.
* [`mob_glow`](mob_glow.md) does something adjacent with a command, per entity type and across the
  whole dimension. The two do not interfere, but a `/mobglow … clear` will also strip a glow this
  module applied.

## Under the hood

**Where the ids come from.** `TrialSpawnerBlockEntity.getTrialSpawner()` and `TrialSpawner.getData()`
are both public; the set itself is `protected` with no getter, so the module reads it through a
one-method `@Accessor` mixin. Worth writing down: that mixin is a convenience, not a necessity —
`BlockEntity.saveCustomOnly(...)` is public and writes the full spawner codec including the tracked
ids, so the same data could be read back out of a tag. That route allocates the spawner's entire
configuration tree per call and hangs on an NBT key name instead of a field name, which is why the
accessor won. The set it returns is the spawner's **live** set, so the module copies it before
iterating.

**Why a server-side effect and not the client-side glow flag.** The tracked ids are not part of the
spawner's update tag and never reach the client, so the approach used by
[`cat_guardian`](cat_guardian.md) and [`axolotl_guardian`](axolotl_guardian.md) — flipping the shared
glow flag locally with an expiry map — would need an extra packet carrying the ids, a network channel
and a client-side handler, for a visually identical result. An effect needs none of that. Setting the
entity's glowing tag instead was rejected for a different reason: that tag persists in the mob's NBT,
so a crash inside the twenty-second window would leave mobs glowing forever. All three routes end up
setting the same internal flag and are equally visible through walls, so "through walls" was never
the thing that decided it.

**Two guards against firing twice.** The right-click event is posted on both the client and the
server, and once per hand. The handler returns unless it is on the server and the hand is the main
one.

**Nothing is cancelled, on purpose.** A trial spawner has no right-click behaviour of its own in
vanilla, so there is nothing to suppress — but it *is* a spawner, and a spawn egg legitimately
reprograms it. Cancelling on the server alone would also leave the client mispredicting what it is
holding.

## See also

* [Mob Glow Command](mob_glow.md) — the same outline, but per entity type and across the dimension
* [Configuration Guide](../guides/configuration.md)
* [All modules](../../README.md#modules)
