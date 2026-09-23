# Create Redstone Link Rebinder

> **TL;DR** — Redstone Links that quietly stopped reaching their receivers after a chunk reload get
> found and reconnected, so a button keeps opening its door instead of needing the transmitter block
> replaced by hand.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `create_redstone_link_rebinder` |
| **Side** | Server only |
| **Requires** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub> |
| **Works with** | — |
| **Download** | [`vpa_create_redstone_link_rebinder.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_create_redstone_link_rebinder.jar) · also needs `vpa_core` |
| **Config section** | `[modules.create_redstone_link_rebinder]` |
| **Since** | `v1.0.0-beta.88` |
<!-- vpa:meta:end -->

## What it does

You come back to your base, press the button that opens the piston door, and nothing happens. The
frequency is right, the items in the link are right, the lever next to the transmitter works — the
receiver simply never hears it. Break the transmitter, place it back, and everything runs again
until the next time the area reloads.

This module finds those links and reconnects them, without you touching a block.

## Why it happens

Create keeps its redstone-link network only in memory. A link joins it from
`LinkBehaviour.initialize()`, which is reached solely through `SmartBlockEntity.tick()` behind an
`initialized` flag that is never saved. So the membership is rebuilt from scratch every time an area
loads — and if that rebuild does not happen, or is undone, the link is simply not in the network any
more.

What makes this so hard to spot from inside the game is that **the block still looks alive**.
Responding to a neighbouring lever goes through `neighborChanged`, which needs no tick at all, so the
transmitter's signal value keeps tracking the lever perfectly. Everything you can see is correct. The
only thing that is wrong is invisible: nobody is listening.

## What it actually does about it

The module remembers where the links are — read from a chunk's block-entity map as it loads, which
costs nothing, never a world scan — and then checks those positions and nothing else:

| When | What is checked |
|---|---|
| shortly after a chunk loads (`post_load_delay_ticks`, default 20) | the links in that chunk, for the case where registration never happened |
| every `check_interval_ticks` (default 100) | every remembered link in every loaded dimension |
| on `/vparelink` | the same sweep, on demand, reporting how many were missing |

If a link is not in the member set of its own frequency, it is added. That is safe to repeat: the
network bucket is a `Set`, and Create's own `addToNetwork` finishes with `updateNetworkOf`, so a
transmitter that rejoins while it is holding a signal pushes that signal to its receivers straight
away.

> **The sweep is the part that matters.** It was built as a mere safety net, but on the first live
> measurement it did all the work: after a cold load ten links were missing from their network —
> including both staircase transmitters that had previously been replaced by hand — and **every one
> of them was found by the sweep, none by the post-load check**. Twenty ticks after the chunk load
> they still looked correctly registered; they dropped out afterwards. So the registration is being
> *removed* after the fact, not merely missed. If you are tuning this module, `check_interval_ticks`
> is the knob that changes how long a door stays dead.

## Commands

**`/vparelink`** — permission level 2 (`LEVEL_GAMEMASTERS`, so an operator or the server console).
Sweeps every tracked link in every loaded chunk immediately and reports how many had fallen out of
their network. Useful when something is broken right now and you would rather not wait for the next
sweep.

<!-- vpa:config:start -->
## Configuration

Section `[modules.create_redstone_link_rebinder]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_create_redstone_link_rebinder-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `auto_rebind_during_sweep` | boolean | `true` | — | Whether the periodic sweep may re-register missing links. This is the one that does the work in practice - turning it off effectively disables the module's automatic repair. |
| `auto_rebind_on_chunk_load` | boolean | `true` | — | Whether the post-load check may re-register a missing link itself. Turning it off leaves only /vparelink. |
| `check_interval_ticks` | integer | `100` | 20-1200 | How often all tracked links are swept. Only remembered positions in loaded chunks are read - never a world scan. This is the value that matters: every one of the ten links repaired after the cold load was found here. Lowering it shortens how long a door stays dead; raising it costs only reaction time. |
| `post_load_delay_ticks` | integer | `20` | 0-600 | Ticks between a chunk with redstone links loading and the targeted check of those links. This is the early net, for links that never register at all. Measured on games2 on 2026-09-23: after a cold load ten links were missing from their network and NOT ONE was caught here - at 20 ticks they all still looked correct and dropped out afterwards. Do not tune this hoping to catch that case; the sweep does. |
<!-- vpa:config:end -->

## Limits

- Only links whose chunk is loaded can be checked — an unloaded link is neither broken nor fixable.
- The module repairs the symptom. The underlying fault is Create's, and until its cause is pinned
  down it has deliberately **not** been reported upstream: a ticket without the cause of the removal
  would be closed unread.
- Nothing is persisted. Everything is rediscovered on chunk load, which is why there is no state to
  migrate or corrupt.
