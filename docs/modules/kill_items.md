# Kill Items

> **TL;DR** — An operator command that sweeps up the dropped items lying around you - sixteen blocks by default, any radius you name, with a dry run that tells you what it would clear before anything disappears.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `kill_items` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_kill_items.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_kill_items.jar) · also needs `vpa_core` |
| **Config section** | `[modules.kill_items]` |
| **Since** | the next release |
<!-- vpa:meta:end -->

## What it does

`/kill-items` removes the dropped items lying around you. With no argument it clears everything
within **16 blocks**; give it a number and it clears that radius instead.

```
/kill-items                 → 16 blocks around you
/kill-items 48              → 48 blocks around you
/kill-items 48 dry-run      → counts them and tells you what is there, deletes nothing
/kill-items dry-run         → the same, at the default radius
```

Every answer names the count, the radius, the position it measured from and the dimension, plus the
three most common item types so you can see what went:

```
Removed 214 dropped item(s) within 16 blocks of 128.5, 64.0, -240.3 in minecraft:overworld. (96x Cobblestone, 64x Andesite, 18x Stick, +7)
```

`/vpakillitems` is the same command under the naming scheme the rest of this mod uses — every other
command here is unhyphenated (`vpaunstuck`, `vparelink`, `chunkreset`, `mobglow`). Use whichever you
remember.

## Why it exists

Vanilla can do this, but not comfortably. `/kill @e[type=item]` empties the whole dimension, which is
never what you want after a fight in one room. `/kill @e[type=item,distance=..16]` is closer, but it
always measures from whoever runs it, cannot be given a sensible default, and tells you nothing about
what it just destroyed.

This command fixes those three things: the radius has a configurable default, the origin comes from
the command source rather than from a player, and `dry-run` answers "what would happen" before
anything happens.

## In detail

### Where the radius is measured from

From the command source, not from a player. That is a small decision with a large payoff:

```
/execute in the_nether positioned 0 64 0 run kill-items 32
/execute at Sebi run kill-items
/execute as @e[type=armor_stand,limit=1] at @s run kill-items 8
```

all work as written. It also means the command runs from the server console — where the origin is the
overworld's shared spawn position, not `0 0 0`. That can surprise you, which is exactly why the answer
always states the position and dimension it used.

By default the radius is a true **sphere**. The box Minecraft actually searches is axis-aligned, so
without the extra distance check a corner of it would reach about 1.7 times the number you typed.
Setting `spherical = false` gives you that cube, if you want it.

### Only loaded chunks, only your dimension

The search asks the level's own entity index, which holds nothing but the entities in that level's
loaded chunks — and loads no chunk to answer. Running the command in the overworld never reaches into
the nether, and items in chunks nobody has loaded are not touched.

### What is not touched

Only loose dropped items. Anything already **inside** something is safe, and not by a filter we wrote
— structurally:

* items in a chest, barrel or hopper are item stacks in a block inventory, not entities;
* items on a Create belt, in a chute or in a funnel are the same, held as Create's own transported
  stacks.

There is no half-state to catch either: a hopper collects and inserts within a single tick, and a
command runs on the server thread inside that tick.

The one thing that **is** removed is an item still physically hovering above a hopper that would have
been sucked up next tick. That is a real dropped item, and it is meant to be included.

### Who may run it

`permission_level` decides, and it is read while the command runs rather than when it is registered —
so changing it takes effect without a restart. The default of `2` is the usual operator level. `0`
opens it to every player, which is worth thinking about twice: a 16-block radius reaches other
people's drops too.

### Dry run instead of a confirmation

There is deliberately no two-step confirmation like [`/chunkreset`](chunk_reset.md) has. That command
needs one because its damage is irreversible and the confirmation has to survive a whole session.
Here, `dry-run` is the better protection: it answers the only question worth asking — *what is
actually in range?* — and holds no state at all while doing it.

<!-- vpa:config:start -->
## Configuration

Section `[modules.kill_items]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_kill_items-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_radius` | int | `16` | 1 ~ 512 | Radius in blocks used when the command is given no radius argument. |
| `feedback_details` | boolean | `true` | — | List the most common item types (up to three, then a +N remainder) in the answer, counted by stack size rather than by entity. |
| `max_radius` | int | `256` | 1 ~ 2048 | Largest radius the command accepts. Checked while the command runs, not while the command tree is built - a limit baked into IntegerArgumentType.integer(1, max) would be read once at server start and then ignore edits to this file until the next /reload. |
| `permission_level` | int | `2` | 0 ~ 4 | Permission level required to run the command. 0 opens it to every player, 2 is the usual operator level, 4 is a full server operator. Read live inside the requires predicate, so a change takes effect without a restart. |
| `spherical` | boolean | `true` | — | Measure the radius as a true sphere. Off means the axis-aligned box handed to getEntitiesOfClass is used as-is, which is a cube whose corner reaches about 1.7 times the typed radius. |
<!-- vpa:config:end -->

## Compatibility and known limits

* **Nothing else in the pack defines this command.** Every jar was searched for both `kill-items`
  and `killitems`; there is no collision, and none with vanilla `/kill` either (see
  [Under the hood](#under-the-hood)).
* **Mods that deliberately keep items around** — `ItemPhysicLite`, `FastItemFrames` and similar —
  still produce ordinary item entities, so their drops are cleared like any other. If you run a farm
  that parks items on the floor on purpose, run `dry-run` first.
* **Items in mid-flight from a Create ejector or a dispenser** are real entities for the moment they
  are airborne and will be caught. Another reason for `dry-run` in a factory.
* `max_radius` is checked while the command runs, so a change to it applies immediately. It is
  deliberately *not* part of the argument type, which would freeze the limit at server start.

## Under the hood

**The hyphen is legal, and it does not collide with `/kill`.** Brigadier's `LiteralCommandNode.parse`
does not test the characters of a literal at all — it compares the input against the literal and only
fails if the character after it is not a space. So a literal may contain anything but a space, and
tab completion still works because suggestions use a plain prefix match. Typing `/kill-items` does
not reach the vanilla `/kill` node: that node matches the first four characters, finds `-` where it
needs a space, rewinds and fails — leaving our node to take the line.

**Removal uses `discard()`.** For an item entity that is behaviourally identical to `kill()` — both
removal reasons are "destroy, do not save", and `ItemEntity` overrides neither — but `DISCARDED` is
the reason vanilla itself uses for something removed administratively, and it reads correctly to any
mod that inspects why an entity went away.

**The return value is the count.** The command returns the number of entities removed, so
`execute store result` and a command block's success count see a real number rather than a plain 1.
The summary counts stack sizes rather than entities, which is why twelve cobblestone in one stack
reads as `12x Cobblestone`.

**Two roots, not a redirect.** `/kill-items` and `/vpakillitems` are registered as two separate
trees. Brigadier's `redirect()` would have been shorter but rewrites the command context, which makes
parse errors talk about the other name.

## See also

* [Chunk Reset](chunk_reset.md) — the other sweeping command, and why that one asks twice
* [Configuration Guide](../guides/configuration.md)
* [All modules](../../README.md#modules)
