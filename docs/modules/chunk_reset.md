# Chunk Reset Command

> **TL;DR** — An operator-only command that deletes the chunk you are standing in — or up to an
> 11×11 square around you — so the world generator builds that terrain again from scratch.

<!-- vpa:meta:start -->
|  |  |
|---|---|
| **Module ID** | `chunk_reset` |
| **Side** | Server only |
| **Requires** | — |
| **Works with** | — |
| **Download** | [`vpa_chunk_reset.jar`](https://github.com/GeraldHofbauerWeb/vanillaplusadditions/releases/latest/download/vpa_chunk_reset.jar) · also needs `vpa_core` |
| **Config section** | `[modules.chunk_reset]` |
| **Since** | `v1.0.0-beta.2` |
<!-- vpa:meta:end -->

## What it does

Stand where the terrain should go away and type `/chunkreset`. Nothing is deleted yet: the command
prints a warning with the chunk coordinates and two clickable buttons. Only a radius greater than 0
gets the chunk count and the `s×s` side length as well — this is what `/chunkreset 1` prints:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠ Chunk Reset Warning
9 chunks (3×3) around [x=12, z=-4] will be PERMANENTLY deleted and regenerated.
All blocks, entities and structures will be LOST!
[✓ Confirm Reset]  [✗ Cancel]
```

Plain `/chunkreset` is the same block with a singular middle line: `Chunk [x=12, z=-4] will be
PERMANENTLY deleted and regenerated.`

Click `[✓ Confirm Reset]` — or type `/chunkreset confirm`, the buttons only run that same command —
and the chunks' entries are cleared from the region file. The next time something loads one of those
positions, the generator builds it again from the current seed and the current datapacks. That is
what the command is for: picking up a worldgen change on ground somebody has already explored,
resetting a test area, or handing a builder's plot back to vanilla terrain.

`/chunkreset <radius>` takes a square around you instead of the single chunk, up to radius 5.

Two things to know before using it. There is no undo — the old region data is gone, and what comes
back is whatever the generator produces today. And the deletion does not always take: a chunk that is
still in memory with unsaved changes is written straight back into the entry that was just cleared.
Both are spelled out below.

## In detail

### The commands

All four need **permission level 4**. The requirement sits on the root literal, so it covers
`confirm` and `cancel` as well.

| Command | Effect |
|---|---|
| `/chunkreset` | Queue the caller's own chunk and print the confirmation prompt |
| `/chunkreset <radius>` | Queue a square of radius 0–5 around the caller |
| `/chunkreset confirm` | Carry out the caller's pending reset |
| `/chunkreset cancel` | Drop the caller's pending reset |

All three handler methods begin with `source.getPlayerOrException()`, so the command needs a real player
behind it: the console, a command block and a `/execute as` with a non-player source all fail with
vanilla's "player required" error. The radius argument is `IntegerArgumentType.integer(0, 5)`, so
brigadier rejects anything outside that range before the module sees it. `MAX_RADIUS` is a private
constant; the ceiling cannot be raised from a config file.

| `radius` | Chunks | Area |
|---|---|---|
| `0` | 1 | 16 × 16 blocks |
| `1` | 9 (3×3) | 48 × 48 |
| `2` | 25 (5×5) | 80 × 80 |
| `3` | 49 (7×7) | 112 × 112 |
| `4` | 81 (9×9) | 144 × 144 |
| `5` | 121 (11×11) | 176 × 176 |

The prompt goes to the caller only (`sendSuccess(…, false)`); the final result is sent with
`allowLogging = true`, so it is also broadcast to the other operators and written to the server log
as an admin command, subject to the usual `sendCommandFeedback` and `logAdminCommands` gamerules.

### What the prompt remembers, and what it does not

A pending reset is one entry in a plain `HashMap<UUID, PendingReset>`, and the record holds two
fields:

```java
private record PendingReset(ChunkPos center, int radius) { }
```

That has consequences worth knowing before the first click:

* **The dimension is not part of it.** `executeConfirm` applies those coordinates to
  `source.getLevel()` at the moment of confirming. Run `/chunkreset` in the Nether, walk through a
  portal and click the button, and the *Overworld* chunks at the same `ChunkPos` are the ones that
  get deleted.
* **A pending reset never expires.** It is dropped only by a confirm, a cancel or a server restart —
  not by logging out. Clicking a confirm button hours later still works.
* **Only the newest one exists.** A second `/chunkreset` overwrites the first through `Map.put`; the
  earlier square can no longer be confirmed, and nothing says so.

`cancel` answers either way: "Chunk reset cancelled." when there was something to drop, "No pending
chunk reset to cancel." when there was not.

### What a reset actually does

For every chunk in the square, `resetSingleChunk` does exactly two things:

1. `level.setChunkForced(pos.x, pos.z, false)` — removes the position from `ForcedChunksSavedData`
   and tells the chunk cache about it.
2. Calls `ChunkStorage.write(ChunkPos, CompoundTag)` on the level's `ChunkMap` with a **null** tag.

`ChunkMap` extends `ChunkStorage` and does not override `write`, so the call ends up on the region
storage for that dimension's `region/` folder, by way of the same `IOWorker` the game itself saves
through. A null tag is vanilla's own delete instruction:

```java
// RegionFileStorage.write
protected void write(ChunkPos chunkPos, @Nullable CompoundTag chunkData) throws IOException {
    RegionFile regionfile = this.getRegionFile(chunkPos);
    if (chunkData == null) {
        regionfile.clear(chunkPos);
    } else {
        …
    }
}
```

The write is asynchronous. `ChunkStorage.write` returns a `CompletableFuture` that the module drops
on the floor, so the per-chunk "succeeded" count means *the deletion was queued*, not *the region
file changed*. A failure inside the worker is logged by vanilla and never reaches chat.

Nothing else is touched. No unload is forced, no flush is requested, the chunk in memory is left
exactly as it is.

### Why the deletion can undo itself

The same `write` is what `ChunkMap.save(ChunkAccess)` calls to persist a chunk:

```java
CompoundTag compoundtag = ChunkSerializer.write(this.level, chunk);
…
this.write(chunkpos, compoundtag).exceptionally(…);
```

A loaded chunk is therefore one autosave away from being written back into the entry that was just
cleared — at 20 tps that is at most 6000 ticks, five minutes, and it happens again when the chunk
unloads and when the server shuts down. The one thing that stops it is the guard at the top of
`save`:

```java
if (!chunk.isUnsaved()) {
    return false;
}
```

So the deletion holds for a chunk that has not been modified since its last save, and is silently
reverted for one that has. `LevelChunk.setBlockState` sets that flag, so does a light-section change
and so does a freshly generated chunk. A crop growing, a leaf decaying or a single block placed
between the reset and the unload is enough.

The command's own wording — "It will regenerate when next loaded" — therefore assumes the chunk
really leaves memory without being written again, and the module does nothing to bring that about.
This is read off vanilla's 1.21.1 sources; it has not been reproduced in game and nothing in this
repository tests it.

### What survives a reset

| Data | Folder | Cleared? |
|---|---|---|
| Blocks, block entities, heightmaps, structure starts | `region/` | Yes — that is the entry the null write clears |
| Persistent entities (mobs, item frames, armour stands, dropped items) | `entities/` | **No** — `ServerLevel` keeps them in a separate `EntityStorage`, which the module never touches |
| Points of interest (beds, workstations, portals) | `poi/` | **No** — same, a separate `SectionStorage` |

Entities have lived in their own region files since 1.17 and are loaded per `ChunkPos` independently
of the chunk data, so the mobs that were standing in a reset chunk come back with the regenerated
terrain — on top of whatever the generator has since put there. The chat warning saying "All blocks,
entities and structures will be LOST!" is accurate for blocks and wrong for entities. Again: deduced
from the source, not reproduced in game.

### Chunks that stay loaded anyway

Step 1 above removes a **vanilla `/forceload` ticket and nothing else**. `ServerLevel.setChunkForced`
only touches vanilla's forceload bookkeeping: it drops the position from `ForcedChunksSavedData` and,
when the position really was in there, calls `ServerChunkCache.updateChunkForced`, which removes the
matching `FORCED` ticket from the `DistanceManager`. Nothing beyond that. Every other reason a chunk
might be loaded survives: NeoForge chunk tickets, the spawn chunks, a nearby player, a Create train —
and this mod's own [Stationary Chunk Loader](stationary_chunk_loader.md), [Minecart Chunk
Loading](minecart_chunk_loading.md) and [Train Chunk Loading](train_chunk_loading.md). A chunk held
by one of those never unloads, which puts it squarely in the rewrite case above.

### Getting out of the way

Before deleting anything, every player of that level whose `chunkPosition()` is inside the square is
teleported and told so ("⚠ You were moved out of a chunk being reset."). The destination is one
chunk beyond the **+Z** edge of the square:

```java
double safeX = center.getMiddleBlockX();
double safeZ = ((center.z + radius) * 16.0) + 17.0;
…
nearby.teleportTo(safeX, nearby.getY(), safeZ);
```

Note what that is not. The Y coordinate is kept, and there is no ground search, no collision check
and no dimension change — `ServerPlayer.teleportTo(double, double, double)` is a plain connection
teleport that keeps the rotation. A player at y = 12 in a cave lands at y = 12 one chunk to the
south, which may be solid stone or open air. Nothing stops them from walking straight back in
either; the chunks are still loaded.

Only entries of `level.players()` are considered. Players in other dimensions are irrelevant, and
mobs, item frames and dropped items are not moved at all — although, per the table above, the
persistent ones among them are not actually deleted.

<!-- vpa:config:start -->
## Configuration

Section `[modules.chunk_reset]` in `config/vanillaplusadditions-common.toml` (or `config/vpa_chunk_reset-common.toml` if you run the standalone jar).

Every module also has the universal `enabled` and `debug_logging` keys — see the [Configuration Guide](../guides/configuration.md).

This module has no settings of its own.
<!-- vpa:config:end -->

## Compatibility and known limits

| Limit | Effect |
|---|---|
| A still-loaded chunk with unsaved changes | Written back over the deletion by the next autosave, by the unload save or at shutdown. The module neither unloads the chunks nor flushes anything. |
| Confirming in a different dimension | The pending reset stores only `ChunkPos` and radius. The confirm deletes those coordinates in whichever level you are standing in at the time. |
| Entities and POIs | Not deleted — they live in `entities/` and `poi/`, which the module never touches, and the chat warning claims otherwise. |
| Mod chunk loaders, spawn chunks, nearby players | Untouched: only a vanilla `/forceload` ticket is removed. Such a chunk cannot unload, so the deletion cannot stick. |
| `radius 0` always reports success | The single-chunk branch prints the green "✓ Chunk … reset." unconditionally; only the radius > 0 branch prints `succeeded/total`. A chunk whose write threw shows up in the log and nowhere else. And even the `n/total` line keeps the green ✓ on a partial failure. |
| The teleport is not a safe-landing search | Same Y, no collision check. A player can be put inside terrain or dropped from mid-air, and can walk back in immediately. |
| A second `/chunkreset` before confirming | Silently replaces the pending square; the older one is unreachable. |
| Pending resets never expire | Held in memory per player UUID until confirm, cancel or restart — logging out does not clear one. |
| Console and command blocks | Rejected. All three handlers call `getPlayerOrException()`. |
| Command name | Plain `chunkreset`, no mod namespace, so it can collide with another chunk-management mod's command. |
| No translations | Every message is a hardcoded English `Component.literal`; the module owns no lang keys at all. |
| Disabled in the config at startup (bundle) | The module is never initialised, never subscribes, and `/vpa module enable chunk_reset` cannot bring the command back — the subscription does not exist. Restart with `enabled = true`. |
| Disabled at runtime (bundle or standalone jar) | `isModuleEnabled()` is only consulted while commands are being registered, so the already-registered `/chunkreset` keeps working until the next command re-registration. |
| Not verified in game | The module's logic has not changed since `v1.0.0-beta.2` (the standalone entry point followed in `v1.0.0-beta.23`) and this repository has no tests. Every statement on this page is read off the module and off vanilla's decompiled 1.21.1 sources. |

## Under the hood

One implementation file of 234 lines, `ChunkResetModule.java`, plus the standalone entry point. No
mixins, no assets, no data files, no registries, no packets, no config class of its own.

| Event | Bus | Purpose |
|---|---|---|
| `RegisterCommandsEvent` | game | Builds the whole command tree; returns early unless `isModuleEnabled()` |

| Class | Role |
|---|---|
| `modules/chunk_reset/ChunkResetModule` | Everything: the command tree, the pending map, the teleport, the deletion |
| `standalone/chunk_reset/ChunkResetStandalone` | `@Mod("vpa_chunk_reset")`, boots the same module through `StandaloneModuleBootstrap` |

**The enable gate behaves differently in the two jars.** `onInitialize` subscribes to
`NeoForge.EVENT_BUS` unconditionally and logs `Chunk Reset module initialized - /chunkreset command
ready!`; the only `isModuleEnabled()` check is inside `onRegisterCommands`. In the bundle,
`ModuleManager.initializeModules` filters by the config *before* calling `initialize`, so a module
switched off at startup never subscribes and cannot be revived by a runtime override. The standalone
jar's `StandaloneModuleBootstrap.boot` initialises its one module unconditionally, so there the
`isModuleEnabled()` check really is the only gate and both directions work from the next command
re-registration.

**Why the write goes through reflection.** `resolveChunkStorageWriteMethod` walks
`ChunkStorage.class.getDeclaredMethods()` and takes the first method whose parameters are exactly
`(ChunkPos, CompoundTag)`, marks it accessible and caches it in a `static volatile Method`:

```java
for (Method m : ChunkStorage.class.getDeclaredMethods()) {
    Class<?>[] params = m.getParameterTypes();
    if (params.length == 2 && params[0] == ChunkPos.class && params[1] == CompoundTag.class) {
        m.setAccessible(true);
        chunkStorageWriteMethod = m;
        return m;
    }
}
```

The comment in the code gives the reason: the method name is not hardcoded, so a mapping difference
does not break the call. In 1.21.1 `write(ChunkPos, CompoundTag)` is the only declared method with
that shape, which makes "first match" deterministic there. The one field hop on the way —
`ServerChunkCache.chunkMap` — needs no accessor, because the field is `public final` and `ChunkMap`
is a public subclass of `ChunkStorage`; there is no Mixin dependency anywhere in this module. The
cache is written without synchronisation, which is harmless: two threads would resolve the same
`Method`. If no such method is found, the `NoSuchMethodException` is caught per chunk in
`resetSingleChunk`, logged, and counted as a failure — see the `radius 0` row above for what that
does *not* show in chat.

**A dead branch.** `executeConfirm` guards with
`if (!(source.getLevel() instanceof ServerLevel serverLevel))`, whose failure message reads "This
command can only be used in a server level." It can never fire: in 1.21.1 `CommandSourceStack` is
declared `public ServerLevel getLevel()`.

## See also

* [Stationary Chunk Loader](stationary_chunk_loader.md) — one of the things that keeps a reset chunk
  loaded, and so alive
* [Minecart Chunk Loading](minecart_chunk_loading.md), [Train Chunk Loading](train_chunk_loading.md)
  — the same, on rails
* [Configuration Guide](../guides/configuration.md) — where the config file lives
* [All modules](../../README.md#-modules)
