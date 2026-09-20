# Block Glow

> **TL;DR** — Type `/blockglow iron_ore` and every matching block around you is outlined in cyan,
> straight through the rock, until the timer runs out or you clear it.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `block_glow` |
| **Side** | Client only |
| **Requires** | — |
| **Works with** | [Sable](https://modrinth.com/mod/sable) <sub>tested 2.0.5</sub> |
| **Download** | [`vpa_block_glow.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_block_glow.jar) · also needs `vpa_core` |
| **Config section** | `[modules.block_glow]` |
| **Since** | `v0.16.0` |
<!-- vpa:meta:end -->

## What it does

`/blockglow <block_id>` outlines every block of that type within a search radius around you. The
outlines are drawn without a depth test, so they stay visible through terrain — an x-ray view of one
block type and nothing else. Useful for tracing an ore vein, for finding the scattered blocks of a
structure, or for checking where a particular block ended up in a build.

| Command | What it does |
|---|---|
| `/blockglow <block_id>` | Highlight using the configured defaults — 24 blocks, 60 seconds |
| `/blockglow <block_id> <radius>` | Custom radius, default duration |
| `/blockglow <block_id> <radius> <duration_seconds>` | Custom radius and duration; `0` means until you clear it |
| `/blockglow clear` | Clear the current highlight |

```
/blockglow deepslate_diamond_ore 32 120
```

`block_id` tab-completes over every block in the registry, modded blocks included, and a bare path is
enough — `iron_ore` resolves to `minecraft:iron_ore`. Radius must be at least 1, duration at least 0;
Brigadier rejects anything else before the command runs. Exactly one highlight is active at a time,
so a second `/blockglow` replaces the first.

This is a **client command**. It is registered into the client's own dispatcher with no permission
gate, so it runs on your machine, asks the server for nothing, and works on a server that has never
heard of this mod. The feedback is printed locally: *Block glow enabled for minecraft:iron\_ore
(radius=24, duration=60 seconds)* in aqua, *Block glow cleared* in yellow.

## In detail

### The search

There is no index and no cache. Every rendered frame, at the `AFTER_TRANSLUCENT_BLOCKS` stage, the
module walks a cube centred on the block you are standing in and asks the client level for each
position in turn:

```java
for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
    for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
        for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
            cursor.set(x, y, z);
            if (!level.getBlockState(cursor).is(targetBlock)) {
                continue;
            }
            AABB worldAabb = new AABB(cursor);
            candidates.add(new BlockGlowHighlight(worldAabb, worldAabb.getCenter().distanceToSqr(playerPos)));
        }
    }
}
```

Two things follow from that, and they are the two things worth knowing about this module.

**`radius` is a cube half-extent, not a sphere.** The loop runs from `centre − radius` to
`centre + radius` on all three axes, so the corners of the box sit about 1.73 × the nominal radius
away. A radius of 24 reaches 24 blocks straight ahead and roughly 41 diagonally. Only the *sorting*
for `nearest` uses a true distance — the squared euclidean distance from the block's centre to your
position.

**The cost grows with the cube.** `(2·radius + 1)³` block-state lookups, once per frame:

| `radius` | Cube | Lookups per frame | At 60 fps |
|---|---|---|---|
| 8 | 17³ | 4,913 | 295 k/s |
| 24 (default) | 49³ | 117,649 | 7.1 M/s |
| 40 | 81³ | 531,441 | 31.9 M/s |
| 64 (`max_radius`) | 129³ | 2,146,689 | 128.8 M/s |

`max_highlights_per_frame` does **not** help here: it caps how many boxes are *drawn*, never how many
blocks are *scanned*, and every match allocates an `AABB` and a record before the budget is applied.
Raising the radius is the one setting that can cost you frames, and searching for a common block
(stone, deepslate) at a large radius is the worst case, because then nearly every lookup is also an
allocation. Keep the radius at what you actually need.

The Y axis counts towards the cube like any other. Standing near bedrock or high in the air puts a
large slice of the box outside the world, and those positions are looked up all the same. The scan
also reads the **client's** level, so it can only find what your client already has.

### Choosing what to draw

Once the frame's candidates are collected, `selection_mode` decides which of them fit into
`max_highlights_per_frame`:

| Mode | How it picks | Result |
|---|---|---|
| `nearest` (default) | A bounded max-heap keeps the closest `max_highlights_per_frame` candidates, then sorts them ascending before drawing | The budget is spent on what is around you |
| `scan_order` | `candidates.subList(0, limit)` — the first ones the loop happened to find | The scan starts at the lowest, most north-westerly corner of the cube, so an overflowing search draws a block of outlines in one corner of the box and nothing near you |

`scan_order` is only sensible when you know the candidates fit into the budget. It has a second
edge: Sable sub-level candidates are appended *after* the main-world ones, so in `scan_order` they
are the first thing the budget cuts off.

The key is a plain string with no validator. Anything that is not exactly `scan_order` — after a trim
and a lowercase — silently falls back to `nearest`, with no warning and no error, so a typo simply
looks like the setting did nothing.

### How long it lasts

The duration is converted once, at command time, into an absolute point on the world clock:
`expiresAtGameTime = currentGameTime + durationSeconds · 20`. A duration of `0` stores `0`, which the
expiry check reads as "never".

That clock is game ticks, not wall-clock seconds: 60 seconds means 1200 ticks of the level's game
time, which stretches when the world runs below 20 ticks per second.

The highlight state is a field on the module instance, and it is cleared in exactly two places — by
`/blockglow clear`, and by the expiry check inside the render event. There is no level-unload or
disconnect listener, so the state **survives leaving the world**, and because the stored value is an
absolute game time it is then compared against a different world's clock:

| You leave a world with an active highlight and join one whose game time is… | What happens |
|---|---|
| …higher | The highlight expires on the first rendered frame |
| …lower | It lives on until that world's clock passes the stored tick |
| …the same world again | It resumes as expected |

The radius is clamped at render time with `Math.min(state.radius(), maxRadius)`. Lowering
`max_radius` while a highlight is active therefore shrinks it live; raising it does not grow an
existing highlight, because the state keeps the radius you asked for.

### The outlines

Drawing goes through a private `RenderType`, `blockglow_xray_lines`, built for exactly this purpose:

| State | Value | Why |
|---|---|---|
| Depth test | `NO_DEPTH_TEST` | The lines show through terrain — this is the x-ray look |
| Cull | `NO_CULL` | Box edges stay complete from every angle |
| Transparency | `TRANSLUCENT_TRANSPARENCY` | `outline_color.alpha` does something |
| Write mask | `COLOR_WRITE` | The lines write no depth, so they cannot disturb what is drawn afterwards |
| Layering | `VIEW_OFFSET_Z_LAYERING` | Nudged towards the viewer so an outline does not fight the block face it sits on |
| Output | `ITEM_ENTITY_TARGET` | The render target vanilla uses for item entities |
| Stage | `AFTER_TRANSLUCENT_BLOCKS` | Drawn after the world, before the frame ends |

The colour is four separate config values, `outline_color.red` / `.green` / `.blue` / `.alpha`, each
0.0–1.0. The default is `0, 1, 1, 1` — opaque cyan. Setting `alpha` to 0 makes the outlines
invisible but changes nothing about the scan, which still runs at full cost every frame.

### Sable sub-levels

With [Sable](https://modrinth.com/mod/sable) installed, the search additionally covers Create
Aeronautics-style sub-levels. A sub-level is a plot of blocks parked elsewhere and drawn at the
ship's real position through a pose, so the plain cubic scan of the main level cannot see it: the
blocks are not where they appear to be.

For each sub-level that intersects the search box, the integration inverse-transforms the box into
that plot's local space, floors and ceils the result into a block range, scans that range in the
sub-level's own level, and transforms each hit's box back into world space before handing it over as
an ordinary candidate. Outlines therefore sit on the moving ship where you see it.

Without Sable, `ModList.get().isLoaded("sable")` is false, nothing of this runs, and blocks on a
sub-level are simply never highlighted — there are no sub-levels without Sable, so nothing is
missing. Note that this second scan has its own cubic cost per intersecting sub-level and is not
bounded by `max_highlights_per_frame` either.

<!-- vpa:config:start -->
## Configuration

Section `[modules.block_glow]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_block_glow-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

| Key | Type | Default | Range | Effect |
|---|---|---|---|---|
| `default_duration_seconds` | int | `60` | 0 ~ 2147483647 (Integer.MAX_VALUE) | Default glow duration in seconds (0 = infinite). |
| `default_radius` | int | `24` | 1 ~ 128 | Default search radius in blocks for /blockglow. |
| `max_highlights_per_frame` | int | `512` | 16 ~ 8192 | Maximum number of block outlines rendered per frame. Caps how many boxes are DRAWN, not how many blocks are scanned. |
| `max_radius` | int | `64` | 1 ~ 256 | Maximum allowed radius in blocks for /blockglow. |
| `outline_color.alpha` | double | `1.0` | 0.0 ~ 1.0 | Alpha component of the outline color. |
| `outline_color.blue` | double | `1.0` | 0.0 ~ 1.0 | Blue component of the outline color. |
| `outline_color.green` | double | `1.0` | 0.0 ~ 1.0 | Green component of the outline color. |
| `outline_color.red` | double | `0.0` | 0.0 ~ 1.0 | Red component of the outline color. |
| `selection_mode` | string | `"nearest"` | free-form string, no validator (define, not defineInList) — only "scan_order" (after trim+lowercase) is honored, every other value silently falls back to "nearest" | Selection mode for which blocks are highlighted: nearest or scan_order. |
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| Large radii | A full cubic scan every frame, no caching. At `max_radius` 64 that is 2.1 million block-state lookups per frame. Treat the radius as a performance setting. |
| `max_highlights_per_frame` | Caps drawing only. It does not reduce the scan and it does not prevent the per-match allocations. |
| `default_radius` above `max_radius` | Legal — the ranges are 1–128 and 1–256 — and then the bare `/blockglow <block>` fails with *Radius cannot exceed \<max\_radius\>*, because the default value goes through the same check as a typed one. |
| `selection_mode` typo | Silently treated as `nearest`. The key is a free-form string with no validator. |
| `minecraft:cave_air`, `minecraft:void_air` | Accepted. Only `minecraft:air` is compared against and filtered out of the suggestions; the other two are distinct blocks and pass both the suggestion filter and the command's air check. |
| Chat messages | Hardcoded English `Component.literal`, not lang keys. The module has no translation entries at all, unlike `mob_glow` whose command output is localisable. |
| Enabling the module at runtime | The command is registered only if the module was enabled at client-command registration time, so `/blockglow` appears after a world rejoin or reconnect. Disabling works immediately: the render and execute paths both re-check. |
| Dedicated server | The module is registered on both sides but has no server behaviour. It logs *Block Glow module initialized* and holds an unused state field. |
| Config location | The settings sit in the common config, which means your own client's file. Nothing is read from the server, and two players on the same server can have entirely different radii and colours. |
| Sable version change | The integration resolves Sable by name through reflection. If a class or method is renamed, the failure is logged once as a WARN and highlighting quietly falls back to the main level only. |
| Standalone jar | `vpa_block_glow` does not declare the optional `sable` dependency — the module-jar generator has no mechanism for optional deps; only the bundle's `neoforge.mods.toml` carries it (`side=CLIENT`). Harmless, because the integration is reflective and gated on `ModList`, but load ordering against Sable is unspecified there. |
| Testing | This repository has no unit tests, and nothing in it records a measured frame-rate figure for this module. The lookup counts above are arithmetic on the loop bounds, not benchmarks. |

## Under the hood

| File | Role |
|---|---|
| `modules/block_glow/BlockGlowModule.java` | The module, the `BlockGlowState` record and its expiry check |
| `modules/block_glow/config/BlockGlowConfig.java` | The nine config values |
| `modules/block_glow/client/BlockGlowClientEvents.java` | Command, render type, scan, selection, drawing — the whole module, effectively |
| `modules/block_glow/client/BlockGlowHighlight.java` | A record: world-space `AABB` plus squared distance |
| `modules/block_glow/client/compat/BlockGlowSableIntegration.java` | The reflective Sable bridge |
| `standalone/block_glow/BlockGlowStandalone.java` | `@Mod("vpa_block_glow")` entry point for the standalone jar |

No mixins, no items, blocks, entities, recipes or keybinds, no network payloads, no `DeferredRegister`
anywhere in the module.

**Two events**, both on the game bus, both inside a class annotated
`@EventBusSubscriber(value = Dist.CLIENT, bus = Bus.GAME)`:

| Event | Purpose |
|---|---|
| `RegisterClientCommandsEvent` | Builds the `/blockglow` tree — gated on `isModuleEnabled()` at registration time |
| `RenderLevelStageEvent` | Every other stage returns immediately; only `AFTER_TRANSLUCENT_BLOCKS` is handled |

**The client split is deliberate.** `BlockGlowModule` itself lives in the common package and is
registered unconditionally, in the bundle and in the standalone jar alike. Everything that touches a
client class sits under `client/`, and the Sable bridge was moved into `client/compat` specifically
because it references `ClientLevel` and must never be loaded on a dedicated server — the class
javadoc says so.

**Three layers of air guard.** The suggestion provider drops every registry key whose block is
`Blocks.AIR`; `executeEnable` rejects an unknown id (*Unknown block type: …*), `Blocks.AIR` (*Cannot
glow air blocks*) and a missing client level (*No active world on client*); and the render path bails
out again if the stored id resolves to `Blocks.AIR`, which is also what the registry hands back for
an id that has since disappeared.

**The Sable reflection surface** is resolved once, behind a double-checked `reflectionInitialized`
flag, and consists of `SubLevelContainer.getContainer/queryIntersecting`, `SubLevel.logicalPose/getLevel`,
`BoundingBox3d`'s two constructors plus `transform`/`transformInverse`, and the `BoundingBox3dc`
interface's `toMojang` and six `minX`…`maxZ` accessors — plus `Pose3dc`, looked up only as the
parameter type of the two transform methods. Nothing in this module compile-depends on Sable,
unlike the other Sable integrations in this mod.

There is one trap the source calls out in a comment: `BoundingBox3d.transformInverse(Pose3dc)`
mutates in place, JOML style. A single query box reused across sub-levels would carry the previous
sub-level's transform into the next one, so a **fresh** box is constructed per iteration. That was a
real bug, fixed in `v0.16.3`.

**Small things, for whoever edits this next.** The module instance is looked up by id through
`ModuleManager` on every event; the four outline-colour config values are read per box rather than
once per frame; and the `Math.min(Long.MAX_VALUE, currentGameTime + (long) durationSeconds * 20L)` in
`setClientState` is a no-op, because the argument is already a `long` — the overflow guard it looks
like is not one.

## See also

* [Mob Glow](mob_glow.md) — the same idea for entities, but server-side and OP-gated
* [Mob Spawn Overlay](mob_spawn_overlay.md) — the other client-side x-ray view in this mod
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [Module System](../guides/module-system.md) — enabling and disabling modules
* [All modules](../../README.md#-modules)
