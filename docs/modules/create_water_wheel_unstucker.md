# Create Water Wheel Unstucker

> **TL;DR** — Create water wheels that quietly stopped turning after a chunk reload get spotted and
> started again: by itself where the repair is provably harmless, and on `/vpaunstuck` where it is a
> judgement call.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `create_water_wheel_unstucker` |
| **Side** | Server only |
| **Requires** | [Create](https://modrinth.com/mod/create) <sub>tested 6.0.10</sub> |
| **Works with** | — |
| **Download** | [`vpa_create_water_wheel_unstucker.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_create_water_wheel_unstucker.jar) · also needs `vpa_core` |
| **Config section** | `[modules.create_water_wheel_unstucker]` |
| **Since** | `v1.0.0-beta.41` |
<!-- vpa:meta:end -->

## What it does

You come back to a base you have not visited for a while and the water wheels stand still. The water
is where it was, nothing is broken, no machine is running — and the wheels either report nothing at
all or claim the network is *Overstressed*. Break one wheel, place it back, and the whole line runs
again.

This module watches for exactly that. It remembers where water wheels are as chunks load, checks
those positions and nothing else, and then sorts the stopped ones into cases it can prove and cases
it cannot. What it does about each depends on the case, not on how confident the code feels:

| A stopped wheel where… | With the default config |
|---|---|
| the network is still carrying stress numbers from a state its members have left | recomputed on the spot, wheel spins again, one line in the log |
| the network charges stress for **zero** unloaded members | the stale tally is dropped, same line |
| the network charges stress for members that really are unloaded, and the loaded ones alone would fit | left to `/vpaunstuck`, which logs the full numbers |
| nothing in the stress bookkeeping explains it, but there is water | detected and then left alone — `/vpaunstuck` re-initialises it, or set `auto_fix = true` |
| the network is genuinely overloaded, or there is no fluid anywhere near | never touched |

So out of the box the module changes **no blocks**. It corrects Create's own numbers where those
numbers contradict themselves, and for everything else it waits to be asked.

**`/vpaunstuck`** is the asking. It needs permission level 2 (`LEVEL_GAMEMASTERS`, so an operator or
the server console), covers every tracked wheel in every dimension whose chunks are currently loaded,
and does the break-and-replace re-init that a player would otherwise do by hand. It reports to chat
and writes a full breakdown to the log, followed a second later by one line per wheel saying whether
the re-init actually restarted it.

## Why it exists

### A stopped wheel and an overloaded wheel look identical from outside

`KineticBlockEntity.getSpeed()` does not report the speed while the network is overstressed. It
reports zero:

```java
public float getSpeed() {
   return !this.overStressed && (this.level == null || !this.level.tickRateManager().isFrozen())
       ? this.getTheoreticalSpeed() : 0.0F;
}
```

And `overStressed` is a plain comparison of two numbers the network hands down, with no check that
those numbers still describe anything real:

```java
public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
   ...
   boolean overStressed = maxStress < currentStress && StressImpact.isEnabled();
```

A wheel that lost its flow score contributes no capacity any more, which can tip its own network
over — so the reload desync frequently arrives *wearing* the overstressed mask. "Overstressed" is
therefore not proof of an overload, and speed 0 is not proof of a stall.

### The unloaded-member tally only ever shrinks when a member comes back

Create counts members in unloaded chunks separately and adds them on top of the loaded ones:

```java
public float calculateStress() {
   float presentStress = 0.0F;
   ... // sums the loaded members
   return presentStress + this.unloadedStress;
}
```

`addSilently` is the only place that tally shrinks, and it runs when a member **loads again** —
subtracting its share, with the counter and the two float totals clamped at zero independently of
each other:

```java
this.unloadedStress = this.unloadedStress - lastStress * getStressMultiplierForSpeed(be.getTheoreticalSpeed());
...
this.unloadedMembers--;
if (this.unloadedMembers < 0) { this.unloadedMembers = 0; }
if (this.unloadedCapacity < 0.0F) { this.unloadedCapacity = 0.0F; }
if (this.unloadedStress < 0.0F) { this.unloadedStress = 0.0F; }
```

A member that is *removed* while its chunk is unloaded never comes back and never subtracts, so its
stress haunts the network for as long as the server runs. Because the three values are clamped
separately, the tally can end up charging stress for zero unloaded members — numbers that no machine
anywhere can be behind. That is precisely the case this repository confirmed in the field
([CHANGELOG, `v1.0.0-beta.66`](../../CHANGELOG.md)): a wheel whose network carried
`unloadedStress=256` at `unloadedMembers=0` — stress charged for *zero* unloaded members. Dropping
that tally left the wheel spinning at 8.0.

A server restart clears it, which is why it is so hard to catch on purpose.

## In detail

### Which wheels are watched, and when

Only remembered positions are ever looked at — there is no world scan and nothing is ever
force-loaded.

| Trigger | What happens |
|---|---|
| `ChunkEvent.Load` | The chunk's **block-entity map** is scanned for wheel centres (structural shell blocks of a large wheel have no block entity, so this is cheap). Found positions are queued for a targeted check. |
| `BlockEvent.EntityPlaceEvent` | A placed wheel centre is registered and queued the same way. |
| `BlockEvent.BreakEvent` | The position is dropped; breaking a shell block of a large wheel resolves to its master first. |
| `ChunkEvent.Unload` | Positions in that chunk are forgotten — they are rediscovered for free when it loads again. |

The queued check runs `post_load_delay_ticks` (60 ticks, 3 s) after the load, which is where the
reload desync strikes and early enough to catch it before anyone notices. On top of that, a sweep
over every tracked position runs every `check_interval_ticks` (100 ticks, 5 s), driven by the global
server tick counter, so every dimension is swept in the same tick.

Both paths run the same per-wheel check, and both refuse to judge a wheel unless **every chunk
overlapping its footprint** — centre ±2 blocks on X and Z, at most four chunks — is already loaded.
Evaluating a wheel with its water chunk absent would read "no flow" and be wrong about it.

There is no persistence anywhere: positions come back from the chunks themselves, and the spawn
chunks cover the server start.

### Speed 0 is not one thing

The per-wheel check walks a ladder and stops at the first rung that explains the standstill:

1. **Speed is not 0.** Healthy. Any counters for this wheel are cleared. (This also self-heals a
   shared network: reviving one wheel revives everything attached to it, and the rest reset
   themselves here on their next check.)
2. **Overstressed.** The stress bookkeeping is settled first — see below. If the wheel spins
   afterwards, done.
3. **Still overstressed and `getGeneratedSpeed() != 0`.** A real overload. Never touched. The
   generated speed is the one number stress cannot reach:

   ```java
   public float getGeneratedSpeed() {
      return (float)(Mth.clamp(this.flowScore, -1, 1) * 8 / this.getSize());
   }
   ```

   It depends on the flow score alone, so a wheel that still generates rotation has intact flow and
   the network simply demands more than it can give. An overstressed wheel that generates **nothing**
   is the reload desync in disguise and goes down the ordinary stall path.
4. **No fluid in the 5×5×5 box around the centre.** A dry or decorative wheel. Never touched. Lava
   counts as fluid, which is deliberate — Create runs wheels on lava — but it does mean a decorative
   wheel beside a lava pool reads as "wet".
5. **Everything else** is a flow stall: detected here, and fixed only if `auto_fix` says so.

### The two stress cures

Neither of them touches a block, so `auto_fix` — which gates block mutation — does not apply to
either. `clear_phantom_stress` gates only the second.

**Recompute.** `updateNetwork()` followed by `sync()` recalculates stress and capacity from the
network's current members and pushes the result to all of them. `updateNetwork()` syncs only on a
change, hence the explicit `sync()`. Nothing is invented; a network that was still holding numbers
from a long-abandoned state simply gets told the truth.

**Drop the tally.** Setting `unloadedMembers`, `unloadedStress` and `unloadedCapacity` to zero and
recomputing. Whether that is allowed depends on who is asking:

| | Periodic sweep | `/vpaunstuck` |
|---|---|---|
| Tally charges stress or capacity with `unloadedMembers == 0` | dropped | dropped |
| Tally has real unloaded members, and `stress − unloadedStress <= capacity − unloadedCapacity` | left alone | dropped, with the full numbers logged |
| Anything else | left alone | left alone |

The split is the whole point. A tally that charges stress for zero members is self-contradictory —
nothing can be behind those numbers, so dropping them cannot take anything away from anybody, and
that case is safe without a human in the loop. A tally with actual unloaded members might be real
machines in chunks nobody has visited, which Create counts on purpose; dropping it makes them stop
counting until their chunk loads, at which point `addSilently` re-registers them with their real
numbers. That is an operator's call, not a sweep's.

Either cure, when it leaves the wheel spinning, logs one line at INFO level regardless of
`debug_logging` — this one is the confirmed case, quoted from the `v1.0.0-beta.66` changelog entry:

```
Unstuck wheel at -3277, 77, -2137 (minecraft:overworld): dropped an orphaned stress tally
(charged stress for 0 unloaded members) - now spinning at 8.0
```

### The re-init

The fix for a flow stall is a break and a re-place, done by code:

1. Capture the wheel's full block state (orientation and axis) and its visual material.
2. Set the position to air with `UPDATE_ALL` and **no drops**, so adjacent water floods the gap.
3. Wait `reinit_flood_ticks` (6 ticks, 0.3 s).
4. Place the state back, restore the material, send a block update, and schedule a block tick for
   good measure — `onPlace` already triggers Create's own flow recompute, and it now runs while the
   flooded water is still moving.
5. 20 ticks later, log whether the wheel actually restarted (`re-init RECOVERED` or `re-init did NOT
   restart`).

Step 2 is the part that matters, and it is why this module's original flow-score kick was reworked
into a break and a re-place. Create already re-runs `determineAndApplyFlowScore()` from `lazyTick`,
and `WaterWheelBlockEntity`'s constructor sets that rate to 60 ticks — so every three seconds the
score is recomputed anyway, and asking for one more recompute (the flow-score half of the old soft
kick) changes nothing at all. The score is read from the fluids' **flow vectors**, and a flow vector
is a pure function of the fluid heights around a block: a level pool of source blocks has no height
difference and yields zero. What produces a score is a gradient that runs *with* the wheel:
each of the four sampled neighbours counts only where its flow points along the wheel's tangent
there (`|dot| > 0.5`), and the four signs are summed — flow that misses the tangent, or that is
symmetric on opposite sides, still adds up to zero. A running stream has such a gradient
permanently, a gap being flooded has one while it fills. Removing the wheel gives the blocks Create
actually samples somewhere to flow into — `determineAndApplyFlowScore()` reads `getOffsetsToCheck()`,
for a small wheel the four neighbours in the plane perpendicular to its axis, never the wheel's own
position — so it is those neighbours that briefly carry a gradient while the gap fills.

A score won that way is not banked. Every run of `determineAndApplyFlowScore()` ends in
`setFlowScoreAndUpdate(...)` with the score it just derived, so 60 ticks later the wheel is judged
again by whatever its neighbours are doing then. The re-placed wheel starts that clock from scratch —
`setLazyTickRate(60)` in the constructor sets the rate *and* the counter. What the flood buys is a
restart, not a guarantee that it lasts: the outcome log fires 20 ticks after the replace, well
before that first lazy tick, so it can only confirm the restart — whether the wheel is still
turning three seconds later is what the "unproven" row below is about.

A pending replace whose chunk unloads in the meantime is kept and retried when the chunk comes back,
rather than dropped.

When the sweep drives this (`auto_fix = true`), a wheel gets `max_fix_attempts` consecutive attempts;
after that it backs off for 6000 ticks (~5 minutes), the attempt counter resets, and the first
exhaustion logs one WARN for that wheel. A wheel seen spinning clears all of its state.
`/vpaunstuck` ignores the escalation entirely and clears any pending backoff.

### What `/vpaunstuck` reports

Chat gets one line; the log gets the breakdown. The shape of it, with the counters filled in
for illustration:

```
/vpaunstuck summary: 2 re-initialised, 1 revived by clearing a stale stress state, 5 already spinning,
0 skipped (genuinely overstressed), 3 skipped (no water nearby). Re-init outcomes logged shortly.
```

Two things to know about the number in chat. It is `re-initialised + revived`, so it can be larger
than the number of wheels a block was touched for. And the command's exit code is 1 both when
exactly one wheel was handled and when nothing was done, so command blocks cannot tell those apart —
the log can.

The command also orders its checks differently from the sweep: it tests for nearby fluid **before**
it looks at the stress state, while the sweep resolves the overstress first. A phantom-overstressed
wheel with no fluid in its box is therefore counted as "skipped (no water nearby)" by the command and
never gets the stress cure there, although the sweep would have attempted it.

### `hard_kick` does nothing

The config key is still there and still carries its old description, but it is dead. Its getter
`isHardKickEnabled()` has no caller anywhere in `src/`, and neither `WaterWheelKinetics.softKick()`
nor `hardKick()` is ever invoked — both survive as unused methods. The soft/hard-kick escalation they
belong to was the module's original design and was replaced by the break-and-replace re-init above.
The `max_fix_attempts` comment in the config file, the class javadoc of
`CreateWaterWheelUnstuckerModule` and the `v1.0.0-beta.39` CHANGELOG entry all still describe that
old escalation; they describe the first release, not the current code.

<!-- vpa:config:start -->
## Configuration

Section `[modules.create_water_wheel_unstucker]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_create_water_wheel_unstucker-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `auto_fix` | boolean | `false` | — | Whether the periodic sweep may re-initialise a stalled wheel itself (break to air, wait reinit_flood_ticks, place back). false (the default) means the sweep changes no blocks and a genuine flow stall is only detected; the fix then comes from /vpaunstuck. With true, a still-stalled wheel is re-initialised once per sweep until max_fix_attempts is reached. It does NOT gate the stress-bookkeeping repairs, which run either way. |
| `check_interval_ticks` | int | `100` | 20 ~ 1200 | How often (in ticks) all tracked water wheels are swept for stalls; only remembered wheel positions whose footprint chunks are already loaded are checked, never a global scan. The sweep is driven by the global server tick counter (server.getTickCount() % interval == 0), so every dimension is swept in the same tick. |
| `clear_phantom_stress` | boolean | `true` | — | Allows dropping a stale unloaded-member stress tally to cure a phantom 'Overstressed' network - automatically in the sweep when the tally is provably orphaned (stress or capacity charged while unloadedMembers == 0), and in the judgement case (real unloaded members, loaded members alone would fit) only via /vpaunstuck. false disables both paths. It does not gate the preceding network recompute, which always runs for an overstressed stalled wheel. |
| `hard_kick` | boolean | `true` | — | DEAD KEY: documented as allowing the hard fix escalation (detach + re-attach the kinetic network), but its getter CreateWaterWheelUnstuckerConfig.isHardKickEnabled() has no caller anywhere in src/, and neither WaterWheelKinetics.softKick() nor hardKick() is ever invoked. Changing it has no effect - see notes. |
| `max_fix_attempts` | int | `3` | 1 ~ 10 | Consecutive re-init attempts per wheel before that wheel backs off for ~5 minutes (STALL_BACKOFF_TICKS = 6000) and the attempt counter resets; the first exhaustion also logs one WARN per wheel. Only relevant when auto_fix = true - the whole escalation state sits behind the auto_fix gate, and /vpaunstuck ignores it and clears any pending backoff. |
| `post_load_delay_ticks` | int | `60` | 0 ~ 600 | Delay in ticks between a chunk with water wheels loading - or a wheel being placed - and the targeted stall check for those wheels, so Create can finish its own init first. |
| `reinit_flood_ticks` | int | `6` | 1 ~ 40 | Ticks the wheel stays removed during a re-init so adjacent water can flood the gap and re-establish active flow before the wheel is placed back. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| The flow-stall half is unproven | Nothing in this repository demonstrates that the break-and-replace re-init has revived a real reload-stalled wheel — the 20-tick outcome log exists precisely to find that out during play. The one confirmed field case (beta.66) was cured by the stress bookkeeping alone, with no block touched. That is also why `auto_fix` defaults to off. |
| Large (3×3) wheels | Their centres are tracked and checked like any other wheel, and the stress cures apply unchanged. The re-init is another matter: it removes and restores the **centre block only**, and its own javadoc says "Sized for small (single-block) wheels". What it does to a large wheel is demonstrated nowhere in this repository — treat it as unverified. |
| A block placed in the gap during a re-init | The wheel is restored only into air or fluid, never over a block a player may have put there. In that case it is gone for good: no drop, no log line. The window is `reinit_flood_ticks` — 0.3 s by default, but real. |
| Level unload or server stop during a re-init | The pending replace lives in memory only and is dropped by both, so the wheel is lost the same way. A **chunk** unload is safe: that state is deliberately kept and retried on reload. |
| A check landing inside the re-init window | While the wheel is removed, its tracked position holds air, so a sweep or post-load check that falls into that window fails `isStillWheel` and drops the position from the registry. `replaceWheel` puts the block back but does not re-register it — a `setBlock` fires no place event — so that wheel stays invisible to the sweep and to `/vpaunstuck` until its chunk unloads and loads again. Unlikely with the defaults (a 6-tick window against a 100-tick sweep), unavoidable once `reinit_flood_ticks` (up to 40) is set above `check_interval_ticks` (down to 20). |
| A genuine flow stall produces no log at all | With the default config the "STALLED wheel …" line is unreachable: it sits behind the `auto_fix` early return *and* behind `debug_logging`. A detected-but-untouched stall is silent. Use `/vpaunstuck`, which logs unconditionally. |
| No force-loading, ever | A wheel in an unloaded chunk is not checked, not fixed and not reported. `/vpaunstuck` covers loaded chunks only. |
| Module disabled in the config at startup | Depends on the jar. In the **bundle**, `ModuleManager` only initialises modules whose config says `enabled`, so no event handler and no command exist and `/vpaunstuck` is simply an unknown command. The **standalone** jar boots through `StandaloneModuleBootstrap`, which initialises the module whenever Create is present — `shouldInitialize()` asks about Create, not about `enabled` — so there the command exists and answers "Water Wheel Unstucker module is disabled." — the registration handler itself only asks whether Create is loaded, the `isModuleEnabled()` check sits inside the command. The three gated handlers (chunk load, block place, server tick) return on their own `isModuleEnabled()` check, and the four cleanup handlers simply run against state that never fills. In the bundle that same reply is reachable by switching the module off at runtime. |
| Create not installed | `shouldInitialize()` returns `ModList.get().isLoaded("create")`, so the module registers nothing. In the bundle Create is declared as an optional dependency (`[6.0,)`, ordering `AFTER`); the standalone jar declares it not at all and gates purely at runtime. |
| A future Create version renaming things | One warning, `isAvailable()` turns false, and every accessor degrades to a neutral no-op. No crash, but also no function. See the reflection table below for the three levels this happens at. |
| Lava counts as water | `hasNearbyFluid` accepts any non-empty fluid. Create's lava wheels need that; the cost is that a decorative wheel beside lava is treated as fixable rather than dry. |
| Other block-entity state on the wheel | The re-init preserves the block state and the visual material. Anything else a mod may have attached to that block entity is not carried across. |

## Under the hood

No mixins, no registries, no items, blocks, recipes, keybinds, network packets, assets or data files
of any kind — and no lang keys either: every line the command prints is hardcoded English via
`Component.literal`. The whole module is four classes, a config class and a standalone entry point.

| Class | Role |
|---|---|
| `modules/create_water_wheel_unstucker/CreateWaterWheelUnstuckerModule` | The eight event handlers and `/vpaunstuck`. Contains no Create import at all. |
| `…/WaterWheelRegistry` | Per-dimension set of wheel **centres**. The only place Create's *Block* classes are referenced at compile time. |
| `…/WaterWheelStallManager` | The per-wheel state machine, the sweep, the stress ladder, the re-init and its outcome log. |
| `…/WaterWheelKinetics` | The reflection layer. The only path to Create's block-entity and network API. |
| `…/config/CreateWaterWheelUnstuckerConfig` | The seven keys. |
| `standalone/create_water_wheel_unstucker/CreateWaterWheelUnstuckerStandalone` | `@Mod("vpa_create_water_wheel_unstucker")`. |

**Events**, all on the NeoForge game bus:

| Event | Purpose |
|---|---|
| `ChunkEvent.Load` | Discover wheels, queue the targeted check |
| `ChunkEvent.Unload` | Forget that chunk's positions and pending checks |
| `BlockEvent.EntityPlaceEvent` | Register a placed wheel, queue a check |
| `BlockEvent.BreakEvent` | Unregister (shell blocks resolve to their master) |
| `ServerTickEvent.Post` | The driver: pending replaces, outcome logs, queue drain, due checks, the sweep |
| `RegisterCommandsEvent` | `/vpaunstuck` |
| `LevelEvent.Unload` | Drop all state of that level |
| `ServerStoppedEvent` | Drop everything |

The four cleanup handlers are deliberately **not** gated on `isModuleEnabled()`, only null-checked,
so state is still released when the module is switched off at runtime.

**Threading.** `ChunkEvent.Load` can fire off the server thread during worldgen, so the registry is a
`ConcurrentHashMap` of concurrent sets and the post-load hand-off is a `ConcurrentLinkedQueue`; due
times are assigned on the server thread while draining. Everything else is server-thread only.

**Why reflection.** `WaterWheelBlockEntity` extends `KineticBlockEntity` extends `SmartBlockEntity`,
which implements Ponder's `VirtualBlockEntity` — and Ponder is not in `libs/`, so any compile-time
reference to that hierarchy fails to build. This is the same trap as the Arm target overlay; the case
study is [in `docs/internal/`](../internal/arm_target_overlay_case_study.md). At runtime, with Create
installed, Ponder is present and everything resolves normally. Create's *Block* classes are free of
it and are imported ordinarily in `WaterWheelRegistry`.

The reflection layer resolves in three independent stages, and losing a later one never costs an
earlier one:

| Stage | Resolved | If it fails |
|---|---|---|
| Core | `getSpeed`, `getGeneratedSpeed`, `isOverStressed`, `detachKinetics`, `removeSource`, `attachKinetics`, `updateGeneratedRotation`, `determineAndApplyFlowScore` | One warning, `isAvailable()` false, no wheel is ever recognised — the module is inert |
| Network | `hasNetwork`, `getOrCreateNetwork`, `updateNetwork`, `sync`, `calculateCapacity`, `calculateStress`, the public `sources` / `members` maps | Warning "stale-stress recovery disabled". Detection and re-init keep working; no stress cure and no numbers |
| Private fields | `unloadedCapacity` / `unloadedStress` / `unloadedMembers` on the network, `capacity` / `stress` on the block entity, via `setAccessible` | Warning "phantom-overload recovery disabled". The recompute still runs; the tally cannot be read or dropped, and the stats fall back to `calculateCapacity()` / `calculateStress()` |

The public `material` field sits outside those stages: it is resolved in a nested `try` of its own,
with a `NoSuchFieldException` leaving it null rather than failing the Core stage. Losing it therefore
costs only the plank material — `getMaterial()`/`setMaterial()` turn into no-ops and a re-initialised
wheel comes back with Create's constructor default, spruce planks — while detection, the stress cures
and the re-init keep working.

Two details in that layer worth knowing when editing it. `readNetworkStats` prefers the `capacity` and
`stress` the wheel was **last told** over `calculate*()`, because those are the numbers its
`overStressed` flag was actually derived from — `calculate*()` would already answer with post-recompute
values. And a network is never created just to look at it: `hasNetwork()` is asked first, and a wheel
without one returns no stats.

Failures inside the accessors go through a `warnOnce` guarded by a single static flag, so only the
very first one is ever printed for the whole class.

**What ends up in the log.** Only the last three lines need `debug_logging = true`; everything above
them is printed regardless — the backoff WARN just never comes up without `auto_fix`:

| Line | Needs |
|---|---|
| `Create Water Wheel Unstucker module initialized (Create reflection available: …)` | — (once, at startup) |
| `Unstuck wheel at … - now spinning at …` | — (a sweep cure landed) |
| `Water wheel at … still stalled after N re-init attempts; backing off ~5 minutes` | `auto_fix`, once per wheel (WARN) |
| everything `/vpaunstuck` writes, including the per-wheel outcome | — |
| `discovery: N wheel(s) in chunk …` | `debug_logging` |
| `Wheel at … recovered, speed …` | `debug_logging`, and only for a wheel that had attempts counted |
| `STALLED wheel at …: speed 0, not overstressed …` | `debug_logging` **and** `auto_fix` |

## See also

* [Arm Target Overlay](arm_target_overlay.md) — another Create module built on the same reflection
  workaround, and [its case study](../internal/arm_target_overlay_case_study.md)
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Debug Logging](../guides/debug-logging.md) — the universal `debug_logging` key
* [All modules](../../README.md#-modules)
